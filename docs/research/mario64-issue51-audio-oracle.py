#!/usr/bin/env python3
"""Run a separately built depp/vadpcm C decoder on two pinned private samples.

No ROM-derived bytes are embedded or written. Only scalar hashes are printed.
Build the external DLL from the documented pinned primary source first.
"""
import argparse
import ctypes
import hashlib
import json
import struct
from pathlib import Path


def digest(data):
    return hashlib.sha256(data).hexdigest().upper()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('rom', type=Path)
    parser.add_argument('--dll', required=True, type=Path)
    args = parser.parse_args()
    rom = args.rom.read_bytes()
    if len(rom) != 8388608 or digest(rom) != '17CE077343C6133F8C9F2D6D6D9A4AB62C8CD2AA57C40AEA1F490B4C8BB21D91':
        raise SystemExit('Pinned ROM identity mismatch')
    dll_path = args.dll.resolve(strict=True)
    library = ctypes.CDLL(str(dll_path))
    decode = library.vadpcm_decode
    decode.argtypes = [ctypes.c_int, ctypes.c_int, ctypes.c_void_p,
                       ctypes.c_void_p, ctypes.c_size_t, ctypes.c_void_p,
                       ctypes.c_void_p]
    decode.restype = ctypes.c_int
    cases = [
        ('instrument0', 0x5936A0, 1774, 0x57B8B0, [1, 7, 23, 197]),
        ('instrument2', 0x596660, 810, 0x57B9D0, [90]),
    ]
    results = []
    for label, start, size, book, frame_counts in cases:
        order, predictors = struct.unpack_from('>II', rom, book)
        if (order, predictors) != (2, 2):
            raise SystemExit('Unexpected pinned codebook dimensions')
        count = order * predictors * 8
        coefficients = (ctypes.c_int16 * count)(
            *struct.unpack_from('>' + str(count) + 'h', rom, book + 8))
        for frames in frame_counts:
            if frames * 9 > size:
                raise SystemExit('Requested frames exceed sample record')
            source = (ctypes.c_ubyte * (frames * 9)).from_buffer_copy(
                rom[start:start + frames * 9])
            state = (ctypes.c_int16 * 8)()
            samples = (ctypes.c_int16 * (frames * 16))()
            error = decode(predictors, order, coefficients, state,
                           frames, samples, source)
            if error:
                raise SystemExit('External decoder error: ' + str(error))
            pcm_le = struct.pack('<' + 'h' * len(samples), *samples)
            results.append({'resource': label, 'frames': frames,
                            'samples': len(samples),
                            'recordBytes': size, 'decodedBytes': frames * 9,
                            'recordTailBeyondDecodedBytes': size - frames * 9,
                            'pcmS16LEHash': digest(pcm_le)})
    print(json.dumps({'externalImplementation': 'depp/vadpcm',
                      'sourceCommit': '413d52043a928c5fac066ccda1e69b73c7f7132b',
                      'dllSha256': digest(dll_path.read_bytes()),
                      'zeroInitialState': True, 'results': results}, indent=2))


if __name__ == '__main__':
    main()
