import json,pathlib,argparse,collections
p=argparse.ArgumentParser();p.add_argument('--manifest',required=True);p.add_argument('--output',required=True);a=p.parse_args();d=json.loads(pathlib.Path(a.manifest).read_text(encoding='utf-8'))
assert d['source']['commit']=='9921382a68bb0c865e5e45eb594d9c64db59b1af'
rows=[]
for path,s in d['script_c_files'].items():
 for area in s['areas']:
  for o in area['object_placements']:
   if o['usa_active'] and any(x in o['args'] for x in ('bhvCannonClosed','bhvCannon','bhvBobombBuddyOpensCannon')):
    rows.append({'kind':'level_object','source':f"{path}:{o['line']}",'area':area['index'],'macro':o['macro'],'args':o['args'],'linked_via':o.get('via',[]),'runtime':'unobserved'})
  for ref in area['macro_object_tables']:
   if not ref['usa_active']:continue
   name=ref['args'][0];table=d['macro_object_tables'].get(name)
   if table:
    for o in table['entries']:
     if o['args'][0] in ('macro_cannon_closed','macro_cannon_open','macro_bobomb_buddy_opens_cannon_1','macro_bobomb_buddy_opens_cannon_2'):
      rows.append({'kind':'macro_object','source':f"{table['source']}:{o['line']}",'area':area['index'],'level_script':path,'macro':o['macro'],'args':o['args'],'runtime':'unobserved'})
pathlib.Path(a.output).write_text(json.dumps({'source_commit':d['source']['commit'],'scope':'active USA level-object placements expanded by issue57, plus referenced macro-table placements; activation/reachability not observed','rows':rows},indent=2)+'\n',encoding='utf-8',newline='\n')
print(len(rows),collections.Counter(x['args'][0] if x['kind']=='macro_object' else next(v for v in x['args'] if v.startswith('bhv')) for x in rows))
