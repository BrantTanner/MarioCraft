"""Independent scalar checks for the bounded actual-world CombatProbe."""
import collections
import json
import math
from pathlib import Path
import re
import sys

trace, log, result, output = map(Path, sys.argv[1:])
rows = [json.loads(line) for line in trace.read_text(encoding="utf-8").splitlines()]
text = log.read_text(encoding="utf-8")
summary = json.loads(result.read_text(encoding="utf-8"))
required = {
    "punch-fixture": ("PUNCH", "800380"),
    "air-kick-fixture": ("KICK", "18008ac"),
    "stomp-fixture": ("STOMP", "3000880"),
    "dive-fixture": ("DIVE", "188088a"),
    "ground-pound-fixture": ("GROUND_POUND", "8008a9"),
    "ground-zombie-ai": ("PUNCH", "800380"),
    "airborne-bat-ai": ("PUNCH", "800380"),
    "projectile-skeleton-ai": ("PUNCH", "800380"),
}
checks = {}
checks["boundedEightRecipes"] = summary["frames"] in (1440,1620)
checks["continuousFrames"] = [r["frame"] for r in rows[:summary['frames']]] == list(range(1, summary['frames']+1))
checks["finitePoses"] = all(math.isfinite(r[k]) for r in rows for k in ("x", "y", "z", "vy", "speed", "face"))
checks["allRecipesActionsAndInputs"] = all(
    any(r["recipe"] == recipe and r["action"] == action for r in rows)
    and any(r["recipe"] == recipe and (r["a"] or r["b"] or r["zButton"]) for r in rows)
    for recipe, (_, action) in required.items())
checks["oneAcceptedHitPerRecipe"] = all(summary["hits"].get(recipe + ":" + attack) == 1 for recipe, (attack, _) in required.items())
checks["oneAttemptPerRecipe"] = all(summary["attempts"].get(recipe + ":" + attack) == 1 for recipe, (attack, _) in required.items())
contacts = re.findall(r"mob-contact target=(\d+) uuid=\S+ attack=(\w+) accepted=(true|false) damage=([0-9.]+) healthBefore=([0-9.]+) healthAfter=([0-9.]+) aiDisabled=(true|false)", text)
checks["serverContactsMatchEightDistinctCreatures"] = len(contacts) == 8 and len({c[0] for c in contacts}) == 8 and all(c[2] == "true" for c in contacts)
checks["separateFixtureAndOrdinaryAiHits"] = sum(c[6] == "true" for c in contacts) == 5 and sum(c[6] == "false" for c in contacts) == 3
policy = {"PUNCH": 3, "KICK": 4, "STOMP": 5, "DIVE": 6, "GROUND_POUND": 8}
checks["requestedDamageMatchesRules"] = all(float(c[3]) == policy[c[1]] for c in contacts)
checks["allContactHealthDecreased"] = all(float(c[5]) < float(c[4]) for c in contacts)
checks["noRejectedServerContact"] = "combat-server-rejected" not in text
checks["ordinaryAiAndLifecycle"] = all(summary[k] for k in (
    "ordinaryAiEnabled", "zombieTargetsRealPlayer", "zombieNavigation", "zombieVanillaAttack",
    "skeletonTargetsRealPlayer", "skeletonNavigation", "skeletonProjectile", "batAirborneMotion",
    "fixtureDeath", "ordinaryItemDrops"))
checks["normalTeardown"] = all(s in text for s in ("native-player-closed", "world-save dim=0", "world-unload dim=0", "server-stopped"))
checks["noMovementWarnings"] = not any(s in text.lower() for s in ("moved wrongly", "moved too quickly"))
checks["zeroTerrainEdits"] = summary["terrainEdits"] == 0
checks["freshIntegratedPass"] = summary.get("passed") is True
report = {"passed": all(checks.values()), "checks": checks, "recordedFrames": min(len(rows), summary['frames']),
    "serverContacts": len(contacts), "fixtureContacts": sum(c[6] == "true" for c in contacts),
    "ordinaryAiContacts": sum(c[6] == "false" for c in contacts),
    "healthChanges": [{"attack": c[1], "requestedDamage": float(c[3]), "before": float(c[4]), "after": float(c[5]), "fixtureNoAI": c[6] == "true"} for c in contacts]}
output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
print(json.dumps(report, indent=2))
sys.exit(0 if report["passed"] else 1)
