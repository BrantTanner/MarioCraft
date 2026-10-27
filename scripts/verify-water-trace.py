"""Independent checks for the bounded real-world water recipes, without publishing raw positions."""
import json
import math
import re
import sys
from pathlib import Path

trace_path, log_path, result_path, output_path = map(Path, sys.argv[1:])
rows = [json.loads(line) for line in trace_path.read_text(encoding="utf-8").splitlines()]
result = json.loads(result_path.read_text(encoding="utf-8"))
log = log_path.read_text(encoding="utf-8")
assert result["passed"] and result["water"]
assert len(rows) == 1680
assert [r["frame"] for r in rows] == list(range(1, 1681))
recipes = list(dict.fromkeys(r["recipe"] for r in rows))
assert len(recipes) == 7
groups = {name: [r for r in rows if r["recipe"] == name] for name in recipes}
for r in rows:
    assert all(math.isfinite(r[k]) for k in ("y", "surface", "speed", "vy"))
    assert 0xFF <= r["health"] <= 0x880
    assert r["camera"] in (8, 16)
for a, b in zip(rows, rows[1:]):
    # Camera controls consume the last native action before the next step.
    if int(a["action"], 16) & 0x2000:
        assert b["camera"] == 8
assert all(not (int(r["action"], 16) & 0x2000) for r in groups[recipes[0]])
assert any(r["wet"] for r in groups[recipes[0]])  # Actual shallow fluid, not dry ground.
entry = groups[recipes[1]]
assert any(int(r["action"], 16) & 0x800 for r in entry)
assert any(r["action"] == "300022e2" for r in entry)
assert any(r["a"] and r["action"] == "300024d0" for r in entry)
assert max(r["speed"] for r in entry) >= 10
assert [r["speed"] for r in entry[60:73]] == [.5, 1, 1.5, 2, 2.5, 2.5, 2.5, 2.5, 4, 5.5, 7, 8.5, 10]
surface = groups[recipes[2]]
assert surface[-1]["health"] == 0x880
assert abs(surface[-1]["surface"] - surface[-1]["y"] - .8) < .011
heals = sum(b["health"] - a["health"] == 26 for a, b in zip(surface, surface[1:]))
assert heals >= 40
shore = groups[recipes[3]]
assert any(r["action"] == "1000889" for r in shore)  # Input-driven native water jump.
ground_index = next(i for i, r in enumerate(shore) if not r["wet"] and not (int(r["action"], 16) & (0x800 | 0x2000)))
assert any(r["wet"] and int(r["action"], 16) & 0x2000 for r in shore[ground_index + 1:])
assert any(r["camera"] == 16 for r in shore[ground_index:])
assert any(r["camera"] == 8 for r in shore[ground_index + 1:])
assert any(int(r["action"], 16) & 0x20000 for r in groups[recipes[5]])
assert re.search(r"incoming-hit .*source=explosion .*admitted=true", log)
drowning = groups[recipes[6]]
drains = sum(a["health"] - b["health"] == 1 for a, b in zip(drowning, drowning[1:]))
assert drains >= 127
assert drowning[-1]["health"] == 0xFF
assert next(i + 1 for i, r in enumerate(drowning) if r["health"] == 0xFF) == 129
assert any(r["action"] == "300032c4" for r in drowning)
assert result["vanillaHealthAndAirUnchanged"] and result["safeSurface"] and result["safeSubmerged"]
assert result["terrainEdits"] == 0 and result["testOnlyHealthResets"] == 7
assert "native-player-closed" in log
assert "world-save" in log and "world-unload" in log
assert not re.search(r"moved (?:wrongly|too quickly)", log, re.I)
costs = [float(x) for x in re.findall(r"collision-mesh .*milliseconds=([\d.]+)", log)]
output = {
    "passed": True, "evidenceKind": "normal-generated-world-input-and-labeled-setup",
    "frames": len(rows), "recipes": recipes,
    "surfacePlus26Steps": heals, "drowningMinusOneSteps": drains,
    "inputWaterJumpGroundAndReturn": True, "nativeDrowningAction": "300032c4",
    "camera8To16To8": True, "vanillaHealthAndAirUnchanged": True,
    "meshSamples": len(costs), "maximumObservedMeshMilliseconds": max(costs),
    "meshSamplesOver33Milliseconds": sum(x > 1000 / 30 for x in costs),
    "newProject64Capture": False, "referenceIssues": [44, 46, 47],
}
output_path.write_text(json.dumps(output, indent=2) + "\n", encoding="utf-8")
print(json.dumps(output))
