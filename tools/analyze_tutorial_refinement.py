"""Curate the frozen v4 experiment. Runtime worlds/reports stay ignored; no result substitution."""
import json
from collections import Counter
from pathlib import Path
from statistics import mean
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt

summary=json.loads(Path('build/tutorial-refined/summary.json').read_text(encoding='utf-8'))
reports=summary['reports']
assert len(reports)==15 and summary['correctnessStatus']=='PASS'
assert all(r['status']=='PASS' and r['repeatRanking']=='PASS' for r in reports)
original,holdout=reports[:10],reports[10:]
out=Path('docs/verification/tutorial-v4'); out.mkdir(parents=True,exist_ok=True)
lines=['# Frozen v4 review experiment: actual results','',
       'The unchanged [original protocol](TUTORIAL_SEARCH_VALIDATION.md) remains **2/10 Top1**.',
       '[Failure attribution](TUTORIAL_SEARCH_ATTRIBUTION.md) was completed before the',
       '[v4 plan](TUTORIAL_SEARCH_REFINEMENT_PLAN.md) was frozen and implemented. No seeds,',
       'weights or safety thresholds were changed after seeing v4 results. Timing-only CPU',
       'instrumentation was then added and the full15 fresh-server experiment repeated.',
       'Base candidate/ranking evidence stayed identical; real-time safety snapshots can',
       'differ between fresh runs even when observed ground heights are unchanged.',
       'Neither protocol certifies',
       'a continuous playable path; v4 additionally requires a real bidirectional +/-4 landing.', '',
       f'**Original10: new Top1 {summary["usefulSeeds"]}/10; Top3-any {summary["originalTop3Useful"]}/10;',
       f'bounded-shift Top1 {summary["originalTop1Shift"]}/10; Top3-any {summary["originalTop3Shift"]}/10.**',
       f'Holdout5: Top1 {summary["holdout"]["top1Useful"]}/5; Top3-any {summary["holdout"]["top3Useful"]}/5;',
       f'bounded-shift Top1 {summary["holdout"]["top1Shift"]}/5; Top3-any {summary["holdout"]["top3Shift"]}/5.',
       f'Quality status: **{summary["status"]}**; useful-majority met: **{summary["majorityMet"]}**.',
       'Keep PR30 Draft. Top3 is a new metric, never a replacement for old Top1.','',
       '## Every seed','',
       '| Group / seed | Center | Top1 / Top3 useful | Top1 / Top3 shift | Top1 F/S adjacent step | Unsafe F/S columns | Top1 causes |',
       '| --- | --- | --- | --- | --- | --- | --- |']

def causes(o):
    result=[]
    if o['fractureAdjacentStep']<2: result.append('NO_FRACTURE_STEP')
    if o['smoothAdjacentStep']>1: result.append('SMOOTH_STILL_STEEP')
    if o['maximumHeightContrast']<4: result.append('LOW_CONTRAST')
    if o['unsafeFractureColumns']: result.append('UNSAFE_FRACTURE')
    if o['unsafeSmoothColumns']: result.append('UNSAFE_SMOOTH')
    if not o['boundedShiftProved']: result.append('NO_FEASIBLE_SHIFT')
    return result or ['USEFUL_LOCAL_CORRIDOR']

for i,r in enumerate(reports):
    o=r['observations'][0]
    lines.append(f'| {"original" if i<10 else "holdout"} {r["seed"]} | {r["centerX"]},{r["centerZ"]} | {int(r["usefulTop1"])}/{int(r["usefulTop3"])} | {int(r["shiftTop1"])}/{int(r["shiftTop3"])} | {o["fractureAdjacentStep"]}/{o["smoothAdjacentStep"]} | {o["unsafeFractureColumns"]}/{o["unsafeSmoothColumns"]} | {", ".join(causes(o))} |')
lines+=['','## Performance and average quality','',
        'CPU is ThreadMXBean current-server-thread CPU over sampling/stage work. computeMs',
        'is elapsed time inside that work (includes descheduling/GC); wall includes tick waits.',
        'Missing CPU support prints -1, never a fabricated estimate. FULL validation and',
        'independent generator cross-checks are excluded from search timing. The initial',
        'measurement has normal JIT/first-use cost. Time is descriptive, not a CI assertion.','',
        'Windows CPU counters have coarse resolution; summing short batch deltas can show',
        'quantization error, including CPU slightly above active elapsed for one run.','',
        '| Seed | Heights | Ticks | CPU ms | Active elapsed ms | Wall ms | Max2-column batch ms | FULL validation requests |',
        '| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |']
