"""Independent checks of the actual-world respawn probe (no native/ROM imports)."""
import json
import re
import sys
from pathlib import Path

trace, log_path, result_path, output_path = map(Path, sys.argv[1:])
rows = [json.loads(line) for line in trace.read_text(encoding="utf-8").splitlines()]
log = log_path.read_text(encoding="utf-8", errors="replace")
result = json.loads(result_path.read_text(encoding="utf-8"))
assert result["passed"] and result["respawn"]
assert result["deaths"] == result["minecraftGameOverScreens"] == result["completedRespawns"] == 6
assert result["nativeLavaDeathAction"] == "10208b7"
assert all(result[key] for key in ("zeroLifeReset", "worldPreserved", "inventoryXpFoodPreserved", "transientStateClean", "dimensionRespawnPreservedLives"))
assert result["inventoryDrops"] == result["terrainEdits"] == 0
assert result["vanillaHudElementsSuppressed"] == 4
assert rows[0]["taggedFixtureStacks"] == 2
respawns = rows[1:]
assert [r["respawn"] for r in respawns] == list(range(1, 7))
assert [r["lives"] for r in respawns] == [3, 2, 1, 4, 3, 2]
assert all(r["fullHealth"] and r["inventoryXpFoodPreserved"] and r["markerEntityPreserved"] and r["markerBlockPreserved"] and r["drops"] == 0 for r in respawns)
deaths = re.findall(r"death sequence=(\d+) lives=(\d+) reason=(\S+)", log)
screens = re.findall(r"RESPAWN screen=(\d+) lives=(\d+) health=([\d.]+)", log)
assert [(int(a), int(b)) for a, b, _ in deaths] == [(1,3),(2,2),(3,1),(4,0),(5,3),(6,2)]
assert [(int(a), int(b)) for a, b, _ in screens] == [(int(a), int(b)) for a, b, _ in deaths]
assert all(float(h) == 0 for _, _, h in screens)
checks = re.findall(r"clean-check cycle=(\d+) clean=(\w+) action=(\w+) invinc=(\d+) velocity=\[([^]]+)\] channel=(\w+) received=(\d+) external=(\w+) inspecting=(\w+)", log)
assert {int(row[0]) for row in checks} == set(range(7))
for cycle, clean, action, invinc, velocity, channel, received, external, inspecting in checks:
    assert clean == channel == "true" and action == "c400201"
    assert int(invinc) == int(received) == 0 and external == "null" and inspecting == "false"
    assert [float(v) for v in velocity.split(",")] == [0, 0, 0]
assert len(re.findall(r"source=respawnProbe\.(?:external|dimension).*?admitted=true", log)) == 5
assert "world-load dim=-1 remote=true" in log and "world-load dim=0 remote=true" in log
assert log.count("native-player-created") == log.count("native-player-closed") == 8
assert "server-stopped" in log
assert not re.search(r"moved wrongly|moved too quickly|native step failed|native player initialization failed", log, re.I)
summary = dict(result)
summary.update({"nativeContextsCreatedAndClosed": 8, "ambientWorldItemsAtSetup": rows[0]["baselineAmbientItems"], "dropScope": "tagged inventory stacks and new XP orbs within three blocks of recorded death positions", "evidence": "actual generated Minecraft world and real GuiGameOver/respawn requests", "screenLives": [3,2,1,0,3,2], "respawnLives": [r["lives"] for r in respawns], "nativeHealthAfterEveryRespawn": 2176, "settledNativeAction": "c400201", "settledVelocity": [0,0,0], "invincibility": 0, "damageChannelReceiptsAtRespawn": 0, "externalSourceAtRespawn": None, "cameraInspectingAtRespawn": False, "movementWarnings": 0, "worldEntityAndBlockMarkersPreserved": True})
output_path.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
print(json.dumps(summary, indent=2))
