"""Verify the bounded actual-world traversal trace; contains no game assets."""
import json
import math
from pathlib import Path
import sys

rows = [json.loads(line) for line in Path(sys.argv[1]).read_text(encoding="utf-8").splitlines()]
assert len(rows) == 360, "Expected 360 consecutive native steps"
assert [r["frame"] for r in rows] == list(range(1, 361)), "Missing native steps"
assert all(math.isfinite(r[k]) for r in rows for k in ("x", "y", "z", "vy", "speed")), "Nonfinite trajectory"
actions = [{r["action"] for r in rows if r["recipe"] == recipe} for recipe in (0, 1)]
assert "3000886" in actions[0], "Wall kick absent from collision recipe"
assert {"800034b", "54c", "54d"} <= actions[1], "Ledge grab/climb sequence absent"
kick = next(r for r in rows if r["action"] == "3000886")
assert abs(kick["vy"] - 58) < 0.001, "Kick first post-update vY differs from #41 representative"
assert kick["a"], "Kick lacks submitted A input"
index = rows.index(kick)
assert not rows[index - 1]["a"], "Kick lacks a fresh A edge"
assert any(r["animFrame"] > 0 for r in rows if r["action"] == "54c"), "Climb animation did not advance"
assert int(rows[-1]["action"], 16) & 0x800 == 0, "Final pose remained airborne"
result = {
    "passed": True,
    "nativeSteps": len(rows),
    "terrainEdits": 0,
    "wallKickFirstPostUpdateVy": kick["vy"],
    "wallKickReference": "existing Project64 issue41 early/late representatives: 58",
    "wallKickFreshA": True,
    "ledgeGrabSlowClimb1SlowClimb2": True,
    "climbAnimationAdvanced": True,
    "finiteTrajectory": True,
    "finalGrounded": True,
    "actionsByRecipe": [sorted(a) for a in actions],
    "referenceLimit": "No successful Project64 ledge-grab or isolated ceiling contact reference was captured in issue42; those gates are code-derived."
}
Path(sys.argv[2]).write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8", newline="\n")
print(json.dumps(result, indent=2))