for r in reports:
    m=r['defaultSearch']
    lines.append(f'| {r["seed"]} | {m["heightSamples"]} | {m["ticks"]} | {m.get("cpuMs",-1):.1f} | {m["computeMs"]:.1f} | {m["wallMs"]:.1f} | {m["maxBatchMs"]:.1f} | {r["validationFullRequests"]} |')
for label,group in [('original10',original),('holdout5',holdout)]:
    observed=[o for r in group for o in r['observations']]
    counts=Counter(c for o in observed for c in causes(o))
    metrics={k:round(mean(r['defaultSearch'].get(k,-1) for r in group),2) for k in ['heightSamples','ticks','cpuMs','computeMs','wallMs','maxBatchMs']}
    lines+=['',f'{label} mean metrics: `{json.dumps(metrics,sort_keys=True)}`.',
            f'All audited Top3 candidate quality: {sum(o["usefulActual"] for o in observed)}/{len(observed)};',
            f'mean actual F/S maximum adjacent step: {mean(o["fractureAdjacentStep"] for o in observed):.2f}/{mean(o["smoothAdjacentStep"] for o in observed):.2f};',
            f'mean unsafe standing columns F/S: {mean(o["unsafeFractureColumns"] for o in observed):.2f}/{mean(o["unsafeSmoothColumns"] for o in observed):.2f}.',
            f'Overlapping failure counts: `{json.dumps(dict(counts),sort_keys=True)}`.']
lines+=['','## Base versus FULL limits','',
        '| Seed | Top3 center-height mismatches | Directionally interesting but actual rejected |',
        '| --- | ---: | ---: |']
for r in reports:
    center_mismatch=sum(o['fractureGround'][16]!=o['corridor']['candidate']['fractureHeight'] or o['smoothGround'][16]!=o['corridor']['candidate']['smoothHeight'] for o in r['observations'])
    directional_rejected=sum(o['corridor']['interesting'] and not o['usefulActual'] for o in r['observations'])
    lines.append(f'| {r["seed"]} | {center_mismatch} | {directional_rejected} |')
lines+=['', 'The directionally interesting label is deliberately UNVERIFIED, not a base',
        'acceptance PASS. Four-block base slopes can miss single-block FULL cuts and',
        'body hazards even when center heights agree. The actual data below reports',
        'these rejections without changing TerrainSampleService semantics.','',
        '## Top3 detail and bounded-shift evidence','',
        'All Top5 signals are preserved in ignored per-seed refined-report.json. Below are',
        'the actual Top3: no lower rank is substituted into the Top1 result. Risk is a base',
        'hint; unsafe counts use real FULL terrain and unchanged LandingSafety. Source feet',
        'use natural ground+1, which can conservatively reject fractional support shapes.','']
for r in reports:
    lines += [f'### Seed {r["seed"]}', '',
              '| Rank x,z | Axis | Base F/S step | Base contrast | Risk / rank score | Actual F/S step | Unsafe F/S | Shift proofs | Causes |',
              '| --- | --- | --- | --- | --- | --- | --- | --- | --- |']
    for j,o in enumerate(r['observations']):
        c=o['corridor']; p=c['candidate']
        lines.append(f'| #{j+1} {p["x"]},{p["z"]} | {c["dx"]},{c["dz"]} | {c["fractureStep"]:.3f}/{c["smoothStep"]:.3f} | {c["heightContrast"]:.1f} | {c["risk"]} / {c["score"]:+.2f} | {o["fractureAdjacentStep"]}/{o["smoothAdjacentStep"]} | {o["unsafeFractureColumns"]}/{o["unsafeSmoothColumns"]} | {len(o["shiftProofs"])} | {", ".join(causes(o))} |')
    o=r['observations'][0]
    lines += [f'Actual shift attempts Top1: sourceUnsafe={o["sourceUnsafe"]}, forwardRejected={o["forwardRejected"]}, reverseRejected={o["reverseRejected"]}.']
    if o['shiftProofs']:
        proof=o['shiftProofs'][0]
        lines += [f'Example actual source -> destination -> reverse: `{json.dumps(proof,sort_keys=True)}`.']
    if r['seed'] in [8675309,0,9223372036854775807,271828182]:
        c=o['corridor']; fig,ax=plt.subplots(figsize=(9,3.7),layout='constrained')
        ts=list(range(-16,17)); ax.plot(ts,o['fractureGround'],label='Fracture FULL ground',color='#d25935')
        ax.plot(ts,o['smoothGround'],label='Smooth FULL ground',color='#2477b4')
        for proof in o['shiftProofs']:
            source=proof['source']; t=(source['x']-.5-c['candidate']['x'])/c['dx'] if c['dx'] else (source['z']-.5-c['candidate']['z'])/c['dz']
            ax.scatter([t],[source['y']-1],s=45,c='#2f8b43',zorder=4)
        ax.set(title=f'Seed {r["seed"]} / Top1 ({c["candidate"]["x"]},{c["candidate"]["z"]}) axis ({c["dx"]},{c["dz"]})\n'+', '.join(causes(o)),xlabel='Adjacent-column offset along selected axis',ylabel='Actual ground Y')
        ax.grid(alpha=.2); ax.legend(loc='best'); file=f'seed-{r["seed"]}.png'; fig.savefig(out/file,dpi=150); plt.close(fig)
        lines += [f'![Actual FULL selected corridor; green marks bounded-shift proofs](verification/tutorial-v4/{file})',
                  'This is a plot of real server-generated terrain data, not a client screenshot or continuous path proof.']
    lines+=['']
