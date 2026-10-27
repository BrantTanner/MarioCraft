"""Independent scalar checks for the opt-in normal-Overworld health probe."""
import json
import math
import re
import sys
from pathlib import Path

trace, log_path, result_path, output_path = map(Path, sys.argv[1:])
rows = [json.loads(line) for line in trace.read_text(encoding="utf-8").splitlines()]
log = log_path.read_text(encoding="utf-8")
result = json.loads(result_path.read_text(encoding="utf-8"))
assert result["passed"] and result["health"] and 1801 <= result["frames"] <= 2100 and result["frames"] <= len(rows) <= result["frames"]+30
assert [r["frame"] for r in rows] == list(range(1, len(rows) + 1))
captured_frames = len(rows)
# Server-side final observations complete asynchronously; retain bounded shutdown tail.
rows = rows[:result["frames"]]
recipes = list(dict.fromkeys(r["recipe"] for r in rows))
assert len(recipes) == 7
groups = {name: [r for r in rows if r["recipe"] == name] for name in recipes}
for row in rows:
    assert all(math.isfinite(row[key]) for key in ("x", "y", "z", "vy"))
    assert row["sections"] == min(8, row["health"] >> 8)
drains = 0
for before, after in zip(rows, rows[1:]):
    if before["recipe"] == after["recipe"] and after["health"] < before["health"]:
        assert before["health"] - after["health"] in (64, 1)
        drains += 1
assert drains > 30
for name, health in zip(recipes[3:6], (0x880, 0x680, 0x480)):
    group = groups[name]
    assert min(r["health"] for r in group) == health
    assert any(r["a"] and int(r["action"], 16) & 0x800 for r in group)
    assert any(r["vy"] < 0 for r in group)
    if health != 0x880:
        assert any(r["vy"] < -55 for r in group)
assert groups[recipes[-1]][-1]["health"] == 0xFF
assert any(r["stickY"] for r in groups[recipes[3]])
hits = re.findall(r"incoming-hit frame=(\d+) source=(\S+).*?damage=(\d+) admitted=(true|false)", log)
assert any(source == "mob" and admitted == "false" for _, source, _, admitted in hits)
for kind in ("mob", "arrow", "explosion"):
    assert any(source == kind and admitted == "true" for _, source, _, admitted in hits)
for frame, source, damage, admitted in hits:
    if admitted == "true":
        index = int(frame) - 1
        assert rows[index]["health"] < rows[index-1]["health"]
bodies = re.findall(r"minecraftDamage=([\d.]+) bodyHeight=([\d.]+) bodyWidth=([\d.]+)", log)
assert len(bodies) == len(hits)
for hit, (amount, height, width) in zip(hits, bodies):
    assert int(hit[2]) == min(8, math.ceil(float(amount) / 2))
    assert abs(float(height) - 1.6) < 1e-8 and abs(float(width) - .74) < 1e-8
assert result["zombieTargetsRealPlayer"] and result["skeletonTargetsRealPlayer"] and result["skeletonArrow"]
assert result["vanillaHealthUnchanged"] and result["terrainEdits"] == 0
assert "native-player-closed" in log and "Saving chunks for level" in log
assert "moved wrongly" not in log and "moved too quickly" not in log
assert "native step failed" not in log
summary = {
    "passed": True, "evidence": "normal-generated-overworld-with-explicit-test-setups",
    "frames": len(rows), "capturedFrames": captured_frames, "recipes": recipes, "finitePoses": True,
    "nativeDrainSteps": drains, "nativeDrainQuantum": 64,
    "incomingRequests": len(hits), "protectedRequests": sum(h[3] == "false" for h in hits),
    "minimumHealth": [min(r["health"] for r in groups[name]) for name in recipes],
    "realAiTargetingAndArrow": True, "vanillaHealthUnchanged": True,
    "eightSectionProjection": True, "normalTeardown": True, "movementWarnings": 0,
    "observedIncomingBodyHeight": 1.6, "observedIncomingBodyWidth": .74,
    "conversionVerifiedFromOriginalAmounts": True, "admittedRequestsChangedNativeHealth": True,
    "terrainEdits": 0, "testOnlyHealthResets": 7, "respawnClaimed": False,
}
output_path.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
print(json.dumps(summary, indent=2))
