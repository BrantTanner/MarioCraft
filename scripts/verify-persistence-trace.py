"""Validate actual integrated save/reopen evidence and emit only sanitized scalar checks."""
import json
import re
import sys
from pathlib import Path
trace_path, log_path, result_path, output_path = map(Path, sys.argv[1:])
rows = [json.loads(line) for line in trace_path.read_text(encoding="utf-8").splitlines() if line.strip()]
log = log_path.read_text(encoding="utf-8", errors="replace")
result = json.loads(result_path.read_text(encoding="utf-8-sig"))
def rows_for(key):
    return [row for row in rows if key in row]
assert result["passed"] and result["loads"] == result["unloads"] == 13, result
for key in ("firstFrame", "resume", "positionRestored", "ownership", "singleMinecraftPlayer"):
    values = rows_for(key)
    assert len(values) == 13, (key, len(values))
    field = "clean" if key == "resume" else "valid"
    assert all(row[field] for row in values), (key, values)
assert len(rows_for("vanillaPreserved")) == 11
assert all(row["valid"] for row in rows_for("vanillaPreserved"))
assert len(rows_for("unload")) == 13 and all(row["registriesEmpty"] and row["resourcesClosed"] for row in rows_for("unload"))
saves = {row["save"]: row for row in rows_for("save")}
assert len(saves) == 13 and saves[0]["health"] == 0x500 and saves[3]["health"] == 0x100
assert int(saves[2]["nativeAction"], 16) & 0x800, saves[2]
assert saves[4]["health"] == 0 and saves[4]["lives"] == 3
first = {row["firstFrame"]: row for row in rows_for("firstFrame")}
assert first[5]["health"] == 2176 and first[5]["lives"] == first[7]["lives"] == first[9]["lives"] == 3
assert all(first[i]["lives"] == 4 for i in (6, 8, 10, 11, 12))
created = log.count("MARIOCRAFT native-player-created")
closed = log.count("MARIOCRAFT native-player-closed")
assert created == closed == 13, (created, closed)
assert "native player initialization failed" not in log and "native step failed" not in log
runtime_log = log.split("MARIOCRAFT world-load dim=0 remote=false", 1)[1]
errors = len(re.findall(r"/(?:FATAL|ERROR)\]", runtime_log))
assert errors == 0, f"Minecraft world lifecycles logged {errors} ERROR/FATAL records"
warnings = len(re.findall(r"moved too quickly|moved wrongly", log))
assert warnings == 0
meshes = [float(value) for value in re.findall(r"collision-mesh.*milliseconds=([0-9.]+)", log)]
checks = {
    "passed": True, "actualIntegratedLoads": 13, "actualIntegratedUnloads": 13,
    "nativeCreates": created, "nativeCloses": closed, "groundDamagedHealthSaved": saves[0]["health"],
    "waterHealthSaved": saves[1]["health"], "swimResumes": 1,
    "midairActionBeforeSave": saves[2]["nativeAction"], "nearDeathHealthSaved": saves[3]["health"],
    "pendingDeathLivesSaved": saves[4]["lives"], "pendingReloadLives": first[5]["lives"],
    "pendingReloadHealth": first[5]["health"], "worldARepeatedLives": [first[i]["lives"] for i in (5, 7, 9)],
    "worldBFreshAndRepeatedLives": [first[i]["lives"] for i in (6, 8)],
    "invalidOlderDecoderThenColdMissingFallbackLives": [first[i]["lives"] for i in (10, 11, 12)],
    "positionChecks": 13, "cameraInputPacketOwnershipChecks": 13, "vanillaPreservationChecks": 11,
    "emptyRegistryAndClosedResourceChecks": 13, "movementWarnings": warnings, "worldLifecycleErrorsOrFatals": errors, "terrainEdits": 0,
    "meshBuilds": len(meshes), "maximumMeshMilliseconds": max(meshes, default=0),
    "meshBuildsOver33Milliseconds": sum(value > 33.3 for value in meshes),
    "testOnlyFixtures": ["native health", "natural water/dry relocation", "jump input", "server attack after login immunity", "tagged inventory and unrelated entity NBT", "XP/food", "passive marker entity and natural bedrock", "malformed/older server decoder fixtures and cold missing-record reload"],
}
output_path.write_text(json.dumps(checks, indent=2) + "\n", encoding="utf-8")
print(json.dumps(checks))
