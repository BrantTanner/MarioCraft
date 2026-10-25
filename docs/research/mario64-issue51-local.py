#!/usr/bin/env python3
"""Private, bounded decoder and loader for selected Mario USA rev 0 assets.

No asset bytes are embedded here. The ROM must be supplied locally and its
identity is checked before any decoding or output. Generated files belong in
the ignored private output directory. This program prints only scalar checks.

The VADPCM arithmetic is adapted from depp/vadpcm codec/decode.c at commit
413d52043a928c5fac066ccda1e69b73c7f7132b, distributed under MPL-2.0.
Source and license: https://github.com/depp/vadpcm/tree/413d52043a928c5fac066ccda1e69b73c7f7132b
This Source Code Form is subject to the terms of the Mozilla Public License,
v. 2.0. If a copy of the MPL was not distributed with this file, You can
obtain one at https://mozilla.org/MPL/2.0/. Copyright (c) 2022 Dietrich Epp.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import platform
import struct
import zlib
import wave
from pathlib import Path

ROM_SHA256 = "17CE077343C6133F8C9F2D6D6D9A4AB62C8CD2AA57C40AEA1F490B4C8BB21D91"
MIO0_RANGE = (0x114750, 0x1279B0)
MIO0_DECODED_SIZE = 0x35378
MIO0_DECODED_SHA256 = "2FB26A131C645493A884642360E6355FB4EEE2C5C4B2C205624D2008CDBAC1C4"
GEO_RANGE = (0x1279B0, 0x12A7E0)
GEO_SHA256 = "00C0DD666AD42E3034762DE5440E0AC933787458DE9117C2049990BC3AABD3C3"
TEXTURE_SEG4 = (0x1090, 0x1890)
TEXTURE_SHA256 = "1BEBDBC8AC4921C0A38852CC640FB531AAD64E82A047791CF40648EE27667559"
VERTEX_SEG4 = (0xC090, 0xC180)
VERTEX_SHA256 = "DEE91D706F0875AABC1ECBECEC4413AEED01C770685D23AFFADFC744AB8F8DB8"
DISPLAY_LIST_SEG4 = (0xCA00, 0xCC98)
DISPLAY_LIST_SHA256 = "692A5C0FEA7CAA08D6435965EF4FAFE6DA7910EFD157D0693388293FFD7B686B"
GEO_LAYOUT_SUBTREE = (0x127EEC, 0x127F50)
GEO_LAYOUT_SUBTREE_SHA256 = "19A639705B8B779A388B9FE753B9B73D0C7544869068CE1C6F13BC9502374805"
ANIMATION_ROM = (0x53F138, 0x54035A)
ANIMATION_SHA256 = "91473B0B32A6326CC2AC0DBF019DEAD837C9270F50ECC77C0A6235720FFF995B"
ANIMATION_TABLE_ENTRIES = (0x4EC008, 0x4EC690)
ANIMATION_TABLE_SHA256 = "C7ECA69D5AC38CE79249AD01C95909E119313738AEEF607D2FC629D9C4C68872"
EXPECTED_PCM_SHA256 = "EA7849B2B490E402B2C5A24FED4AD3382E161CF17EA5724A57B67524C35F1941"
EXPECTED_PCM2_SHA256 = "82047EE03497C63DFDB80EB1254D85B76A82B080EB128978F6739A22F47F69C2"
EXPECTED_SELECTED_VERTEX_FIELDS_SHA256 = "8BB477862AD756AC1F75604F8EA5F7B2A763EEA9AC635896F6F81D1F38854F64"
EXPECTED_SELECTED_TEXTURE_PIXELS_SHA256 = "FEBB0D571719A262E36BA393DC981B2036F3723413A2336CDCA53258EBB4EAF0"
EXPECTED_SELECTED_ANIMATION_SAMPLES_SHA256 = "409DC8A1C47EBD802177D87E36879779DE2F0176595DDCEEA79A2F5B04998635"
AUDIO = {
    "sample_record": (0x57B890, 0x57B8A4, "1CCCA9A30A6161A4B3A565CADD0846771927DEE38FD2B9FDD5D0272287CB98D8"),
    "book": (0x57B8B0, 0x57B8F8, "BD6DF63EC1B532FDFC64D41387F910BFCA04F66BEE07749F4A7A35770183B4EF"),
    "loop": (0x57B900, 0x57B910, "A79E7AE9E32B39A08DEF7BBC4F68B986CDC8094AC26107B51DA966526F7CCFCC"),
    "sample": (0x5936A0, 0x593D8E, "D6D58E52B91976C735A29EE344260F85CA0B785EFF118758282DC586C45359F0"),
    "instrument2_sample_record": (0x57B9B0, 0x57B9C4, None),
    "instrument2_book": (0x57B9D0, 0x57BA18, "08BDFD665B81D40E9B967613F45DE9BE2B54484E9C05A1824CA39785AAB2D285"),
    "instrument2_loop": (0x57BA20, 0x57BA30, None),
    "instrument2_sample": (0x596660, 0x59698A, "CFCB907835F9B94FE42152043D34F4D676D2B0C5A3F85AC47FCB1A29DA0CC8C3"),
}


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest().upper()


def signed(value: int, bits: int) -> int:
    sign = 1 << (bits - 1)
    return (value ^ sign) - sign


def mio0(rom: bytes, start: int, end: int) -> bytes:
    if rom[start:start + 4] != b"MIO0":
        raise ValueError("MIO0 signature mismatch")
    size, comp_off, raw_off = (int.from_bytes(rom[start + i:start + i + 4], "big") for i in (4, 8, 12))
    if size != MIO0_DECODED_SIZE or not (16 <= comp_off < raw_off < end - start):
        raise ValueError("MIO0 header bounds mismatch")
    flags, comp, raw, out = start + 16, start + comp_off, start + raw_off, bytearray()
    while len(out) < size:
        if flags >= end:
            raise ValueError("MIO0 flags exceed pinned range")
        bits = rom[flags]
        flags += 1
        for bit in range(7, -1, -1):
            if len(out) == size:
                break
            if bits & (1 << bit):
                if raw >= end:
                    raise ValueError("MIO0 literal exceeds pinned range")
                out.append(rom[raw]); raw += 1
            else:
                if comp + 2 > end:
                    raise ValueError("MIO0 back-reference exceeds pinned range")
                first, second = rom[comp], rom[comp + 1]
                comp += 2
                n, distance = (first >> 4) + 3, ((first & 15) << 8) | second
                src = len(out) - distance - 1
                if src < 0:
                    raise ValueError("MIO0 reference precedes output")
                for _ in range(n):
                    out.append(out[src]); src += 1
                    if len(out) == size:
                        break
    decoded = bytes(out)
    if sha(decoded) != MIO0_DECODED_SHA256:
        raise ValueError("decoded MIO0 hash mismatch")
    return decoded


def checked_span(rom: bytes, span: tuple[int, int, str | None], label: str) -> bytes:
    start, end, expected = span
    data = rom[start:end]
    if len(data) != end - start or (expected is not None and sha(data) != expected):
        raise ValueError(f"{label} exact selected ROM bytes failed hash/bounds validation")
    return data


def read_vertex(data: bytes, off: int) -> dict:
    if off < 0 or off + 16 > len(data):
        raise ValueError("vertex record outside decoded segment")
    b = data[off:off + 16]
    return {
        "position": [int.from_bytes(b[i:i + 2], "big", signed=True) for i in (0, 2, 4)],
        "flag": int.from_bytes(b[6:8], "big"),
        "uv": [int.from_bytes(b[i:i + 2], "big", signed=True) for i in (8, 10)],
        "normal": [signed(b[i], 8) for i in (12, 13, 14)],
        "alpha": b[15],
    }


def decode_first_geometry(bank: bytes) -> tuple[list[dict], list[tuple[int, int, int]]]:
    a, b = VERTEX_SEG4
    verts_raw = bank[a:b]
    if sha(verts_raw) != VERTEX_SHA256 or len(verts_raw) != 15 * 16:
        raise ValueError("selected first vertex batch mismatch")
    vertices = [read_vertex(bank, a + i * 16) for i in range(15)]
    dl_a, dl_b = DISPLAY_LIST_SEG4
    dl = bank[dl_a:dl_b]
    if sha(dl) != DISPLAY_LIST_SHA256 or len(dl) % 8:
        raise ValueError("selected display-list block mismatch")
    # Target command 0x04 loads 15 Vtx records from 0x0400C090. The following
    # six 0xBF commands are one triangle batch; a second VTX command marks the
    # end of this bounded extraction. Triangle indices are byte fields in
    # half-vertex units for this selected F3D command form.
    if dl[0] != 0x04 or int.from_bytes(dl[4:8], "big") != 0x0400C090:
        raise ValueError("first display-list vertex load did not match target selection")
    faces = []
    for off in range(8, len(dl), 8):
        op = dl[off]
        if op == 0x04:
            break
        if op == 0xBF:
            w0 = int.from_bytes(dl[off:off + 4], "big")
            face = tuple(((w0 >> shift) & 0xFF) // 2 for shift in (16, 8, 0))
            if any(i >= len(vertices) for i in face):
                raise ValueError("triangle references outside selected vertex batch")
            faces.append(face)
    if len(faces) != 6:
        raise ValueError(f"expected six first-batch triangle commands, got {len(faces)}")
    return vertices, faces


def decode_geo_skeleton_subset(rom: bytes) -> dict:
    """Decode only the target-verified Mario GeoLayout node chain and command lengths."""
    start, end = GEO_LAYOUT_SUBTREE
    if sha(rom[start:end]) != GEO_LAYOUT_SUBTREE_SHA256:
        raise ValueError("selected GeoLayout subtree hash mismatch")
    commands = []
    cursor = start
    lengths = {0x02: 8, 0x04: 4, 0x12: 8, 0x13: 12, 0x18: 8}
    while cursor < end:
        opcode = rom[cursor]
        if opcode not in lengths:
            raise ValueError(f"unsupported GeoLayout opcode 0x{opcode:02X} in selected subtree")
        size = lengths[opcode]
        if cursor + size > end:
            raise ValueError("GeoLayout command crosses selected subtree boundary")
        row = {"romOffset": f"0x{cursor:X}", "opcode": opcode, "bytes": size}
        if opcode == 0x13:
            raw = rom[cursor:cursor + size]
            dl = int.from_bytes(raw[8:12], "big")
            row.update({"layer": raw[1], "translation": [int.from_bytes(raw[i:i + 2], "big", signed=True) for i in (2, 4, 6)], "displayList": f"0x{dl:08X}"})
        elif opcode == 0x18:
            row["callbackWordAddress"] = f"0x{cursor + 4:X}"
            row["callbackTargetInterpreted"] = False
        commands.append(row)
        cursor += size
    expected = [(0x127EEC, 0x13, 12), (0x127EF8, 0x04, 4), (0x127EFC, 0x13, 12),
                (0x127F08, 0x04, 4), (0x127F0C, 0x18, 8), (0x127F14, 0x18, 8),
                (0x127F1C, 0x12, 8), (0x127F24, 0x04, 4), (0x127F28, 0x13, 12),
                (0x127F34, 0x04, 4), (0x127F38, 0x13, 12), (0x127F44, 0x04, 4),
                (0x127F48, 0x02, 8)]
    got = [(int(r["romOffset"], 16), r["opcode"], r["bytes"]) for r in commands]
    if got != expected:
        raise ValueError("selected GeoLayout command boundaries differed from independent target review")
    nodes = {int(r["romOffset"], 16): r for r in commands if r["opcode"] == 0x13}
    # An open-node command makes the following command its child. Command 0x12
    # is the intervening rotation node between the butt and torso animated parts.
    parent = {0x127EEC: None, 0x127EFC: 0x127EEC, 0x127F28: 0x127F1C, 0x127F38: 0x127F28}
    nodes[0x127F1C] = {"romOffset": "0x127F1C", "opcode": 0x12, "bytes": 8, "nodeType": "rotation"}
    for address, parent_address in parent.items():
        nodes[address]["parentRomOffset"] = None if parent_address is None else f"0x{parent_address:X}"
    nodes[0x127F1C]["parentRomOffset"] = "0x127EFC"
    return {"romRange": [f"0x{start:X}", f"0x{end:X}"], "romSha256": sha(rom[start:end]), "commands": commands, "nodes": list(nodes.values()), "coverage": "root, butt, intervening rotation node, torso, one torso child; later branch target not expanded"}


def rgba5551(data: bytes) -> bytes:
    if len(data) != 32 * 32 * 2:
        raise ValueError("texture must be 32x32 RGBA16")
    pixels = bytearray()
    for off in range(0, len(data), 2):
        value = int.from_bytes(data[off:off + 2], "big")
        r, g, b, alpha = (value >> 11) & 31, (value >> 6) & 31, (value >> 1) & 31, value & 1
        pixels.extend(((r << 3) | (r >> 2), (g << 3) | (g >> 2), (b << 3) | (b >> 2), 255 if alpha else 0))
    return bytes(pixels)


def png_rgba(width: int, height: int, pixels: bytes) -> bytes:
    def chunk(kind: bytes, payload: bytes) -> bytes:
        return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF)
    rows = b"".join(b"\x00" + pixels[y * width * 4:(y + 1) * width * 4] for y in range(height))
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">2I5B", width, height, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(rows, 9)) + chunk(b"IEND", b"")


def load_png_rgba(data: bytes) -> tuple[int, int, bytes]:
    """Read the emitted non-interlaced RGBA8 PNG back through a local parser."""
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise ValueError("PNG signature is invalid")
    offset, compressed, width, height = 8, bytearray(), 0, 0
    while offset < len(data):
        size = int.from_bytes(data[offset:offset + 4], "big")
        kind = data[offset + 4:offset + 8]
        payload = data[offset + 8:offset + 8 + size]
        checksum = int.from_bytes(data[offset + 8 + size:offset + 12 + size], "big")
        if zlib.crc32(kind + payload) & 0xFFFFFFFF != checksum:
            raise ValueError("PNG chunk CRC mismatch")
        if kind == b"IHDR":
            width, height, depth, color_type, compression, filtering, interlace = struct.unpack(">2I5B", payload)
            if (depth, color_type, compression, filtering, interlace) != (8, 6, 0, 0, 0):
                raise ValueError("unsupported local PNG pixel format")
        elif kind == b"IDAT":
            compressed.extend(payload)
        elif kind == b"IEND":
            break
        offset += 12 + size
    raw = zlib.decompress(compressed)
    stride = width * 4
    if len(raw) != height * (stride + 1):
        raise ValueError("PNG decoded row bounds mismatch")
    pixels = bytearray()
    for y in range(height):
        row = raw[y * (stride + 1):(y + 1) * (stride + 1)]
        if row[0] != 0:
            raise ValueError("unsupported PNG filter in local loader")
        pixels.extend(row[1:])
    return width, height, bytes(pixels)


def decode_animation(payload: bytes) -> dict:
    if len(payload) < 24 or sha(payload) != ANIMATION_SHA256:
        raise ValueError("selected animation 114 payload mismatch")
    flags, divisor, start, loop_start, loop_end, parts = (int.from_bytes(payload[i:i + 2], "big", signed=True) for i in range(0, 12, 2))
    value_off, index_off, declared_len = (int.from_bytes(payload[i:i + 4], "big") for i in (12, 16, 20))
    if declared_len != len(payload) or (value_off, index_off) != (300, 48) or parts != 20:
        raise ValueError("animation header differs from selected target values")
    if not (24 <= index_off < value_off < declared_len) or (value_off - index_off) % 4:
        raise ValueError("animation index/value boundaries invalid")
    channel_count = (value_off - index_off) // 4
    if channel_count != 63 or (declared_len - value_off) % 2:
        raise ValueError("animation selected channel/value layout invalid")
    values = [int.from_bytes(payload[i:i + 2], "big", signed=True) for i in range(value_off, declared_len, 2)]
    tracks = []
    for ch in range(channel_count):
        count = int.from_bytes(payload[index_off + 4 * ch:index_off + 4 * ch + 2], "big")
        offset = int.from_bytes(payload[index_off + 4 * ch + 2:index_off + 4 * ch + 4], "big")
        if count == 0 or offset + count > len(values):
            raise ValueError(f"animation channel {ch} points outside selected value array")
        tracks.append({"channel": ch, "frameLimit": count, "valueOffsetElements": offset, "values": values[offset:offset + count]})
    return {"animationId": 114, "header": {"flags": flags, "divisor": divisor, "start": start, "loopStart": loop_start, "loopEnd": loop_end, "partCount": parts}, "tracks": tracks}


def sample_animation(track: dict, frame: int) -> int:
    values = track["values"]
    # Target channel accessor returns valueOffset+frame below the count and
    # valueOffset+count-1 at/after the count.
    return values[min(frame, len(values) - 1)]


def decode_vadpcm_complete_frames(sample: bytes, book: bytes) -> tuple[list[int], dict]:
    """Decode all complete 9-byte VADPCM frames; leave any partial tail opaque.

    Arithmetic is adapted from the pinned depp/vadpcm C implementation. It returns
    scalar accounting for an incomplete trailing byte and never reads it.
    """
    if len(book) != 72:
        raise ValueError("selected predictor book length mismatch")
    order, predictors = int.from_bytes(book[0:4], "big"), int.from_bytes(book[4:8], "big")
    if (order, predictors) != (2, 2):
        raise ValueError("selected VADPCM book header mismatch")
    coeffs = [int.from_bytes(book[i:i + 2], "big", signed=True) for i in range(8, 72, 2)]
    if len(coeffs) != order * predictors * 8:
        raise ValueError("predictor coefficient count mismatch")
    frames, tail = divmod(len(sample), 9)
    state = [0] * 8
    pcm: list[int] = []
    for frame_no in range(frames):
        frame = sample[frame_no * 9:frame_no * 9 + 9]
        control = frame[0]
        scaling, predictor_index = control >> 4, control & 15
        if predictor_index >= predictors:
            raise ValueError(f"VADPCM frame {frame_no} selects invalid predictor")
        predictor = coeffs[predictor_index * order * 8:(predictor_index + 1) * order * 8]
        for vector in range(2):
            accum = [0] * 8
            for k in range(order):
                previous = state[8 - order + k]
                for i in range(8):
                    accum[i] += previous * predictor[k * 8 + i]
            residuals = []
            for i in range(4):
                byte = frame[1 + 4 * vector + i]
                residuals.extend((signed(byte >> 4, 4), signed(byte & 15, 4)))
            pred_tail = predictor[(order - 1) * 8:order * 8]
            for k, residual4 in enumerate(residuals):
                residual = residual4 << scaling
                accum[k] += residual << 11
                for i in range(7 - k):
                    accum[k + 1 + i] += residual * pred_tail[i]
            decoded = []
            for value in accum:
                value >>= 11
                if value < -0x8000 or value > 0x7FFF:
                    value = (value >> 31) ^ 0x7FFF
                decoded.append(value)
            state = decoded + state[8:]
            pcm.extend(decoded)
    return pcm, {"frames": frames, "decodedSamples": len(pcm), "tailBytesNotDecoded": tail}


def verify_loaded(output: Path) -> dict:
    """Reload generated files and check their formats/ranges before declaring load success."""
    texture = (output / "texture.rgba.png").read_bytes()
    png_width, png_height, png_pixels = load_png_rgba(texture)
    if (png_width, png_height) != (32, 32):
        raise ValueError("local PNG loader returned unexpected dimensions")
    geometry = (output / "geometry.obj").read_text(encoding="ascii")
    obj_positions, obj_uvs, obj_normals, obj_faces = [], [], [], []
    for line in geometry.splitlines():
        fields = line.split()
        if not fields:
            continue
        if fields[0] == "v": obj_positions.append(tuple(float(x) for x in fields[1:]))
        elif fields[0] == "vt": obj_uvs.append(tuple(float(x) for x in fields[1:]))
        elif fields[0] == "vn": obj_normals.append(tuple(float(x) for x in fields[1:]))
        elif fields[0] == "f":
            face = [tuple(int(index) for index in ref.split("/")) for ref in fields[1:]]
            if len(face) != 3 or any(len(ref) != 3 or not (1 <= ref[0] <= 15 and 1 <= ref[1] <= 15 and 1 <= ref[2] <= 15) for ref in face):
                raise ValueError("local OBJ parser rejected a face reference")
            obj_faces.append(face)
    nv, nf = len(obj_positions), len(obj_faces)
    anim = json.loads((output / "animation-114.json").read_text(encoding="utf-8"))
    skeleton = json.loads((output / "skeleton-subset.json").read_text(encoding="utf-8"))
    skeleton_parents = {node["romOffset"]: node.get("parentRomOffset") for node in skeleton["nodes"]}
    if skeleton_parents != {"0x127EEC": None, "0x127EFC": "0x127EEC", "0x127F28": "0x127F1C", "0x127F38": "0x127F28", "0x127F1C": "0x127EFC"}:
        raise ValueError("local skeleton loader rejected the selected parent chain")
    with wave.open(str(output / "sample0-bounded-preview.wav"), "rb") as wav:
        audio_frames = wav.getnframes()
        audio_channels = wav.getnchannels()
        audio_width = wav.getsampwidth()
    with wave.open(str(output / "sample2-preview.wav"), "rb") as wav:
        complete_audio_frames = wav.getnframes()
        complete_audio_channels = wav.getnchannels()
        complete_audio_width = wav.getsampwidth()
    if nv != 15 or len(obj_uvs) != 15 or len(obj_normals) != 15 or nf != 6 or len(anim["tracks"]) != 63 or len(skeleton["nodes"]) != 5 or audio_channels != 1 or audio_width != 2 or audio_frames != 3152 or complete_audio_frames != 1440 or complete_audio_channels != 1 or complete_audio_width != 2:
        raise ValueError("local asset loader count/bounds check failed")
    for track in anim["tracks"]:
        if len(track["values"]) != track["frameLimit"] or not track["values"]:
            raise ValueError("local animation loader found an invalid track")
    for wav_path, expected_frames in ((output / "sample0-bounded-preview.wav", 3152), (output / "sample2-preview.wav", 1440)):
        with wave.open(str(wav_path), "rb") as wav:
            pcm = wav.readframes(expected_frames + 1)
            if len(pcm) != expected_frames * 2 or wav.getnframes() != expected_frames:
                raise ValueError("local WAV loader rejected sample frame bounds")
    return {"loadedTextureDimensions": [png_width, png_height], "loadedTextureRgbaSha256": sha(png_pixels), "loadedVertices": nv, "loadedUvSets": len(obj_uvs), "loadedNormals": len(obj_normals), "loadedTriangles": nf, "loadedSkeletonNodes": len(skeleton["nodes"]), "loadedAnimationTracks": len(anim["tracks"]), "loadedSample0Frames": audio_frames, "loadedSample2Frames": complete_audio_frames, "loadedAudioChannels": audio_channels, "loadedAudioSampleWidthBytes": audio_width}


def canonical_hash(value: object) -> str:
    return sha(json.dumps(value, sort_keys=True, separators=(",", ":")).encode("utf-8"))


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("rom", type=Path, help="private 8 MiB Mario USA rev 0 ROM")
    ap.add_argument("--output", type=Path, required=True, help="new/ignored child directory under this script's private directory")
    ap.add_argument("--validation-fixture", type=Path, help="optional private independent scalar fixture; never needed for public reproduction")
    args = ap.parse_args()
    rom = args.rom.read_bytes()
    if len(rom) != 0x800000 or rom[:4] != bytes.fromhex("80371240") or sha(rom) != ROM_SHA256:
        raise SystemExit("ROM identity mismatch; refusing to decode")
    script_dir = Path(__file__).resolve().parent
    repository_root = next((p for p in (script_dir, *script_dir.parents) if (p / ".gitignore").is_file() and (p / "tools").is_dir()), None)
    if repository_root is None:
        raise SystemExit("could not locate MarioCraft repository root for private-output guard")
    private_dir = repository_root / "tools" / "issue11-local" / "issue51-decoded"
    ignore_file = repository_root / ".gitignore"
    if not ignore_file.is_file() or "tools/" not in ignore_file.read_text(encoding="utf-8").splitlines():
        raise SystemExit("private output safety check failed: tools/ is not ignored by the repository")
    output_dir = args.output.resolve()
    if output_dir == private_dir or not output_dir.is_relative_to(private_dir):
        raise SystemExit("output must be a child of tools/issue11-local/issue51-decoded")
    if output_dir.exists() and any(output_dir.iterdir()):
        raise SystemExit("output directory must be new or empty; choose a fresh private child directory")
    output_dir.mkdir(parents=True, exist_ok=True)
    bank = mio0(rom, *MIO0_RANGE)
    geo = rom[GEO_RANGE[0]:GEO_RANGE[1]]
    if sha(geo) != GEO_SHA256:
        raise SystemExit("raw GeoLayout bank mismatch")
    skeleton = decode_geo_skeleton_subset(rom)
    (output_dir / "skeleton-subset.json").write_text(json.dumps(skeleton, separators=(",", ":")), encoding="utf-8")
    texdata = bank[TEXTURE_SEG4[0]:TEXTURE_SEG4[1]]
    if sha(texdata) != TEXTURE_SHA256:
        raise SystemExit("texture bytes mismatch")
    pixels = rgba5551(texdata)
    (output_dir / "texture.rgba.png").write_bytes(png_rgba(32, 32, pixels))
    vertices, faces = decode_first_geometry(bank)
    obj = ["# Private selected geometry from pinned Mario USA rev 0; first 15-vertex batch only."]
    obj += ["v " + " ".join(str(n) for n in v["position"]) for v in vertices]
    obj += ["vt " + " ".join(f"{n / 32:.6f}" for n in v["uv"]) for v in vertices]
    obj += ["vn " + " ".join(f"{n / 127:.6f}" for n in v["normal"]) for v in vertices]
    obj += ["f " + " ".join(f"{i+1}/{i+1}/{i+1}" for i in face) for face in faces]
    (output_dir / "geometry.obj").write_text("\n".join(obj) + "\n", encoding="ascii")
    animation = decode_animation(checked_span(rom, (*ANIMATION_ROM, ANIMATION_SHA256), "animation 114"))
    vertex_selectors = (0, 7, 14)
    vertex_fields = [{"index": i, "seg4Offset": f"0x{VERTEX_SEG4[0] + i * 16:x}", **vertices[i]} for i in vertex_selectors]
    pixel_selectors = ((0, 0), (7, 11), (15, 16), (31, 31))
    texture_pixels = []
    rgba = rgba5551(texdata)
    for x, y in pixel_selectors:
        offset = (y * 32 + x) * 4
        value16 = int.from_bytes(texdata[(y * 32 + x) * 2:(y * 32 + x) * 2 + 2], "big")
        texture_pixels.append({"x": x, "y": y, "rgba5": [(value16 >> 11) & 31, (value16 >> 6) & 31, (value16 >> 1) & 31, value16 & 1], "rgba8": list(rgba[offset:offset + 4])})
    animation_selectors = ((0, 0), (0, 7), (1, 3), (6, 0), (6, 11), (62, 100))
    animation_samples = []
    for track_id, frame in animation_selectors:
        track = animation["tracks"][track_id]
        index = track["valueOffsetElements"] + min(frame, track["frameLimit"] - 1)
        animation_samples.append({"track": track_id, "frame": frame, "count": track["frameLimit"], "valueOffsetElements": track["valueOffsetElements"], "sampledElementIndex": index, "valueS16": sample_animation(track, frame)})
    selected_hashes = {"vertices": canonical_hash(vertex_fields), "texturePixels": canonical_hash(texture_pixels), "animationSamples": canonical_hash(animation_samples)}
    if selected_hashes != {"vertices": EXPECTED_SELECTED_VERTEX_FIELDS_SHA256, "texturePixels": EXPECTED_SELECTED_TEXTURE_PIXELS_SHA256, "animationSamples": EXPECTED_SELECTED_ANIMATION_SAMPLES_SHA256}:
        raise ValueError(f"independently selected scalar field digest mismatch: {json.dumps(selected_hashes, sort_keys=True)}")
    if args.validation_fixture:
        fixture_path = args.validation_fixture.resolve()
        if not fixture_path.is_file():
            raise ValueError("private validation fixture does not exist")
        fixture = json.loads(fixture_path.read_text(encoding="utf-8"))
        expected_vertices = fixture.get("vertices")
        expected_pixels = fixture.get("texturePixels")
        expected_animation = fixture.get("animationSamples")
        if expected_vertices is not None and expected_vertices != vertex_fields:
            raise ValueError("private independent vertex fixture mismatch")
        if expected_pixels is not None and expected_pixels != texture_pixels:
            raise ValueError("private independent texture fixture mismatch")
        if expected_animation is not None and expected_animation != animation_samples:
            raise ValueError("private independent animation fixture mismatch")
    if (animation["header"]["partCount"] + 1) * 3 != len(animation["tracks"]):
        raise ValueError("independent animation structure check failed")
    (output_dir / "animation-114.json").write_text(json.dumps(animation, separators=(",", ":")), encoding="utf-8")
    # Validate all exact audio inputs and linkage now. Waveform decoding remains
    # gated on the separately reviewed decoder/boundary oracle; raw bytes stay
    # in memory and are never emitted by this program.
    audio_inputs = {name: checked_span(rom, span, name) for name, span in AUDIO.items()}
    if len(audio_inputs["sample"]) != 1774:
        raise ValueError("sample stream length changed")
    pcm, audio_accounting = decode_vadpcm_complete_frames(audio_inputs["sample"], audio_inputs["book"])
    # 22050 Hz is a preview container rate only. Exact instrument playback-rate
    # reconstruction is outside this bounded sample-resource check.
    with wave.open(str(output_dir / "sample0-bounded-preview.wav"), "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(22050)
        wav.writeframes(struct.pack("<" + "h" * len(pcm), *pcm))
    pcm_le = struct.pack("<" + "h" * len(pcm), *pcm)
    if sha(pcm_le) != EXPECTED_PCM_SHA256:
        raise ValueError("independent C decoder PCM oracle hash mismatch")
    pcm2, audio2_accounting = decode_vadpcm_complete_frames(audio_inputs["instrument2_sample"], audio_inputs["instrument2_book"])
    if (audio2_accounting["frames"], audio2_accounting["decodedSamples"], audio2_accounting["tailBytesNotDecoded"]) != (90, 1440, 0):
        raise ValueError("complete instrument-2 sample must cover exactly 90 complete frames")
    pcm2_le = struct.pack("<" + "h" * len(pcm2), *pcm2)
    if sha(pcm2_le) != EXPECTED_PCM2_SHA256:
        raise ValueError("independent C decoder instrument-2 PCM oracle hash mismatch")
    with wave.open(str(output_dir / "sample2-preview.wav"), "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(22050)
        wav.writeframes(pcm2_le)
    loaded = verify_loaded(output_dir)
    print(json.dumps({
        "romSha256": ROM_SHA256,
        "workflowVersion": "issue51-local-decoder-v1",
        "scriptSha256": sha(Path(__file__).read_bytes()),
        "runtime": {"python": platform.python_version(), "platform": platform.platform(), "standardLibraryOnly": True},
        "decodedMio0Sha256": sha(bank),
        "geoBankSha256": sha(geo),
        "textureSourceSha256": sha(texdata),
        "textureRgbaSha256": sha(pixels),
        "firstVertexBatchSha256": sha(bank[slice(*VERTEX_SEG4)]),
        "geometryVertices": len(vertices),
        "geometryTriangles": len(faces),
        "animation114Sha256": sha(checked_span(rom, (*ANIMATION_ROM, ANIMATION_SHA256), "animation 114")),
        "animationChannels": len(animation["tracks"]),
        "skeletonGeoLayoutRangeSha256": skeleton["romSha256"],
        "skeletonDecodedNodes": len(skeleton["nodes"]),
        "skeletonCommandRecords": len(skeleton["commands"]),
        "audioExactInputs": {k: {"bytes": len(v), "sha256": sha(v)} for k, v in audio_inputs.items()},
        "independentSelectedChecks": {"vertexFieldDigest": selected_hashes["vertices"], "texturePixelDigest": selected_hashes["texturePixels"], "animationSampleDigest": selected_hashes["animationSamples"], "privateFixtureCompared": bool(args.validation_fixture)},
        "audioDecode": {
            "instrument0": {**audio_accounting, "pcmSha256": sha(pcm_le), "independentCOracleSha256": EXPECTED_PCM_SHA256, "nominalLoopEnd": 3153, "boundaryStatus": "one trailing byte not decoded; nominal loop end exceeds complete-frame output by one sample"},
            "instrument2": {**audio2_accounting, "pcmSha256": sha(pcm2_le), "independentCOracleSha256": EXPECTED_PCM2_SHA256, "nominalLoopEnd": 1440, "boundaryStatus": "complete frames agree with nominal loop end"},
            "previewSampleRateHz": 22050,
            "sampleRateStatus": "preview choice; target playback rate unresolved",
        },
        "loaded": loaded,
        "privateOutputFiles": {file.name: {"bytes": file.stat().st_size, "sha256": sha(file.read_bytes())} for file in sorted(output_dir.iterdir()) if file.is_file()},
        "publicationSafety": "outputs contain private derived assets; do not publish output directory",
    }, indent=2))


if __name__ == "__main__":
    main()
