# Mario 64 audio request and sequence lifecycle (issue #50)

## Scope and evidence rules

This dossier maps the target ROM's sound-request and music-sequence plumbing, then records narrowly scoped Project64 observations. Runtime values are numeric guest fields and queued request IDs. They do not establish that a request was accepted by a voice, decoded, or audible. No waveform or whole-system audio was captured. The analysis-copy ROM and state fixtures remain local; this report contains no ROM bytes, extracted samples, raw memory, save states, or screenshots.

The target was the 8 MiB USA revision-0 analysis copy (SHA-256 `17CE077343C6133F8C9F2D6D6D9A4AB62C8CD2AA57C40AEA1F490B4C8BB21D91`) in the portable Project64 build (SHA-256 `C257C662D4E0B2CE1732B7F4A7D5DD4682F604CB082BBDBFCE5181470D6DBFB8`). All runtime reads used a guarded external `OpenProcess(PROCESS_QUERY_INFORMATION|PROCESS_VM_READ)` / `ReadProcessMemory` sampler; it did not inject inputs or write memory. Inputs used the already reviewed Project64-scoped helper, SHA-256 `2E78A7B6685C010C8FD2DDAD9D92815A77E9FB87202EAE6DA640A8579B0F9463`. Samples include per-read loop-counter brackets, which are not atomic snapshots and are not an audio clock.

## Sound requests and effect callers

The request submitter at PC `8031EB00` (ROM `D9B00`, 48 bytes, SHA-256 `1C8030B66E81554A34AD2DE254654341111204835C71FC45C6B4EF5AEF4FD57F`) appends a 32-bit ID and a source-position pointer to a 256-entry ring at `80361498`, with 8-byte records. U8 write/read cursors are at `80332F44` and `80332F40`; the drain loop is PC `8031EDEC` (ROM `D9DEC`, 132 bytes, SHA-256 `9C0717CFE716FB661D699D59FBF33AF365087AD7265703C66A3FD2A0390C34B2`). The consumer at PC `8031EB30` (ROM `D9B30`, 700-byte address envelope, 696 Ghidra body bytes, SHA-256 `6BE924A8F694F774C3D85171FE612D3139A3A0081D1229D54B1A29D015A435E8`) selects a bank from the high ID nibble, extracts the sound index and priority, checks bank limits and disable flags, and arbitrates against an existing active sound entry associated with the source pointer. A new request replaces that entry only when its priority is at least as high. This is request/state arbitration, not proof of a synthesized voice. Therefore a queue ID is a submission, not proof of acceptance or playback. The byte envelopes include gaps where the disassembler body is shorter; hashes cover the full stated envelope.

Mario's action/surface path adds the Mario state's terrain sound offset (`+0x14`) to a base request in PC `80251310`, with exceptions controlled by Mario flags and certain IDs. The source position comes from the Mario object at state `+0x88`, object `+0x54`. PC `80251444` gates one-shot requests on action flags; PC `8025163C` combines action sound and voice helpers. PC `802518A8` maps numeric floor types to terrain offsets; this report keeps those as numeric mappings because surface labels require the separate issue #43 cross-check. Animation-frame requests are routed through PC `80263EE4`; eligible step frames call the terrain helper with base ID `0x06108081` and terrain selector zero (or `0x06208081` for the documented alternate frame). A punch action path at PC `80274F10` submits `0x241E8081` on its matching animation case.

The voice helper at PC `8025118C` selects numeric IDs from two families: for action `0x01000882`, `0x242B8081 + (random % 5)*0x10000`; otherwise `0x24008081 + (random % 3)*0x10000`. It uses one-shot flag `0x20000`. Numeric family membership does not establish a recognizable voice identity.

## Music tables and sequence control

Initialization at PC `8031950C` loads sequence, bank-control, sample, and sequence-to-bank tables through the target loaders. The target table loader at PC `80325CD8` adds the ROM base to each table entry's relative offset. The header inventories are:

