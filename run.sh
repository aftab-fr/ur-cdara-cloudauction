#!/usr/bin/env bash
# Reproduce all results of the UR-CDARA paper. Run from the repository root.
set -e
mkdir -p out results figures
echo "Compiling..."
javac -nowarn -d out $(find sources -name '*.java') examples/org/cloudbus/cloudsim/examples/auction/AuctionExample5/*.java
echo "Main experiment (30 seeds)..."
java -cp out org.cloudbus.cloudsim.auction.auctioneer.URCDARAExperiment results/results.csv 30
echo "Whitewashing test (30 seeds)..."
java -cp out org.cloudbus.cloudsim.auction.auctioneer.URCDARAExperiment results/whitewash.csv 30 whitewash
echo "Analysis..."
python3 analysis/analyze.py > /dev/null
python3 analysis/ww.py > /dev/null
python3 analysis/figs.py > /dev/null
echo "Done. See results/ and figures/."
