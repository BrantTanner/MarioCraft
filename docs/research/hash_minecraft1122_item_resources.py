import csv,hashlib,pathlib,sys,zipfile
jar=pathlib.Path(sys.argv[1]); out=pathlib.Path(sys.argv[2])
expected='8ada07da5ee77dad3527bd7278fbd05ee1fc8a597813b216a871a2d7d64cc64f'
got=hashlib.sha256(jar.read_bytes()).hexdigest()
if got!=expected: raise SystemExit(f'client jar SHA-256 mismatch: {got}')
rows=[]
with zipfile.ZipFile(jar) as z:
 for entry in z.infolist():
  p=entry.filename
  if p.startswith('assets/minecraft/recipes/') and p.endswith('.json'): kind='recipe'
  elif p.startswith('assets/minecraft/loot_tables/') and p.endswith('.json'): kind='loot_table'
  else: continue
  data=z.read(entry)
  rows.append((kind,p,len(data),hashlib.sha256(data).hexdigest()))
if sum(r[0]=='recipe' for r in rows)!=432 or sum(r[0]=='loot_table' for r in rows)!=82: raise SystemExit(f'unexpected resource inventory: {len(rows)}')
out.parent.mkdir(parents=True,exist_ok=True)
with out.open('w',newline='',encoding='utf-8') as f:
 w=csv.writer(f);w.writerow(['kind','original_client_jar_path','uncompressed_bytes','entry_sha256']);w.writerows(sorted(rows))
print(f'hashed recipes={sum(r[0]=="recipe" for r in rows)} loot_tables={sum(r[0]=="loot_table" for r in rows)} total={len(rows)} client_sha256={got}')