| Table | ROM header | Revision / count | Aligned header+entries | SHA-256 |
|---|---:|---:|---:|---|
| Sequences | `0x7B0860` | 3 / 35 | 288 B | `14A4FF536CC2D8FC28E2E0EB234FF6A5A2C6840418244D9F652289C79E08F9FC` |
| Bank control | `0x57B720` | 1 / 38 | 320 B | `2012230D09CDC2BDDDA5850987E1C9CB9D5C850ACB766870638286D1F827DAE2` |
| Samples | `0x593560` | 2 / 38 | 320 B | `6E1283529E9A9DA8CFD6A21CCC913488CA3D6B8BBD9179DC4CFD218EA258B4C3` |

Entries carry relative offset/size pairs after the header. The sequence-bank association table begins at ROM `0x7CC620`; its target-loaded 256-byte window `[0x7CC620,0x7CC720)` hashes to `C7DE922BC792C9C715284C8FC0BF679732ED73DB00C4827E49C4250CD521C4C1`. This is a loaded window, not a complete bank-map format/catalog. All 35 sequence, 38 control, and 38 sample entry ranges audited within the 8 MiB ROM; their entry unions span `0x7B0980–0x7CC620`, `0x57B860–0x593520`, and `0x5936A0–0x7B0850` respectively (half-open extrema, not a claim that every byte in the union is occupied). Bank-control entries contain instrument/drum references; relocation code connects instrument sample records to sample-bank data, loop metadata, and predictor-book metadata. The target loader/transfer path uses 9-byte compressed blocks representing 16-sample frames and references predictor books. This supports a bounded ADPCM-style 9-byte/16-sample framing description, not a complete decoder or a claim about the last byte of any sample stream. One examined sample data region was 1,774 bytes, which is not divisible by 9; the final decoding boundary remains unresolved for the decoder audit.

The sequence starter at PC `80320544` records player-0 sequence ID (`803331AC`, low 7 bits of its argument), resets associated counters, sets player state, and enters the sequence loader. Player-0 requests pass through PC `803219AC`: a U8 queue count at `80333238` and up to six `(sequence ID, priority)` pairs at `80364B88` support priority ordering, duplicate detection, immediate start, deferral, and advancement/removal at PC `80321BAC`. Transition helpers at `80321E48` and `80321F48` manage additional music state. The stop path at `80320678` sets the current sequence byte to `0xFF` for player 0 and calls `8031E578`; this proves a sequence-stop request path, not audible fade completion. The task scheduler at `8024651C` chooses one of two task pointers based on its argument, calls the target task loader and starter, then sets its wrapper state; this branch alone does not identify a user-visible track.

### Sequence bytecode and tick timing

The sequence driver at `8031E240` iterates three players at stride `0x140` and calls the sequence-player interpreter at `8031D9EC` only when the player's `0x80000000` enable bit is set. The interpreter advances a U16 tempo accumulator at player `+0x0C` by the U16 tempo at `+0x0A`, compares it with global threshold `0x80226B7C`, subtracts the threshold when a sequence tick is due, and processes a U16 delay at `+0x12` before reading more commands. It dispatches up to 16 channel slots through the per-channel update routine. The Project64 host audio-tick duration was not measured; the main-loop counter is not an audio clock. Pause wrappers toggle player flag bit `0x20000000`; target sequence-player gating checks the player pause mask at `+4`, including bit `0x80`, before processing.

Three target readers define the operand encodings: `8031C03C` reads one U8 and advances one byte; `8031C050` reads a big-endian U16 and advances two bytes; `8031C080` reads one U8 when the first byte is below `0x80`, otherwise forms a two-byte value from its low seven bits and the following byte. The channel-start command family `0x90` reads a U16 offset relative to sequence base `+0x14` and starts a channel within the sequence player. The interpreter handles `0xFD` as a delay operand, `0xFE` as a yield, `0xFF` as a return or sequence-player end when its call stack is empty, `0xFC` as a sequence-relative call, and `0xF8`/`0xF7` as counted-loop setup/iteration. `0xDD` sets tempo from the next U8 multiplied by 48; `0xDC` adjusts tempo by a signed byte times 48 and clamps it to the global threshold. This is a representative control-flow subset; the complete command catalog and host timing calibration remain outside the MVP boundary.

