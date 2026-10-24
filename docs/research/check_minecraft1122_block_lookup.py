import csv
rows=list(csv.DictReader(open(__import__('sys').argv[1],encoding='utf-8')))
blocks=[r for r in rows if r['kind']=='block']
lookups=[r for r in rows if r['kind']=='block-item']
air_returns=[r for r in lookups if r['registry_name']=='minecraft:air']
non_air_returns=[r for r in lookups if r['registry_name']!='minecraft:air']
air_block=[r for r in lookups if r['block_registry_name']=='minecraft:air']
non_air_block_air_returns=[r for r in lookups if r['block_registry_name']!='minecraft:air' and r['registry_name']=='minecraft:air']
assert len(blocks)==len(lookups)==254
assert len(air_returns)==53 and len(non_air_returns)==201 and len(air_block)==1 and len(non_air_block_air_returns)==52
assert len(air_block)+len(non_air_block_air_returns)+len(non_air_returns)==254
print('total blocks=254; lookups=254; AIR returns=53; non-AIR returns=201; air-block row=1; non-air blocks returning AIR=52')
print('partition: 1 air block + 52 non-air blocks without item + 201 non-air block-item mappings = 254')

