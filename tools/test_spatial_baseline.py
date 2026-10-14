import unittest
import copy,json,tempfile,subprocess,sys
from pathlib import Path
import numpy as np
from spatial_baseline import matrix,metrics,validate_server,validate_client,EXPECTED,WORLDS

class SpatialBaselineTest(unittest.TestCase):
    def test_plane_and_flat_statistics(self):
        a=np.tile(np.arange(5)*16,(5,1));m=metrics(a,16)
        self.assertEqual(m['slopeMean'],1);self.assertEqual(m['anisotropy'],1);self.assertEqual(m['localReliefMedian'],32)
        m=metrics(np.zeros((3,3)),16);self.assertEqual(m['slopeMean'],0);self.assertEqual(m['anisotropy'],0);self.assertIsNone(m['gradientAxisDegrees'])
    def test_missing_duplicate_and_nonfinite_grid(self):
        for rows in [[{'x':0,'z':0,'height':1},{'x':0,'z':0,'height':1}],[{'x':0,'z':0,'height':'nan'}]]:
            with self.assertRaises(ValueError):matrix(rows,'height')
    def test_metrics_reject_bad_input(self):
        for a,step in [(np.zeros((2,2)),16),(np.zeros((3,3)),0),(np.full((3,3),np.inf),16)]:
            with self.assertRaises(ValueError):metrics(a,step)
    def test_report_failure_paths(self):
        with self.assertRaises(ValueError):validate_server({}, {},123)
        base={'status':'PASS','seed':123,'protocol':'WPH01601_LEGACY_BASELINE_V1','phaseFullChunkRequests':0,'rows':EXPECTED,'rawSeeds':{w:123 for w in WORLDS}}
        with self.assertRaisesRegex(ValueError,'Missing heights'):validate_server(base,{k:[] for k in EXPECTED},123)
        base['rawSeeds']['cell']=124
        with self.assertRaisesRegex(ValueError,'Raw seed'):validate_server(base,{},123)
        with self.assertRaises(ValueError):validate_client({},123,None)
        with self.assertRaises(ValueError):validate_client({'status':'CAPTURES_COMPLETE_NOT_TERRAIN_OR_GAMEPLAY_APPROVAL','seed':123,'protocol':'WPH01601_LEGACY_BASELINE_V1','photos':[]},123,None)

    def test_client_settings_and_evidence_fail_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory=Path(tmp);(directory/'screenshots').mkdir();photos=[]
            for window,(x,z) in {'forest':(-383.5,896.5),'highland':(1344.5,1200.5)}.items():
                for world in WORLDS:
                    name=f'{window}-{world}.png';__import__('PIL.Image',fromlist=['Image']).new('RGB',(1280,720)).save(directory/'screenshots'/name)
                    photos.append(dict(window=window,world=world,x=x,z=z,y=240,yaw=135,pitch=60,time=6000,rain=0,thunder=0,fov=70,gamma=.5,viewDistance=16,simulationDistance=5,graphicsBase='FANCY',graphics='CUSTOM',clouds='OFF',particles='MINIMAL',ambientOcclusion=True,mipmapLevels=4,biomeBlendRadius=2,resourcePacks='[]',settleTicks=379,screenshot=name))
            report=dict(status='CAPTURES_COMPLETE_NOT_TERRAIN_OR_GAMEPLAY_APPROVAL',seed=123,protocol='WPH01601_LEGACY_BASELINE_V1',photos=photos)
            validate_client(report,123,directory)
            for field,value in [('status','RUNNING'),('seed',456),('protocol','other')]:
                bad=copy.deepcopy(report);bad[field]=value
                with self.assertRaises(ValueError):validate_client(bad,123,directory)
            for field,value in [('time',6001),('simulationDistance',12),('x',-384),('resourcePacks','[custom]'),('settleTicks',100),('clouds','FANCY')]:
                bad=copy.deepcopy(report);bad['photos'][0][field]=value
                with self.assertRaises(ValueError):validate_client(bad,123,directory)
            bad=copy.deepcopy(report);bad['photos'][0]=copy.deepcopy(bad['photos'][1])
            with self.assertRaises(ValueError):validate_client(bad,123,directory)
            (directory/'screenshots'/photos[0]['screenshot']).unlink()
            with self.assertRaisesRegex(ValueError,'Missing native screenshot'):validate_client(report,123,directory)

    def test_cli_missing_report_nonzero(self):
        with tempfile.TemporaryDirectory() as tmp:
            result=subprocess.run([sys.executable,str(Path(__file__).with_name('spatial_baseline.py')),'--run-root',tmp,'--require-native'],capture_output=True,text=True)
            self.assertNotEqual(result.returncode,0)
            self.assertIn('SPATIAL_BASELINE_FAIL:',result.stderr)

if __name__=='__main__':unittest.main()
