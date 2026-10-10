"""Curate frozen-protocol attribution; audited subsets never become a whole-pool safety oracle."""
import json
from pathlib import Path

summary = json.loads(Path('build/tutorial-attribution/summary.json').read_text(encoding='utf-8'))
assert summary['correctnessStatus'] == 'PASS'
reports = summary['reports']
assert len(reports) == 10 and all(r['status'] == 'PASS' for r in reports)

def qualified(s):
    return s['heightContrast'] >= 4 and s['fractureSlope'] >= .375 and s['smoothSlope'] <= .25 and s['slopeContrast'] >= .25

def axes(f, s):
    result = []
    for dx, dz in [(1,0),(0,1),(1,1),(1,-1)]:
        fh = [f[16+dz*t][16+dx*t] for t in range(-16,17)]
        sh = [s[16+dz*t][16+dx*t] for t in range(-16,17)]
        result.append((dx,dz,max(abs(a-b) for a,b in zip(fh,fh[1:])),
                       max(abs(a-b) for a,b in zip(sh,sh[1:])),min(abs(a-b) for a,b in zip(fh,sh))))
    return result

lines = ['# Frozen 3f510d0 baseline: failure attribution', '',
         'Original seeds/radius512/count64, generation3 and all original score thresholds/weights are unchanged.',
         'The original **2/10 Top1** protocol and its complete report remain in',
         '[baseline validation](TUTORIAL_SEARCH_VALIDATION.md). This analysis precedes new ranking code.', '',
         'Every raw64-point stencil is captured. FULL audits inspect old Top3 and at most',
         'one additional shape-qualified sea/coast candidate; at most4 per seed. Uninspected',
         'FULL candidates are **unknown**, never classified as nonexistent. Sea false negatives',
         'below mean numeric shape-qualified hints whose two center bodies stand safely;',
         'the stricter all-nine-footprint safety audit is shown separately. Neither proves',
         'a usable route or feasible +/-4 shift.', '',
         '| Seed | Pool old-qualified | Sea-only shape rejects | Directional-proxy misses | Audited FULL | FULL old-qualified | Sea rejects: both centers safe / all9 safe | Base-positive -> FULL-negative | Classification |',
         '| --- | ---: | ---: | ---: | ---: | ---: | --- | ---: | --- |']
