"""Independent checks over private final production-run evidence; output contains only scalars."""
from pathlib import Path
import argparse,json,re
p=argparse.ArgumentParser();p.add_argument('evidence',type=Path);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
checks={};results={}
for mode in ['combat','distance','presentation','admission','clean','hud']:
    r=json.loads((a.evidence/mode/'result.json').read_text(encoding='utf-8-sig'))
    log=(a.evidence/mode/'latest.log').read_text(encoding='utf-8',errors='replace')
    world=log[log.index('MARIOCRAFT server-starting'):]
    checks[mode+'Passed']=r.get('passed') is True
    checks[mode+'AllRequiredBooleans']=all(v for k,v in r.items() if type(v) is bool and k!='forcedActionOrVelocity')
    checks[mode+'NoWorldErrors']=not re.search(r'\b(?:ERROR|FATAL)\b|moved too quickly|moved wrongly|native step failed|audio disabled after failure|audio unavailable; Mario simulation continues',world)
    checks[mode+'LifecycleClosed']='MARIOCRAFT server-stopped' in world and 'Stopping!' in world
    if mode!='admission': checks[mode+'NativeBalanced']=log.count('MARIOCRAFT native-player-created')==log.count('MARIOCRAFT native-player-closed')>0
    else: checks['admissionNoNativeOwner']='MARIOCRAFT native-player-created' not in log
    results[mode]=r
r=results['distance']
checks['distanceBeyondInitialView']=r.get('distanceBlocks',0)>=176 and r.get('chunkTransitions',0)>=10
checks['distanceRealNewChunks']=r.get('freshNaturalChunks',0)>=5 and r.get('naturalClientSamples',0)>=10 and r.get('naturalServerSamples',0)>=10 and r.get('serverWatcher') is True
checks['distanceInputOnly']=r.get('terrainEdits')==0 and r.get('forcedActionOrVelocity') is False
checks['finalHudClear']=results['hud'].get('tutorialDisabled') is True and results['hud'].get('crosshairHidden') is True and results['hud'].get('cleanup0') is True
checks['cleanMarioHudClear']=results['clean'].get('tutorialDisabled') is True and results['clean'].get('crosshairHidden') is True
checks['cleanProductionAudio']=results['clean'].get('managedPlayback0') is True and results['clean'].get('managedPlayback1') is True
for mode in ['presentation','clean']:
    r=results[mode]
    checks[mode+'MatchedNativeFrames']=all(r.get('matchedNativeFrame'+str(f)) is True for f in [60,100,130,160])
    checks[mode+'NativeRateAcrossRenderCaps']=all(29<=r.get('achievedNativeHz'+str(i),0)<=31 for i in range(2))
    checks[mode+'ActualDistinctRenderRates']=25<=r.get('achievedRenderHz0',0)<=35 and r.get('achievedRenderHz1',0)>=80
report={'passed':all(checks.values()),'checks':checks,'results':results}
a.out.parent.mkdir(parents=True,exist_ok=True);a.out.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'passed':report['passed'],'checks':len(checks),'failed':[k for k,v in checks.items() if not v]}))
if not report['passed']: raise SystemExit(1)
