#!/usr/bin/env python3
"""Reproducible legacy measurements/plots. Requires numpy + matplotlib; never generates terrain."""
import argparse, csv, gzip, hashlib, itertools, json, math, shutil
from pathlib import Path
import numpy as np
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'docs/verification/spatial-retune/v1'
WORLDS=['smooth','ridge','cell','fracture','overworld']
EXPECTED={'heights':13780,'columns':2580,'density':196090,'probes':270,'attribution':270}

def read_csv(path):
    with gzip.open(path,'rt') as f:return list(csv.DictReader(f))

def matrix(rows,field):
    xs=sorted({int(r['x']) for r in rows});zs=sorted({int(r['z']) for r in rows})
    if len(rows)!=len(xs)*len(zs):raise ValueError('Incomplete/duplicate rectangular sample grid')
    values={(int(r['x']),int(r['z'])):float(r[field]) for r in rows}
    if len(values)!=len(rows):raise ValueError('Duplicate sample coordinates')
    a=np.array([[values[x,z] for x in xs] for z in zs])
    if not np.isfinite(a).all():raise ValueError('Non-finite sample')
    return xs,zs,a

def metrics(a,step):
    if a.ndim!=2 or min(a.shape)<3 or step<=0 or not np.isfinite(a).all():raise ValueError('Invalid height grid')
    gz,gx=np.gradient(a,step);slope=np.hypot(gx,gz)
    tensor=np.array([[np.mean(gx*gx),np.mean(gx*gz)],[np.mean(gx*gz),np.mean(gz*gz)]])
    eigen,vec=np.linalg.eigh(tensor);total=float(sum(eigen))
    patches=np.lib.stride_tricks.sliding_window_view(a,(3,3));relief=patches.max((-1,-2))-patches.min((-1,-2))
    edges=np.concatenate([np.abs(np.diff(a,axis=i)).ravel()/step for i in (0,1)])
    # Eight-connected upper-quartile patch fraction: a sampled connectivity diagnostic, not a crest detector.
    mask=a>np.percentile(a,75);seen=set();components=[]
    for z,x in zip(*np.where(mask)):
        if (z,x) in seen:continue
        stack=[(z,x)];seen.add((z,x));n=0
        while stack:
            p,q=stack.pop();n+=1
            for dz,dx in itertools.product((-1,0,1),repeat=2):
                u,v=p+dz,q+dx
                if 0<=u<a.shape[0] and 0<=v<a.shape[1] and mask[u,v] and (u,v) not in seen:seen.add((u,v));stack.append((u,v))
        components.append(n)
    return dict(min=float(a.min()),max=float(a.max()),mean=float(a.mean()),std=float(a.std()),p5=float(np.percentile(a,5)),p50=float(np.median(a)),p95=float(np.percentile(a,95)),
                slopeMean=float(slope.mean()),slopeP95=float(np.percentile(slope,95)),localReliefMedian=float(np.median(relief)),localReliefP95=float(np.percentile(relief,95)),
                flatEdgeFraction=float(np.mean(edges<=.125)),steepEdgeFraction=float(np.mean(edges>=.5)),anisotropy=float((eigen[1]-eigen[0])/total) if total else 0,
                gradientAxisDegrees=float(math.degrees(math.atan2(vec[1,1],vec[0,1]))) if total else None,
                highPatchCount=len(components),largestHighPatchFraction=max(components,default=0)/int(mask.sum()) if mask.sum() else 0,
                seaOrLowerFraction=float(np.mean(a<63)))

