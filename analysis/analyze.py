import pandas as pd, numpy as np
from scipy.stats import wilcoxon
d=pd.read_csv('results/results.csv')
d=d[d['round']>1]
g=d.groupby(['N','beta','eps','gamma','seed','policy']).sum(numeric_only=True).reset_index()
g['served']=(g.servedGood+g.servedBad)/(g.nGood+g.nBad)*100
g['servedGood']=g.servedGood/g.nGood*100
g['servedBad']=g.servedBad/g.nBad*100
g['utilGoodU']=g.utilGood/g.nGood*1000
g['utilBadU']=g.utilBad/g.nBad*1000
g['badShare']=g.unitsBad/(g.unitsGood+g.unitsBad)*100
g['incRate']=g.incidents/(g.unitsGood+g.unitsBad)*100
g['PS']=g.psSum/g.activeProv
g['welfare']=g.utilGood+g.utilBad+g.provSurplus
g['userUtil']=g.utilGood+g.utilBad
mets=['served','servedGood','servedBad','utilGoodU','utilBadU','badShare','incRate','PS','revenue','userUtil','provSurplus','welfare']
rows=[]
for key,sub in g.groupby(['N','beta','eps','gamma']):
    a=sub[sub.policy=='CDARA'].sort_values('seed'); b=sub[sub.policy=='UR-CDARA'].sort_values('seed')
    for m in mets:
        x=a[m].values; y=b[m].values
        try: p=wilcoxon(x,y).pvalue
        except Exception: p=np.nan
        rows.append(dict(N=key[0],beta=key[1],eps=key[2],gamma=key[3],metric=m,CDARA=x.mean(),CDARA_sd=x.std(ddof=1),UR=y.mean(),UR_sd=y.std(ddof=1),diff=(y-x).mean(),p=p))
R=pd.DataFrame(rows); R.to_csv('results/summary.csv',index=False)
pd.set_option('display.width',200); pd.set_option('display.max_rows',300)
print(R.round(4).to_string())
