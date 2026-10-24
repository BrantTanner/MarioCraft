import csv,pathlib,re,sys
source=pathlib.Path(sys.argv[1])
output=pathlib.Path(sys.argv[2])
item=(source/'ain.java').read_text(encoding='utf-8')
block=(source/'aow.java').read_text(encoding='utf-8')
blocks=(source/'aox.java').read_text(encoding='utf-8')
block_rows=[(int(m.group(1)),m.group(2) or 'air') for m in re.finditer(r'aow\.a\((\d+),\s*(?:"([^"]+)"|a),',block)]
block_by_name={name:bid for bid,name in block_rows}
block_fields={m.group(1):m.group(2) for m in re.finditer(r'^\s*(\w+)\s*=.*?aox\.a\("([^"]+)"\)',blocks,re.M)}
explicit=list(re.finditer(r'ain\.a\((\d+),\s*"([^"]+)"\s*,',item))
custom=list(re.finditer(r'ain\.a\(\s*(?:\(aow\)\s*)?aox\.(\w+)\s*,',item))
default=list(re.finditer(r'ain\.b\(\s*(?:\(aow\)\s*)?aox\.(\w+)\s*\)',item))
rows=[[int(m.group(1)),m.group(2),'explicit-id item','',''] for m in explicit]
for kind,matches in [('custom ItemBlock',custom),('default ItemBlock',default)]:
 for m in matches:
  field=m.group(1); name=block_fields.get(field)
  if name is None or name not in block_by_name: raise SystemExit(f'unmatched block helper call: {field}')
  rows.append([block_by_name[name],name,kind,field,''])
rows=sorted({(r[0],r[1],r[2]):r for r in rows}.values(),key=lambda r:(r[0] if r[0] is not None else 99999,r[1]))
if len(rows)!=411 or len(explicit)!=209 or len(custom)!=56 or len(default)!=146 or len(block_rows)!=254 or len(block_fields)!=254:
 raise SystemExit(f'unexpected grammar counts rows={len(rows)} explicit={len(explicit)} custom={len(custom)} default={len(default)} blocks={len(block_rows)} fields={len(block_fields)}')
with output.open('w',newline='',encoding='utf-8') as f:
 w=csv.writer(f);w.writerow(['numeric_id_1_12_2','registry_name','registration_kind','block_field','notes']);w.writerows(rows)
print(f'blocks={len(block_rows)} block_fields={len(block_fields)} item registrations={len(rows)} direct={len(explicit)} custom_block_item={len(custom)} default_block_item={len(default)}')

