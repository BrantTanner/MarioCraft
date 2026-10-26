Warning: fs was declared with const; use let for reassignable variables.
Warning: s was declared with const; use let for reassignable variables.
import argparse, json, re, hashlib, subprocess
from pathlib import Path
COMMIT='9921382a68bb0c865e5e45eb594d9c64db59b1af'
parser=argparse.ArgumentParser(description='Build the pinned USA SM64 course and level source manifest.')
parser.add_argument('--source-root',required=True,type=Path,help='Path to an n64decomp/sm64 checkout at the pinned commit')
parser.add_argument('--output',required=True,type=Path,help='Output JSON manifest path')
args=parser.parse_args()
ROOT=args.source_root.resolve(); OUT=args.output.resolve()
head=subprocess.run(['git','-C',str(ROOT),'rev-parse','HEAD'],capture_output=True,text=True,check=True).stdout.strip()
if head.lower()!=COMMIT.lower():
    raise SystemExit(f'Source checkout must be at {COMMIT}; got {head}')
USA={'VERSION_US':True,'VERSION_JP':False,'VERSION_SH':False,'VERSION_EU':False,'VERSION_CN':False,'NO_SEGMENTED_MEMORY':False}

def mask_comments(s):
    """Replace comments with spaces, retaining every character offset and newline."""
    out=list(s); i=0; state='code'; quote=''
    while i<len(s):
        c=s[i]; n=s[i+1] if i+1<len(s) else ''
        if state=='code':
            if c in ('"', "'"): state='string'; quote=c; i+=1; continue
            if c=='/' and n=='*': out[i]=out[i+1]=' '; state='block'; i+=2; continue
            if c=='/' and n=='/': out[i]=out[i+1]=' '; state='line'; i+=2; continue
            i+=1
        elif state=='string':
            if c=='\\': i+=2
            elif c==quote: state='code'; i+=1
            else: i+=1
        elif state=='block':
            if c=='*' and n=='/': out[i]=out[i+1]=' '; state='code'; i+=2
            else:
                if c!='\n': out[i]=' '
                i+=1
        else:
            if c=='\n': state='code'
            else: out[i]=' '
            i+=1
    return ''.join(out)

def line_at(s,pos): return s.count('\n',0,pos)+1

def split_args(s):
    parts=[]; start=0; depth=0; quote=None; esc=False
    for i,c in enumerate(s):
        if quote:
            if esc: esc=False
            elif c=='\\': esc=True
            elif c==quote: quote=None
            continue
        if c in ('"',"'"): quote=c
        elif c=='(': depth+=1
        elif c==')': depth-=1
        elif c==',' and depth==0: parts.append(s[start:i].strip()); start=i+1
    parts.append(s[start:].strip())
    return parts

def calls(src, offset=0, linebase=0):
    """Yield only outer uppercase macro/function calls; nested argument calls are skipped."""
    s=mask_comments(src); i=0; n=len(s)
    while i<n:
        if (s[i].isalpha() or s[i]=='_') and (i==0 or not (s[i-1].isalnum() or s[i-1]=='_')):
            j=i+1
            while j<n and (s[j].isalnum() or s[j]=='_'): j+=1
            name=s[i:j]
            if name.isupper() or (name.startswith('script_func_') and name.upper()==name):
                k=j
                while k<n and s[k].isspace(): k+=1
                if k<n and s[k]=='(':
                    st=k+1; dep=1; q=None; esc=False; z=st
                    while z<n and dep:
                        c=s[z]
                        if q:
                            if esc: esc=False
                            elif c=='\\': esc=True
                            elif c==q: q=None
                        elif c in ('"',"'"): q=c
                        elif c=='(': dep+=1
                        elif c==')': dep-=1
                        z+=1
                    if dep==0:
                        pos=offset+i; lineno=linebase+line_at(src,i)
                        yield {'macro':name,'args':split_args(s[st:z-1]),'line':lineno,'offset':pos}
                        i=z; continue
        i+=1

def tri_not(x): return None if x is None else not x
def tri_and(a,b):
    if a is False or b is False: return False
    if a is None or b is None: return None
    return True
