"""Check retained real-world hazard observations, separate from synthetic smoke."""
import json,math,re,sys
from pathlib import Path
trace,logpath,resultpath,out=map(Path,sys.argv[1:])
rows=[json.loads(s) for s in trace.read_text(encoding="utf-8").splitlines() if s.strip()]
result=json.loads(resultpath.read_text(encoding="utf-8"));log=logpath.read_text(encoding='utf-8',errors='replace')
assert result['passed'] and result['hazards'] and len(rows)>=1200
rows=rows[:1200]
assert [r['frame'] for r in rows]==list(range(1,1201))
assert all(math.isfinite(r[k]) for r in rows for k in ['x','y','z','vx','vy','vz','speed'])
burn={'10208b4','10208b5','20449'}
checks={}
for phase in range(5):
    rr=[r for r in rows if r['phase']==phase]
    assert len(rr)==240
    checks[str(phase)]={'minimumHealth':min(r['health'] for r in rr),'actions':sorted(set(r['action'] for r in rr)),'fireContactSteps':sum(r['contactBeforeStep']==1 for r in rr),'lavaBodyContactSteps':sum(r['contactBeforeStep']==2 for r in rr)}
    if phase<2:
        assert any(r['contactBeforeStep']==1 and r['action'] in burn for r in rr)
        assert any(a['health']-b['health']==10 and a['action'] in burn for a,b in zip(rr,rr[1:]))
        assert any(r['action'] not in burn and r['health']<0x880 and r['health']>0xFF for r in rr[100:])
    else:
        assert any(r['action']=='10208b7' for r in rr)
        assert any(a['health']-b['health']==64 for a,b in zip(rr,rr[1:]))
        if phase in (2,3):
            assert any(r['health']>255 and r['action'] not in {'10208b7','8000239'} for r in rr[180:])
            checks[str(phase)]['lateAliveRecovery']=True
assert min(r['health'] for r in rows if r['phase']==4)==255
assert checks['2']['minimumHealth']>255 and checks['3']['minimumHealth']>255
assert checks['3']['lavaBodyContactSteps']>0 and result['lavaSideEntries']>=1 and result['lavaRecovery']
# A real wall stops the forced run while its native action/damage continues.
blocked=sum(a['action']=='20449' and b['action']=='20449' and a['x']==b['x'] and a['y']==b['y'] and a['z']==b['z'] and b['speed']>=8 for a,b in zip(rows,rows[1:]))
assert blocked>10
# First actual floor bounce: floor 31.89 -> 32.73, then native gravity export80.8.
floor=[r for r in rows if r['phase']==2]
a,b=floor[0],floor[1]
assert a['health']==0x880 and b['action']=='10208b7' and abs((b['y']-a['y'])*100-84)<.01 and abs(b['vy']-80.8)<.01
assert b['animation']==41 and b['health']==0x840
assert result['vanillaHealthUnchanged']
assert 'native-player-closed' in log and 'world-save dim=-1' in log
assert not re.search('moved wrongly|moved too quickly',log,re.I)
mesh=[float(s) for s in re.findall(r'collision-mesh.*?milliseconds=([0-9.]+)',log)]
value={'passed':True,'nativeSteps':1200,'naturalSameSaveDimension':-1,'phaseChecks':checks,'meshSamples':len(mesh),'meshMaximumMilliseconds':max(mesh) if mesh else None,'meshAbove33ms':sum(x>1000/30 for x in mesh),'blockedBurningRunPairs':blocked,'actualFirstLavaRiseUnits':(b['y']-a['y'])*100,'actualFirstLavaVerticalVelocity':b['vy'],'terrainEdits':0,'limits':['Natural Nether generation and setup-only relocations; no terrain edits or forced actions/velocities.','Existing Project64 references reused; no new emulator capture.','Synthetic smoke separately verifies exact first bounce and water/protection gates.']}
out.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
print(json.dumps(value))
