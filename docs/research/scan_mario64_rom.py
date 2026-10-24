"""Read-only marker and zero-run scan for a supplied N64 ROM image.

This reports candidate offsets only. A marker hit does not prove that the
surrounding bytes are a valid compressed stream, asset, script, or microcode.
"""
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path


MARKERS = (b"MIO0", b"Yay0", b"Yay!", b"RNC\x01", b"RNC\x02", b"gspF3DEX", b"aspMain")


def find_all(data: bytes, marker: bytes):
    start = 0
    while True:
        offset = data.find(marker, start)
        if offset < 0:
            return
        yield offset
        start = offset + 1


def zero_runs(data: bytes, minimum: int = 0x1000):
    pos = 0
    while pos < len(data):
        if data[pos] != 0:
            pos += 1
            continue
        end = pos + 1
        while end < len(data) and data[end] == 0:
            end += 1
        if end - pos >= minimum:
            yield pos, end - pos
        pos = end


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("rom", type=Path, help="Read-only N64 ROM path")
    args = parser.parse_args()
    data = args.rom.read_bytes()
    print(f"size_bytes={len(data)}")
    print(f"sha256={hashlib.sha256(data).hexdigest()}")
    print(f"header_magic={data[:4].hex(' ').upper()}")
    print(f"title={data[0x20:0x34].decode('ascii', errors='replace').rstrip()!r}")
    maker = data[0x3B:0x3C].decode("ascii", errors="replace")
    game_id = data[0x3C:0x3E].decode("ascii", errors="replace")
    print(f"maker={maker!r} game_id={game_id!r} catalog_code={(maker + game_id + chr(data[0x3E]))!r}")
    print(f"country={chr(data[0x3E])!r} revision={data[0x3F]}")
    for marker in MARKERS:
        offsets = list(find_all(data, marker))
        print(f"marker {marker!r}: count={len(offsets)} range=" + (f"0x{offsets[0]:X}..0x{offsets[-1]:X}" if offsets else "none"))
        if marker == b"MIO0":
            print("  offsets=" + ",".join(f"0x{x:X}" for x in offsets))
        if marker == b"MIO0":
            print("  MIO0 header candidates (first 16):")
            plausible_count = 0
            for index, offset in enumerate(offsets[:16]):
                if offset + 16 > len(data):
                    print(f"    0x{offset:X}: truncated header")
                    continue
                output_size = int.from_bytes(data[offset + 4 : offset + 8], "big")
                compressed_offset = int.from_bytes(data[offset + 8 : offset + 12], "big")
                raw_offset = int.from_bytes(data[offset + 12 : offset + 16], "big")
                next_offset = offsets[index + 1] if index + 1 < len(offsets) else len(data)
                structurally_plausible = 16 <= compressed_offset < raw_offset < next_offset - offset
                plausible_count += int(structurally_plausible)
                print(
                    f"    0x{offset:06X}: output=0x{output_size:X} "
                    f"compressedOffset=0x{compressed_offset:X} rawOffset=0x{raw_offset:X} "
                    f"nextMarkerDelta=0x{next_offset - offset:X} "
                    f"plausibleHeader={structurally_plausible}"
                )
            all_plausible = 0
            for index, offset in enumerate(offsets):
                if offset + 16 > len(data):
                    continue
                compressed_offset = int.from_bytes(data[offset + 8 : offset + 12], "big")
                raw_offset = int.from_bytes(data[offset + 12 : offset + 16], "big")
                next_offset = offsets[index + 1] if index + 1 < len(offsets) else len(data)
                all_plausible += int(16 <= compressed_offset < raw_offset < next_offset - offset)
            print(f"  structurally_plausible_headers={all_plausible}/{len(offsets)} (candidate check only)")
    runs = sorted(zero_runs(data), key=lambda item: item[1], reverse=True)
    print("zero_runs_ge_0x1000 (largest 32):")
    for offset, length in runs[:32]:
        print(f"  0x{offset:06X}..0x{offset + length - 1:06X} length=0x{length:X}")


if __name__ == "__main__":
    main()

