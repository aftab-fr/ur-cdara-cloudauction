package org.cloudbus.cloudsim.auction.auctioneer;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.auction.bid.DatacenterBid;
import org.cloudbus.cloudsim.auction.bid.DatacenterBrokerBid;
import org.cloudbus.cloudsim.auction.vm.ClonedVm;
import org.cloudbus.cloudsim.auction.vm.DatacenterAbstractVm;
import org.cloudbus.cloudsim.auction.vm.VmCharacteristics;
import org.cloudbus.cloudsim.auction.vm.VmUtils;

/**
 * Multi-round experiment for UR-CDARA.
 * Reuses the CloudAuction bid classes (BidCalculation, bid density "norm", matching rule,
 * mid-price rule) of the CDARA implementation and adds the provider-feedback rank of users.
 * Policy CDARA: users ordered by BDU (Eq. 3). Policy URCDARA: users ordered by R_i * BDU (Eq. 14).
 */
public class URCDARAExperiment {

    /** Feedback score given to users with no rating history (Eq. 12, default 3). */
    static double NEWCOMER = 3.0;

    // VM types taken from the CloudAuction example factories: mips, ram, maxBw, maxSize, costPerSec range
    static final double[] T_MIPS = {250, 400, 500, 1000};
    static final int[]    T_RAM  = {256, 512, 1024, 2048};
    static final long[]   T_BW   = {400, 400, 600, 1000};
    static final long[]   T_SIZE = {10000, 16000, 20000, 40000};
    static final double[] T_CPS_LO = {2.8, 2.4, 2.4, 1.9};
    static final double[] T_CPS_HI = {3.0, 2.9, 2.6, 2.0};

    static final VmCharacteristics vc(double cps) {
        return new VmCharacteristics("x86", "Linux", "Xen", 10.0, cps, 0.03, 0.02, 0.1);
    }

    static class User {
        int id; boolean bad; List<Double> fbHistory = new ArrayList<>();
        double fb() { if (fbHistory.isEmpty()) return NEWCOMER; double s = 0; for (double x : fbHistory) s += x; return s / fbHistory.size(); }
    }

    static class Assign { BidCalculation dc; Vm offered; Vm requested; int amount; double unitPrice; }

    static class RoundResult {
        int servedGood, servedBad, nGood, nBad, unitsGood, unitsBad, incidents, activeProviders;
        double utilGood, utilBad, revenue, providerSurplus, psSum;
    }

