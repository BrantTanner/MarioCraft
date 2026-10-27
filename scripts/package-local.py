"""Assemble a PRIVATE local Windows runtime from pinned official manifests.
No credential lookup, launcher modification, or public game-asset distribution.
"""
from pathlib import Path
import argparse, hashlib, json, shutil, urllib.request, zipfile
from concurrent.futures import ThreadPoolExecutor
p=argparse.ArgumentParser();p.add_argument('--java-home',required=True);p.add_argument('--output',type=Path);a=p.parse_args()
root=Path(__file__).resolve().parents[1];local=root/'.local';out=a.output.resolve() if a.output else local/'MarioCraft';out.mkdir(parents=True,exist_ok=True)
cache=local/'gradle-home/caches/forge_gradle'
def fetch(path,url,digest=None,algorithm='sha1',size=None):
    def valid(data): return (digest is None or hashlib.new(algorithm,data).hexdigest()==digest) and (size is None or len(data)==size)
    if path.exists() and valid(path.read_bytes()): return path
    if not url.startswith('https://'): raise RuntimeError('HTTPS dependency URL required')
    urls=[url]
    if 'https://files.minecraftforge.net/maven/' in url: urls.append(url.replace('https://files.minecraftforge.net/maven/','https://maven.minecraftforge.net/'))
    if 'maven.minecraftforge.net/' in url:
        urls.append(url.replace('maven.minecraftforge.net/','files.minecraftforge.net/maven/'))
        urls.append(url.replace('https://maven.minecraftforge.net/','https://repo.maven.apache.org/maven2/'))
        urls.append(url.replace('https://maven.minecraftforge.net/','https://libraries.minecraft.net/'))
    errors=[];data=None
    for attempt in range(3):
        for u in urls:
            try:
                with urllib.request.urlopen(urllib.request.Request(u,headers={"User-Agent":"Mozilla/5.0"}),timeout=60) as r: data=r.read()
                break
            except Exception as ex: errors.append(type(ex).__name__+': '+str(ex))
        if data is not None: break
    if data is None: raise RuntimeError('Dependency download failed: '+path.name+'; '+ '; '.join(errors))
    if not valid(data): raise RuntimeError('Dependency hash/size mismatch: '+path.name)
    path.parent.mkdir(parents=True,exist_ok=True);temp=path.with_suffix('.download');temp.write_bytes(data);temp.replace(path);return path
installer=fetch(local/'forge-installer.jar','https://files.minecraftforge.net/maven/net/minecraftforge/forge/1.12.2-14.23.5.2864/forge-1.12.2-14.23.5.2864-installer.jar','2c0065938de6df6f3deb4a08a3018940a1cc86111adb34a3be55d657857a11cc','sha256')
with zipfile.ZipFile(installer) as z:
    forge=json.loads(z.read('version.json'));f=forge['libraries'][0]['downloads']['artifact'];dest=out/'libraries'/f['path'];dest.parent.mkdir(parents=True,exist_ok=True);data=z.read('maven/'+f['path'])
    if hashlib.sha1(data).hexdigest()!=f['sha1']: raise RuntimeError('Bundled Forge hash mismatch')
    dest.write_bytes(data)
# This metadata is already downloaded and verified by ForgeGradle during the build.
vanilla=json.loads((cache/'minecraft_repo/versions/1.12.2/version.json').read_text(encoding='utf-8'))
client=vanilla['downloads']['client'];fetch(out/'minecraft.jar',client['url'],client['sha1'],size=client['size'])
def allowed(lib):
    result='rules' not in lib
    for rule in lib.get('rules',[]):
        if 'os' not in rule or rule['os'].get('name')=='windows': result=rule['action']=='allow'
    return result
# Forge overrides coordinates from the base manifest (notably Log4j).
libs={}
for lib in vanilla['libraries']+forge['libraries']:
    if allowed(lib): libs[':'.join(lib['name'].split(':')[:2])]=lib
