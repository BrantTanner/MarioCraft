#!/usr/bin/env python3
"""Print scalar validation for the bounded Mario model asset chain.

This reads a private big-endian Mario 64 USA rev 0 ROM and decodes the
verified group-0 MIO0 bank only in memory. It never writes or emits asset
bytes, screenshots, RAM, or save data. Source labels are navigation aids;
all reported byte hashes and command values come from the selected ROM.
"""
from __future__ import annotations

import argparse
import hashlib
from collections import Counter
from pathlib import Path


ROM_GROUP0_MIO0 = (0x114750, 0x1279B0)
ROM_GROUP0_GEO = (0x1279B0, 0x12A7E0)
EXPECTED_ROM_SHA256 = "17CE077343C6133F8C9F2D6D6D9A4AB62C8CD2AA57C40AEA1F490B4C8BB21D91"
EXPECTED_GROUP0_OUTPUT_SIZE = 0x35378
EXPECTED_GROUP0_OUTPUT_SHA256 = "2FB26A131C645493A884642360E6355FB4EEE2C5C4B2C205624D2008CDBAC1C4"
MARIO_GEO_ROOT_ROM = 0x12A784  # seg17:0x2DD4
MARIO_GEO_BODY_ROM = 0x127EEC  # seg17:0x053C
MARIO_TORSO_ANIM_PART_ROM = 0x127F28
MARIO_BUTT_ANIM_PART_ROM = 0x127EFC
ROM_ANIMATION_TABLE = (0x4EC000, 0x4EC690)
EXPECTED_ANIMATION_TABLE_ENTRIES_SHA256 = "C7ECA69D5AC38CE79249AD01C95909E119313738AEEF607D2FC629D9C4C68872"
ANIMATION_SPANS = {
    114: (0x53F138, 0x54035A),
    170: (0x564D2C, 0x56520C),
    171: (0x56520C, 0x56560E),
    172: (0x565610, 0x565DCE),
    178: (0x5681D0, 0x568A04),
}


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest().upper()


def be32(data: bytes, offset: int) -> int:
    return int.from_bytes(data[offset : offset + 4], "big")


def decode_mio0(rom: bytes, start: int, end: int) -> bytes:
    if rom[start : start + 4] != b"MIO0":
        raise ValueError(f"missing MIO0 signature at ROM 0x{start:X}")
    out_size = be32(rom, start + 4)
    comp_offset, raw_offset = be32(rom, start + 8), be32(rom, start + 12)
    if not (16 <= comp_offset < raw_offset < end - start):
        raise ValueError("MIO0 compressed/raw offsets are outside the declared ROM span")
    comp = start + comp_offset
    raw = start + raw_offset
    flags = start + 16
    out = bytearray()
    while len(out) < out_size:
        if flags >= end:
            raise ValueError("MIO0 flag stream passed the declared ROM span")
        bits = rom[flags]
        flags += 1
        for bit in range(7, -1, -1):
            if len(out) == out_size:
                break
            if bits & (1 << bit):
                if raw >= end:
                    raise ValueError("MIO0 literal stream passed the declared ROM span")
                out.append(rom[raw])
                raw += 1
            else:
                if comp + 2 > end:
                    raise ValueError("MIO0 back-reference stream passed the declared ROM span")
                first, second = rom[comp], rom[comp + 1]
                comp += 2
                count = (first >> 4) + 3
                distance = ((first & 0x0F) << 8) | second
                source = len(out) - distance - 1
                if source < 0:
                    raise ValueError("MIO0 back-reference points before decoded output")
                for _ in range(count):
                    out.append(out[source])
                    source += 1
                    if len(out) == out_size:
                        break
    return bytes(out)


def report_rom_span(label: str, rom: bytes, start: int, end: int) -> None:
    chunk = rom[start:end]
    print(f"{label}: ROM 0x{start:X}..0x{end:X} bytes={len(chunk)} sha256={sha256(chunk)}")


def report_span(label: str, data: bytes, start: int, end: int) -> None:
    chunk = data[start:end]
    print(f"{label}: seg4+0x{start:X}..0x{end:X} bytes={len(chunk)} sha256={sha256(chunk)}")