totals = dict(pool=0, sea=0, directions=0, center_false_negative=0, stencil_false_negative=0, full_miss=0, audited=0, ranking_miss=0, height_mismatch=0, slope_mismatch=0)
details = []
for r in reports:
    pool = r['legacyPool']; obs = r['observations']
    good = sum(qualified(e['candidate']['score']) and e['candidate']['score']['hazardPenalty']==0 for e in pool)
    sea = sum(qualified(e['candidate']['score']) and 0<e['candidate']['score']['hazardPenalty']<=120 and
              max(e['candidate']['score']['fractureSlope'],e['candidate']['score']['smoothSlope'])<=2 for e in pool)
    directions = 0
    for e in pool:
        if e['candidate']['score']['smoothSlope'] <= .25: continue
        for a,b,length in [(1,2,16),(3,4,16),(5,8,2**.5*16),(6,7,2**.5*16)]:
            f,s=e['fracture'],e['smooth']
            fs=max(abs(f[a]-f[0]),abs(f[b]-f[0]))/length
            ss=max(abs(s[a]-s[0]),abs(s[b]-s[0]))/length
            if fs>=.375 and ss<=.25 and fs-ss>=.25:
                directions+=1; break
    full_good=sum(o['usefulActual'] for o in obs)
    sea_audits=[o for o in obs if qualified(o['candidate']['score']) and 0<o['candidate']['score']['hazardPenalty']<=120]
    center_fn=sum(o['FRACTURE']['centerLandingSafe'] and o['SMOOTH']['centerLandingSafe'] for o in sea_audits)
    stencil_fn=sum(all(o['FRACTURE']['stencilStandingSafe']) and all(o['SMOOTH']['stencilStandingSafe']) for o in sea_audits)
    misses=sum(o['usefulBase'] and not o['usefulActual'] for o in obs)
    ranking_miss=not obs[0]['usefulActual'] and any(o['usefulActual'] for o in obs[1:])
    height_mismatch=sum(o['FRACTURE']['groundHeights'][16][16]!=o['candidate']['fractureHeight'] or o['SMOOTH']['groundHeights'][16][16]!=o['candidate']['smoothHeight'] for o in obs)
    slope_mismatch=sum(any(o['actualScore'][key]!=o['candidate']['score'][key] for key in ['fractureSlope','smoothSlope']) for o in obs)
    classification='NO_OLD_QUALIFIER_IN_POOL; FULL outside audit unknown' if not good else 'OLD_BASE_AND_FULL_TOP1_PASS'
    if ranking_miss: classification += '; AUDITED_LOWER_RANK_BETTER'
    if center_fn: classification += '; COAST_CENTER_FALSE_NEGATIVE'
    if directions: classification += '; DIRECTIONAL_PROXY_DISAGREEMENT'
    if misses: classification += '; BASE_FULL_FALSE_POSITIVE'
    lines.append(f'| {r["seed"]} | {good} | {sea} | {directions} | {len(obs)} | {full_good} | {center_fn}/{stencil_fn} | {misses} | {classification} |')
    for key,value in [('pool',good),('sea',sea),('directions',directions),('center_false_negative',center_fn),('stencil_false_negative',stencil_fn),('full_miss',misses),('audited',len(obs)),('ranking_miss',int(ranking_miss)),('height_mismatch',height_mismatch),('slope_mismatch',slope_mismatch)]: totals[key]+=value
    details += [f'## Seed {r["seed"]}: actual audit', '',
                f'Base/FULL center mismatch: {height_mismatch}/{len(obs)}; local-slope mismatch: {slope_mismatch}/{len(obs)}. Positive-to-negative classification: {misses}.', '',
                '| Old rank / audit | x,z | Base/FULL useful | Center height delta F/S | Actual safe9 F/S | Actual axes (dx,dz:Fstep/Sstep/minHeightGap) |',
                '| --- | --- | --- | --- | --- | --- |']
    for o in obs:
        c=o['candidate']; f=o['FRACTURE']['groundHeights']; s=o['SMOOTH']['groundHeights']
        ax='; '.join(f'{dx},{dz}:{fs}/{ss}/{gap}' for dx,dz,fs,ss,gap in axes(f,s))
        details.append(f'| {o["label"]} | {c["x"]},{c["z"]} | {o["usefulBase"]}/{o["usefulActual"]} | {f[16][16]-c["fractureHeight"]:+d}/{s[16][16]-c["smoothHeight"]:+d} | {sum(o["FRACTURE"]["stencilStandingSafe"])}/{sum(o["SMOOTH"]["stencilStandingSafe"])} | {ax} |')
    details += ['']
lines += ['', '## Aggregate and causal limits', '',
          f'Raw old-qualified candidates: {totals["pool"]}; sea-only shape rejections: {totals["sea"]};',
          f'directional proxy disagreements: {totals["directions"]}. Audited {totals["audited"]} FULL candidates.',
          f'Coast hints with both centers actually safe: {totals["center_false_negative"]}; all-nine safe: {totals["stencil_false_negative"]}.',
          f'Observed base-positive/FULL-negative cases: {totals["full_miss"]}; seeds with audited lower-rank old success: {totals["ranking_miss"]}.', '',
          f'Base/FULL center heights differ in {totals["height_mismatch"]}/{totals["audited"]} audits; local slopes differ in {totals["slope_mismatch"]}/{totals["audited"]}.',
          'These differences affect already-rejected locations, but none turns an old positive into a FULL negative in this audited subset. They are not evidence that base columns certify gameplay safety.', '',
          'Qualified-first legacy ranking cannot hide an old-base-qualified candidate behind an',
          'unqualified Top1: eight old Top1 failures therefore have no qualifying point in their',
          '64-point **base** pool. This does not prove natural qualified locations do not exist.',
          'The FULL audit additionally checks whether lower-ranked candidates pass the old standard.',
          'No whole-pool FULL scan is performed, so any unobserved real ranking miss remains unknown.', '',
          'Maximum center-to-neighbor slope is a directionless mean-gradient proxy. The actual',
          '33-cell line measurements above show adjacent-block steps and the minimum same-x/z',
          'ground-height gap for four axes. These do not inspect all body hazards/paths and do',
          'not count as verified shifts. The maximum-long old success has no <=4 ground gap',
          'on these lines despite its strong visual contrast; the8675309 old success has',
          'height-compatible positions. Shape improvement and shift feasibility must be separate.', '',
          'New experiments must retain the original report and report old-protocol Top1, new',
          'Top1/Top3 and actual bounded-shift rates separately. No weight/threshold change was',
          'made to the old finder in this attribution pass.', ''] + details
Path('docs/TUTORIAL_SEARCH_ATTRIBUTION.md').write_text('\n'.join(lines),encoding='utf-8')
print(json.dumps(totals))
