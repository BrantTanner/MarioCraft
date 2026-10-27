"""Build pinned upstream locally with the portable compiler; no game-derived DLL is packaged."""
from pathlib import Path
import subprocess, sys, importlib.util, os

root = Path(__file__).resolve().parents[1]
local = root / '.local'
source = local / 'libsm64-fd11813208272b4271d92bd92feb8f3fdbe61be5'
compiler = local / 'llvm-mingw-20241001-ucrt-x86_64/bin/x86_64-w64-mingw32-clang.exe'
if not compiler.exists() or not source.exists():
    raise SystemExit('Run python scripts/fetch-native.py first')
subprocess.run([sys.executable, 'import-mario-geo.py'], cwd=source, check=True)
out = local / 'native'
out.mkdir(exist_ok=True)
folders = ['src', 'src/decomp', 'src/decomp/engine', 'src/decomp/include/PR', 'src/decomp/game', 'src/decomp/pc', 'src/decomp/pc/audio', 'src/decomp/mario', 'src/decomp/tools', 'src/decomp/audio']
files = [str(p.relative_to(source)).replace('\\', '/') for folder in folders for p in sorted((source / folder).glob('*.c'))]
command = [str(compiler), '-shared', '-O2', '-fno-strict-aliasing', '-Wno-incompatible-pointer-types', '-Wno-implicit-function-declaration', '-Wno-int-conversion', '-Wno-pointer-sign', '-Wno-format', '-DSM64_LIB_EXPORT', '-DGBI_FLOATS', '-DVERSION_US', '-DNO_SEGMENTED_MEMORY', '-Isrc/decomp/include', '-Isrc', *files, str(root / 'scripts/native-abi.c'), '-o', str(out / 'sm64.dll'), '-lm']
subprocess.run(command, cwd=source, check=True)
print('Built local native backend:', out / 'sm64.dll')