def validate_server(report,rows,seed):
    if report.get('status')!='PASS' or report.get('protocol')!='WPH01601_LEGACY_BASELINE_V1' or report.get('seed')!=seed:raise ValueError(f'Incomplete/mismatched server report seed={seed}')
    if report.get('phaseFullChunkRequests')!=0 or report.get('rows')!=EXPECTED:raise ValueError('Unexpected chunk request/count contract')
    if report.get('rawSeeds')!={w:seed for w in WORLDS}:raise ValueError('Raw seed mismatch')
    for name,count in EXPECTED.items():
        if len(rows[name])!=count:raise ValueError(f'Missing {name} rows')
    for name in ('columns','density','probes','attribution'):
        seen=set();groups={}
        for r in rows[name]:
            kind=r.get('kind',r.get('axis','point'));group=(r['world'],r['window'],kind)
            key=group+(r['x'],r.get('y',''),r['z'])
            if key in seen:raise ValueError(f'Duplicate {name} coordinate')
            seen.add(key);groups[group]=groups.get(group,0)+1
            if r['world'] not in WORLDS or r['window'] not in ('forest','highland'):raise ValueError('Unknown measurement world/window')
            if name=='columns':
                states=r['states_y_minus64_to319']
                if len(states)!=384 or set(states)-set('AWLS'):raise ValueError('Incomplete/invalid column')
            for field in ('density','raw','interpolated','g0','gphase','base3d','terrain','offset','factor'):
                if field in r and not math.isfinite(float(r[field])):raise ValueError('Non-finite density')
        kinds={'columns':{'x':129,'z':129},'density':{'x':6192,'z':6192,'volume':7225},'probes':{'point':27},'attribution':{'point':27}}[name]
        expected={(w,window,k):n for w in WORLDS for window in ('forest','highland') for k,n in kinds.items()}
        if groups!=expected:raise ValueError(f'Incomplete {name} groups')

def validate_client(report,seed,directory):
    if report.get('status')!='CAPTURES_COMPLETE_NOT_TERRAIN_OR_GAMEPLAY_APPROVAL' or report.get('seed')!=seed or report.get('protocol')!='WPH01601_LEGACY_BASELINE_V1':raise ValueError('Missing or mismatched native report')
    photos=report.get('photos',[])
    if len(photos)!=10 or {(r['window'],r['world']) for r in photos}!={(n,w) for n in ('forest','highland') for w in WORLDS}:raise ValueError('Missing/duplicate native view')
    centers={'forest':(-383.5,896.5),'highland':(1344.5,1200.5)}
    for r in photos:
        expected={'y':240,'yaw':135,'pitch':60,'time':6000,'rain':0,'thunder':0,'fov':70,'gamma':.5,'viewDistance':16,'simulationDistance':5,'graphicsBase':'FANCY','clouds':'OFF','particles':'MINIMAL','ambientOcclusion':True,'mipmapLevels':4,'biomeBlendRadius':2}
        if any(r.get(k)!=v for k,v in expected.items()) or (r['x'],r['z'])!=centers[r['window']] or r.get('settleTicks',0)<379:raise ValueError('Unmatched camera/settings/environment')
        if not r.get('graphics')=='CUSTOM' or r.get('resourcePacks')!='[]':raise ValueError('Unmatched graphics/resource packs')
        file=directory/'screenshots'/r['screenshot']
        if not file.is_file():raise ValueError('Missing native screenshot')
        from PIL import Image
        with Image.open(file) as picture:
            if picture.size!=(1280,720) or picture.format!='PNG':raise ValueError('Invalid native screenshot dimensions/format')
            picture.verify()