Pause wrappers at `80248C58` and `80248CE8` call a shared pause-flag helper at `803210D4` with 1 and 0 respectively. The helper toggles bit `0x20000000` across the three player flags. The pause-related mode transition is corroborated by the runtime trial below. Start/stop and level-transition callers can request background music, fade-out, shell/cap music, and secondary music transitions; these describe sequence control requests, not captured output.


### Target code ranges used

The static function hashes below cover the stated target ROM ranges. For main-image functions, PC-to-ROM mapping is used only within the validated main code interval; other audio addresses were checked against exact target ROM blocks. The manifest lists the remaining helper, loader, queue, pause, and transition ranges and full hashes. Byte envelopes include any gaps up to `maxBody+1`; the separate body count is retained where it differs.

| Target function | PC range | ROM range | Bytes / body | SHA-256 |
|---|---|---|---:|---|
| action voice helper | `[8025118C,80251274)` | `[C18C,C274)` | 232 | `26DDFFE726725BCDBD56429DDBB29C723F171A0AA0CB82CE235920E92C561650` |
| terrain-adjusted submit caller | `[80251310,80251444)` | `[C310,C444)` | 308 | `FDEC2D4334401E5A3A51228395C580FB499AC95CD8393CA709F01472156E28B8` |
| one-shot action gate | `[80251444,802514AC)` | `[C444,C4AC)` | 104 | `73C1C28EFB150A0E812B902227B72FD2010E42D16FF8275C206430D634A30AD7` |
| action/voice dispatcher | `[8025163C,80251708)` | `[C63C,C708)` | 204 | `212B0FA058C47B72EE005CE20F65F2845C02D0FBE9B850B13361672835AF60C0` |
| floor-type terrain mapping | `[802518A8,80251A48)` | `[C8A8,CA48)` | 416 / 408 | `631FA3592EAAF41F57359BCC0274011DF6BBD8141F38E7BE470FCE692CB249ED` |
| animation step caller | `[80263EE4,80264024)` | `[1EEE4,1F024)` | 320 | `B0E8F69D4691467DB735FC93391C898222AA9BB1713CA74B718527AD78D83BB3` |
| punch action caller | `[80274F10,80275328)` | `[2FF10,30328)` | 1048 / 1040 | `733D16BF0971742B00C72E1BE793F4AB938F0613BD51D5D7492257228CF67DCF` |
| player-0 sequence starter | `[80320544,80320678)` | `[DB544,DB678)` | 308 | `2E8AD70754CAA80FE59E4631C9689F2A59C357D4A1ED87330189BD9ABEF44C63` |
| priority sequence queue | `[803219AC,80321BAC)` | `[DC9AC,DCBAC)` | 512 / 508 | `3EC0D72BC29FAF2C80B5FFCB2AA0359A16587FACA6BCE006DD50C29DF4DEC28D` |
| music table initialization | `[8031950C,80319914)` | `[D450C,D4914)` | 1032 | `7E73675AEEB84482BCFA5C8580040849858A252A574C27B19BA653E60D1BE441` |
| sequence relative-offset patch | `[80325CD8,80325D18)` | `[E0CD8,E0D18)` | 64 | `5A4ABE723B1ADE23A5042F2245782F5118DC69C7316D19FD88A79D88424801EE` |
| bank-control relocation loader | `[803188F4,80318B30)` | `[D38F4,D3B30)` | 572 | `A65BBBF9E9FB2C40EE9978ED1FE31413518A8A2E4DD9332D6329BA9BE4814657` |
| relocated bank entry loader | `[80318B30,80318C8C)` | `[D3B30,D3C8C)` | 348 | `DB879EFDB614002F14CB6F5D5CF6297B570DCEC9263B444C6DB97E195329C583` |
| sample DMA/decode framing | `[80315590,80316010)` | `[D0590,D1010)` | 2688 / 2676 | `4BAA93EBE4896878CF9F7637C0FF389BAC053A6204BD1CF5BBACB51ACB2B3622` |
| stop request wrapper | `[80320678,803206BC)` | `[DB678,DB6BC)` | 68 | `555F45FCAD1E4C826F88ED4A21EE2FFC0775BADE98AFC00C3993F6A3F60C353F` |
| stop command helper | `[8031E578,8031E5C0)` | `[D9578,D95C0)` | 72 | `A0B0CEEB19FCC95B1252385FAAFDE8462B57253D86D1519B79426C05413AF6F1` |
| sequence driver | `[8031E240,8031E2E8)` | `[D9240,D92E8)` | 168 | `2183F5AEEB66BFE46AB3D217DD3749F845A6F1DAE8D30BE0DCFA10C6A41ABCDF` |
| sequence-player interpreter | `[8031D9EC,8031E240)` | `[D89EC,D9240)` | 2132 / 2120 | `B72317B57394321B140F72F1D1AEE0EEBFB2DD15BB4E7EF671778D6DB016A3F9` |
| bytecode U8 reader | `[8031C03C,8031C050)` | `[D703C,D7050)` | 20 | `F0A001A8E406B4DC1EE42FC4D9B2B936BD6C7F2805C9DFF509914CB976C6482F` |
| bytecode BE16 reader | `[8031C050,8031C080)` | `[D7050,D7080)` | 48 | `EA0A4C014CCB671B8BB7362E15550543ACC0D56D50520B1BC8F7CB49FCAFFA82` |
| bytecode variable-width reader | `[8031C080,8031C0C4)` | `[D7080,D70C4)` | 68 | `A4FD186DBC089104A9C06443A9BDA75E783CA984DEC523641D2EC7932FF8C0F6` |
| shared pause-flag writer | `[803210D4,8032112C)` | `[DC0D4,DC12C)` | 88 | `914611EBE7BE61F071CEB8CC9B9F7C91107105C5454BE24D5B0B50FFC015430A` |
## Repeatable Project64 observations

