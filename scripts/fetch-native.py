"""Project-local upstream downloads. Native output contains imported Mario geometry; never publish it."""
from pathlib import Path
import urllib.request, zipfile, hashlib

ROOT = Path(__file__).resolve().parents[1] / '.local'
ROOT.mkdir(exist_ok=True)
SOURCES = [
    ('llvm.zip', 'https://github.com/mstorsjo/llvm-mingw/releases/download/20241001/llvm-mingw-20241001-ucrt-x86_64.zip', '09b9aecf4a9ceb4ad942aa8b6b6126895678383532f565e26219adc493e50466'),
    ('libsm64.zip', 'https://codeload.github.com/libsm64/libsm64/zip/fd11813208272b4271d92bd92feb8f3fdbe61be5', '693d94a895388e8f60dff09d65739f75c4628946b5460ecd0dadbebec6293578'),
]
for name, url, expected in SOURCES:
    path = ROOT / name
    if not path.exists():
        urllib.request.urlretrieve(url, path)
    actual = hashlib.sha256(path.read_bytes()).hexdigest()
    if actual != expected:
        raise SystemExit('Archive SHA-256 mismatch: ' + name)
    print(name, actual, flush=True)
    with zipfile.ZipFile(path) as archive:
        archive.extractall(ROOT)
