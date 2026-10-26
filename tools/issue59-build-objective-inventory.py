import re,json,pathlib,subprocess,argparse
P='9921382a68bb0c865e5e45eb594d9c64db59b1af'
p=argparse.ArgumentParser();p.add_argument('--source-root',required=True);p.add_argument('--manifest',required=True);p.add_argument('--output',required=True);a=p.parse_args();root=pathlib.Path(a.source_root)
assert subprocess.check_output(['git','-C',str(root),'rev-parse','HEAD'],text=True).strip()==P
assert (root/'text/us/courses.h').read_bytes().replace(b'\r\n',b'\n')==subprocess.check_output(['git','-C',str(root),'show',P+':text/us/courses.h'])
m=json.loads(pathlib.Path(a.manifest).read_text(encoding='utf-8'));assert m['source']['commit']==P
traces={
 'bhvWhompKingBoss':('boss defeat action completes its dialog and spawns the star','src/game/behaviors/whomp.inc.c:225'),
 'bhvCCMTouchedStarSpawn':('slide-entry flag is set when the exit spawner runs','src/game/behaviors/spawn_star_exit.inc.c:3'),
 'bhvTuxiesMother':('mother interaction reaches the reward state after returning the baby','src/game/behaviors/tuxie.inc.c:55'),
 'bhvRacingPenguin':('winning flag remains set after final dialog','src/game/behaviors/racing_penguin.inc.c:144'),
 'bhvSnowmansHead':('head reward action finishes dialog','src/game/behaviors/snowman.inc.c:218'),
 'bhvGhostHuntBigBoo':('ghost hunt Big Boo defeat reaches its star spawn','src/game/behaviors/boo.inc.c:574'),
 'bhvMerryGoRoundBooManager':('merry-go-round Big Boo defeat reaches its star spawn','src/game/behaviors/boo.inc.c:582'),
 'bhvBalconyBigBoo':('balcony Big Boo defeat reaches its star spawn','src/game/behaviors/boo.inc.c:578'),
 'bhvMrI':('qualifying Mr I death action reaches timer 104','src/game/behaviors/mr_i.inc.c:130'),
 'bhvBigBully':('large bully death path spawns a star','src/game/behaviors/bully.inc.c:195'),
 'bhvBigBullyWithMinions':('big bully lava-death helper reports death','src/game/behaviors/bully.inc.c:291'),
 'bhvBigChillBully':('chill bully death path spawns a star','src/game/behaviors/bully.inc.c:195'),
 'bhvKlepto':('hit Klepto while holding a star','src/game/behaviors/klepto.inc.c:360'),
 'bhvEyerokBoss':('boss death action reaches timer 60 and completes dialog','src/game/behaviors/eyerok.inc.c:119'),
 'bhvJetStreamRingSpawner':('collect five jet-stream rings','src/game/behaviors/water_ring.inc.c:178'),
 'bhvMantaRay':('collect five manta rings','src/game/behaviors/manta_ray.inc.c:110'),
 'bhvUkikiCage':('cage star object reaches its spawn-star action','src/game/behaviors/ukiki_cage.inc.c:36'),
 'bhvWigglerHead':('defeat action reaches shrink threshold after timer 20','src/game/behaviors/wiggler.inc.c:351'),
 'bhvStar':('touch the placed star; area and act mask govern declaration','src/game/interaction.c:769'),
 'bhvHiddenRedCoinStar':('red-coin counter reaches 8 and controller spawns star','src/game/behaviors/spawn_star.inc.c:145'),
 'bhvHiddenStar':('five hidden triggers increment counter to 5 and controller spawns star','src/game/behaviors/hidden_star.inc.c:3'),
 'bhvFirePiranhaPlant':('five qualifying fire piranha plants die, incrementing shared kill counter to 5','src/game/behaviors/fire_piranha_plant.inc.c:51'),
 'bhvKingBobomb':('defeat sequence reaches final dialog and completes it','src/game/behaviors/king_bobomb.inc.c:199'),
 'bhvUnagi':('active star-bearing eel segment approaches Mario within 150 units','src/game/behaviors/unagi.inc.c:170'),
 'bhvTreasureChestsJRB':('chest sequence counter reaches 5, then action timer reaches 60','src/game/behaviors/treasure_chest.inc.c:156'),
 'bhvTreasureChestsDDD':('chest sequence counter reaches 5, then action timer reaches 60','src/game/behaviors/treasure_chest.inc.c:189'),
 'bhvKoopa':('race endpoint registers eligible nonzero win and finishes dialog','src/game/behaviors/koopa.inc.c:710'),
}
lines=(root/'text/us/courses.h').read_text().splitlines(); rows=[]
for i,line in enumerate(lines):
 match=re.match(r'COURSE_ACTS\((COURSE_\w+),',line)
 if not match:continue
 titles=re.findall(r'_\("([^"]*)"\)',' '.join(lines[i:i+3]));assert len(titles)==7
 course=match.group(1);folder=course[7:].lower();path=f'levels/{folder}/script.c'; candidates={j:[] for j in range(1,7)}
 for area in m['script_c_files'][path]['areas']:
  for o in area['object_placements']:
   if not o['usa_active']:continue
   found=re.search(r'STAR_INDEX_ACT_([1-6])',' '.join(o['args']))
   if found:
    behavior=next((v for v in o['args'] if v.startswith('bhv')),None)
    if behavior:candidates[int(found.group(1))].append({'source':f"{path}:{o['line']}",'area':area['index'],'behavior':behavior,'object_macro':o['macro'],'act_mask':o['args'][-1] if o['macro']=='OBJECT_WITH_ACTS' else 'implicit all acts','linked_via':o.get('via',[]),'evidence':'active USA same-index declaration; objective linkage and reachability unproved'})
  for ref in area['macro_object_tables']:
   if not ref['usa_active']:continue
   table=m['macro_object_tables'].get(ref['args'][0]);
   if not table:continue
   for o in table['entries']:
    found=re.fullmatch(r'macro_box_star_act_([1-6])',o['args'][0])
    if found:candidates[int(found.group(1))].append({'source':f"{table['source']}:{o['line']}",'area':area['index'],'behavior':'macro_box_star_act_'+found.group(1),'evidence':'referenced macro-table same-index box declaration; reachability and objective linkage unproved'})
 for entries in candidates.values():
  for c in entries:
   key=c['behavior']; trace=traces.get(key)
   if key.startswith('macro_box_star_act_') or key=='bhvExclamationBox': trace=('break the configured box to spawn its content; verify its source parameter','src/game/behaviors/exclamation_box.inc.c:23')
   if trace:c['source_behavior_trigger'],c['behavior_source']=trace
 for j,title in enumerate(titles[1:],1): rows.append({'course':course,'act_star_ordinal':j,'star_index':j-1,'act_selection':j,'title':title,'title_source':f'text/us/courses.h:{i+1}','matching_index_candidates':candidates[j], 'trigger':'see named objective; exact trigger trace unresolved unless a linked behavior is independently traced','result':'star/key interaction can set corresponding course star flag; exit behavior depends on subtype/action','runtime':'unobserved'})
 rows.append({'course':course,'act_star_ordinal':7,'star_index':6,'act_selection':None,'title':'100 coins','trigger':'coin interaction crosses from below 100 to at least 100 in a main course','result':'spawn no-level-exit star with index 6; collection updates course star flag','source':'src/game/interaction.c:745-752','runtime':'unobserved'})
assert len(rows)==105
pathlib.Path(a.output).write_text(json.dumps({'schema_version':2,'source_commit':P,'evidence_boundary':'source declarations only; names do not prove activation, placement linkage, exact trigger or runtime execution','objectives':rows},indent=2)+'\n',encoding='utf-8',newline='\n')
print('rows',len(rows),'named rows with same-index candidates',sum(bool(r.get('matching_index_candidates')) for r in rows))