    // ---------------- one auction round ----------------
    static RoundResult runRound(List<User> users, int nProv, Random bidRnd, Random fbRnd, boolean useRank, double gamma,
                                double eps, double pIncBad, double pIncGood) {
        int idBase = 1000;
        // providers
        List<BidCalculation> dcCalcs = new ArrayList<>();
        Map<BidCalculation, Integer> dcIndex = new HashMap<>();
        for (int j = 0; j < nProv; j++) {
            DatacenterBid b = new DatacenterBid(idBase + j);
            int nTypes = 2 + bidRnd.nextInt(3);
            List<Integer> types = new ArrayList<>(Arrays.asList(0, 1, 2, 3));
            Collections.shuffle(types, bidRnd);
            for (int t = 0; t < nTypes; t++) {
                int k = types.get(t);
                double cps = T_CPS_LO[k] + bidRnd.nextDouble() * (T_CPS_HI[k] - T_CPS_LO[k]);
                long bw = (long) (T_BW[k] * (0.5 + 0.5 * bidRnd.nextDouble()));
                long size = (long) (T_SIZE[k] * (0.4 + 0.6 * bidRnd.nextDouble()));
                b.addVM(new DatacenterAbstractVm(0, 0, 0, 0, T_MIPS[k], 1, T_RAM[k], bw, size, vc(cps)), 1 + bidRnd.nextInt(6));
            }
            BidCalculation c = new BidCalculation(b);
            dcCalcs.add(c); dcIndex.put(c, j);
        }
        // users
        List<BidCalculation> brCalcs = new ArrayList<>();
        Map<BidCalculation, User> brUser = new HashMap<>();
        Map<BidCalculation, Double> brPrice = new HashMap<>();
        for (User u : users) {
            int nTypes = 1 + bidRnd.nextInt(3);
            List<Integer> types = new ArrayList<>(Arrays.asList(0, 1, 2, 3));
            Collections.shuffle(types, bidRnd);
            double value = 0; int qtyTot = 0;
            List<Object[]> vms = new ArrayList<>();
            for (int t = 0; t < nTypes; t++) {
                int k = types.get(t);
                int qty = 1 + bidRnd.nextInt(3);
                double mips = T_MIPS[k] * (0.85 + 0.15 * bidRnd.nextDouble());
                long bw = (long) (T_BW[k] * (0.25 + 0.25 * bidRnd.nextDouble()));
                long size = (long) (T_SIZE[k] * (0.1 + 0.3 * bidRnd.nextDouble()));
                Vm vm = new Vm(false, 0, 0, 0, 0, ClonedVm.getID(), u.id, mips, 1, T_RAM[k], bw, size, "Xen", new CloudletSchedulerTimeShared());
                double refCost = (T_CPS_LO[k] + T_CPS_HI[k]) / 2 / T_MIPS[k];
                value += refCost * (0.6 + 1.0 * bidRnd.nextDouble()) * qty;
                qtyTot += qty;
                vms.add(new Object[]{vm, qty});
            }
            long duration = 10 + bidRnd.nextInt(51);
            DatacenterBrokerBid bb = new DatacenterBrokerBid(u.id, value, duration);
            for (Object[] o : vms) bb.addVM((Vm) o[0], (Integer) o[1]);
            BidCalculation c = new BidCalculation(bb);
            brCalcs.add(c); brUser.put(c, u); brPrice.put(c, value);
        }
        // ordering: providers ascending BDP, users descending BDU (or R*BDU)
        dcCalcs.sort((a, b) -> Double.compare(a.getNorm(), b.getNorm()));
        Map<BidCalculation, Double> key = new HashMap<>();
        for (BidCalculation c : brCalcs) {
            double r = useRank ? Math.pow(brUser.get(c).fb() / 5.0, gamma) : 1.0;
            key.put(c, c.getNorm() * r);
        }
        brCalcs.sort((a, b) -> Double.compare(key.get(b), key.get(a)));

        Map<BidCalculation, List<Assign>> alloc = new HashMap<>();
        for (BidCalculation br : brCalcs) {
            List<Assign> mine = new ArrayList<>();
            List<Vm> req = br.getOrderedResources();
            for (BidCalculation dc : dcCalcs) {
                if (br.getAverageBid() < dc.getAverageBid()) continue;
                double unit = (br.getAverageBid() + dc.getAverageBid()) / 2.0;
                List<Vm> off = dc.getOrderedResources();
                for (Vm rv : req) {
                    for (Vm ov : off) {
                        int ra = br.getRemainingQuantity(rv);
                        if (ra == 0) break;
                        int oa = dc.getRemainingQuantity(ov);
                        if (oa == 0) continue;
                        if (VmUtils.canServe(ov, rv)) {
                            int a = Math.min(ra, oa);
                            br.assignAllocation(rv, a, unit * a);
                            dc.assignAllocation(ov, a, unit * a);
                            Assign as = new Assign(); as.dc = dc; as.offered = ov; as.requested = rv; as.amount = a; as.unitPrice = unit;
                            mine.add(as);
                            break;
                        }
                    }
                }
            }
            if (!br.isSatisfied()) { // all-or-nothing, as in CloudAuction satisfactionHandling
                for (Assign as : mine) {
                    br.releaseAllocation(as.requested, as.amount, as.unitPrice * as.amount);
                    as.dc.releaseAllocation(as.offered, as.amount, as.unitPrice * as.amount);
                }
                mine.clear();
            }
            alloc.put(br, mine);
        }
        // outcomes
        RoundResult rr = new RoundResult();
        double[] provRatingSum = new double[nProv]; int[] provRated = new int[nProv];
        int[] provUnits = new int[nProv]; int[] provOk = new int[nProv];
        for (BidCalculation br : brCalcs) {
            User u = brUser.get(br);
            if (u.bad) rr.nBad++; else rr.nGood++;
            List<Assign> mine = alloc.get(br);
            if (mine.isEmpty()) continue;
            double paid = 0; int units = 0;
            Set<Integer> servedBy = new HashSet<>();
            for (Assign as : mine) {
                paid += as.unitPrice * as.amount; units += as.amount;
                int j = dcIndex.get(as.dc); servedBy.add(j);
                double ask = as.dc.getAverageBid();
                rr.providerSurplus += (as.unitPrice - ask) * as.amount;
                for (int x = 0; x < as.amount; x++) {
                    boolean inc = fbRnd.nextDouble() < (u.bad ? pIncBad : pIncGood);
                    provUnits[j]++; if (inc) rr.incidents++; else provOk[j]++;
                }
            }
            rr.revenue += paid;
            double util = brPrice.get(br) - paid;
            if (u.bad) { rr.servedBad++; rr.utilBad += util; rr.unitsBad += units; }
            else { rr.servedGood++; rr.utilGood += util; rr.unitsGood += units; }
            // each serving provider rates the user on five criteria (1..5, higher = better)
            for (int j : servedBy) {
                double s = 0;
                for (int c = 0; c < 5; c++) {
                    int score;
                    if (fbRnd.nextDouble() < eps) score = 1 + fbRnd.nextInt(5);         // noisy / dishonest rating
                    else score = u.bad ? 1 + fbRnd.nextInt(3) : 4 + fbRnd.nextInt(2);   // bad: 1-3, good: 4-5
                    s += score;
                }
                double avg = s / 5.0;
                u.fbHistory.add(avg);
                provRatingSum[j] += avg; provRated[j]++;
            }
        }
        for (int j = 0; j < nProv; j++) {
            if (provUnits[j] == 0) continue;
            rr.activeProviders++;
            double fbBar = provRatingSum[j] / provRated[j];
            rr.psSum += 0.5 * (fbBar / 5.0 + (double) provOk[j] / provUnits[j]);
        }
        return rr;
    }