lines+=['## Actual command output and maximum workload','']
for r in reports:
    if r.get('commandOutput'):
        lines += [f'Seed {r["seed"]} real registered command output:', '```text',*r['commandOutput'],'```','']
    if r.get('maximumSearch'):
        lines += [f'Seed {r["seed"]} isolated bulk256 metrics (not normal command wall latency): `{json.dumps(r["maximumSearch"],sort_keys=True)}`.','']
lines+=['## Interpretation and next design decision','',
        'Directional analysis and separate shift feasibility solve diagnostic blind spots,',
        'but do not produce a useful majority. Large inter-Phase offsets, natural Smooth',
        'river/coast cuts, actual body hazards, and the strict +/-4 shift condition remain',
        'independent constraints. This bounded experiment cannot isolate their global',
        'causal proportions: no wider radius, whole-pool FULL scan or preset change was run.',
        'The ten original and five independent seeds argue against claiming a reliable',
        'pure-natural first lesson. Stop this tuning round rather than select favorable seeds.','',
        'Game Design section11 and Opening Flow already allow a **controlled Anchor Station',
        'plus a nearby natural obstacle**. Recommend guaranteeing the initial interaction',
        'and safe demonstration area there, with natural candidates as optional nearby',
        'context/fallback. A later design decision must choose authored station constraints',
        'and failure fallback; this PR places no Anchor, changes no spawn/policy and adds',
        'no persistence. Future #14 can consume immutable seed/config/coordinates plus',
        'explicit UNVERIFIED/validated status after review, not a bare mysterious score.','',
        '## Reproduce','', '```sh',
        './gradlew tutorialVerification -PtutorialProtocol=attribution --console=plain',
        './gradlew tutorialVerification -PtutorialProtocol=refined --console=plain',
        './gradlew coreRegressionAll build --console=plain','```',
        'See the API document for accepted EULA/setup. Correctness failures return nonzero;',
        'empirical QUALITY_SHORTFALL stays explicit and does not become an acceptance PASS.','']
replay_path=Path('build/tutorial-refined/pre-cpu-summary.json')
replay=json.loads(replay_path.read_text(encoding='utf-8'))['reports'] if replay_path.exists() else []
if replay: assert len(replay)==len(reports)
lines+=['## Timing-instrumentation replay audit','',
        'All15 seeds kept identical Top5 and complete fine-analyzed base evidence.' if replay else 'No earlier replay archive is available in this checkout.',
        'Earlier safety snapshots are retained rather than silently substituted.',
        'Ground-column and body-safety differences are distinct. The probe does not',
        'capture every entity/fluid/block-state cause; a same-height safety change',
        'is not attributed to a specific mob without that evidence. Neither run',
        'promises future safety.','',
        '| Seed | Earlier/final Top1 useful | Earlier/final Top3 shift | Top3 ground-changed count | Top3 safety-count-changed count |',
        '| --- | --- | --- | ---: | ---: |']
for old,new in zip(replay,reports):
    assert old['seed']==new['seed'] and old['top5']==new['top5'] and old['analyzed']==new['analyzed']
    ground=sum(a['fractureGround']!=b['fractureGround'] or a['smoothGround']!=b['smoothGround'] for a,b in zip(old['observations'],new['observations']))
    safety=sum(a['unsafeFractureColumns']!=b['unsafeFractureColumns'] or a['unsafeSmoothColumns']!=b['unsafeSmoothColumns'] for a,b in zip(old['observations'],new['observations']))
    lines.append(f'| {new["seed"]} | {int(old["usefulTop1"])}/{int(new["usefulTop1"])} | {int(old["shiftTop3"])}/{int(new["shiftTop3"])} | {ground} | {safety} |')
if not replay: lines+=['Earlier replay archive is not present in this checkout; no replay equality is asserted.']
Path('docs/TUTORIAL_SEARCH_REFINEMENT_VALIDATION.md').write_text('\n'.join(lines),encoding='utf-8')
print({k:v for k,v in summary.items() if k!='reports'})