Each trace is a local JSONL numeric record and is identified by its exact sampler version and hash. Raw traces and metadata stay in the private portable-analysis directory; only scalar summaries and hashes belong in this dossier.

| Trial | Scenario and verified input | Observed numeric result | Trace SHA-256 |
|---|---|---|---|
| `issue50-courtyard-idle-a` | Courtyard baseline; no input; v1 sampler | 450 rows, 3,127 submitted requests. Four background recurring request IDs `0x50514001`, `0x80504001`, `0x90524001`, `0x40010001` occurred 772 times each; `0x240E8081` ×20 and `0x240F8081` ×19. Mario action `0x0C000203`, play mode 0. No acceptance/playback claim. | `F2DC4E0BF7C19C64D85383CEC4A13E6EF93A94A66FA1DE07E75BF0913D0320DE` |
| `issue50-action-terrain-b` | Courtyard; AnalogUp 1,000 ms then B within a 1,400 ms bounded helper hold; v1 | 450 rows. Actions include `0x0C400201`, `0x0C000203/204`, `0x04000440`, `0x00800457`. `0x06108081` appeared twice during `0x04000440`; `0x241E8081` once during `0x00800457`. Target caller paths link these IDs to an animation-gated step and punch action. | `9A6F5D1A4CCE2F836F586740B864BF4309FACD074574522F09EA5ACA909CD0B5` |
| `issue50-jump-courtyard-b` | Courtyard; AnalogUp 900 ms, A 180 ms; v2 | 900 rows. Action `0x03000880` (jump), step/landing phases, then standing. Three `0x06108081` submissions correlate with step-frame action `0x04000440/4A`; `0x04008081` and `0x24008081` occur during jump; `0x04088081` during landing; voice-family IDs also occur. Sampled Mario `+0x14` terrain-offset scalar remained `0x00000000`. No terrain variation was tested. | `1054A274ED994DC1899B400140B23274E47F086FA626B8F2C2D633277C2C8DBF` |
| `issue50-music-pause-a` | Courtyard; Start pulse, 1.2 s gap, Start pulse; v2 | 450 rows. 23 rows report play mode 2 and player-0 flags `0x60000000`; other rows report mode 0 and flags `0x40000000`. Sequence byte remains 30, sequence counter 0, queue count 3. This is pause-flag correlation; the sequence byte can be retained state and does not prove active music. | `2F09BE39477BE484281A043D90BEB6B0F74BEAD6AA235809CDF14F18EBCF087C` |
| `issue50-title-fileselect-music-a` | Preserved title fixture `sm64-title-state-before-file-select.pj.zip` (SHA-256 `68EB47233F30DFE14C15116C2D937FA165585A3A78E7C50C42A6449E48F0107D`); Start once into SELECT FILE; no slot or bottom option activated; v2 | 900 rows, play mode 4. Player-0 flags `0xC0000000` and queue count 1 with sequence 2 for rows 0–230; rows 231–242 show flags `0xC0000100`; rows 243–244 show current sequence `0xFF`, flags `0x40000100`, queue count 0; rows 245–899 show sequence 33, flags `0xC0000000`, queue count 1. The sequence selection/queue transition is observed, not audible playback. | `4D36ECB2B46FDECACE5859207436311B14713C97FEE8422131B58C967AE998B5` |