def report_display_list(label: str, data: bytes, start: int, end: int) -> None:
    chunk = data[start:end]
    if len(chunk) % 8:
        raise ValueError(f"{label} is not a whole number of 8-byte Gfx commands")
    opcodes = Counter(chunk[i] for i in range(0, len(chunk), 8))
    print(f"{label}: seg4+0x{start:X}..0x{end:X} bytes={len(chunk)} sha256={sha256(chunk)}")
    print("  Gfx opcode counts=" + ", ".join(f"0x{k:02X}:{v}" for k, v in sorted(opcodes.items())))
    if start == 0xCA00:
        vertex_ptrs = []
        for offset in range(0, len(chunk), 8):
            if chunk[offset] == 0x04:
                vertex_ptrs.append(be32(chunk, offset + 4))
        print("  G_VTX segmented addresses=" + ", ".join(f"0x{x:08X}" for x in sorted(set(vertex_ptrs))))
    for offset in range(0, len(chunk), 8):
        w0 = int.from_bytes(chunk[offset : offset + 4], "big")
        w1 = int.from_bytes(chunk[offset + 4 : offset + 8], "big")
        opcode = w0 >> 24
        if opcode == 0xFD:  # G_SETTIMG
            fmt, siz, width_field = (w0 >> 21) & 7, (w0 >> 19) & 3, (w0 & 0xFFF) + 1
            print(f"  G_SETTIMG at +0x{start + offset:X}: fmt={fmt} siz={siz} widthField={width_field} image=0x{w1:08X}")
        elif opcode == 0xF2:  # G_SETTILESIZE, 10.2 texture coordinates
            sl, tl = (w0 >> 12) & 0xFFF, w0 & 0xFFF
            sh, th = (w1 >> 12) & 0xFFF, w1 & 0xFFF
            width, height = (sh - sl) // 4 + 1, (th - tl) // 4 + 1
            print(f"  G_SETTILESIZE at +0x{start + offset:X}: sl={sl} tl={tl} sh={sh} th={th} dimensions={width}x{height}")
        elif opcode == 0xF3:  # G_LOADBLOCK
            print(f"  G_LOADBLOCK at +0x{start + offset:X}: lrs={(w1 >> 12) & 0xFFF} dxt={w1 & 0xFFF}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("rom", type=Path, help="path to the private big-endian Mario USA rev 0 ROM")
    args = parser.parse_args()
    rom = args.rom.read_bytes()
    if len(rom) != 0x800000 or rom[:4] != bytes.fromhex("80371240") or sha256(rom) != EXPECTED_ROM_SHA256:
        raise SystemExit("ROM identity mismatch; expected the pinned 8 MiB big-endian Mario USA rev 0 ROM")

    a, b = ROM_GROUP0_MIO0
    bank = rom[a:b]
    decoded = decode_mio0(rom, a, b)
    if len(decoded) != EXPECTED_GROUP0_OUTPUT_SIZE or sha256(decoded) != EXPECTED_GROUP0_OUTPUT_SHA256:
        raise SystemExit("decoded group0 bank did not match the pinned output size and SHA-256")
    print(f"group0 MIO0 ROM 0x{a:X}..0x{b:X}: bytes={len(bank)} sha256={sha256(bank)}")
    print(f"group0 MIO0 decoded in memory: bytes={len(decoded)} sha256={sha256(decoded)}")
    print(f"MIO0 header: output=0x{be32(rom, a + 4):X} compOffset=0x{be32(rom, a + 8):X} rawOffset=0x{be32(rom, a + 12):X}")

    table_start, table_end = ROM_ANIMATION_TABLE
    table_entries = rom[table_start + 8 : table_end]
    if sha256(table_entries) != EXPECTED_ANIMATION_TABLE_ENTRIES_SHA256:
        raise SystemExit("animation table entries did not match the pinned target bytes")
    report_rom_span("animation table header+entries", rom, table_start, table_end)
    report_rom_span("animation table entries", rom, table_start + 8, table_end)
    for animation_id, (start, end) in ANIMATION_SPANS.items():
        payload = rom[start:end]
        report_rom_span(f"animation ID {animation_id}", rom, start, end)
        fields = [int.from_bytes(payload[i:i + 2], "big", signed=True) for i in range(0, 12, 2)]
        values_offset = be32(payload, 12)
        index_offset = be32(payload, 16)
        declared_length = be32(payload, 20)
        if declared_length != len(payload) or not (24 <= index_offset < values_offset < declared_length):
            raise SystemExit(f"animation ID {animation_id} header offsets/length failed validation")
        print(f"  header s16(flags,divisor,start,loopStart,loopEnd,count)={fields} valuesOffset=0x{values_offset:X} indexOffset=0x{index_offset:X} declaredLength={declared_length}")

    # These source labels and offsets are navigation anchors from the pinned
    # n64decomp/sm64 snapshot; byte hashes and command parsing are ROM-derived.
    report_span("mario_texture_metal candidate", decoded, 0x90, 0x1090)
    report_span("mario_texture_yellow_button candidate", decoded, 0x1090, 0x1890)
    report_span("mario_butt_dl_vertex_group1 candidate", decoded, 0xC090, 0xC190)
    report_display_list("mario_butt_dl candidate", decoded, 0xCA00, 0xCC98)
    report_display_list("mario_butt shared list candidate", decoded, 0xCC98, 0xCCC8)
    report_display_list("mario_torso material/list candidate", decoded, 0x10370, 0x103F0)

    ga, gb = ROM_GROUP0_GEO
    geo = rom[ga:gb]
    print(f"group0 raw GeoLayout ROM 0x{ga:X}..0x{gb:X}: bytes={len(geo)} sha256={sha256(geo)}")
    root_end = MARIO_GEO_ROOT_ROM + 0x5C
    root = rom[MARIO_GEO_ROOT_ROM:root_end]
    print(f"Mario GeoLayout root candidate ROM 0x{MARIO_GEO_ROOT_ROM:X}..0x{root_end:X}: bytes={len(root)} sha256={sha256(root)}")
    for label, addr in (("Mario root -> load-body branch", 0x12A7B8),
                        ("Mario root -> render-body branch", 0x12A7C0),
                        ("High-poly load-body -> Mario body branch", 0x12A69C)):
        command = rom[addr : addr + 8]
        target = be32(command, 4)
        print(f"{label}: ROM 0x{addr:X}..0x{addr + 8:X} bytes=8 sha256={sha256(command)} opcode=0x{command[0]:02X} target=0x{target:08X}")
    for label, addr in (("Mario body animated part: butt", MARIO_BUTT_ANIM_PART_ROM),
                        ("Mario body animated part: torso", MARIO_TORSO_ANIM_PART_ROM)):
        record = rom[addr : addr + 12]
        opcode, layer = record[0], record[1]
        x = int.from_bytes(record[2:4], "big", signed=True)
        y = int.from_bytes(record[4:6], "big", signed=True)
        z = int.from_bytes(record[6:8], "big", signed=True)
        display_list = be32(record, 8)
        print(f"{label}: ROM 0x{addr:X}..0x{addr + 12:X} bytes=12 sha256={sha256(record)} opcode=0x{opcode:02X} layer={layer} xyz=({x},{y},{z}) displayList=0x{display_list:08X}")

    # Level script evidence: four tested load commands and the immediately
    # following MODEL_MARIO GEO pointer. Only scalars are emitted.
    for addr in (0x2ABCA0, 0x2ABCAC, 0x2ABCB8, 0x2ABCC4, 0x2ABCD0):
        opcode, length, reserved, segment = rom[addr : addr + 4]
        start, end = be32(rom, addr + 4), be32(rom, addr + 8)
        print(f"level command ROM 0x{addr:X}: opcode=0x{opcode:02X} length={length} reserved=0x{reserved:02X} segment=0x{segment:02X} arg0=0x{start:08X} arg1=0x{end:08X}")
    model = rom[0x2ABCE0 : 0x2ABCE8]
    print(f"MODEL_MARIO command ROM 0x2ABCE0: opcode=0x{model[0]:02X} length={model[1]} modelId={int.from_bytes(model[2:4], 'big')} geo=0x{be32(model,4):08X}")


if __name__ == "__main__":
    main()