def tri_or(a,b):
    if a is True or b is True: return True
    if a is None or b is None: return None
    return False

def eval_expr(expr):
    x=expr.strip()
    x=re.sub(r'defined\s*\(\s*(\w+)\s*\)',lambda m:str(USA.get(m.group(1))),x)
    x=re.sub(r'defined\s+(\w+)',lambda m:str(USA.get(m.group(1))),x)
    ids=set(re.findall(r'\b[A-Za-z_]\w*\b',x))
    for name in sorted(ids,key=len,reverse=True):
        if name in ('and','or','not','True','False','None'): continue
        if name in USA: x=re.sub(r'\b'+name+r'\b',str(USA[name]),x)
        else: return None
    x=x.replace('&&',' and ').replace('||',' or ')
    x=re.sub(r'!(?!=)',' not ',x)
    try: return bool(eval(x,{'__builtins__':{}},{}))
    except Exception: return None

def conditional_lines(src):
    lines=src.splitlines(); active=True; stack=[]; by_line={}; sections=[]
    for no,line in enumerate(lines,1):
        by_line[no]=active
        m=re.match(r'\s*#\s*(if|ifdef|ifndef|elif|else|endif)\b(.*)',mask_comments(line))
        if not m: continue
        op,arg=m.group(1),m.group(2).strip()
        if op in ('if','ifdef','ifndef'):
            cond=eval_expr(arg) if op=='if' else USA.get(arg) if op=='ifdef' else tri_not(USA.get(arg))
            frame={'directive':op,'expression':arg,'line':no,'parent_active':active,'taken':cond,'active':tri_and(active,cond)}
            stack.append(frame); active=frame['active']; sections.append({'directive':op,'expression':arg,'line':no,'branch_active':active})
        elif op in ('elif','else') and stack:
            f=stack[-1]
            if op=='elif':
                cond=eval_expr(arg); branch=tri_and(tri_and(f['parent_active'],tri_not(f['taken'])),cond); f['taken']=tri_or(f['taken'],cond)
            else:
                branch=tri_and(f['parent_active'],tri_not(f['taken'])); f['taken']=True
            f['active']=branch; active=branch; sections.append({'directive':op,'expression':arg,'line':no,'branch_active':active})
        elif op=='endif' and stack:
            f=stack.pop(); active=f['parent_active']; sections.append({'directive':'endif','expression':'','line':no,'branch_active':active})
    return by_line,sections

def active_at(by_line,line): return by_line.get(line,True)

def array_blocks(src):
    masked=mask_comments(src)
    pat=re.compile(r'\b(?:static\s+)?const\s+LevelScript\s+(\w+)\s*\[\s*\]\s*=\s*\{')
    for m in pat.finditer(masked):
        depth=1; i=m.end(); q=None; esc=False
        while i<len(masked) and depth:
            c=masked[i]
            if q:
                if esc: esc=False
                elif c=='\\': esc=True
                elif c==q: q=None
            elif c in ('"',"'"): q=c
            elif c=='{': depth+=1
            elif c=='}': depth-=1
            i+=1
        if depth==0:
            yield m.group(1),m.start(),m.end(),i-1,src[m.end():i-1]

