"""Compare the initial cartridge image with one raw Project64 RDRAM capture.

Read-only and intentionally pinned to the verified issue #10 inputs. A different
ROM or dump must be audited explicitly before updating expected hashes.
"""
from __future__ import annotations
import argparse
import hashlib
from pathlib import Path

EXPECTED_ROM_SHA256 = "17ce077343c6133f8c9f2d6d6d9a4ab62c8cd2aa57c40aea1f490b4c8bb21d91"
EXPECTED_RAM_SHA256 = "3b94d5fdc08c1f53d9bc54a5da9f3a0e0b8c41269404421c109d9429e03ed1a5"
ROM_OFFSET = 0x1000
RAM_OFFSET = 0x246000

def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("rom", type=Path)
    parser.add_argument("rdram", type=Path)
    args = parser.parse_args()
    rom = args.rom.read_bytes()
    ram = args.rdram.read_bytes()
    rom_hash, ram_hash = digest(rom), digest(ram)
    print(f"rom_size={len(rom)} rom_sha256={rom_hash}")
    print(f"rdram_size={len(ram)} rdram_sha256={ram_hash}")
    if len(rom) != 8_388_608 or rom[:4] != bytes.fromhex("80371240") or rom[0x3E:0x3F] != b"E" or rom[0x3F] != 0:
        raise SystemExit("ROM identity/size/header mismatch; refusing comparison")
    if rom_hash != EXPECTED_ROM_SHA256:
        raise SystemExit("ROM SHA-256 mismatch; refusing comparison")
    if len(ram) != 4_194_304 or ram_hash != EXPECTED_RAM_SHA256:
        raise SystemExit("RDRAM size/hash mismatch; refusing comparison")
    compared = min(len(rom) - ROM_OFFSET, len(ram) - RAM_OFFSET)
    left, right = rom[ROM_OFFSET:ROM_OFFSET + compared], ram[RAM_OFFSET:RAM_OFFSET + compared]
    first = next((i for i, (a, b) in enumerate(zip(left, right)) if a != b), None)
    if first is None:
        print(f"compared_bytes={compared} all_equal=true")
        return
    print(f"compared_bytes={compared} matching_prefix_bytes={first}")
    print(f"first_mismatch_rom=0x{ROM_OFFSET + first:X} rdram_file_offset=0x{RAM_OFFSET + first:X} rdram_address=0x{0x80000000 + RAM_OFFSET + first:X}")
    print(f"rom_byte=0x{left[first]:02X} rdram_byte=0x{right[first]:02X}")

if __name__ == "__main__":
    main()

