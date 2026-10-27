"""Strict scalar verification of private integrated issue-30 evidence."""
import argparse
import json
import re
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument('--result', type=Path, default=Path('.local/run-client/gameplay-result.json'))
p.add_argument('--log', type=Path, default=Path('.local/run-client/logs/latest.log'))
p.add_argument('--out', type=Path)
a = p.parse_args()
r = json.loads(a.result.read_text(encoding='utf-8-sig'))
required = ['title', 'nonlocalConnectionRejected', 'multiplayerBlocked', 'settings',
            'audioSettings', 'keyboardSettings', 'controllerSettings',
            'settingsPersistOff', 'settingsPersistOn', 'worldSelection',
            'audioContextRecreated', 'twoAudioCloses', 'passed']
per_load = ['useBlocked', 'placeBlocked', 'mineBlocked', 'itemPickupBlocked',
            'xpPickupBlocked', 'portalBlocked', 'commandModeBlocked', 'commandGuard',
            'nativeVoice', 'nativeAction', 'pcm', 'managedPlayback',
            'musicVolumeRetained', 'minecraftMusic', 'alternatePackets', 'movement',
            'inventoryBlocked', 'creativeInventoryBlocked', 'lanBlocked', 'pause',
            'pauseFlush', 'playersMute', 'masterMute', 'masterGain', 'focusFlush',
            'dropsRemain', 'mobSimulation', 'inventoryPreserved', 'cleanup', 'audioClosed']
required += [k + str(i) for i in range(2) for k in per_load]
errors = [k for k in required if r.get(k) is not True]
errors += [k for k, v in r.items() if isinstance(v, bool) and not v]
for i in range(2):
    for name in ['pcmPeak', 'voiceEvents', 'actionEvents']:
        if type(r.get(name + str(i))) is not int or r[name + str(i)] <= 0:
            errors.append(name + str(i))
log = a.log.read_text(encoding='utf-8', errors='replace')
world = log[log.find('MARIOCRAFT server-starting'):]
counts = {
    'nativeCreates': log.count('MARIOCRAFT native-player-created'),
    'nativeCloses': log.count('MARIOCRAFT native-player-closed'),
    'serverStarts': log.count('MARIOCRAFT server-starting'),
    'soundEngines': log.count('Sound engine started'),
    'runtimeErrors': len(re.findall(r'\[(?:Client|Server) thread/(?:ERROR|FATAL)\]', world)),
    'movementWarnings': len(re.findall(r'moved too quickly|moved wrongly', world)),
    'audioFailures': len(re.findall(r'MARIOCRAFT audio unavailable|audio backend already unavailable', world)),
}
for k in ['nativeCreates', 'nativeCloses', 'serverStarts', 'soundEngines']:
    if counts[k] != 2:
        errors.append(k)
for k in ['runtimeErrors', 'movementWarnings', 'audioFailures']:
    if counts[k] != 0:
        errors.append(k)
out = {'schemaVersion': 1, 'passed': not errors,
       'booleanChecks': len(required), 'counts': counts, 'results': r}
if errors:
    out['failedChecks'] = sorted(set(errors))
text = json.dumps(out, indent=2) + '\n'
if a.out:
    a.out.parent.mkdir(parents=True, exist_ok=True)
    a.out.write_text(text, encoding='utf-8', newline='\n')
print(text)
raise SystemExit(0 if not errors else 1)