def plot_window(seed,window,rows,out):
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    from matplotlib.colors import ListedColormap,BoundaryNorm
    data={}
    for grid,step in [('coarse',64),('fine',16)]:
        fig,axes=plt.subplots(2,5,figsize=(17,6),constrained_layout=True)
        for col,w in enumerate(WORLDS):
            subset=[r for r in rows['heights'] if r['world']==w and r['window']==window and r['grid']==grid]
            xs,zs,a=matrix(subset,'height');data[w,grid]=a
            slope=np.hypot(*np.gradient(a,step))
            for row,arr,cmap,lo,hi in [(0,a,'terrain',-32,224),(1,slope,'magma',0,1.5)]:
                im=axes[row,col].imshow(arr,origin='lower',extent=(xs[0],xs[-1],zs[0],zs[-1]),cmap=cmap,vmin=lo,vmax=hi,interpolation='nearest')
                axes[row,col].set_title(f'{w}: '+('height y' if row==0 else '|gradient| blocks/block'))
                axes[row,col].set_xlabel('x');axes[row,col].set_ylabel('z');fig.colorbar(im,ax=axes[row,col],shrink=.65)
        fig.suptitle(f'MEASURED base terrain, seed {seed}, {window}, step {step}; common scales; water excluded from height')
        fig.savefig(out/f'{seed}-{window}-{grid}-maps.png',dpi=120);plt.close(fig)
    fig,axes=plt.subplots(2,5,figsize=(17,6),constrained_layout=True)
    palette=ListedColormap(['#eeeef0','#3885bf','#ef5c27','#725c47'])
    for c,w in enumerate(WORLDS):
        for row,axis in enumerate(('x','z')):
            columns=sorted([r for r in rows['columns'] if r['window']==window and r['world']==w and r['axis']==axis],key=lambda r:int(r[axis]))
            a=np.array([[{'A':0,'W':1,'L':2,'S':3}[s] for s in r['states_y_minus64_to319']] for r in columns]).T
            axes[row,c].imshow(a,origin='lower',aspect='auto',extent=(-512,512,-64,320),cmap=palette,norm=BoundaryNorm([-.5,.5,1.5,2.5,3.5],4),interpolation='nearest')
            axes[row,c].axhline(63,color='#3885bf',lw=.5);axes[row,c].set_title(f'{w} {axis}/y');axes[row,c].set_xlabel('offset blocks');axes[row,c].set_ylabel('y')
    fig.suptitle(f'MEASURED native base columns {seed} {window}: white air / blue water / orange lava / brown solid; no FULL decoration')
    fig.savefig(out/f'{seed}-{window}-columns.png',dpi=120);plt.close(fig)
    fig,axes=plt.subplots(2,5,figsize=(17,6),constrained_layout=True)
    for c,w in enumerate(WORLDS):
        for row,axis in enumerate(('x','z')):
            rs=[r for r in rows['density'] if r['window']==window and r['world']==w and r['kind']==axis]
            coords=sorted({int(r[axis]) for r in rs});ys=sorted({int(r['y']) for r in rs});d={(int(r[axis]),int(r['y'])):float(r['density']) for r in rs}
            a=np.array([[d[x,y] for x in coords] for y in ys]);axes[row,c].imshow(a,origin='lower',aspect='auto',extent=(-512,512,-64,312),cmap='RdBu',vmin=-.458,vmax=.458,interpolation='nearest')
            if a.min()<0<a.max():axes[row,c].contour(np.linspace(-512,512,len(coords)),ys,a,levels=[0],colors='black',linewidths=.4)
            axes[row,c].set_title(f'{w} {axis}/y');axes[row,c].set_xlabel('offset blocks');axes[row,c].set_ylabel('y')
    fig.suptitle(f'MEASURED raw final-router density {seed} {window}: blue positive solid; black zero; BEFORE native interpolation; step8')
    fig.savefig(out/f'{seed}-{window}-density-planes.png',dpi=120);plt.close(fig)
    fig=plt.figure(figsize=(22,5.8));fig.subplots_adjust(left=.01,right=.97,bottom=.04,top=.85,wspace=.08)
    for c,w in enumerate(WORLDS):
        ax=fig.add_subplot(1,5,c+1,projection='3d');rs=[r for r in rows['density'] if r['world']==w and r['window']==window and r['kind']=='volume']
        xs=sorted({int(r['x']) for r in rs});zs=sorted({int(r['z']) for r in rs});ys=sorted({int(r['y']) for r in rs});d={(int(r['x']),int(r['y']),int(r['z'])):float(r['density']) for r in rs}
        cx=xs[len(xs)//2];cz=zs[len(zs)//2]
        # Three orthogonal slices sampled from an actual 3D volume, not extruded height maps.
        for normal in ('x','z','y'):
            aa,bb=np.meshgrid(xs if normal!='x' else zs,ys if normal!='y' else zs)
            xx=np.full_like(aa,cx) if normal=='x' else aa
            zz=aa if normal=='x' else np.full_like(aa,cz) if normal=='z' else bb
            yy=np.full_like(aa,64) if normal=='y' else bb
            colors=np.array([[plt.cm.RdBu((d[int(x),int(y),int(z)]+.458)/.916) for x,y,z in zip(xrow,yrow,zrow)] for xrow,yrow,zrow in zip(xx,yy,zz)])
            ax.plot_surface(xx-cx,zz-cz,yy,facecolors=colors,shade=False,rstride=1,cstride=1,alpha=.85)
        ax.set_title(w);ax.set_xlabel('dx',labelpad=0);ax.set_ylabel('dz',labelpad=0);ax.set_zlabel('y',labelpad=0);ax.tick_params(labelsize=7,pad=0);ax.set_zticks([-32,0,64,128,160]);ax.view_init(23,-55);ax.set_zlim(-32,160);ax.set_box_aspect((1,1,1.2))
    fig.suptitle(f'MEASURED raw 3D density orthogonal sections: seed {seed}, {window}; blue solid/red air; 8-block spacing')
    fig.savefig(out/f'{seed}-{window}-density-3d.png',dpi=120);plt.close(fig)
    fig,axes=plt.subplots(1,2,figsize=(12,4),constrained_layout=True)
    for w in WORLDS:
        a=data[w,'coarse'];ordered=np.sort(a.ravel());axes[0].plot(ordered,np.arange(1,ordered.size+1)/ordered.size,label=w)
        fine=data[w,'fine'];patch=np.lib.stride_tricks.sliding_window_view(fine,(3,3));relief=np.sort((patch.max((-1,-2))-patch.min((-1,-2))).ravel())
        axes[1].plot(relief,np.arange(1,relief.size+1)/relief.size,label=w)
    axes[0].set_xlabel('base surface y, step64');axes[1].set_xlabel('local max-min blocks, 3x3 at step16 (span32)')
    for ax in axes:ax.set_ylabel('empirical cumulative fraction');ax.legend();ax.grid(alpha=.25)
    fig.suptitle(f'MEASURED height / local relief distributions, {seed} {window}')
    fig.savefig(out/f'{seed}-{window}-distributions.png',dpi=120);plt.close(fig)
    return data

def native_gallery(seed,native,directory,out):
    import matplotlib.pyplot as plt
    from PIL import Image
    for window in ('forest','highland'):
        fig,axes=plt.subplots(2,3,figsize=(16,7),constrained_layout=True)
        for ax in axes.flat:ax.axis('off')
        for ax,world in zip(axes.flat,WORLDS):
            photo=next(r for r in native['photos'] if r['window']==window and r['world']==world)
            with Image.open(directory/'screenshots'/photo['screenshot']) as image:ax.imshow(image)
            ax.set_title(world,fontsize=13)
        fig.suptitle(f'ACTUAL native screenshots: seed {seed}, {window}; identical pose/time/settings; not route evidence')
        fig.savefig(out/f'{seed}-{window}-native-contact.png',dpi=120);plt.close(fig)

def main():
    p=argparse.ArgumentParser();p.add_argument('--run-root',type=Path,default=ROOT/'run/spatial-baseline-v1');p.add_argument('--require-native',action='store_true');p.add_argument('--plots',action='store_true');p.add_argument('--archive',action='store_true');args=p.parse_args()
    protocol=json.loads((ASSETS/'protocol.json').read_text());out=ASSETS/'plots';out.mkdir(exist_ok=True)
    for name,digest in protocol['sourceSha256'].items():
        if hashlib.sha256((ROOT/name).read_bytes()).hexdigest()!=digest:raise ValueError('Frozen production source changed '+name)
    result={'protocol':protocol['protocol'],'status':'MEASURED_NOT_PROTOTYPE_OR_ROUTE_APPROVAL','seeds':[]}
    for i,seed in enumerate(protocol['seeds']):
        directory=args.run_root/f'seed-{i}-server';report=json.loads((directory/'spatial-baseline.json').read_text());rows={k:read_csv(directory/f'{k}.csv.gz') for k in EXPECTED};validate_server(report,rows,seed)
        entry={'seed':seed,'server':report,'windows':[]};result['seeds'].append(entry)
        for window in ('forest','highland'):
            grids={}
            for grid,step in [('coarse',64),('fine',16)]:
                arrays={w:matrix([r for r in rows['heights'] if r['world']==w and r['window']==window and r['grid']==grid],'height')[2] for w in WORLDS}
                inland=arrays['overworld']>=70
                stats={w:metrics(a,step) for w,a in arrays.items()}
                for w,a in arrays.items():stats[w]['inlandHeightStd']=float(a[inland].std()) if inland.any() else None
                pairs=[]
                for a,b in itertools.combinations(WORLDS,2):
                    delta=arrays[b]-arrays[a];pairs.append({'first':a,'second':b,'heightCorrelation':float(np.corrcoef(arrays[a].ravel(),arrays[b].ravel())[0,1]) if arrays[a].std()>0 and arrays[b].std()>0 else None,'meanAbsDelta':float(np.abs(delta).mean()),'heightWithin4ProxyFraction':float(np.mean(np.abs(delta)<=4))})
                grids[grid]={'step':step,'inlandCount':int(inland.sum()),'sampleCount':int(inland.size),'worlds':stats,'pairs':pairs}
            density={}
            for w in WORLDS:
                vol=[float(r['density']) for r in rows['density'] if r['world']==w and r['window']==window and r['kind']=='volume']
                columns=[r['states_y_minus64_to319'] for r in rows['columns'] if r['world']==w and r['window']==window]
                transitions=[sum((a=='S')!=(b=='S') for a,b in zip(s,s[1:])) for s in columns]
                probes=[abs(float(r['raw'])-float(r['interpolated'])) for r in rows['probes'] if r['world']==w and r['window']==window]
                algebra=[abs(float(r['terrain'])-float(r['gphase'])-float(r['base3d'])) for r in rows['attribution'] if r['world']==w and r['window']==window]
                density[w]={'rawVolumeSolidFraction':float(np.mean(np.array(vol)>0)),'columnSolidNonSolidTransitionsMean':float(np.mean(transitions)),'rawVsInterpolatedMaxAbs':max(probes),'terrainAlgebraMaxAbsResidual':max(algebra)}
            entry['windows'].append({'id':window,'grids':grids,'density':density})
            if args.plots:plot_window(seed,window,rows,out)
        if args.require_native:
            client=args.run_root/f'seed-{i}-client';native=json.loads((client/'spatial-client.json').read_text());validate_client(native,seed,client);entry['native']=native
            optionKeys={'ao','biomeBlendRadius','enableVsync','entityDistanceScaling','fov','fovEffectScale','graphicsPreset','prioritizeChunkUpdates','fullscreen','gamma','maxFps','inactivityFpsLimit','mipmapLevels','particles','renderDistance','simulationDistance','resourcePacks','syncChunkWrites'}
            options={line.split(':',1)[0]:line.split(':',1)[1] for line in (client/'options.txt').read_text().splitlines() if ':' in line and line.split(':',1)[0] in optionKeys}
            entry['additionalNativeOptions']=options
            if args.plots:native_gallery(seed,native,client,out)
        if args.archive:
            target=ASSETS/'measurements'/str(seed);target.mkdir(parents=True,exist_ok=True)
            for name in ['spatial-baseline.json']+[f'{k}.csv.gz' for k in EXPECTED]:shutil.copyfile(directory/name,target/name)
            if args.require_native:
                shutil.copyfile(client/'spatial-client.json',target/'spatial-client.json')
                (target/'selected-options.json').write_text(json.dumps(options,indent=2)+'\n')
                (ASSETS/'native').mkdir(exist_ok=True)
                for photo in native['photos']:shutil.copyfile(client/'screenshots'/photo['screenshot'],ASSETS/'native'/photo['screenshot'])
    (ASSETS/'results.json').write_text(json.dumps(result,indent=2,allow_nan=False)+'\n')
    print(json.dumps({'status':result['status'],'seeds':len(result['seeds']),'nativeRequired':args.require_native,'output':str(ASSETS/'results.json')}))

if __name__=='__main__':
    try:main()
    except (OSError,ValueError,KeyError,EOFError) as error:raise SystemExit('SPATIAL_BASELINE_FAIL: '+str(error))
