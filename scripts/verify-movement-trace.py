"""Check our scalar integrated trace; does not launch or control a game."""
import argparse
import json
import math
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("trace", type=Path)
parser.add_argument("output", type=Path)
args = parser.parse_args()
rows = [json.loads(line) for line in args.trace.read_text(encoding="utf-8").splitlines()]
assert len(rows) == 1800 and [r["frame"] for r in rows] == list(range(1800))
expected = {
    "crouch-crawl": ["c008220", "4008448", "c008224", "c008222"],
    "backflip": ["1000883", "400047a"],
    "ground-pound": ["8008a9", "80023c", "c00023e"],
    "long-jump": ["4808459", "3000888", "479"],
    "sideflip": ["443", "1000887", "4000473"],
    "jump-chain": ["3000880", "3000881", "1000882", "4000478"],
    "slide-kick": ["4808459", "18008aa", "80045a", "8000225"],
    "ground-dive-rollout": ["188088a", "880456", "10008a6"],
    "air-kick": ["3000880", "18008ac"],
    "air-dive": ["3000880", "188088a", "880456", "386"],
}
result = {"passed": True, "nativeSteps": len(rows), "recipes": {}}
for recipe, actions in expected.items():
    group = [r for r in rows if r["recipe"] == recipe]
    observed = list(dict.fromkeys(r["action"] for r in group))
    assert all(action in observed for action in actions), (recipe, observed)
    for row in group:
        assert all(math.isfinite(row[key]) for key in ("x", "y", "z", "vx", "vy", "vz", "speed"))
    result["recipes"][recipe] = {"observedActions": observed}

# Same post-update phase as retained #39 observations. This is a numeric check
# tolerance for float output, not an invented whole-trajectory equivalence band.
comparisons = []
for recipe, action, reference_vy in (
    ("backflip", "1000883", 58), ("sideflip", "1000887", 58),
    ("long-jump", "3000888", 28), ("jump-chain", "1000882", 65),
):
    group = [r for r in rows if r["recipe"] == recipe and r["action"] == action]
    first = group[0]
    assert abs(first["vy"] - reference_vy) <= 0.001
    assert len({r["animFrame"] for r in group}) > 1
    comparisons.append({"recipe": recipe, "frame": first["frame"], "action": action,
                        "vy": first["vy"], "referenceVy": reference_vy,
                        "forwardSpeed": first["speed"], "animation": first["anim"]})
result["postUpdateComparisons"] = comparisons
result["comparisonTolerance"] = 0.001
result["reference"] = "MARIO64-ISSUE39-JUMP-ARCS-AIR-CONTROL-AND-COMBINATIONS.md"
chain = [r for r in rows if r["recipe"] == "jump-chain"]
release = next((before, after) for before, after in zip(chain, chain[1:])
               if before["a"] and not after["a"] and before["action"] == "3000880")
assert abs(release[1]["vy"] - release[0]["vy"] / 4) <= 0.001
result["jumpRelease"] = {"frame": release[1]["frame"], "beforeVy": release[0]["vy"],
                         "afterVy": release[1]["vy"], "rule": "quarter upward velocity"}
args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8", newline="\n")
print("Movement scalar verification passed: 1800 steps, 10 recipes, four entry comparisons, A-release rule.")
