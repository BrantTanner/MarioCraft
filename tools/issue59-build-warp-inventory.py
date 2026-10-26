#!/usr/bin/env python3
"""Compare pinned source warp declarations with canonical cartridge commands.

Reads the ROM without modification; writes numeric metadata and hashes only.
Source area associations and command matches do not establish runtime execution.
"""
import argparse
import hashlib
import json
import struct
from collections import Counter
from pathlib import Path

PIN = "9921382a68bb0c865e5e45eb594d9c64db59b1af"
ROM_SHA = "17CE077343C6133F8C9F2D6D6D9A4AB62C8CD2AA57C40AEA1F490B4C8BB21D91"
SPECIAL = {"SUCCESS": 240, "DEATH": 241, "TOTWC": 242, "WARP_FLOOR": 243,
           "CREDITS_START": 248, "CREDITS_MIN": 248, "CREDITS_NEXT": 249,
           "CREDITS_END": 250}


def digest(data):
    return hashlib.sha256(data).hexdigest().upper()


def number(text):
    text = text.strip()
    if text.startswith("WARP_NODE_"):
        tail = text[len("WARP_NODE_"):]
        return SPECIAL[tail] if tail in SPECIAL else int(tail, 16)
    return int(text, 0)


def build(course, assets, rom):
    if digest(rom) != ROM_SHA:
        raise ValueError("Canonical USA rev0 ROM SHA-256 mismatch")
    if course["source"]["commit"] != PIN or assets["source"]["commit"] != PIN:
        raise ValueError("Source manifests must use the pinned commit")
    levels = {row["id"]: index + 1 for index, row in enumerate(course["level_rows"])}
    levels["LEVEL_NONE"] = 0
    folder_ids = {r["folder"]: levels[r["id"]] for r in course["level_rows"]
                  if r.get("folder")}
    banks = {b["folder"]: b for b in assets["sourceEnumCourseBankEvidence"]["courseBanks"]}
    direct = {}
    for folder, bank in banks.items():
        if folder_ids[folder] != bank["levelId"]:
            raise ValueError("Source/dispatcher level enum mismatch: " + folder)
        start, end = bank["bankRomStart"], bank["bankRomEndExclusive"]
        if not 0 <= start < end <= len(rom) or digest(rom[start:end]) != bank["bankSha256"]:
            raise ValueError("Canonical course-bank bounds/hash mismatch: " + folder)
        filename = "levels/" + folder + "/script.c"
        script = course["script_c_files"][filename]
        commands = [c for c in script["all_levelscript_arrays"][script["entry_array"]]["commands"]
                    if c["usa_active"] is True]
        if len(commands) != bank["entryCommandCount"]:
            raise ValueError("Source entry-command count mismatch: " + folder)
        cursor = bank["entryRomStart"]
        for command in commands:
            size = rom[cursor + 1]
            if size < 4 or size % 4 or cursor + size > end:
                raise ValueError("Invalid canonical entry command size")
            direct[(filename, command["line"], command["macro"])] = cursor
            cursor += size
        if cursor != bank["entryRomStart"] + bank["entryBytesCompared"]:
            raise ValueError("Source/ROM entry span mismatch: " + folder)
    rows = []
    for filename, script in course["script_c_files"].items():
        folder = script["folder"]
        for area in script["areas"]:
            for category in ("warp_nodes", "painting_warps", "instant_warps"):
                for command in area[category]:
                    if command["usa_active"] is not True:
                        continue
                    args, macro = command["args"], command["macro"]
                    row = {"source": filename, "line": command["line"], "macro": macro,
                           "sourceLevel": folder_ids.get(folder), "sourceArea": number(area["index"]),
                           "sourceArguments": args, "via": command.get("via", []),
                           "runtimeStatus": "unobserved", "evidence": "pinned-USA-source-declaration"}
                    if macro == "INSTANT_WARP":
                        values = [number(v) for v in args]
                        encoded = struct.pack(">BBBBhhhH", 40, 12, values[0], values[1], *values[2:], 0)
                        row.update(index=values[0], destinationLevel=row["sourceLevel"],
                                   destinationArea=values[1], displacement=values[2:])
                    else:
                        node, dest_level, dest_area, dest_node, flag = args
                        flags = {"WARP_NO_CHECKPOINT": 0, "WARP_CHECKPOINT": 128}[flag]
                        values = [number(node), levels[dest_level], number(dest_area), number(dest_node)]
                        opcode = 38 if macro == "WARP_NODE" else 39
                        encoded = bytes([opcode, 8, *values, flags, 0])
                        row.update(nodeOrPaintingIndex=values[0], destinationLevel=values[1],
                                   destinationArea=values[2], destinationNode=values[3], checkpointFlag=flags)
                    bank = banks[folder]
                    pos = direct.get((filename, command["line"], macro))
                    if pos is not None:
                        if rom[pos:pos + len(encoded)] != encoded:
                            raise ValueError("Warp operands mismatch at direct command: " + filename + ":" + str(command["line"]))
                        matches, basis = [pos], "exact-command-at-source-corresponding-entry-position"
                    else:
                        matches = [p for p in range(bank["bankRomStart"], bank["bankRomEndExclusive"] - len(encoded) + 1, 4)
                                   if rom[p:p + len(encoded)] == encoded]
                        basis = "bounded-bank-command-candidates; local-source-context-not-independently-address-resolved"
                    row["cartridge"] = {"romOffsets": matches, "byteLength": len(encoded),
                                        "sha256": digest(encoded), "basis": basis,
                                        "matchCount": len(matches)}
                    rows.append(row)
    areas = {(folder_ids.get(s["folder"]), number(a["index"]))
             for s in course["script_c_files"].values() for a in s["areas"]}
    nodes = {(r["sourceLevel"], r["sourceArea"], r["nodeOrPaintingIndex"])
             for r in rows if r["macro"] == "WARP_NODE"}
    for row in rows:
        target = row["destinationLevel"], row["destinationArea"]
        row["destinationAreaDeclared"] = target in areas
        if "destinationNode" in row:
            row["destinationNodeDeclared"] = (*target, row["destinationNode"]) in nodes
    counts = Counter(r["macro"] for r in rows)
    return {"schemaVersion": 1, "sourcePin": PIN, "romSha256": ROM_SHA,
            "coverage": {"declarations": len(rows), "byMacro": dict(counts),
                         "withoutCartridgeCandidate": sum(not r["cartridge"]["matchCount"] for r in rows),
                         "withMultipleCartridgeCandidates": sum(r["cartridge"]["matchCount"] > 1 for r in rows),
                         "destinationAreaNotDeclared": sum(not r["destinationAreaDeclared"] for r in rows),
                         "destinationNodeNotDeclared": sum(r.get("destinationNodeDeclared") is False for r in rows)},
            "rows": rows,
            "sourceDefinitions": ["include/level_commands.h", "src/game/level_update.h"],
            "limits": ["Source area/link associations are distinct from runtime execution.",
                       "Direct entry positions reuse #58's bounded source-opcode/length correspondence.",
                       "Local-array command candidates can be ambiguous or shared across source areas.",
                       "Declared destination nodes do not prove reachable spawn objects or a successful transition.",
                       "No ROM bytes, RAM, assets, emulator states or captures are exported."]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--course-manifest", type=Path, required=True)
    parser.add_argument("--asset-manifest", type=Path, required=True)
    parser.add_argument("--rom", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = build(json.loads(args.course_manifest.read_text(encoding="utf-8-sig")),
                   json.loads(args.asset_manifest.read_text(encoding="utf-8-sig")), args.rom.read_bytes())
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(json.dumps(result["coverage"]))


if __name__ == "__main__":
    main()
