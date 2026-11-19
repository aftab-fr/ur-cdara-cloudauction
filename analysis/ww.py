"""Summarise the whitewashing test (Table 5). Run from the repository root:
    python3 analysis/ww.py
Reads results/whitewash.csv, writes results/whitewash_summary.csv."""
import pandas as pd, numpy as np
from scipy.stats import wilcoxon
d = pd.read_csv('results/whitewash.csv'); d = d[d['round'] > 1]
g = d.groupby(['k', 'f0', 'seed', 'policy']).sum(numeric_only=True).reset_index()
g['sg'] = g.servedGood / g.nGood * 100; g['sb'] = g.servedBad / g.nBad * 100
g['ug'] = g.utilGood / g.nGood * 1000; g['ub'] = g.utilBad / g.nBad * 1000
g['inc'] = g.incidents / (g.unitsGood + g.unitsBad) * 100
g['wel'] = g.utilGood + g.utilBad + g.provSurplus
rows = []
for (k, f0), s in g.groupby(['k', 'f0']):
    a = s[s.policy == 'CDARA'].sort_values('seed'); b = s[s.policy == 'UR-CDARA'].sort_values('seed')
    r = dict(k=k, f0=f0)
    for m in ['sg', 'sb', 'inc']:
        dd = b[m].values - a[m].values; r['d_' + m] = dd.mean(); r['p_' + m] = wilcoxon(a[m], b[m]).pvalue if np.any(dd) else 1
    for m in ['ug', 'ub', 'wel']:
        dd = (b[m].values - a[m].values) / a[m].values * 100; r['d_' + m + '%'] = dd.mean(); r['p_' + m] = wilcoxon(a[m], b[m]).pvalue
    rows.append(r)
R = pd.DataFrame(rows); R.to_csv('results/whitewash_summary.csv', index=False)
print(R.round(3).to_string())