optional=[name for name in libs if name.startswith(('org.scala-lang:', 'org.scala-lang.plugins:', 'org.scala-lang.modules:', 'com.typesafe:', 'com.typesafe.akka:'))]
excluded=[libs.pop(name)['name'] for name in optional]
classpath=[];natives=[]
for lib in libs.values():
    downloads=lib.get('downloads',{})
    if 'artifact' in downloads: classpath.append(downloads['artifact'])
    if 'natives' in lib and 'windows' in lib['natives']: natives.append(downloads['classifiers'][lib['natives']['windows'].replace('${arch}','64')])
def library(item):
    path=out/'libraries'/item['path']
    return fetch(path,item['url'],item['sha1'],size=item['size'])
# The installer supplied this one verified artifact; it intentionally has no URL.
with ThreadPoolExecutor(max_workers=8) as pool: list(pool.map(library,[i for i in classpath+natives if i['url']]))
(out/'natives').mkdir(exist_ok=True)
for item in natives:
    with zipfile.ZipFile(out/'libraries'/item['path']) as z:
        for name in z.namelist():
            if name.endswith('.dll') and '/' not in name: (out/'natives'/name).write_bytes(z.read(name))
# Copy only indexed/verified asset objects, never worlds or logs from the development client.
info=vanilla['assetIndex'];assets=out/'assets';index=fetch(assets/'indexes'/(info['id']+'.json'),info['url'],info['sha1'],size=info['size'])
objects={o['hash']:o['size'] for o in json.loads(index.read_text(encoding='utf-8'))['objects'].values()}
def asset(pair):
    h,size=pair;path=assets/'objects'/h[:2]/h;cached=cache/'assets/objects'/h[:2]/h
    if not path.exists() and cached.exists() and cached.stat().st_size==size and hashlib.sha1(cached.read_bytes()).hexdigest()==h:
        path.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(cached,path)
    fetch(path,'https://resources.download.minecraft.net/'+h[:2]+'/'+h,h,size=size)
with ThreadPoolExecutor(max_workers=8) as pool: list(pool.map(asset,objects.items()))
# JNA is an ordinary dependency of the mod, available from the verified build cache.
jna=list((local/'gradle-home/caches/modules-2/files-2.1/net.java.dev.jna/jna/4.4.0').rglob('jna-4.4.0.jar'))
if len(jna)!=1: raise RuntimeError('Expected one pinned JNA build dependency')
shutil.copy2(jna[0],out/'jna.jar');shutil.copy2(local/'native/sm64.dll',out/'sm64.dll')
mods=out/'game/mods';mods.mkdir(parents=True,exist_ok=True);shutil.copy2(root/'build/libs/mariocraft-0.1.0.jar',mods/'mariocraft-0.1.0.jar')
# A private runtime remains self-contained after this assembly, except the authorized ROM discovery.
shutil.copytree(Path(a.java_home)/'jre',out/'runtime',dirs_exist_ok=True)
cp=['libraries/'+i['path'] for i in classpath]+['minecraft.jar','jna.jar']
manifest={'format':1,'minecraft':'1.12.2','forge':forge['id'],'assetIndex':info['id'],'mainClass':forge['mainClass'],'classpath':cp,'privateLocalOnly':True,'assetObjects':len(objects),'excludedOptionalScalaLibraries':excluded}
manifest['sha256']={str(path.relative_to(out)).replace('\\','/'):hashlib.sha256(path.read_bytes()).hexdigest() for path in [out/'sm64.dll',out/'jna.jar',mods/'mariocraft-0.1.0.jar',out/'minecraft.jar',out/'runtime/bin/java.exe']+[out/x for x in cp if x.startswith('libraries/')]}
(out/'runtime.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
shutil.copy2(root/'scripts/play.py',out/'play.py')
(out/'Launch MarioCraft.cmd').write_text('@echo off\r\ncd /d "%~dp0"\r\npython play.py\r\nif errorlevel 1 pause\r\n',encoding='utf-8')
print('Private playable runtime assembled:',out,'assets:',len(objects),'libraries:',len(cp))
