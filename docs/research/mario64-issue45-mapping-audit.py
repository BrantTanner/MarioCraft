#!/usr/bin/env python3
"""Audit issue-45 PC/ROM range arithmetic against the validated linear map.

Usage: python issue45_mapping_audit.py ROM DOSSIER CSV [JSON_OUTPUT]
Independent engine byte-matches are intentionally reported as outside-map, not
translated using the main-image base. This validates written coordinate pairs;
it does not prove that an address/function attribution is correct.
"""
import csv, json, re, sys
from pathlib import Path

BASE_PC = 0x80245000
MIN_ROM = 0x1000
MAX_ROM_EXCLUSIVE = 0xE8560
PC_RE = re.compile(r"PC\s*`?\[\s*(0x[0-9A-Fa-f]+)\s*,\s*(0x[0-9A-Fa-f]+)\s*\)")
ROM_RE = re.compile(r"ROM\s*`?\[\s*(0x[0-9A-Fa-f]+)\s*,\s*(0x[0-9A-Fa-f]+)\s*\)")

def audit_text(label, text):
    results=[]
    for m in PC_RE.finditer(text):
        ps, pe=(int(x,16) for x in m.groups())
        window=text[max(0,m.start()-140):min(len(text),m.end()+180)]
        candidates=list(ROM_RE.finditer(window))
        if not candidates: continue
        rr=min(candidates,key=lambda x: abs((max(0,m.start()-140)+x.start())-m.start()))
        rs,re_=(int(x,16) for x in rr.groups())
        # Main PC->ROM relation is valid only for the stated map interval.
        if BASE_PC+MIN_ROM <= ps and pe <= BASE_PC+MAX_ROM_EXCLUSIVE:
            expected=(ps-BASE_PC,pe-BASE_PC)
            ok=(rs,re_)==expected
            status='main-map-pass' if ok else 'MAIN-MAP-ERROR'
        else:
            status='outside-main-map; independent mapping evidence required'
            ok=True
        results.append({'source':label,'pc':[hex(ps),hex(pe)],'rom':[hex(rs),hex(re_)],
                        'expected_main_map':([hex(ps-BASE_PC),hex(pe-BASE_PC)] if BASE_PC+MIN_ROM <= ps and pe <= BASE_PC+MAX_ROM_EXCLUSIVE else None),
                        'status':status,'ok':ok})
    return results

def main():
    rom_path,dossier,csv_path,*out=sys.argv[1:]
    rom=Path(rom_path).read_bytes()
    docs=audit_text(dossier,Path(dossier).read_text(encoding='utf-8-sig'))
    rows=[]
    with open(csv_path,encoding='utf-8-sig',newline='') as f:
        for i,row in enumerate(csv.DictReader(f),2):
            for item in audit_text(f'CSV row {i} surface {row.get("idHex", "?")}', ' '.join(row.values())):
                rows.append(item)
    report={'rom_size':len(rom),'main_mapping':{'pc_base':hex(BASE_PC),'rom_min':hex(MIN_ROM),'rom_end_exclusive':hex(MAX_ROM_EXCLUSIVE)},
            'dossier_range_pairs':docs,'csv_range_pairs':rows,
            'errors':[r for r in docs+rows if not r['ok']]}
    if out:
        Path(out[0]).write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(f"ROM bytes: {len(rom)}; dossier PC/ROM pairs: {len(docs)}; CSV pairs: {len(rows)}; errors: {len(report['errors'])}")
    for e in report['errors']: print(e)
    if report['errors']: raise SystemExit(1)
if __name__=='__main__': main()
