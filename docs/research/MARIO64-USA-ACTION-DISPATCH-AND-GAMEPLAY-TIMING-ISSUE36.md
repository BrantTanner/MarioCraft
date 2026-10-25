# USA revision 0 action dispatch and gameplay timing

Status: issue #36 evidence captured; numeric action IDs are mapped only on the branches listed below. This report supports closing #36 while issue #11 remains open. It does not claim complete action-table or dynamic level-script coverage.

## Identity and reproducibility

Target ROM: Super Mario 64 (USA), 8 MiB, SHA-256 `17CE077343C6133F8C9F2D6D6D9A4AB62C8CD2AA57C40AEA1F490B4C8BB21D91`. Analysis used the local copy; original bytes were not modified. Disassembly is reproducible with `python MarioCraft/tools/issue11-local/disasm_range.py START END`; ranges are half-open ROM offsets and the script prints big-endian instruction words and decoded MIPS. The verified linear ROM/RAM mapping is ROM `0x1000` → RDRAM `0x80246000`, equivalently PC `0x80245000 + ROM offset`, only through ROM offset `0xE855F`. Do not extend it to later runtime targets.

Range hashes below are SHA-256 over the exact ROM byte intervals. Static inspection used the local Ghidra 12.1.4 MIPS big-endian analysis project and a temporary project copy. Source navigation used `n64decomp/sm64` commit `9921382a68bb0c865e5e45eb594d9c64db59b1af` to name known loop functions; source names are corroboration, while instruction evidence comes from the exact ROM.

| ROM range | Runtime PC range | Finding | SHA-256 |
|---|---|---|---|
| `0xDCF4..0xDE58` | `0x80252CF4..0x80252E58` | Common action setter; stores old action, new action, argument, clears state and timer | `1e210f62f404d4be0e0cd008271c03d47d1166bdf85fe25d50b0b3242918f0bc` |
| `0xE178..0xE1A4` | `0x80253178..0x802531A4` | Wrapper calls common setter | `0fbb84f63084069aa3a1d5128c4b505a5ae079c34df053136535da7310758837` |
| `0x22728..0x22954` | `0x80267728..0x80267954` | Input tests include bit `0x0002` → action `0x03000880` | `387d31b43762e7c5497c41467cda8002cebff38174fc7cbad499e0e95983b0f7` |
| `0x26BB4..0x26CB8` | `0x8026BBB4..0x8026BCB8` | Context-gated tests: bit `0x2000` → `0x830008AB`; bit `0x8000` → `0x008008A9` | `78a87e4cafbdc3b6caeebd2d062b4d3837a0c4fc237c7a40e1c636e02ae1ef78` |
| `0x26DCC..0x26E70` | `0x8026BDCC..0x8026BE70` | Parallel tests: bit `0x2000` → `0x0188088A`; bit `0x8000` → `0x008008A9` | `b0628624ac654289768ae94138443c2717d545db20a3e100e24cd7449ac1cd64` |

All runtime addresses in the table use the verified prefix formula, e.g. ROM `0x26C18` maps to PC `0x8026BC18`.

The setter receives the Mario-state pointer as an argument. Its verified field effects are previous action `+0x10`, current action `+0x0C`, action argument `+0x1C`, action state `+0x18` reset, and action timer `+0x1A` reset. Runtime sampling used candidate Mario base `0x8033B170`; these offsets are structurally corroborated by the setter, while the global base remains a runtime candidate. The setter dispatches pre-transition work by action group bits `(action & 0x1C0)` for values `0x40`, `0x80`, `0xC0`, and `0x100`; that is not a complete action inventory.

## Runtime correlation and limits

The published sanitized trace [batch 04 JSONL](mario64-issue11-trial04-sanitized.jsonl), SHA-256 `bf6caf471b97e2a3af9b4ac32e0127a6dd2769db75fc5256e693018c491d1610`, contains 45 numeric samples and 11 input-event records selected from a 3,600-row Project64 draw-callback capture. The capture script also recorded host timestamps and counter reads around field groups. The raw trace, helper logs, save state, ROM, and memory dump remain local.

Observed associations in that capture:

