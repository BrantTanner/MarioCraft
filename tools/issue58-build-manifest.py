#!/usr/bin/env python3
"""Build a safe numeric MIO0/source-load census for SM64 USA rev 0.

The ROM is read-only. Decoded MIO0 bytes exist only in memory; output contains
offsets, lengths, counts, and SHA-256 digests, never ROM or decoded bytes.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import struct
from pathlib import Path

EXPECTED_ROM_SHA256 = "17CE077343C6133F8C9F2D6D6D9A4AB62C8CD2AA57C40AEA1F490B4C8BB21D91"
PINNED_COMMIT = "9921382a68bb0c865e5e45eb594d9c64db59b1af"


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest().upper()


def decode_mio0(data: bytes, start: int, limit: int) -> dict:
    if data[start:start + 4] != b"MIO0" or start + 16 > limit:
        raise ValueError("missing or truncated MIO0 header")
    size, packed_off, raw_off = struct.unpack_from(">III", data, start + 4)
    if not (16 <= packed_off <= raw_off and start + raw_off <= limit):
        raise ValueError("invalid header offsets")
    if not (0 < size <= 0x800000):
        raise ValueError("unreasonable declared output size")
    layout = start + 16
    packed = start + packed_off
    raw = start + raw_off
    out = bytearray()
    mask = bits = 0
    literal_count = backrefs = 0
    overshoot = False
    while len(out) < size:
        if mask == 0:
            if layout >= start + packed_off:
                raise ValueError("layout overlaps packed stream")
            bits = data[layout]
            layout += 1
            mask = 0x80
        if bits & mask:
            if raw >= limit:
                raise ValueError("raw read outside ROM bound")
            out.append(data[raw])
            raw += 1
            literal_count += 1
        else:
            if packed + 2 > start + raw_off:
                raise ValueError("packed read overlaps raw stream")
            token = struct.unpack_from(">H", data, packed)[0]
            packed += 2
            length = (token >> 12) + 3
            distance = (token & 0xFFF) + 1
            if distance > len(out):
                raise ValueError("back-reference precedes output start")
            backrefs += 1
            overshoot |= len(out) + length > size
            for _ in range(length):
                if len(out) == size:
                    break
                out.append(out[-distance])
        mask >>= 1
    consumed_end = max(layout, packed, raw)
    minimal_end = max(start + 16 + (layout - (start + 16)), packed, raw)
    aligned_end = (minimal_end + 15) & ~15
    return {
        "valid": True,
        "romStart": start,
        "declaredOutputBytes": size,
        "packedOffset": packed_off,
        "rawOffset": raw_off,
        "outputBytes": len(out),
        "outputSha256": sha256(out),
        "layoutBytesUsed": layout - (start + 16),
        "packedBytesUsed": packed - (start + packed_off),
        "rawBytesUsed": raw - (start + raw_off),
        "literalCommands": literal_count,
        "backReferenceCommands": backrefs,
        "consumedEndExclusive": consumed_end,
        "consumedEnvelopeBytes": consumed_end - start,
        "alignedMemberEndCandidate": aligned_end,
        "truncatedFinalBackReference": overshoot,
    }


def linker_mio0_slots(root: Path) -> list[str]:
    ld = (root / "sm64.ld").read_text(encoding="utf-8")
    slots: list[str] = []
    for line in ld.splitlines():
        if line.lstrip().startswith("#"):
            continue
        m = re.search(r"\bMIO0_SEG\(([^,]+),", line)
        if m:
            slots.append(m.group(1).strip() + "_mio0")
        m = re.search(r"\bSTANDARD_OBJECTS\(([^,]+),", line)
        if m:
            slots.append(m.group(1).strip() + "_mio0")
        if "BEGIN_SEG(intro_segment_7," in line:
            slots.append("intro_segment_7")
        if "BEGIN_SEG(menu_segment_7," in line:
            slots.append("menu_segment_7")
    leveldefs = (root / "levels/level_defines.h").read_text(encoding="utf-8")
    for line in leveldefs.splitlines():
        if line.lstrip().startswith("#"):
            continue
        m = re.search(r"\bDEFINE_LEVEL\([^,]+,[^,]+,[^,]+,\s*([^,]+),", line)
        if m:
            slots.append(m.group(1).strip() + "_segment_7")
    return slots


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--rom", required=True, type=Path, help="canonical USA rev 0 ROM; read only")
    ap.add_argument("--source-root", required=True, type=Path, help="pinned n64decomp/sm64 checkout")
    ap.add_argument("--course-manifest", required=True, type=Path, help="sanitized #57 source census JSON")
    ap.add_argument("--output", required=True, type=Path, help="numeric/hash-only JSON output")
    args = ap.parse_args()

    rom = args.rom.read_bytes()
    rom_hash = sha256(rom)
    if len(rom) != 0x800000 or rom_hash != EXPECTED_ROM_SHA256:
        raise SystemExit(f"unexpected ROM identity: bytes={len(rom)} sha256={rom_hash}")
    head = __import__("subprocess").check_output(
        ["git", "-C", str(args.source_root), "rev-parse", "HEAD"], text=True
    ).strip()
    if head != PINNED_COMMIT:
        raise SystemExit(f"unexpected source HEAD: {head}")

    starts: list[int] = []
    pos = 0
    while True:
        pos = rom.find(b"MIO0", pos)
        if pos < 0:
            break
        starts.append(pos)
        pos += 4
    items = []
    for i, start in enumerate(starts):
        limit = starts[i + 1] if i + 1 < len(starts) else len(rom)
        item = decode_mio0(rom, start, limit)
        item["nextMarker"] = limit if i + 1 < len(starts) else None
        items.append(item)

    slots = linker_mio0_slots(args.source_root)
    if len(slots) != 79 or len(items) != 79:
        raise SystemExit(f"bounded census mismatch: source linker slots={len(slots)}, ROM markers={len(items)}")
    slot_rows = []
    for ordinal, (name, item) in enumerate(zip(slots, items), 1):
        item["linkerOrdinal"] = ordinal
        item["linkerSymbol"] = name
        item["associationEvidence"] = "ordered source-linker slot paired with same ordinal ROM MIO0 marker; not an exact source-build byte match"
        item["alignedMemberEndCandidate"] = item["alignedMemberEndCandidate"]
        slot_rows.append(item)

    raw_intervals = {}
    for i, item in enumerate(slot_rows[:-1]):
        next_item = slot_rows[i + 1]
        start, end, name = item["alignedMemberEndCandidate"], next_item["romStart"], item["linkerSymbol"]
        if name.endswith("_mio0") and (name.startswith("group") or name == "common0_mio0"):
            raw_intervals[name[:-5] + "_geo"] = {
                "romStart": start, "romEndExclusive": end,
                "boundaryEvidence": "adjacent STANDARD_OBJECTS linker sections; start follows MIO0 alignment and end is the next compressed member start",
            }
        elif name.endswith("_segment_7") and name not in ("intro_segment_7", "menu_segment_7"):
            raw_intervals[name[:-10]] = {
                "romStart": start, "romEndExclusive": end,
                "boundaryEvidence": "adjacent STANDARD_LEVEL linker sections; start follows MIO0 alignment and end is the next compressed member start",
            }

    # Recover the exact numeric operands for the bounded global load prefix.
    # The signature is built from the five source load classes/segment IDs,
    # the pool allocation command, and the first model-geo header; no ROM
    # command bytes are emitted into the report or manifest.
    prefix_headers = [bytes((op, 12, 0, seg)) for op, seg in (
        (0x18, 0x04), (0x18, 0x03), (0x17, 0x17), (0x17, 0x16), (0x17, 0x13)
    )]
    prefix_hits = []
    for start in range(0, len(rom) - 68, 4):
        if (all(rom[start + 12 * i:start + 12 * i + 4] == header
                for i, header in enumerate(prefix_headers))
                and rom[start + 60:start + 64] == bytes((0x1D, 4, 0, 0))
                and rom[start + 64:start + 66] == bytes((0x22, 8))):
            prefix_hits.append(start)
    if len(prefix_hits) != 1:
        raise SystemExit(f"global load prefix is not unique: matches={len(prefix_hits)}")
    prefix_start = prefix_hits[0]
    prefix_names = ("group0_mio0", "common1_mio0", "group0_geo", "common1_geo", "behavior")
    prefix_loads = []
    for i, name in enumerate(prefix_names):
        command = prefix_start + 12 * i
        opcode, _, _, segment = rom[command:command + 4]
        rom_start, rom_end = struct.unpack_from(">II", rom, command + 4)
        if not (0 <= rom_start < rom_end <= len(rom)):
            raise SystemExit(f"invalid global load bounds for {name}")
        row = {
            "sourceSymbolStem": name, "commandRomOffset": command,
            "opcode": opcode, "segmentId": segment,
            "romStart": rom_start, "romEndExclusive": rom_end,
            "spanBytes": rom_end - rom_start,
            "encodedRomSpanSha256": sha256(rom[rom_start:rom_end]),
        }
        prefix_loads.append(row)
        if name in ("group0_geo", "common1_geo", "behavior"):
            raw_intervals[name] = {
                "romStart": rom_start, "romEndExclusive": rom_end,
                "boundaryEvidence": "numeric ROM operands recovered from the unique canonical global load-prefix match; source symbol bounds, not exact built-source byte equivalence",
                "sourceRomEvidence": "numeric ROM bounds recovered from the unique canonical global load-prefix match; runtime execution is established only where separately captured",
            }
    prefix_evidence = {
        "canonicalRomOffset": prefix_start,
        "uniqueAlignedMatchCount": len(prefix_hits),
        "bytesChecked": 66,
        "sourceContext": "levels/scripts.c main entry: five loads, pool allocation, first model-geo header",
        "loads": prefix_loads,
        "limits": "category/segment prefix and numeric source-symbol bounds only; no runtime execution or full entry-array equivalence claimed",
    }

    # This is a sanitized summary of the separately guarded passive capture
    # supplied for this report. It contains only guest addresses, ROM spans,
    # byte counts, hashes, and the five relevant segment IDs; no process or
    # host-memory identifiers or captured bytes are included.
    passive_snapshot = {
        "capturedUtc": "2026-10-26T05:24:53Z",
        "context": {"level": 9, "course": 1, "saveFileNumber": 1,
                    "romSha256": EXPECTED_ROM_SHA256,
                    "readOnly": True, "noGuestInputOrWrites": True,
                    "stateCounterBracketsEqual": True,
                    "targetSegmentBasesStable": True},
        "scope": "five additional source-prefix segments from one passive loaded-state sample; does not establish a transition",
        "segments": [
            {"segment": 4, "sourceSymbol": "group0_mio0", "guestVirtualBase": "0x8007EC20", "romStart": "0x114750", "romEndExclusive": "0x1279B0", "bytesCompared": 217976, "liveSha256": "2FB26A131C645493A884642360E6355FB4EEE2C5C4B2C205624D2008CDBAC1C4", "canonicalSha256": "2FB26A131C645493A884642360E6355FB4EEE2C5C4B2C205624D2008CDBAC1C4", "exactHashMatch": True},
            {"segment": 3, "sourceSymbol": "common1_mio0", "guestVirtualBase": "0x800B3FB0", "romStart": "0x201410", "romEndExclusive": "0x218DA0", "bytesCompared": 209672, "liveSha256": "F1B5CF5E3AC8FA0C6909889D4F50591CCB685ACEA20DD3C03B8685C48D656433", "canonicalSha256": "F1B5CF5E3AC8FA0C6909889D4F50591CCB685ACEA20DD3C03B8685C48D656433", "exactHashMatch": True},
            {"segment": 23, "sourceSymbol": "group0_geo", "guestVirtualBase": "0x800E72D0", "romStart": "0x1279B0", "romEndExclusive": "0x12A7E0", "bytesCompared": 11824, "liveSha256": "00C0DD666AD42E3034762DE5440E0AC933787458DE9117C2049990BC3AABD3C3", "canonicalSha256": "00C0DD666AD42E3034762DE5440E0AC933787458DE9117C2049990BC3AABD3C3", "exactHashMatch": True},
            {"segment": 22, "sourceSymbol": "common1_geo", "guestVirtualBase": "0x800EA110", "romStart": "0x218DA0", "romEndExclusive": "0x219E00", "bytesCompared": 4192, "liveSha256": "67CC16706D8F8C8F4D681CB153E4858A76C4F6A49A605B0289086E055DDDF5D8", "canonicalSha256": "67CC16706D8F8C8F4D681CB153E4858A76C4F6A49A605B0289086E055DDDF5D8", "exactHashMatch": True},
            {"segment": 19, "sourceSymbol": "behavior", "guestVirtualBase": "0x800EB180", "romStart": "0x219E00", "romEndExclusive": "0x21F4C0", "bytesCompared": 22208, "liveSha256": "EAD9150A4EB2105623772A763229BA880374167D76BEDCAF5D6B510789704296", "canonicalSha256": "EAD9150A4EB2105623772A763229BA880374167D76BEDCAF5D6B510789704296", "exactHashMatch": True},
        ],
    }

    # Link every defined course's generated EXECUTE wrapper to its source
    # entry command sequence. This directly decodes source-encoded bank bounds
    # and verifies opcode/length patterns; it does not claim rebuilt asset
    # byte identity or that the command was executed in a runtime transition.
    course = json.loads(args.course_manifest.read_text(encoding="utf-8"))
    levels = [(i + 1, row) for i, row in enumerate(course.get("level_rows", []))
              if row.get("definition") == "defined" and row.get("folder")]
    if len(levels) != 31:
        raise SystemExit(f"unexpected defined-level count: {len(levels)}")
    jump_len = 4 + len(levels) * 12 + 4
    jump_hits = []
    for start in range(0, len(rom) - jump_len, 4):
        if rom[start:start + 4] != bytes((0x3C, 4, 1, 3)):
            continue
        cursor = start + 4
        branch_rows = []
        for level_id, level in levels:
            if (rom[cursor:cursor + 4] != bytes((0x0C, 12, 2, 0))
                    or struct.unpack_from(">I", rom, cursor + 4)[0] != level_id):
                break
            branch_rows.append((level_id, level, struct.unpack_from(">I", rom, cursor + 8)[0]))
            cursor += 12
        else:
            if rom[cursor:cursor + 4] == bytes((0x02, 4, 0, 0)):
                jump_hits.append((start, branch_rows))
    if len(jump_hits) != 1:
        raise SystemExit(f"source-enum level dispatch table is not unique: matches={len(jump_hits)}")
    jump_start, branch_rows = jump_hits[0]
    exec_start = jump_start + jump_len
    command_header = (args.source_root / "include/level_commands.h").read_text(encoding="utf-8")
    command_opmap = {}
    header_lines = command_header.splitlines()
    line_index = 0
    while line_index < len(header_lines):
        define = re.match(r"#define\s+(\w+)\s*\(", header_lines[line_index])
        body = header_lines[line_index]
        line_index += 1
        if not define:
            continue
        while body.rstrip().endswith("\\") and line_index < len(header_lines):
            body += "\n" + header_lines[line_index]
            line_index += 1
        encoded = re.search(r"CMD_(?:BBH|BBBB)\(\s*(0x[0-9A-Fa-f]+),\s*(0x[0-9A-Fa-f]+)", body)
        if encoded:
            command_opmap.setdefault(define.group(1), (int(encoded.group(1), 16), int(encoded.group(2), 16)))
    command_opmap["OBJECT"] = (0x24, 0x18)
    level_bank_rows = []
    bank_load_operands = {}
    bank_raw_intervals = {}
    global_bases = []
    for index, (level_id, level, jump_pointer) in enumerate(branch_rows):
        folder = level["folder"]
        wrapper = exec_start + index * 20
        if rom[wrapper:wrapper + 4] != bytes((0x00, 0x10, 0x00, 0x0E)) or rom[wrapper + 16:wrapper + 20] != bytes((0x07, 0x04, 0, 0)):
            raise SystemExit(f"invalid generated EXECUTE/RETURN wrapper for {folder}")
        script_start_ptr, script_end_ptr, entry_ptr = struct.unpack_from(">III", rom, wrapper + 4)
        if (jump_pointer >> 24 != 0x15 or entry_ptr >> 24 != 0x0E):
            raise SystemExit(f"unexpected segmented pointer family for {folder}")
        global_base = wrapper - (jump_pointer & 0xFFFFFF)
        global_bases.append(global_base)
        # EXECUTE carries numeric ROM start/end operands; only its entry
        # pointer uses the loaded script segment.
        bank_start = script_start_ptr
        bank_end = script_end_ptr
        entry_start = bank_start + (entry_ptr & 0xFFFFFF)
        if not (0 <= bank_start < bank_end <= len(rom) and bank_start <= entry_start < bank_end):
            raise SystemExit(f"invalid generated bank bounds for {folder}")
        script_path = f"levels/{folder}/script.c"
        source_info = course["script_c_files"][script_path]
        commands = source_info["all_levelscript_arrays"][source_info["entry_array"]]["commands"]
        commands = [cmd for cmd in commands if cmd.get("usa_active") is not False]
        cursor = entry_start
        entry_loads = []
        for cmd in commands:
            expected = command_opmap.get(cmd["macro"])
            if expected is None:
                raise SystemExit(f"no command opcode/size mapping for {cmd['macro']} in {script_path}")
            opcode, size = expected
            if rom[cursor:cursor + 2] != bytes((opcode, size)):
                raise SystemExit(f"source command pattern mismatch for {folder} line {cmd.get('line')} at 0x{cursor:X}")
            if opcode in (0x17, 0x18, 0x1A):
                segment = struct.unpack_from(">H", rom, cursor + 2)[0]
                rom_start, rom_end = struct.unpack_from(">II", rom, cursor + 4)
                if not (0 <= rom_start < rom_end <= len(rom)):
                    raise SystemExit(f"invalid encoded load bounds for {folder} line {cmd.get('line')}")
                load = {"source": script_path, "line": cmd.get("line"), "commandRomOffset": cursor,
                        "macro": cmd["macro"], "opcode": opcode, "commandSize": size, "segmentId": segment,
                        "romStart": rom_start, "romEndExclusive": rom_end,
                        "spanBytes": rom_end - rom_start,
                        "encodedRomSpanSha256": sha256(rom[rom_start:rom_end])}
                entry_loads.append(load)
                bank_load_operands[(script_path, cmd.get("line"))] = load
            cursor += size
        if cursor > bank_end:
            raise SystemExit(f"source entry pattern runs beyond bank for {folder}")
        bank_raw_intervals[folder] = {
            "romStart": bank_start, "romEndExclusive": bank_end,
            "boundaryEvidence": "generated EXECUTE wrapper encodes this exact bank interval; linked via the unique source-enum dispatcher table",
            "sourceRomEvidence": "direct source-enum EXECUTE linkage and source entry opcode/length pattern match; exact built asset bytes and runtime execution are not established",
        }
        level_bank_rows.append({
            "folder": folder, "levelId": level_id,
            "dispatcherCommandRomOffset": jump_start + 4 + index * 12,
            "wrapperRomOffset": wrapper,
            "bankRomStart": bank_start, "bankRomEndExclusive": bank_end,
            "bankBytes": bank_end - bank_start,
            "bankSha256": sha256(rom[bank_start:bank_end]),
            "entryRomStart": entry_start, "entryBytesCompared": cursor - entry_start,
            "entryCommandSha256": sha256(rom[entry_start:cursor]),
            "entryCommandCount": len(commands),
            "entryOpcodeLengthPatternMatches": True,
            "loadOperands": entry_loads,
        })
    if len(set(global_bases)) != 1 or global_bases[0] != prefix_start:
        raise SystemExit("generated EXECUTE wrappers do not agree on the global script base")
    raw_intervals.update(bank_raw_intervals)
    bank_evidence = {
        "jumpTableRomStart": jump_start,
        "jumpTableRomEndExclusive": jump_start + jump_len,
        "jumpTableBytes": jump_len,
        "jumpTableSha256": sha256(rom[jump_start:jump_start + jump_len]),
        "jumpTableUniqueMatchCount": len(jump_hits),
        "generatedExecTableRomStart": exec_start,
        "generatedExecTableRomEndExclusive": exec_start + len(levels) * 20,
        "generatedExecWrapperCount": len(levels),
        "globalScriptSegment21Base": global_bases[0],
        "courseBanks": level_bank_rows,
        "limits": "All 31 source enum IDs link through generated EXECUTE wrappers; source entry opcode/size patterns and encoded load operands match. This is not exact rebuilt-asset equivalence or runtime transition evidence.",
    }

    loads = []
    for path, info in sorted(course["script_c_files"].items()):
        arrays = info.get("all_levelscript_arrays", {})
        for array_name, array in arrays.items():
            for cmd in array.get("commands", []):
                if cmd.get("macro") not in ("LOAD_MIO0", "LOAD_MIO0_TEXTURE", "LOAD_RAW") or not cmd.get("usa_active", True):
                    continue
                a = cmd.get("args", [])
                if len(a) < 3:
                    continue
                rom_start_symbol = a[1].strip().lstrip("_")
                linker_symbol = re.sub(r"SegmentRomStart$", "", rom_start_symbol)
                ordinal = slots.index(linker_symbol) + 1 if linker_symbol in slots else None
                row = {
                    "source": path,
                    "line": cmd.get("line"),
                    "array": array_name,
                    "directive": cmd["macro"],
                    "segmentId": a[0].strip(),
                    "romStartSymbol": a[1].strip(),
                    "romEndSymbol": a[2].strip(),
                    "linkerSymbol": linker_symbol,
                    "linkerMio0Ordinal": ordinal,
                    "loadClass": "MIO0" if cmd["macro"] == "LOAD_MIO0" else "MIO0_TEXTURE" if cmd["macro"] == "LOAD_MIO0_TEXTURE" else "RAW",
                }
                row["lifetimeEvidence"] = {
                    "MIO0": "native helper allocates decoded output and installs the segment base; temporary compressed copy is freed on success; exact pool owner/replacement timing is not observed",
                    "MIO0_TEXTURE": "native helper decodes to fixed working memory, installs the segment base, and frees the temporary compressed copy; transition lifetime is not observed",
                    "RAW": "native helper copies the declared interval and installs the segment base; per-course release timing is not observed",
                }[row["loadClass"]]
                if ordinal is not None and row["loadClass"] != "RAW":
                    row["romCandidateOffset"] = items[ordinal - 1]["romStart"]
                    row["streamConsumedEndExclusive"] = items[ordinal - 1]["consumedEndExclusive"]
                    row["decodedBytes"] = items[ordinal - 1]["outputBytes"]
                    row["decodedSha256"] = items[ordinal - 1]["outputSha256"]
                    row["sourceRomEvidence"] = "linker-order candidate; runtime use and exact source-build identity unverified"
                elif row["loadClass"] == "RAW":
                    if linker_symbol in raw_intervals:
                        row.update(raw_intervals[linker_symbol])
                        row.setdefault("sourceRomEvidence", "adjacent linker-section boundary candidate; exact source-build identity and runtime use unverified")
                    else:
                        row["sourceRomEvidence"] = "source load declared; full raw segment ROM bounds unresolved"
                else:
                    row["sourceRomEvidence"] = "source symbol did not match a compressed linker slot"
                linked_load = bank_load_operands.get((path, row["line"]))
                if linked_load is not None:
                    row["canonicalCommandOperands"] = {
                        "commandRomOffset": linked_load["commandRomOffset"],
                        "opcode": linked_load["opcode"], "segmentId": linked_load["segmentId"],
                        "romStart": linked_load["romStart"], "romEndExclusive": linked_load["romEndExclusive"],
                        "spanBytes": linked_load["spanBytes"],
                        "encodedRomSpanSha256": linked_load["encodedRomSpanSha256"],
                        "sourceOpcodeLengthPatternMatched": True,
                    }
                    row["sourceRomEvidence"] = "source entry command opcode/length and numeric ROM load operands match the linked canonical course bank; runtime execution and exact rebuilt asset identity unverified"
                loads.append(row)

    scripts_source = args.source_root / "levels/scripts.c"
    for line_number, line in enumerate(scripts_source.read_text(encoding="utf-8").splitlines(), 1):
        if line.lstrip().startswith("#"):
            continue
        for match in re.finditer(r"\b(LOAD_MIO0_TEXTURE|LOAD_MIO0|LOAD_RAW)\s*\(([^;]*?)\)", line):
            macro, inside = match.groups()
            inside = re.sub(r"/\*.*?\*/|//.*", "", inside)
            a = [x.strip() for x in inside.split(",")]
            if len(a) < 3:
                continue
            symbol = re.sub(r"SegmentRomStart$", "", a[1].lstrip("_"))
            row = {
                "source": "levels/scripts.c", "line": line_number,
                "array": "level_main_scripts_entry", "directive": macro,
                "segmentId": a[0], "romStartSymbol": a[1], "romEndSymbol": a[2],
                "linkerSymbol": symbol,
                "linkerMio0Ordinal": slots.index(symbol) + 1 if symbol in slots else None,
                "loadClass": "RAW" if macro == "LOAD_RAW" else "MIO0_TEXTURE" if macro == "LOAD_MIO0_TEXTURE" else "MIO0",
            }
            row["lifetimeEvidence"] = {
                "MIO0": "native helper allocates decoded output and installs the segment base; temporary compressed copy is freed on success; exact pool owner/replacement timing is not observed",
                "MIO0_TEXTURE": "native helper decodes to fixed working memory, installs the segment base, and frees the temporary compressed copy; transition lifetime is not observed",
                "RAW": "native helper copies the declared interval and installs the segment base; per-course release timing is not observed",
            }[row["loadClass"]]
            ordinal = row["linkerMio0Ordinal"]
            if ordinal is not None and macro != "LOAD_RAW":
                item = items[ordinal - 1]
                row.update({"romCandidateOffset": item["romStart"], "streamConsumedEndExclusive": item["consumedEndExclusive"], "decodedBytes": item["outputBytes"], "decodedSha256": item["outputSha256"], "sourceRomEvidence": "linker-order candidate; runtime use and exact source-build identity unverified"})
            elif symbol in raw_intervals:
                row.update(raw_intervals[symbol])
                row.setdefault("sourceRomEvidence", "adjacent linker-section boundary candidate; exact source-build identity and runtime use unverified")
            else:
                row["sourceRomEvidence"] = "source load declared; full raw segment ROM bounds unresolved"
            loads.append(row)

    for relative in ("levels/scripts.c", "levels/entry.c"):
        source_file = args.source_root / relative
        for line_number, line in enumerate(source_file.read_text(encoding="utf-8").splitlines(), 1):
            if line.lstrip().startswith("#") or "##" in line:
                continue
            for match in re.finditer(r"\b(EXIT_AND_EXECUTE|EXECUTE)\s*\(([^;]*?)\)", line):
                macro, inside = match.groups()
                inside = re.sub(r"/\*.*?\*/|//.*", "", inside)
                a = [x.strip() for x in inside.split(",")]
                if len(a) < 4 or "SegmentRomStart" not in a[1]:
                    continue
                symbol = re.sub(r"SegmentRomStart$", "", a[1].lstrip("_"))
                row = {
                    "source": relative, "line": line_number,
                    "array": "source-declared-transition", "directive": macro,
                    "segmentId": a[0], "romStartSymbol": a[1], "romEndSymbol": a[2],
                    "entrySymbol": a[3], "linkerSymbol": symbol,
                    "linkerMio0Ordinal": None, "loadClass": "RAW_SCRIPT_GEO",
                    "lifetimeEvidence": "EXECUTE or EXIT_AND_EXECUTE loads the script bank while changing VM/pool state; return/exit handlers restore state; real transition timing is not observed",
                }
                if symbol in raw_intervals:
                    row.update(raw_intervals[symbol])
                    row.setdefault("sourceRomEvidence", "adjacent linker-section boundary candidate; exact source-build identity and runtime use unverified")
                else:
                    row["sourceRomEvidence"] = "source transition declared; full raw script/geometry span unresolved"
                loads.append(row)

    for level in course.get("level_rows", []):
        folder = level.get("folder")
        if not folder or level.get("definition") != "defined":
            continue
        row = {
            "source": "levels/scripts.c", "line": 164,
            "levelDefinitionSource": level.get("source"),
            "array": "script_exec_" + folder, "directive": "EXECUTE",
            "segmentId": "0x0E", "romStartSymbol": "_" + folder + "SegmentRomStart",
            "romEndSymbol": "_" + folder + "SegmentRomEnd",
            "entrySymbol": "level_" + folder + "_entry", "linkerSymbol": folder,
            "linkerMio0Ordinal": None, "loadClass": "RAW_SCRIPT_GEO",
            "lifetimeEvidence": "EXECUTE saves caller continuation and pushes pool state before loading the script bank; return/exit handlers restore state; real transition timing is not observed",
        }
        if folder in raw_intervals:
            row.update(raw_intervals[folder])
            row.setdefault("sourceRomEvidence", "adjacent STANDARD_LEVEL linker-section boundary candidate; exact source-build identity and runtime use unverified")
        else:
            row["sourceRomEvidence"] = "source EXECUTE declared; full raw script/geometry span unresolved"
        loads.append(row)

    usage = {}
    for row in loads:
        if row.get("linkerMio0Ordinal") is not None:
            usage.setdefault(row["linkerSymbol"], []).append(row)
    for item in slot_rows:
        refs = usage.get(item["linkerSymbol"], [])
        source_files = sorted({r["source"] for r in refs})
        item["loadReferenceCount"] = len(refs)
        item["referencingSources"] = source_files
        item["sourceAssociationStatus"] = "referenced by source load directives; ROM pairing remains linker-order candidate" if refs else "unreferenced by bounded course/dispatcher load directives"
        item["lifetimeEvidence"] = "source uses invoke the statically mapped loader; temporary compressed copies are freed on success, while resident segment lifetime follows pool/segment replacement paths not observed in a transition" if refs else "no load lifetime established from the bounded course/dispatcher source set"
        name = item["linkerSymbol"]
        if name.endswith("_segment_7"):
            item["assetGroup"] = "level-specific geometry/collision/data container"
        elif name.startswith("common"):
            item["assetGroup"] = "shared common actor assets"
        elif name.startswith("group"):
            item["assetGroup"] = "shared actor group assets"
        elif name.endswith("_skybox_mio0"):
            item["assetGroup"] = "shared skybox assets"
        elif name.endswith("_mio0") and name.split("_", 1)[0] in ("fire", "spooky", "generic", "water", "sky", "snow", "cave", "machine", "mountain", "grass", "outside", "inside", "effect"):
            item["assetGroup"] = "shared texture bins"
        else:
            item["assetGroup"] = "shared system or menu assets"
        item["sharing"] = "referenced by multiple source files" if len(source_files) > 1 else "one or no source-file reference"

    output = {
        "schemaVersion": 1,
        "title": "USA revision 0 MIO0 and course load manifest",
        "rom": {"bytes": len(rom), "sha256": rom_hash, "byteOrder": "big-endian"},
        "source": {"repository": "n64decomp/sm64", "commit": head, "variant": "VERSION_US"},
        "method": {
            "mio0": "MSB-first layout flags; literal byte or overlapping length/distance back-reference; stop at declared output size; decoded bytes kept in memory only",
            "association": "source sm64.ld MIO0 slots expanded in linker order and paired ordinally with the 4-byte-aligned ROM marker census; this is bounded linker-order evidence, not an exact source-build byte match",
            "consumedSpan": "minimal consumed envelope ends at the last byte used by layout, packed, or literal streams; alignedMemberEndCandidate is a derived 16-byte alignment candidate, not a proven linker symbol end",
        },
        "coverage": {
            "sourceCompressedSlots": len(slots),
            "romMio0Markers": len(items),
            "validMio0Decodes": sum(x["valid"] for x in items),
            "uniqueDecodedDigests": len({x["outputSha256"] for x in items}),
            "crossesNextMarker": sum(x["consumedEndExclusive"] > x["nextMarker"] for x in items if x["nextMarker"] is not None),
            "truncatedFinalBackReferences": sum(x["truncatedFinalBackReference"] for x in items),
            "sourceLoadDirectives": len(loads),
            "unreferencedCompressedSlots": sum(x["loadReferenceCount"] == 0 for x in slot_rows),
            "rawLoadsWithUnresolvedRomBounds": sum(x["loadClass"] in ("RAW", "RAW_SCRIPT_GEO") and "romStart" not in x for x in loads),
        },
        "mio0Slots": slot_rows,
        "rawLinkerIntervals": raw_intervals,
        "globalLoadPrefixEvidence": prefix_evidence,
        "sourceEnumCourseBankEvidence": bank_evidence,
        "passiveSnapshotAdditionalSegments": passive_snapshot,
        "sourceLoadDirectives": loads,
        "limits": [
            "MIO0 structural validity does not identify the decoded data or show runtime use.",
            "Ordinal linker association assumes ROM asset order follows the pinned linker script; independent byte equality against built source assets is not claimed.",
            "Raw segment full ROM boundaries are not established by this census.",
            "Runtime segment bases and allocation lifetime are native-code properties; Project64 observations remain open.",
            "No ROM bytes or decoded asset bytes are written to output.",
        ],
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(output["coverage"], sort_keys=True))


if __name__ == "__main__":
    main()
