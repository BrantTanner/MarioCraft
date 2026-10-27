"""Validate bounded camera observations; raw traces remain private."""
import json
import math
import sys
from pathlib import Path

rows = [json.loads(line) for line in Path(sys.argv[1]).read_text(encoding="utf-8").splitlines()]
assert [r["frame"] for r in rows] == list(range(1, 361)), "missing or duplicate native step"
assert all(math.isfinite(r[k]) for r in rows for k in ("controlYaw", "renderYaw", "pitch", "eyeX", "eyeY", "eyeZ", "fraction"))
assert all(0 <= r["fraction"] <= 1 for r in rows)
assert all(rows[i-1]["mode"] == 6 for i in range(91, 131))
assert rows[130]["mode"] == 16
assert all(rows[i-1]["cRight"] for i in range(31,46))
assert rows[90]["cUp"] and rows[130]["cDown"]
assert all(rows[i-1]["stickX"] == -1 for i in range(101,116))
assert all(rows[i-1]["stickY"] == 1 for i in range(211,241))
assert any(int(r["action"],16) & 0x800 for r in rows[225:260]), "jump transition absent"
wrap = lambda angle: (angle + 180) % 360 - 180
turn = abs(wrap(rows[44]["controlYaw"] - rows[29]["controlYaw"]))
settled = abs(wrap(rows[84]["controlYaw"] - rows[29]["controlYaw"]))
assert turn > 20 and settled < 2
result = {"passed": True, "nativeSteps": len(rows), "finite": True,
          "inspectionSteps": 40, "keyBindingPulseAndExit": True,
          "nativeJumpObserved": True, "cPulseDegrees": turn,
          "settledDegrees": settled,
          "minimumObstructionFraction": min(r["fraction"] for r in rows if r["mode"] != 6),
          "waterEvidence": "synthetic camera contract only; no in-world swimming claim"}
Path(sys.argv[2]).write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
print(json.dumps(result))
