"""Launch the private packaged client. Uses no account credentials or network login."""
from pathlib import Path
import argparse,json,subprocess,uuid,os,shutil,time,hashlib
p=argparse.ArgumentParser();p.add_argument('--probe',choices=['presentation','combat','distance','admission','hud']);p.add_argument('--verify',action='store_true');a=p.parse_args()
root=Path(__file__).resolve().parent;m=json.loads((root/'runtime.json').read_text(encoding='utf-8'));game=root/('probe-game' if a.probe else 'game')
for rel,expected in m['sha256'].items():
    if hashlib.sha256((root/rel).read_bytes()).hexdigest()!=expected: raise SystemExit('Runtime checksum mismatch: '+rel)
if a.verify:
    print('Verified private runtime:',len(m['sha256']),'payload hashes');raise SystemExit(0)
if a.probe:
    (game/'mods').mkdir(parents=True,exist_ok=True);shutil.copy2(root/'game/mods/mariocraft-0.1.0.jar',game/'mods/mariocraft-0.1.0.jar')
args=[str(root/'runtime/bin/java.exe'),'-Xmx2G','-Djava.library.path='+str(root/'natives'),'-Dmariocraft.native='+str(root/'sm64.dll')]
if a.probe:
    args+=['-Dmariocraft.'+a.probe+'Probe=true']
    if a.probe=='hud': args+=['-Dmariocraft.presentationProbe=true']
    if a.probe not in ('presentation','admission','hud'): args+=['-Dmariocraft.gameplayProbe=true','-Dmariocraft.clientProbe=true']
cp=os.pathsep.join(str(root/x) for x in m['classpath'])
args+=['-cp',cp,m['mainClass'],'--tweakClass','net.minecraftforge.fml.common.launcher.FMLTweaker','--username','MarioPlayer','--version',m['forge'],'--gameDir',str(game),'--assetsDir',str(root/'assets'),'--assetIndex',m['assetIndex'],'--uuid',uuid.uuid3(uuid.NAMESPACE_DNS,'OfflinePlayer:MarioPlayer').hex,'--accessToken','0','--userType','legacy','--versionType','Forge']
# Token above is the literal offline placeholder; no authentication secrets are read.
result=game/'gameplay-result.json'
if a.probe and result.exists():
    archive=game/'probe-results'/str(time.time_ns());archive.mkdir(parents=True)
    for item in [result,game/'logs/latest.log']+list(game.glob('*-trace.jsonl'))+list((game/'screenshots').glob('mariocraft-*.png')):
        if item.exists(): shutil.copy2(item,archive/item.name)
    result.unlink()
code=subprocess.call(args,cwd=game)
if code: raise SystemExit(code)
if a.probe and (not result.exists() or not json.loads(result.read_text(encoding='utf-8')).get('passed')): raise SystemExit('Packaged runtime probe did not pass')