The action/terrain and title trials are separate scenes and trials. For exact timing, bracket reads and queue wrap limits, see the local metadata; they are not published. Captures are not atomically synchronized, loop counter `8032D5D4` is not an audio clock, and queue IDs do not prove acceptance. The action-to-request association is temporal and target-caller supported, not a causal assertion from one row.

## Boundaries and follow-up

This work covers request submission, the queue consumer/priority gate, target action/terrain/voice request paths, music tables and relative linkage, sequence-priority handling, and numeric runtime sequences/pause flags. It does not identify every sound ID semantically, establish audible voice identities, capture or compare waveforms, prove a request played, decode the complete sample format, characterize all courses or terrain types, or exhaust pause/fade/transition priority behavior. The sample decoder and additional course/area sequences remain finite follow-up research.

## MVP boundary and reproduction

The bounded MVP target supported by this evidence is positional numeric SFX submission and consumer arbitration, selected action/terrain/voice request families, priority-ordered music sequence requests, and pause-related player flags. Reproducing exact audio assets, semantic voice labels, all terrain/action mappings, all course sequences, complete spatial mixing/channel behavior, full decoder output, and exhaustive fade/overlap behavior remains outside this ticket and belongs to finite follow-up work.

The following command illustrates the guarded sampler interface. Supply only fresh values verified for the exact portable Project64 process and never reuse a trial ID. `-AnalysisRomPath` stays local; output files are private artifacts. For the named jump trial, start the sampler, issue `AnalogUp` for 900 ms, wait 350 ms, then issue `A` for 180 ms using the reviewed helper and the same PID/HWND. For the title transition, load the preserved title fixture with Project64 System → Load..., press Start once, and leave all file slots and bottom menu choices untouched.

```powershell
$projectRoot = '<local MarioCraft root>'
$analysisRom = '<verified USA rev0 analysis-copy path>'
& (Join-Path $projectRoot 'docs/research/mario64-issue50-audio-lifecycle-v2.ps1') `
  -TrialId issue50-jump-courtyard-c -TargetPid <verified-pid> `
  -TargetHwnd <verified-hwnd> -HostBaseHex <verified-rdram-base> `
  -ProjectRoot $projectRoot -AnalysisRomPath $analysisRom -Limit 900 -PollMs 20
