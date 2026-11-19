import pandas as pd, numpy as np, matplotlib
matplotlib.use('Agg'); import matplotlib.pyplot as plt
plt.rcParams.update({'font.family':'serif','font.serif':['DejaVu Serif'],'font.size':9,'axes.spines.top':False,'axes.spines.right':False})
C1,C2='#888888','#1f5fa8'
raw=pd.read_csv('results/results.csv')
d=raw[raw['round']>1]
g=d.groupby(['N','beta','eps','gamma','seed','policy']).sum(numeric_only=True).reset_index()
g['sg']=g.servedGood/g.nGood*100; g['sb']=g.servedBad/g.nBad*100
g['ug']=g.utilGood/g.nGood*1000; g['ub']=g.utilBad/g.nBad*1000
g['inc']=g.incidents/(g.unitsGood+g.unitsBad)*100; g['PS']=g.psSum/g.activeProv
g['welfare']=g.utilGood+g.utilBad+g.provSurplus
base=g[(g.beta==0.3)&(g.eps==0.1)&(g.gamma==1)]
def ms(sub,col): return sub[col].mean(), sub[col].std(ddof=1)
# Fig 2 served rate
fig,axs=plt.subplots(1,2,figsize=(6.3,2.5),sharey=True)
for ax,(col,title) in zip(axs,[('sg','Well-behaved users'),('sb','Misbehaving users')]):
    x=np.arange(3); w=0.36
    for k,(pol,c) in enumerate([('CDARA',C1),('UR-CDARA',C2)]):
        vals=[ms(base[(base.N==n)&(base.policy==pol)],col) for n in [20,100,500]]
        ax.bar(x+(k-0.5)*w,[v[0] for v in vals],w,yerr=[v[1] for v in vals],color=c,capsize=2,label=pol)
    ax.set_xticks(x); ax.set_xticklabels(['N=20','N=100','N=500']); ax.set_title(title,fontsize=9); ax.set_ylim(40,68)
axs[0].set_ylabel('Users served (%)'); axs[0].legend(frameon=False,fontsize=8,loc='upper left')
fig.tight_layout(); fig.savefig('figures/fig2_served.png',dpi=300); plt.close()
# Fig 3 utility
fig,axs=plt.subplots(1,2,figsize=(6.3,2.5),sharey=True)
for ax,(col,title) in zip(axs,[('ug','Well-behaved users'),('ub','Misbehaving users')]):
    x=np.arange(3); w=0.36
    for k,(pol,c) in enumerate([('CDARA',C1),('UR-CDARA',C2)]):
        vals=[ms(base[(base.N==n)&(base.policy==pol)],col) for n in [20,100,500]]
        ax.bar(x+(k-0.5)*w,[v[0] for v in vals],w,yerr=[v[1] for v in vals],color=c,capsize=2,label=pol)
    ax.set_xticks(x); ax.set_xticklabels(['N=20','N=100','N=500']); ax.set_title(title,fontsize=9)
axs[0].set_ylabel(r'Utility per user ($\times10^{-3}$)'); axs[0].set_ylim(0,5.3); axs[0].legend(frameon=False,fontsize=8,loc='upper left',ncol=2)
fig.tight_layout(); fig.savefig('figures/fig3_utility.png',dpi=300); plt.close()
# Fig 4 per-round share of units to misbehaving users (N=100)
r=raw[(raw.N==100)&(raw.beta==0.3)&(raw.eps==0.1)&(raw.gamma==1)].copy()
r['bs']=r.unitsBad/(r.unitsGood+r.unitsBad)*100
fig,ax=plt.subplots(figsize=(4.2,2.6))
for pol,c in [('CDARA',C1),('UR-CDARA',C2)]:
    s=r[r.policy==pol].groupby('round').bs; m=s.mean(); sd=s.std(ddof=1); se=sd/np.sqrt(30)
    ax.plot(m.index,m.values,'-o',ms=3,color=c,label=pol); ax.fill_between(m.index,m-1.96*se,m+1.96*se,color=c,alpha=0.2)
ax.set_xlabel('Auction round'); ax.set_ylabel('Units to misbehaving users (%)'); ax.legend(frameon=False,fontsize=8); ax.set_xticks(range(1,11))
fig.tight_layout(); fig.savefig('figures/fig4_rounds.png',dpi=300); plt.close()
# Fig 5 gamma trade-off (N=100)
gg=g[(g.N==100)&(g.beta==0.3)&(g.eps==0.1)]
a=gg[gg.policy=='CDARA'].set_index('seed')
fig,axs=plt.subplots(1,2,figsize=(6.3,2.8))
gam=[1,2,4]; dg=[];db=[];dw=[];di=[]
for gm in gam:
    b=gg[(gg.policy=='UR-CDARA')&(gg.gamma==gm)].set_index('seed')
    a0=gg[(gg.policy=='CDARA')&(gg.gamma==gm)].set_index('seed')
    dg.append(((b.sg-a0.sg).mean(),(b.sg-a0.sg).std(ddof=1)))
    db.append(((b.sb-a0.sb).mean(),(b.sb-a0.sb).std(ddof=1)))
    dw.append((((b.welfare-a0.welfare)/a0.welfare*100).mean(),((b.welfare-a0.welfare)/a0.welfare*100).std(ddof=1)))
    di.append(((b.inc-a0.inc).mean(),(b.inc-a0.inc).std(ddof=1)))
x=np.arange(3); w=0.36
axs[0].bar(x-w/2,[v[0] for v in dg],w,yerr=[v[1] for v in dg],color=C2,capsize=2,label='Well-behaved')
axs[0].bar(x+w/2,[v[0] for v in db],w,yerr=[v[1] for v in db],color='#d08c2c',capsize=2,label='Misbehaving')
axs[0].axhline(0,color='k',lw=0.6); axs[0].set_xticks(x); axs[0].set_xticklabels([f'γ={v}' for v in gam]); axs[0].set_ylabel('Change in users served (pp)'); axs[0].legend(frameon=False,fontsize=7.5,loc='lower center',bbox_to_anchor=(0.5,1.0),ncol=2)
axs[1].bar(x-w/2,[v[0] for v in di],w,yerr=[v[1] for v in di],color='#5a9e6f',capsize=2,label='Incident rate (pp)')
axs[1].bar(x+w/2,[v[0] for v in dw],w,yerr=[v[1] for v in dw],color='#b04a4a',capsize=2,label='Social welfare (%)')
axs[1].axhline(0,color='k',lw=0.6); axs[1].set_xticks(x); axs[1].set_xticklabels([f'γ={v}' for v in gam]); axs[1].set_ylabel('Change vs CDARA'); axs[1].legend(frameon=False,fontsize=7.5,loc='lower center',bbox_to_anchor=(0.5,1.0),ncol=2)
fig.tight_layout(); fig.savefig('figures/fig5_gamma.png',dpi=300); plt.close()
print(dw, di)
