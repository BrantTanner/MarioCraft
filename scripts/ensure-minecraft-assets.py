"""Populate ForgeGradle's asset cache over HTTPS, verifying Mojang's indexed hashes."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import re
from pathlib import Path
import urllib.request

parser = argparse.ArgumentParser()
parser.add_argument('--gradle-home', required=True)
args = parser.parse_args()
cache = Path(args.gradle_home) / 'caches/forge_gradle'
metadata = json.loads((cache / 'minecraft_repo/versions/1.12.2/version.json').read_text())
index_info = metadata['assetIndex']
if index_info['id'] != '1.12':
    raise RuntimeError('Unexpected asset index for pinned Minecraft 1.12.2')
assets = cache / 'assets'

def ensure(path, url, digest, size):
    if not re.fullmatch(r'[0-9a-f]{40}', digest) or not isinstance(size, int) or size < 0:
        raise RuntimeError('Invalid Minecraft asset hash/size')
    def valid(data):
        return len(data) == size and hashlib.sha1(data).hexdigest() == digest
    if path.exists() and valid(path.read_bytes()):
        return 0
    with urllib.request.urlopen(url, timeout=60) as response:
        data = response.read()
    if not valid(data):
        raise RuntimeError('Minecraft asset size/hash mismatch: ' + digest)
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix('.download')
    temporary.write_bytes(data)
    temporary.replace(path)
    return 1

index = assets / 'indexes' / (index_info['id'] + '.json')
ensure(index, index_info['url'], index_info['sha1'], index_info['size'])
# Deduplicate aliases before parallel downloads (several icon paths share one hash).
objects = {obj['hash']: obj['size'] for obj in json.loads(index.read_text())['objects'].values()}
def fetch(item):
    digest, size = item
    relative = digest[:2] + '/' + digest
    return ensure(assets / 'objects' / relative,
                  'https://resources.download.minecraft.net/' + relative, digest, size)
with ThreadPoolExecutor(max_workers=8) as pool:
    downloaded = sum(pool.map(fetch, objects.items()))
print('Verified Minecraft assets:', len(objects), 'objects;', downloaded, 'downloaded over HTTPS')