```

In the second console, use the reviewed helper with the same freshly verified PID/HWND:

```powershell
$helper = Join-Path $projectRoot 'docs/research/mario64-issue47-project64-input.ps1'
& $helper -ExpectedPid <verified-pid> -ExpectedHwnd <verified-hwnd> `
  -TargetInstance Issue11Portable -Key AnalogUp -HoldMs 900
Start-Sleep -Milliseconds 350
& $helper -ExpectedPid <verified-pid> -ExpectedHwnd <verified-hwnd> `
  -TargetInstance Issue11Portable -Key A -HoldMs 180
```

The courtyard trials used the preserved baseline `tools/issue11-local/issue35-mario-gameplay-baseline.pj.zip` (SHA-256 `6E2C8265A8A9A19761BCC6CE29FA20694FAD5444B325B1F22CA987776A2D6DFE`) loaded through Project64's state-load UI before each separate bounded scenario. The title trial used `tools/issue10-local/sm64-title-state-before-file-select.pj.zip` (SHA-256 `68EB47233F30DFE14C15116C2D937FA165585A3A78E7C50C42A6449E48F0107D`). Both are existing local fixtures; no new save state or EEPROM slot was written.
## Reproduction artifacts (local only)

- v1 request sampler: `docs/research/mario64-issue50-audio-queue-capture.ps1`, SHA-256 `FBB5D1540652F4CEEFADC85206DF56D640A2129048CDB2C38EB2B978641D3DAE`.
- v2 lifecycle sampler: `docs/research/mario64-issue50-audio-lifecycle-v2.ps1`, SHA-256 `DBE39831F86A40F14A9BB15D7B906569529772FC97B70D0A21875CCAA537E08D`.
- Input helper: `docs/research/mario64-issue47-project64-input.ps1`, SHA-256 `2E78A7B6685C010C8FD2DDAD9D92815A77E9FB87202EAE6DA640A8579B0F9463`.

Published evidence is limited to this report, sanitized manifest, and the two reviewed read-only sampler scripts; raw RAM reads, trace rows, save states, ROM/sample data, and screenshots remain local.

### Representative sample-resource linkage (handoff to issue #51)

Target bank-0 control entry 0 is 1,056 bytes at ROM `[0x57B860,0x57BC80)`, with six instruments and no drums; its relocation base is `0x57B870`. Instrument 0 is at `0x57BBC0` and references sample record 0 at `0x57B890`. That record links sample data at `0x5936A0` (1,774 bytes), loop metadata at `0x57B900`, and predictor-book metadata at `0x57B8B0` (order 2, two predictors). The sample's length is not divisible by 9; decoder-boundary interpretation is deferred to issue #51. These are ROM offsets, lengths, hashes, and relationship scalars only; no sample or book bytes are included here.

| Resource | ROM range | Bytes | SHA-256 |
|---|---:|---:|---|
| bank-0 control entry 0 | `[0x57B860,0x57BC80)` | 1,056 | `C361EC78F013578DB70B5A36DB8D16694B8260A6290DCD665CD60EC767252796` |
| instrument 0 record | `[0x57BBC0,0x57BBE0)` | 32 | `4A27C60D86D311D52ACA0B8ECDEABB78D3CCC83CCB320DB20AC2C3D1A859D55A` |
| sample 0 record | `[0x57B890,0x57B8A4)` | 20 | `1CCCA9A30A6161A4B3A565CADD0846771927DEE38FD2B9FDD5D0272287CB98D8` |
| predictor-book metadata | `[0x57B8B0,0x57B8F8)` | 72 | `BD6DF63EC1B532FDFC64D41387F910BFCA04F66BEE07749F4A7A35770183B4EF` |
| loop metadata | `[0x57B900,0x57B910)` | 16 | `A79E7AE9E32B39A08DEF7BBC4F68B986CDC8094AC26107B51DA966526F7CCFCC` |
| sample 0 data (private ROM region) | `[0x5936A0,0x593D8E)` | 1,774 | `D6D58E52B91976C735A29EE344260F85CA0B785EFF118758282DC586C45359F0` |