- A key: raw controller down/pressed mask `0x8000`; Mario input candidate `0x0082`; current action candidate `0x03000880`; candidate Y moved from 260 through 467.5 and back to 260. The exact handler tests input bit `0x0002` and passes `0x03000880`. This is a numeric correlation; the human action label is not asserted.
- Z key: raw controller mask `0x2000`; Mario input candidate included `0x8000`; current action candidate changed to `0x008008A9`. Several exact-ROM handlers assign this word on input bit `0x8000`, supporting the branch association. The full input transformation and caller gating remain incomplete.
- B key: raw mask `0x4000`; candidate action changed to `0x00800380`. No exact caller branch has been matched to this event. Keep it numeric and unclassified.

No action names are asserted. The public reconstruction’s label for `0x008008A9` is navigation-only and is not treated as target-ROM proof. Conditions, state gates, transformations, alternate callers, and the full action table are incomplete. Dynamic level-script code is called at runtime address `0x803805C8`, beyond the verified linear mapping; its target and full dispatch remain unresolved for later issues.

## Gameplay timer evidence

The two exact-ROM store sites targeting `0x8032D5D4` within the verified code prefix are:

| ROM range | Runtime PC | Instruction | SHA-256 |
|---|---|---|---|
| `0x2FB0..0x2FC4` | `0x80247FB0..0x80247FC4` | load, increment, store `0x8032D5D4` in render initialization | `c6ab86d45fe2bf7e66b07e3f91e5bd63c39c85fec99e1729a0ea12267b97fd6b` |
| `0x31B8..0x31C8` | `0x802481B8..0x802481C8` | load, increment, store `0x8032D5D4` after display/vblank waits and buffer swap | `3d666dc88e661cdd57fdbf370aa60f71515c8158f09039058b53b16e47d61005` |

