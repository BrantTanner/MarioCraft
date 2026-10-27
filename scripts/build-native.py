"""Pinned native backend plus mod-owned entry shims; generated DLL stays local."""
from pathlib import Path
import subprocess, sys, os, hashlib, json

root = Path(__file__).resolve().parents[1]
local = root / '.local'
pin = 'fd11813208272b4271d92bd92feb8f3fdbe61be5'
source = local / ('libsm64-' + pin)
compiler = local / 'llvm-mingw-20241001-ucrt-x86_64/bin/x86_64-w64-mingw32-clang.exe'
if not compiler.exists() or not source.exists():
    raise SystemExit('Run python scripts/fetch-native.py first')
out = local / 'native'
out.mkdir(exist_ok=True)
manifest = out / 'build-manifest.json'
dll = out / 'sm64.dll'

def digest_inputs():
    inputs = sorted(p for p in (source / 'src').rglob('*') if p.is_file())
    inputs += [Path(__file__), source / 'import-mario-geo.py', root / 'scripts/native-abi.c', root / 'scripts/native-hazards.c', root / 'scripts/native-sound.c']
    digest = hashlib.sha256(pin.encode())
    for path in inputs:
        digest.update(str(path.relative_to(root)).encode())
        digest.update(path.read_bytes())
    digest.update(compiler.read_bytes())
    return digest.hexdigest()

# Content, not timestamp or ABI number, invalidates the build cache.
try:
    previous = json.loads(manifest.read_text())
except (OSError, ValueError):
    previous = {}
if dll.exists() and previous.get('inputs') == digest_inputs() and previous.get('dll') == hashlib.sha256(dll.read_bytes()).hexdigest():
    print('Native inputs and DLL verified; hazard ABI 1')
    sys.exit(0)
subprocess.run([sys.executable, 'import-mario-geo.py'], cwd=source, check=True)
# Enumerate after the importer so a cold checkout includes generated C files.
folders = ['src', 'src/decomp', 'src/decomp/engine', 'src/decomp/include/PR', 'src/decomp/game', 'src/decomp/pc', 'src/decomp/pc/audio', 'src/decomp/mario', 'src/decomp/tools', 'src/decomp/audio']
files = [str(p.relative_to(source)).replace('\\', '/') for folder in folders for p in sorted((source / folder).glob('*.c'))]
files.remove('src/play_sound.c')
files.append(str(root / 'scripts/native-sound.c'))
staged = out / 'sm64.build.dll'
command = [str(compiler), '-shared', '-O2', '-fno-strict-aliasing', '-Wno-incompatible-pointer-types', '-Wno-implicit-function-declaration', '-Wno-int-conversion', '-Wno-pointer-sign', '-Wno-format', '-DSM64_LIB_EXPORT', '-DGBI_FLOATS', '-DVERSION_US', '-DNO_SEGMENTED_MEMORY', '-Isrc/decomp/include', '-Isrc', *files, str(root / 'scripts/native-abi.c'), str(root / 'scripts/native-hazards.c'), '-o', str(staged), '-lm']
subprocess.run(command, cwd=source, check=True)
# Load only in a short-lived child so Windows releases the staged file before
# replacement. Export check does not initialize game resources.
subprocess.run([sys.executable, '-c', 'import ctypes,sys; x=ctypes.CDLL(sys.argv[1]); assert x.mariocraft_hazard_abi()==1; assert x.mariocraft_hazard_contact', str(staged)], check=True)
os.replace(staged, dll)
manifest.write_text(json.dumps({'upstream': pin, 'inputs': digest_inputs(), 'dll': hashlib.sha256(dll.read_bytes()).hexdigest(), 'hazardAbi': 1}), encoding='utf-8')
print('Built local native backend with mod-owned hazard entry adapter')
