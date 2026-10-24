import csv,sys
catalog=list(csv.DictReader(open(sys.argv[1],encoding='utf-8')))
runtime=list(csv.DictReader(open(sys.argv[2],encoding='utf-8')))
items={(int(r['numeric_id']),r['registry_name'].removeprefix('minecraft:')) for r in runtime if r['kind']=='item'}
blocks={(int(r['numeric_id']),r['registry_name'].removeprefix('minecraft:')) for r in runtime if r['kind']=='block'}
relations={(int(r['numeric_id']),r['registry_name'].removeprefix('minecraft:'),int(r['block_numeric_id']),r['block_registry_name'].removeprefix('minecraft:')) for r in runtime if r['kind']=='block-item' and (r['registry_name']!='minecraft:air' or int(r['block_numeric_id'])==0)}
cat_items={(int(r['numeric_id_1_12_2']),r['registry_name']) for r in catalog}
cat_rel={(int(r['numeric_id_1_12_2']),r['registry_name'],int(r['numeric_id_1_12_2']),r['registry_name']) for r in catalog if 'ItemBlock' in r['registration_kind']}
assert len(items)==len(cat_items)==411 and items==cat_items
assert len(blocks)==254
assert len(relations)==len(cat_rel)==202 and relations==cat_rel
print('item identities: 411/411 exact; block identities: 254/254; block-item registrations: 202/202 exact; differences: 0')