def parse_level_script(path,level_folders):
    txt=path.read_text(encoding='utf-8'); by_line,cond_sections=conditional_lines(txt)
    arrays={}
    for name,begin,body_start,end,body in array_blocks(txt):
        items=list(calls(body,offset=body_start,linebase=line_at(txt,body_start)-1))
        arrays[name]={'name':name,'line':line_at(txt,begin),'commands':items}
    folder=path.parent.name
    entry_name='level_'+folder+'_entry'
    if entry_name not in arrays:
        # intro/menu have their own entry naming; ending is part of level folder mapping.
        candidates=[n for n in arrays if n.endswith('_entry') or n.startswith('level_main_menu_entry')]
        entry_name=candidates[0] if candidates else None
    local_defs={k:v for k,v in arrays.items() if k.startswith('script_func_local_')}
    local_defs={k:v for k,v in arrays.items() if k.startswith('script_func_local_')}
    entry_name='level_'+folder+'_entry'
    if entry_name not in arrays:
        entry_name=next((n for n in arrays if n.startswith('level_') and not n.startswith('script_func_local_')),None)
    areas=[]; entry_commands=[]; global_refs=[]; script_arrays={}
    for array_name,array in arrays.items():
        active_area=None; array_areas=[]; array_commands=[]
        for source_item in array['commands']:
            item=dict(source_item); item['usa_active']=active_at(by_line,item['line'])
            if item['macro']=='AREA':
                args=item['args']; active_area={'array':array_name,'index':args[0] if args else None,'geometry':args[1] if len(args)>1 else None,'source_line':item['line'],'commands':[]}; array_areas.append(active_area); areas.append(active_area); continue
            if item['macro']=='END_AREA':
                active_area=None; continue
            if active_area is not None: active_area['commands'].append(item)
            else: array_commands.append(item)
            if item['macro']=='JUMP_LINK' and item['args'] and item['args'][0].startswith('script_func_global_'):
                global_refs.append({'function':item['args'][0],'line':item['line'],'array':array_name,'usa_active':item['usa_active']})
        script_arrays[array_name]={'outside_area_commands':array_commands,'area_count':len(array_areas)}
        if array_name==entry_name: entry_commands=array_commands
    def expand(items,pathstack=()):
        output=[]; ref_records=[]; cycles=[]; unresolved=[]
        for item in items:
            if item.get('usa_active') is False: continue
            target=item['args'][0] if item['macro']=='JUMP_LINK' and item['args'] else None
            if target and target.startswith('script_func_local_'):
                ref_records.append({'function':target,'line':item['line'],'via':list(pathstack)})
                if target in pathstack:
                    cycles.append({'function':target,'path':list(pathstack)+[target]}); output.append(dict(item,via=list(pathstack))); continue
                if target not in local_defs:
                    unresolved.append({'function':target,'line':item['line']}); output.append(dict(item,via=list(pathstack))); continue
                output.append(dict(item,via=list(pathstack)))
                fn=local_defs[target]
                fn_items=[]
                for sub in fn['commands']:
                    sub=dict(sub); sub['usa_active']=active_at(by_line,sub['line']); fn_items.append(sub)
                nested,nrefs,ncycles,nunresolved=expand(fn_items,pathstack+(target,))
                output.extend(nested); ref_records.extend(nrefs); cycles.extend(ncycles); unresolved.extend(nunresolved)
            else: output.append(dict(item,via=list(pathstack)))
        return output,ref_records,cycles,unresolved
    local_summaries={}
    for name,fn in local_defs.items():
        local_summaries[name]={'line':fn['line'],'commands':[dict(x,usa_active=active_at(by_line,x['line'])) for x in fn['commands']]}
    for area in areas:
        expanded,refs,cycles,unresolved=expand(area['commands'])
        def take(names,items=expanded): return [x for x in items if x['macro'] in names]
        area['local_script_refs']=refs; area['local_ref_cycles']=cycles; area['unresolved_local_refs']=unresolved
        area['object_placements']=take({'OBJECT','OBJECT_WITH_ACTS'})
        area['terrain_data']=take({'TERRAIN'}); area['terrain_type']=take({'TERRAIN_TYPE'})
        area['macro_object_tables']=take({'MACRO_OBJECTS'})
        area['warp_nodes']=take({'WARP_NODE'}); area['painting_warps']=take({'PAINTING_WARP_NODE'}); area['instant_warps']=take({'INSTANT_WARP'})
        area['related_script_refs']=take({'JUMP_LINK','JUMP_LINK_IF','CALL','CALL_LOOP'})
        area['entry_command_count_source_variants']=len(area['commands'])
        area['counts']={'usa_active_direct_commands':sum(x['usa_active'] is True for x in area['commands']),'usa_inactive_direct_commands':sum(x['usa_active'] is False for x in area['commands']),'expanded_objects_usa':len(area['object_placements']),'terrain_declarations_usa':len(area['terrain_data'])+len(area['terrain_type']),'warp_declarations_usa':len(area['warp_nodes'])+len(area['painting_warps'])+len(area['instant_warps'])}
    all_array_data={name:{'line':value['line'],'commands':[dict(c,usa_active=active_at(by_line,c['line'])) for c in value['commands']]} for name,value in arrays.items()}
    return {'folder':folder,'is_defined_level':folder in level_folders,'entry_array':entry_name,'entry_commands':entry_commands,'script_arrays':script_arrays,'areas':areas,'global_script_refs':global_refs,'local_script_definitions':local_summaries,'all_levelscript_arrays':all_array_data,'conditional_directives':cond_sections,'source_sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'source':'https://github.com/n64decomp/sm64/blob/'+COMMIT+'/'+path.relative_to(ROOT).as_posix()}

course_src=(ROOT/'levels/course_defines.h').read_text(encoding='utf-8'); courses=[]
for item in calls(course_src):
    if item['macro'] in ('DEFINE_COURSE','DEFINE_BONUS_COURSE'):
        a=item['args']; courses.append({'index':len(courses),'id':a[0],'dance_cutscene_mask':a[1],'kind':'main' if item['macro']=='DEFINE_COURSE' else 'bonus','evidence':'source_declaration','source':f"levels/course_defines.h:{item['line']}"})
level_src=(ROOT/'levels/level_defines.h').read_text(encoding='utf-8'); levels=[]
for item in calls(level_src):
    if item['macro'] not in ('DEFINE_LEVEL','STUB_LEVEL'): continue
    a=item['args']; stub=item['macro']=='STUB_LEVEL'
    if stub:
        rec={'id':a[1],'course_id':a[2],'folder':None,'internal_rom_name':None,'texture_bin':None,'acoustic_reach_source':a[3],'acoustic_reach_usa':60000 if a[3]=='VAL_DIFF' else a[3],'echo_levels':a[4:7],'dynamic_music':a[7],'camera_table':a[8],'definition':'stub'}
    else:
        rec={'id':a[1],'course_id':a[2],'folder':a[3],'internal_rom_name':a[0].strip('"'),'texture_bin':a[4],'acoustic_reach_source':a[5],'acoustic_reach_usa':60000 if a[5]=='VAL_DIFF' else a[5],'echo_levels':a[6:9],'dynamic_music':a[9],'camera_table':a[10],'definition':'defined'}
    rec.update({'source':f"levels/level_defines.h:{item['line']}",'evidence':'source_declaration'}); levels.append(rec)
level_folders={x['folder'] for x in levels if x['folder']}
script_paths=sorted((ROOT/'levels').glob('*/script.c'))
script_facts={p.relative_to(ROOT).as_posix():parse_level_script(p,level_folders) for p in script_paths}

# The shared dispatcher and global functions live in levels/scripts.c.
shared_path=ROOT/'levels/scripts.c'; shared_txt=shared_path.read_text(encoding='utf-8'); shared_by_line,_=conditional_lines(shared_txt)
shared_arrays={}
for name,begin,body_start,end,body in array_blocks(shared_txt):
    if name.startswith('script_func_global_'):
        shared_arrays[name]={'line':line_at(shared_txt,begin),'commands':[dict(c,usa_active=active_at(shared_by_line,c['line'])) for c in calls(body,offset=body_start,linebase=line_at(shared_txt,body_start)-1)]}
# Literal entry/dispatcher arrays not covered by level-folder scripts or shared globals.
dispatcher_arrays={}
for source_path in (ROOT/'levels/entry.c',ROOT/'levels/scripts.c'):
    source_text=source_path.read_text(encoding='utf-8'); by_line,_=conditional_lines(source_text)
    for name,begin,body_start,end,body in array_blocks(source_text):
        if source_path.name=='scripts.c' and name.startswith('script_func_global_'): continue
        dispatcher_arrays[f'{source_path.relative_to(ROOT).as_posix()}::{name}']={'name':name,'source':source_path.relative_to(ROOT).as_posix(),'line':line_at(source_text,begin),'commands':[dict(c,usa_active=active_at(by_line,c['line'])) for c in calls(body,offset=body_start,linebase=line_at(source_text,body_start)-1)],'evidence':'source_declaration'}
source_mask=mask_comments(shared_txt)
dispatcher_templates=[]
for m in re.finditer(r'^\s*#define\s+(STUB_LEVEL|DEFINE_LEVEL)\b[^\n]*',source_mask,re.M):
    dispatcher_templates.append({'macro':m.group(1),'line':line_at(shared_txt,m.start()),'source':'levels/scripts.c','evidence':'source_declaration'})
for m in re.finditer(r'^\s*#include\s+"levels/level_defines\.h"',source_mask,re.M):
    dispatcher_templates.append({'macro':'include level_defines.h','line':line_at(shared_txt,m.start()),'source':'levels/scripts.c','evidence':'source_declaration'})
shared_mask=mask_comments(shared_txt)
actor_groups=sorted(set(re.findall(r'#include\s+"actors/(common\d+|group\d+)\.h"',shared_mask)))
shared={'entry_dispatcher_source':'levels/scripts.c','entrypoint_source':'levels/entry.c','actor_group_headers':actor_groups,'global_script_functions':shared_arrays,'dispatcher_entry_arrays':dispatcher_arrays,'level_table_macro_templates':dispatcher_templates,'global_script_refs_by_level':{p:s['global_script_refs'] for p,s in script_facts.items() if s['is_defined_level']},'level_main_scripts_entry_refs':sorted(set(re.findall(r'\b(script_func_global_\d+)\b',shared_mask))),'source':'https://github.com/n64decomp/sm64/blob/'+COMMIT+'/levels/scripts.c','evidence':'source_declaration'}

# Parse finite macro-object placement tables and resolve area pointers by symbol.
macro_tables={}; macro_files=sorted((ROOT/'levels').rglob('macro.inc.c'))
for p in macro_files:
    txt=p.read_text(encoding='utf-8'); masked=mask_comments(txt)
    pat=re.compile(r'\bconst\s+MacroObject\s+(\w+)\s*\[\s*\]\s*=\s*\{')
    for m in pat.finditer(masked):
        end=masked.find('};',m.end())
        if end<0: continue
        body=txt[m.end():end]; entries=[]; terminators=[]
        for c in calls(body,offset=m.end(),linebase=line_at(txt,m.end())-1):
            if c['macro']=='MACRO_OBJECT_END': terminators.append(c['line'])
            elif c['macro'] in ('MACRO_OBJECT','MACRO_OBJECT_WITH_BHV_PARAM'):
                entries.append({'macro':c['macro'],'args':c['args'],'line':c['line'],'evidence':'source_declaration'})
        rel=p.relative_to(ROOT).as_posix(); macro_tables[m.group(1)]={'source':rel,'line':line_at(txt,m.start()),'entry_count':len(entries),'entries':entries,'table_terminated':len(terminators)==1,'terminator_count':len(terminators),'terminator_lines':terminators,'source_sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'evidence':'source_declaration'}
macro_resolution=[]
for src,script in script_facts.items():
    for area in script['areas']:
        for cmd in area['macro_object_tables']:
            symbol=cmd['args'][0] if cmd['args'] else None
            table=macro_tables.get(symbol); area['macro_table_resolution']={'symbol':symbol,'resolved':table is not None,'source':table['source'] if table else None,'line':table['line'] if table else None,'entry_count':table['entry_count'] if table else None}
            if symbol and symbol not in macro_tables: macro_resolution.append({'source':src,'area':area['index'],'symbol':symbol,'status':'unresolved bounded search: levels/**/macro.inc.c'})

# Resolve geometry and collision references against actual source array definitions.
symbol_defs={}
for source_path in sorted((ROOT/'levels').rglob('*.c')):
    source_text=source_path.read_text(encoding='utf-8'); masked=mask_comments(source_text)
    for m in re.finditer(r'\bconst\s+(GeoLayout|Collision)\s+(\w+)\s*\[',masked):
        symbol_defs.setdefault(m.group(2),[]).append({'kind':m.group(1),'source':source_path.relative_to(ROOT).as_posix(),'line':line_at(source_text,m.start()),'evidence':'source_declaration'})
for src,script in script_facts.items():
    for area in script['areas']:
        geom=area['geometry']; area['geometry_source_ref']={'symbol':geom,'definitions':symbol_defs.get(geom,[]),'status':'resolved' if symbol_defs.get(geom) else 'unresolved_bounded_search_levels_c_sources'}
        terr=[c['args'][0] for c in area['terrain_data'] if c['args']]
        area['terrain_source_refs']=[{'symbol':sym,'definitions':symbol_defs.get(sym,[]),'status':'resolved' if symbol_defs.get(sym) else 'unresolved_bounded_search_levels_c_sources'} for sym in terr]

manifest={'schema_version':2,'title':'USA course and level script source inventory','source':{'repository':'n64decomp/sm64','commit':COMMIT,'url':'https://github.com/n64decomp/sm64/tree/'+COMMIT,'variant':'USA flags: VERSION_US defined; VERSION_JP, VERSION_SH, VERSION_EU, VERSION_CN, NO_SEGMENTED_MEMORY undefined'},'evidence_classes':['source_declaration','direct_rom_evidence','runtime_observation','inference','unknown'],'course_rows':courses,'level_rows':levels,'script_file_count':len(script_paths),'defined_level_folder_count':len(level_folders),'script_c_files':script_facts,'shared_script_families':shared,'macro_object_tables':macro_tables,'unresolved_macro_tables':macro_resolution,'coverage':{'course_define_rows':len(courses),'main_course_rows_including_none':sum(x['kind']=='main' for x in courses),'main_playable_courses':sum(x['kind']=='main' and x['id']!='COURSE_NONE' for x in courses),'bonus_course_rows':sum(x['kind']=='bonus' for x in courses),'level_table_rows':len(levels),'defined_levels':sum(x['definition']=='defined' for x in levels),'stubs':sum(x['definition']=='stub' for x in levels),'script_c_files':len(script_paths),'top_level_dispatcher_arrays':len(dispatcher_arrays),'levelscript_arrays':sum(len(sc['all_levelscript_arrays']) for sc in script_facts.values()),'unparsed_course_rows':0,'unparsed_level_rows':0,'unparsed_script_files':0,'level_folder_scripts':sum(x['is_defined_level'] for x in script_facts.values()),'nonlevel_special_scripts':sum(not x['is_defined_level'] for x in script_facts.values()),'areas':sum(len(x['areas']) for x in script_facts.values()),'areas_with_usa_terrain':sum(1 for s in script_facts.values() for a in s['areas'] if a['terrain_data'] or a['terrain_type']),'macro_tables':len(macro_tables),'macro_table_entries':sum(x['entry_count'] for x in macro_tables.values()),'macro_table_terminators':sum(x['terminator_count'] for x in macro_tables.values()),'geometry_references_resolved':sum(a['geometry_source_ref']['status']=='resolved' for sc in script_facts.values() for a in sc['areas']),'terrain_references_resolved':sum(r['status']=='resolved' for sc in script_facts.values() for a in sc['areas'] for r in a['terrain_source_refs']),'unresolved_macro_table_refs':len(macro_resolution)},'known_unknowns':['Runtime execution of any listed level script is not established by source or the static BOB byte correspondence.','Native level-script VM handler semantics are outside this static inventory.','Symbolic macro preset names are inventoried but not expanded into behavior/model identities.','A general segmented-pointer-to-ROM mapping and all-course ROM asset mapping remain unknown.'],'rom_crosscheck':{'status':'bounded representative direct ROM correspondence','canonical_rom_sha256':'17CE077343C6133F8C9F2D6D6D9A4AB62C8CD2AA57C40AEA1F490B4C8BB21D91','sample':{'level':'LEVEL_BOB','method':'4-byte-aligned whole-ROM scan of pinned BOB entry 48-command opcode/length sequence','candidate_count':1,'rom_start':'0x405CC4','rom_end_exclusive':'0x405E9C','length_bytes':472,'sha256':'BC942670A2A6B580713467E425913277B895CC2FCDB7336F4F3F3652BF1A27EB','operands':['five direct placement coordinate/angle tuples','25 placements via three local script refs; all 25 linked coordinate/angle tuples independently agree','seven warp destination tuples','MARIO_POS area 1 yaw 135 position (-6558,0,6464)','all nine segment IDs']},'entry_bank':{'rom_header':'0x2AC0F8','segment_id':'0x0E','bank_rom_start':'0x405A60','bank_rom_end_exclusive':'0x405FB0','entry_segmented_address':'0x0E000264','bank_length_bytes':1360,'sha256':'F8CFF6DEEDEABA3998A8D9DB8173E0831E3D471640C8041A3E35979806DE8913','entry_arithmetic':'0x405A60 + 0x264 = 0x405CC4','local_script_refs':['0x0E000000','0x0E00007C','0x0E0001E8']},'limits':['The BOB script/bank and operand correspondences establish one bounded source-encoded relationship; they do not show observed loader execution or native VM handler semantics.','Segmented addresses are not generally ROM offsets.','This does not map all level assets.']},'limits':['Source macro declarations are not compiled commands or runtime observations.','The scanner retains source lines and nested macro arguments but only records outer command invocations.','USA active declarations are selected; inactive guarded alternatives remain in source array command lists with usa_active=false.','Local script references are expanded recursively for area placement categories. Cycles and unresolved targets are recorded; global functions are inventoried separately.','Macro-object table entries exclude each MACRO_OBJECT_END terminator; symbolic presets are not expanded into actor/model/behavior identities.','C preprocessor and linker evaluation beyond the listed USA flags is not performed; unknown conditions are retained as usa_active=null.']}

# Assertions validate known anchors and prevent partial or mis-scoped manifests.
assert len(courses)==26 and len(levels)==38 and len(script_paths)==33
assert sum(x['entry_count'] for x in macro_tables.values())==1528
assert sum(x['terminator_count'] for x in macro_tables.values())==42
assert all(x['table_terminated'] for x in macro_tables.values())
assert sum(x['definition']=='defined' for x in levels)==31 and sum(x['definition']=='stub' for x in levels)==7
bob=script_facts['levels/bob/script.c']; assert bob['entry_array']=='level_bob_entry'
bobarea=bob['areas'][0]
assert len(bob['entry_commands'])==26 and len(bobarea['commands'])==20
assert len(bob['entry_commands'])+len(bobarea['commands'])+2==48
assert len(bobarea['object_placements'])==30, len(bobarea['object_placements'])
assert [x['line'] for x in bobarea['object_placements'] if not x['via']]==[81,82,83,84,85]
assert sum(bool(x['via']) for x in bobarea['object_placements'])==25
assert len(bobarea['warp_nodes'])==7

# Explicitly retain known source conditional outcomes for USA selection.
for folder in ('ccm','jrb'):
    sc=script_facts[f'levels/{folder}/script.c']
    assert all(d['branch_active'] is True for d in sc['conditional_directives'] if d['directive']=='ifndef' and d['expression']=='VERSION_JP')
assert any(d['directive']=='if' and d['branch_active'] is False for d in script_facts['levels/jrb/script.c']['conditional_directives'])
assert any(d['directive']=='else' and d['branch_active'] is True for d in script_facts['levels/jrb/script.c']['conditional_directives'])

OUT.parent.mkdir(parents=True,exist_ok=True)
OUT.write_text(json.dumps(manifest,separators=(',',':'),ensure_ascii=False)+'\n',encoding='utf-8')
print(json.dumps(manifest['coverage'],indent=2))
print('BOB sequence commands',len(bob['entry_commands'])+len(bobarea['commands'])+2,'entry body',len(bob['entry_commands']),'area body',len(bobarea['commands']),'direct placements',sum(not x['via'] for x in bobarea['object_placements']),'linked local placements',sum(bool(x['via']) for x in bobarea['object_placements']),'warps',len(bobarea['warp_nodes']))
print('BOB area command counts',bobarea['counts'])
print('CCM guards',[(x['directive'],x['expression'],x['branch_active']) for x in script_facts['levels/ccm/script.c']['conditional_directives']])
print('JRB guards',[(x['directive'],x['expression'],x['branch_active']) for x in script_facts['levels/jrb/script.c']['conditional_directives']])
print('manifest bytes',OUT.stat().st_size,'output',OUT)