The containing render-init function at ROM `0x2F08..0x2FDC` (SHA-256 `26f8dff9ce1d7c2ae52bd4a2416c4a2944475c4df9d0e9eb8d29d102682b01bc`) increments during one-time initialization; it is not evidence of an ongoing gameplay tick. The steady loop at ROM `0x3090..0x31E0` (SHA-256 `e99eae5c8e55a9d9ea6c448bcbb5cc3f2f7f8fbb18268feb21ef03a2761821bf`) calls the blocking queue receive three times: first on queue `0x8033B028`, then submits a display task, then receives twice on queue `0x8033B010`; between the latter two receives it calls `FUN_80323A00` with the selected buffer address. It then increments `0x8032D5D4`. The interpretation of these queue addresses as graphics-completion/vblank events is supported by source navigation, but the event-producer registration is not independently traced. The pinned source names these functions `render_init` and `display_and_vsync`; the normal `thread5_game_loop` calls input sampling, level-script execution, then `display_and_vsync` once per iteration. The exact caller window at ROM `0x3BB4..0x3BE0` (PC `0x80248BB4..0x80248BE0`, SHA-256 `1e8a7baf5c57bbfd916f17ec97dc39cc3b867f5af9bfdae75fa12d85fa8cd493`) shows calls to controller processing, controller sampling `0x80248638`, dynamic level script `0x803805C8`, and loop body `0x80248090` in that order. The target `0x803805C8` is reached through dynamic code and is not resolved by this caller window. Supporting utility bodies are also inside the validated ROM/RAM prefix: blocking queue receive `0x80322800` is ROM `0xDD800..0xDD938`, SHA-256 `30c7c392e89a155e24ce6d4c13795b592026aa188255df26956af23eb12d95a7`; buffer-swap routine `0x80323A00` is ROM `0xDEA00..0xDEA50`, SHA-256 `1bd01f8313f326adaf7c67a77594cb518761ccac2846c04b7f3c22e581846cbf`. The receive body checks queue count and loops through the scheduler wait when empty; the swap body writes the selected buffer pointer and sets VI-context flag `0x10`. The pinned source navigation to [`game_init.c` at the exact commit](https://github.com/n64decomp/sm64/blob/9921382a68bb0c865e5e45eb594d9c64db59b1af/src/game/game_init.c) names the corresponding loop functions. These facts establish the recurring word as the main-loop/global timer counter in the normal loop, not as a universal Mario physics step or proof that every gameplay state updates Mario once per increment.

Project64 runtime batch 04 recorded 3,600 draw callbacks across 60.082 host seconds. The counter moved from 5,607 to 7,406 (1,799 delta; about 29.95 increments/s). Issue #35’s two 1,200-callback runs each advanced 599 over about 20.01 seconds. This calibrates observed cadence for the tested Project64 setup and courtyard state only. Draw callbacks are host-driven observations, not emulated ticks; paired counter reads bracket reads but are not atomic snapshots. Pause, menu, level-state, lag, and VI scheduling effects were not separately measured. The VI event producer registration was not independently traced, so a fixed 30 Hz guarantee is not claimed.

## Completion boundary

This report completes issue #36’s bounded static action-dispatch map and validates `0x8032D5D4` as the recurring main-loop timer under the tested normal loop, with runtime correlation and limits documented. It does not complete issue #11. Unnamed IDs, the unlocated B transition, handler gates, the complete dynamic level-script/action dispatcher, exact per-state Mario update cadence, and VI event source remain future work (including dependent issues #39/#40 and ROM segment-loader work in #15).

## Standalone ROM listing and range-hash script

This Python 3 script uses only the standard library. Save as `mario64-rom-range.py`; it validates the USA ROM hash, prints decoded common MIPS instructions with the verified-prefix PC mapping, and hashes each exact half-open range. It intentionally refuses offsets outside the prefix mapping.

```python
from pathlib import Path
import hashlib
import sys

ROM_SHA256 = "17CE077343C6133F8C9F2D6D6D9A4AB62C8CD2AA57C40AEA1F490B4C8BB21D91"
BASE = 0x80245000
LIMIT = 0xE8560  # exclusive; mapping verified through byte 0xE855F
REG = "zero at v0 v1 a0 a1 a2 a3 t0 t1 t2 t3 t4 t5 t6 t7 s0 s1 s2 s3 s4 s5 s6 s7 t8 t9 k0 k1 gp sp s8 ra".split()
OPS = {9:"addiu",10:"slti",11:"sltiu",12:"andi",13:"ori",14:"xori",15:"lui",32:"lb",33:"lh",35:"lw",36:"lbu",37:"lhu",40:"sb",41:"sh",43:"sw",55:"ld"}
FUN = {8:"jr",9:"jalr",32:"add",33:"addu",34:"sub",35:"subu",36:"and",37:"or",38:"xor",39:"nor",0:"sll"}

def decode(w, pc):
    op=w>>26; rs=(w>>21)&31; rt=(w>>16)&31; rd=(w>>11)&31; sh=(w>>6)&31; fn=w&63
    imm=w&0xffff; si=imm-(0x10000 if imm&0x8000 else 0)
    if op==0 and fn in FUN:
        if fn in (8,): return f"jr {REG[rs]}"
        if fn==9: return f"jalr {REG[rd]},{REG[rs]}"
        if fn==0: return f"sll {REG[rd]},{REG[rt]},{sh}"
        return f"{FUN[fn]} {REG[rd]},{REG[rs]},{REG[rt]}"
    if op in (2,3): return f"{'j' if op==2 else 'jal'} 0x{(((pc+4)&0xf0000000)|((w&0x3ffffff)<<2)):08X}"
    if op in (4,5,6,7): return f"{ {4:'beq',5:'bne',6:'blez',7:'bgtz'}[op]} {REG[rs]},{REG[rt]},0x{pc+4+(si<<2):08X}"
    if op in OPS:
        name=OPS[op]
        if op in (32,33,35,36,37,40,41,43,55): return f"{name} {REG[rt]},{si}({REG[rs]})"
        return f"{name} {REG[rt]},{REG[rs]},{(imm if op in (12,13,14,15) else si):#x}"
    return "?"

rom=Path(sys.argv[1]).read_bytes()
assert hashlib.sha256(rom).hexdigest().upper()==ROM_SHA256, "unexpected ROM"
for arg in sys.argv[2:]:
    start_s,end_s=arg.split(":"); start,end=int(start_s,16),int(end_s,16)
    assert start%4==0 and end%4==0 and 0x1000<=start<end<=LIMIT
    print(f"RANGE {start:06X}..{end:06X} sha256={hashlib.sha256(rom[start:end]).hexdigest()}")
    for off in range(start,end,4):
        w=int.from_bytes(rom[off:off+4],"big"); pc=BASE+off
        print(f"{off:06X} {w:08X} {pc:08X} {decode(w,pc)}")
```

Example invocation: `python mario64-rom-range.py "C:\\path\\Super Mario 64 (USA).z64" 0x26C18:0x26C88 0xDCF4:0xDE58 0x31B8:0x31C8`. Unsupported instruction forms print `?` after their raw 32-bit word; the raw word, ROM offset, PC, and range hash remain reproducible evidence.