    /** Whitewashing test: misbehaving users re-register (lose their history) every k rounds. */
    static void whitewash(String out, int seeds) throws Exception {
        PrintWriter pw = new PrintWriter(new FileWriter(out));
        pw.println("N,beta,eps,gamma,k,f0,seed,policy,round,nGood,nBad,servedGood,servedBad,utilGood,utilBad,unitsGood,unitsBad,incidents,revenue,provSurplus,psSum,activeProv");
        int n = 100, m = 35, rounds = 10; double beta = 0.3, eps = 0.1, gamma = 1;
        int[] ks = {0, 1, 3};
        double[] f0s = {3.0, 2.5, 2.0};
        for (int k : ks) for (double f0 : f0s) {
            if (k == 0 && f0 != 3.0) continue;
            NEWCOMER = f0;
            for (int seed = 1; seed <= seeds; seed++) for (int pol = 0; pol < 2; pol++) {
                boolean useRank = pol == 1;
                Random typeRnd = new Random(seed * 7919L + n);
                List<User> users = new ArrayList<>();
                for (int i = 0; i < n; i++) { User u = new User(); u.id = i + 1; u.bad = typeRnd.nextDouble() < beta; users.add(u); }
                for (int r = 1; r <= rounds; r++) {
                    if (k > 0 && r > 1 && (r - 1) % k == 0) for (User u : users) if (u.bad) u.fbHistory.clear();
                    Random bidRnd = new Random(seed * 1000003L + r * 101L + n);
                    Random fbRnd = new Random(seed * 31337L + r * 17L + n + pol * 7L);
                    RoundResult rr = runRound(users, m, bidRnd, fbRnd, useRank && r > 1, gamma, eps, 0.5, 0.05);
                    pw.printf(Locale.US, "%d,%.2f,%.2f,%.0f,%d,%.1f,%d,%s,%d,%d,%d,%d,%d,%.6f,%.6f,%d,%d,%d,%.6f,%.6f,%.6f,%d%n",
                        n, beta, eps, gamma, k, f0, seed, useRank ? "UR-CDARA" : "CDARA", r, rr.nGood, rr.nBad, rr.servedGood, rr.servedBad,
                        rr.utilGood, rr.utilBad, rr.unitsGood, rr.unitsBad, rr.incidents, rr.revenue, rr.providerSurplus, rr.psSum, rr.activeProviders);
                }
            }
        }
        NEWCOMER = 3.0;
        pw.close();
        System.out.println("done " + out);
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 2 && args[2].equals("whitewash")) { whitewash(args[0], Integer.parseInt(args[1])); return; }
        String out = args.length > 0 ? args[0] : "results.csv";
        int seeds = args.length > 1 ? Integer.parseInt(args[1]) : 30;
        int rounds = 10;
        int[] sizes = {20, 100, 500};
        double[] betas = {0.1, 0.3, 0.5};
        double[] epss = {0.0, 0.1, 0.3};
        PrintWriter pw = new PrintWriter(new FileWriter(out));
        pw.println("N,M,beta,eps,gamma,seed,policy,round,nGood,nBad,servedGood,servedBad,utilGood,utilBad,unitsGood,unitsBad,incidents,revenue,provSurplus,psSum,activeProv");
        List<double[]> configs = new ArrayList<>();
        for (int n : sizes) configs.add(new double[]{n, 0.3, 0.1, 1});
        for (double b : betas) if (b != 0.3) configs.add(new double[]{100, b, 0.1, 1});
        for (double e : epss) if (e != 0.1) configs.add(new double[]{100, 0.3, e, 1});
        for (double g : new double[]{2, 4}) configs.add(new double[]{100, 0.3, 0.1, g});
        for (double[] cfg : configs) {
            int n = (int) cfg[0]; double beta = cfg[1], eps = cfg[2], gamma = cfg[3];
            int m = (int) Math.ceil(n * 7.0 / 20.0);
            for (int seed = 1; seed <= seeds; seed++) {
                for (int pol = 0; pol < 2; pol++) {
                    boolean useRank = pol == 1;
                    Random typeRnd = new Random(seed * 7919L + n);
                    List<User> users = new ArrayList<>();
                    for (int i = 0; i < n; i++) { User u = new User(); u.id = i + 1; u.bad = typeRnd.nextDouble() < beta; users.add(u); }
                    for (int r = 1; r <= rounds; r++) {
                        Random bidRnd = new Random(seed * 1000003L + r * 101L + n);   // same bids for both policies
                        Random fbRnd = new Random(seed * 31337L + r * 17L + n + pol * 7L);
                        boolean rankOn = useRank && r > 1; // round 1 always uses Algorithm 1
                        RoundResult rr = runRound(users, m, bidRnd, fbRnd, rankOn, gamma, eps, 0.5, 0.05);
                        pw.printf(Locale.US, "%d,%d,%.2f,%.2f,%.0f,%d,%s,%d,%d,%d,%d,%d,%.6f,%.6f,%d,%d,%d,%.6f,%.6f,%.6f,%d%n",
                            n, m, beta, eps, gamma, seed, useRank ? "UR-CDARA" : "CDARA", r, rr.nGood, rr.nBad, rr.servedGood, rr.servedBad,
                            rr.utilGood, rr.utilBad, rr.unitsGood, rr.unitsBad, rr.incidents, rr.revenue, rr.providerSurplus, rr.psSum, rr.activeProviders);
                    }
                }
            }
        }
        pw.close();
        System.out.println("done " + out);
    }
}
