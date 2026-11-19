# UR-CDARA: User Rank-Based Combinatorial Double Auction for Cloud Resource Allocation

Code, raw results and analysis scripts for the paper:

> N. Muhammad, A. Umer, M. A. Uddin, J. Gaber. *User Rank-Based Combinatorial Double Auction Resource Allocation Model for Cloud Computing.* (under review)

UR-CDARA extends the CDARA combinatorial double auction. After each auction round, providers rate the users they served on five criteria. The auctioneer turns the ratings into a feedback score `FB_i` and a rank factor `R_i = (FB_i / 5)^gamma`, and sorts user bids by `R_i * BDU_i` in later rounds. The pricing rule (mid-price) is not changed.

## What is in this repository

| Folder | Content |
|---|---|
| `sources/` | CloudSim 2.1.1 with the CloudAuction module, plus the UR-CDARA experiment driver `sources/org/cloudbus/cloudsim/auction/auctioneer/URCDARAExperiment.java` |
| `examples/` | Original CloudAuction example scenario (`AuctionExample5`) |
| `results/` | Raw results (`results.csv`, `whitewash.csv`) and summaries with paired Wilcoxon tests |
| `analysis/` | Python scripts that produce the summaries and figures |
| `figures/` | Figures 2 to 5 of the paper |
| `third_party/` | Original CloudSim readme, changelog and release notes |

## Requirements

- Java 8 or newer (JDK, for `javac`)
- Python 3 with `pandas`, `numpy`, `scipy`, `matplotlib` (only for the analysis)

## Reproduce the results

Run everything from the repository root:

```bash
./run.sh
```

Or step by step:

```bash
# 1. Compile
mkdir -p out
javac -nowarn -d out $(find sources -name '*.java') examples/org/cloudbus/cloudsim/examples/auction/AuctionExample5/*.java

# 2. Main experiment (Sections 5.3.1 to 5.3.4): 30 seeds
java -cp out org.cloudbus.cloudsim.auction.auctioneer.URCDARAExperiment results/results.csv 30

# 3. Whitewashing test (Section 5.3.5, Table 5)
java -cp out org.cloudbus.cloudsim.auction.auctioneer.URCDARAExperiment results/whitewash.csv 30 whitewash

# 4. Statistics and figures
python3 analysis/analyze.py   # results/summary.csv
python3 analysis/ww.py        # results/whitewash_summary.csv
python3 analysis/figs.py      # figures/fig2-5
```

All random numbers are seeded, so the runs are deterministic and give the same CSV files.

## Experiment design (short)

- Market sizes: N = 20, 100, 500 users; M = ceil(0.35 N) providers
- 10 rounds per run; round 1 always uses CDARA (no feedback yet); 30 seeds
- Both policies (CDARA and UR-CDARA) receive the same bids in each seed (common random numbers)
- Behaviour model: a share beta of users misbehave (default 0.3); providers rate well-behaved users 4 to 5 and misbehaving users 1 to 3; each score is replaced by a random value with probability eps (default 0.1)
- Sensitivity: beta in {0.1, 0.3, 0.5}, eps in {0, 0.1, 0.3}, gamma in {1, 2, 4}
- Whitewashing: misbehaving users re-register every 1 or 3 rounds; newcomer score FB0 in {3, 2.5, 2}

## Note on the original example code

`AuctionExample5` and `Auctioneer.java` are kept unchanged for reference. In that code the provider ratings are random and are not used in the allocation, and fixed Q1 to Q4 values are added to the trade price. The UR-CDARA experiment driver does not use the Q1 to Q4 terms and implements the ranking described in the paper.

## Credits and licence

This repository is based on **CloudSim 2.1.1** (CLOUDS Lab, The University of Melbourne) and the **CloudAuction** module by Youness Teimoury, both distributed under the GNU GPL. Original author notices are kept in the source files. See `NOTICE.md`.

This repository is released under the **GNU General Public License v3.0** (see `LICENSE`).

## How to cite

See `CITATION.cff`. Please cite the paper and the archived version of this code (Zenodo DOI to be added).

## Contact

MD Aftab Uddin, md.uddin@utbm.fr
