# MarioCraft autonomous execution plan

Parent specification: https://github.com/BrantTanner/MarioCraft/issues/1

## Latest user direction

The user requested agents to complete the full specification independently, without requiring routine user work. This supersedes the earlier human dossier approval and research-start gates in the original interview and spec. Preserve the sequence: complete Minecraft research, complete Mario research, pass an agent-led evidence and architecture review, then implement and validate. No human approval is required between these stages.

The user explicitly requested that agents complete the specification independently, without routine user work. This latest instruction supersedes earlier references in the parent/planning documents to manual research approval: complete Minecraft research first, Mario research second, then an agent-led evidence/architecture review before any gameplay prototype. Do not wait for a human sign-off. Agents may make and document implementation choices, repair/install required project-local tooling from official sources, automate builds and runtime scenarios, and use the authorized local Minecraft artifacts, supplied Mario USA ROM, Ghidra and installed Project64. Resolve known artifact locations from local project context; ask no questions that can be answered by inspection or a documented reasonable technical choice. Preserve original game files and unrelated data. Keep ROMs, derived game assets, credentials and sensitive/local captures out of GitHub.

Work only when blocking issues and their acceptance criteria are actually complete. For broad research, begin with an inventory and split oversized or newly discovered gaps into bounded sub-issues with dependencies; continue that work autonomously and link the evidence. Do not close a partially completed research or implementation slice just because a context window ends. Choose defaults from the research, record their rationale, and refine them using objective measurements. Escalate only a genuine external access/policy blocker that cannot be solved within existing authorization; never fabricate evidence or silently reduce scope.

Use the agreed highest-level test seam: drive the integrated Mario player in actual Minecraft and observe gameplay results, with repeatable Project64 references for Mario-specific behavior. Each slice must include its executable checks/observations and completion evidence; documentation alone does not satisfy a playable ticket. No separate obstacle-course world. Use issue comments/linked repository documentation for resumable handoffs and discovered decisions. Completion requires a concise result, reproduction instructions and evidence references.

## Ticket index

All 30 tickets are labeled `ready-for-agent`. Readiness still depends on every blocker being completed. Begin with issue #2. GitHub sub-issue and blocking relationships accompany the explicit references in each issue.

| Slice | Issue | Blocked by |
| --- | --- | --- |
| 1 | [#2: Verify Minecraft 1.12.2 and map the complete research scope](https://github.com/BrantTanner/MarioCraft/issues/2) | None |
| 2 | [#3: Explain Minecraft player input, movement, and interaction](https://github.com/BrantTanner/MarioCraft/issues/3) | [#2](https://github.com/BrantTanner/MarioCraft/issues/2) |
| 3 | [#4: Explain Minecraft terrain, blocks, fluids, and dimensions](https://github.com/BrantTanner/MarioCraft/issues/4) | [#2](https://github.com/BrantTanner/MarioCraft/issues/2) |
| 4 | [#5: Explain Minecraft creatures, combat, and environmental damage](https://github.com/BrantTanner/MarioCraft/issues/5) | [#2](https://github.com/BrantTanner/MarioCraft/issues/2) |
| 5 | [#6: Explain Minecraft items, crafting, and progression](https://github.com/BrantTanner/MarioCraft/issues/6) | [#2](https://github.com/BrantTanner/MarioCraft/issues/2) |
| 6 | [#7: Explain Minecraft rendering, resources, menus, and audio](https://github.com/BrantTanner/MarioCraft/issues/7) | [#2](https://github.com/BrantTanner/MarioCraft/issues/2) |
| 7 | [#8: Explain Minecraft lifecycle, saving, networking, and server execution](https://github.com/BrantTanner/MarioCraft/issues/8) | [#2](https://github.com/BrantTanner/MarioCraft/issues/2) |
| 8 | [#9: Audit and complete the Minecraft research dossier](https://github.com/BrantTanner/MarioCraft/issues/9) | [#3](https://github.com/BrantTanner/MarioCraft/issues/3), [#4](https://github.com/BrantTanner/MarioCraft/issues/4), [#5](https://github.com/BrantTanner/MarioCraft/issues/5), [#6](https://github.com/BrantTanner/MarioCraft/issues/6), [#7](https://github.com/BrantTanner/MarioCraft/issues/7), [#8](https://github.com/BrantTanner/MarioCraft/issues/8) |
| 9 | [#10: Validate Mario 64 ROM loading and map the full research scope](https://github.com/BrantTanner/MarioCraft/issues/10) | [#9](https://github.com/BrantTanner/MarioCraft/issues/9) |
| 10 | [#11: Explain Mario input, action transitions, and movement physics](https://github.com/BrantTanner/MarioCraft/issues/11) | [#10](https://github.com/BrantTanner/MarioCraft/issues/10) |
| 11 | [#12: Explain Mario collision, surfaces, health, and hazards](https://github.com/BrantTanner/MarioCraft/issues/12) | [#10](https://github.com/BrantTanner/MarioCraft/issues/10) |
| 12 | [#13: Explain Mario camera, character graphics, HUD, and audio](https://github.com/BrantTanner/MarioCraft/issues/13) | [#10](https://github.com/BrantTanner/MarioCraft/issues/10) |
| 13 | [#14: Explain Mario objects, enemies, and interaction systems](https://github.com/BrantTanner/MarioCraft/issues/14) | [#10](https://github.com/BrantTanner/MarioCraft/issues/10) |
| 14 | [#15: Explain Mario courses, scripts, assets, and progression](https://github.com/BrantTanner/MarioCraft/issues/15) | [#10](https://github.com/BrantTanner/MarioCraft/issues/10) |
| 15 | [#16: Explain Mario scheduling, memory, hardware interfaces, and saves](https://github.com/BrantTanner/MarioCraft/issues/16) | [#10](https://github.com/BrantTanner/MarioCraft/issues/10) |
| 16 | [#17: Audit and complete the Mario 64 research dossier](https://github.com/BrantTanner/MarioCraft/issues/17) | [#11](https://github.com/BrantTanner/MarioCraft/issues/11), [#12](https://github.com/BrantTanner/MarioCraft/issues/12), [#13](https://github.com/BrantTanner/MarioCraft/issues/13), [#14](https://github.com/BrantTanner/MarioCraft/issues/14), [#15](https://github.com/BrantTanner/MarioCraft/issues/15), [#16](https://github.com/BrantTanner/MarioCraft/issues/16) |
| 17 | [#18: Choose the integration architecture and pass the research readiness review](https://github.com/BrantTanner/MarioCraft/issues/18) | [#9](https://github.com/BrantTanner/MarioCraft/issues/9), [#17](https://github.com/BrantTanner/MarioCraft/issues/17) |
| 18 | [#19: Launch MarioCraft and control Mario in a normal Overworld](https://github.com/BrantTanner/MarioCraft/issues/19) | [#18](https://github.com/BrantTanner/MarioCraft/issues/18) |
| 19 | [#20: Play MarioCraft with configurable keyboard and controller bindings](https://github.com/BrantTanner/MarioCraft/issues/20) | [#19](https://github.com/BrantTanner/MarioCraft/issues/19) |
| 20 | [#21: Perform Mario's ordinary advanced movement and action combinations](https://github.com/BrantTanner/MarioCraft/issues/21) | [#19](https://github.com/BrantTanner/MarioCraft/issues/19) |
| 21 | [#22: Traverse Minecraft walls, ledges, ceilings, and tight spaces as Mario](https://github.com/BrantTanner/MarioCraft/issues/22) | [#21](https://github.com/BrantTanner/MarioCraft/issues/21) |
| 22 | [#23: Explore the Overworld with faithful Mario camera behavior](https://github.com/BrantTanner/MarioCraft/issues/23) | [#19](https://github.com/BrantTanner/MarioCraft/issues/19) |
| 23 | [#24: Fight Minecraft creatures using Mario attacks](https://github.com/BrantTanner/MarioCraft/issues/24) | [#21](https://github.com/BrantTanner/MarioCraft/issues/21) |
| 24 | [#25: Take creature, fall, and environmental damage through Mario health](https://github.com/BrantTanner/MarioCraft/issues/25) | [#19](https://github.com/BrantTanner/MarioCraft/issues/19) |
| 25 | [#26: Swim, drown, and recover health in Minecraft water](https://github.com/BrantTanner/MarioCraft/issues/26) | [#25](https://github.com/BrantTanner/MarioCraft/issues/25) |
| 26 | [#27: React to Minecraft fire and lava with Mario behavior](https://github.com/BrantTanner/MarioCraft/issues/27) | [#25](https://github.com/BrantTanner/MarioCraft/issues/25) |
| 27 | [#28: Die and respawn through Minecraft with Mario's life counter](https://github.com/BrantTanner/MarioCraft/issues/28) | [#25](https://github.com/BrantTanner/MarioCraft/issues/25) |
| 28 | [#29: Save and safely resume Mario's position, health, and lives](https://github.com/BrantTanner/MarioCraft/issues/29) | [#26](https://github.com/BrantTanner/MarioCraft/issues/26), [#28](https://github.com/BrantTanner/MarioCraft/issues/28) |
| 29 | [#30: Finish MarioCraft menus, audio, settings, and scope restrictions](https://github.com/BrantTanner/MarioCraft/issues/30) | [#19](https://github.com/BrantTanner/MarioCraft/issues/19) |
| 30 | [#31: Validate, repair, and package the complete playable MarioCraft MVP](https://github.com/BrantTanner/MarioCraft/issues/31) | [#20](https://github.com/BrantTanner/MarioCraft/issues/20), [#22](https://github.com/BrantTanner/MarioCraft/issues/22), [#23](https://github.com/BrantTanner/MarioCraft/issues/23), [#24](https://github.com/BrantTanner/MarioCraft/issues/24), [#27](https://github.com/BrantTanner/MarioCraft/issues/27), [#29](https://github.com/BrantTanner/MarioCraft/issues/29), [#30](https://github.com/BrantTanner/MarioCraft/issues/30) |

## Decisions delegated to agents

Select Java physics versus a native simulation bridge after evidence review. Select compatible tooling, build and automation approaches. Define evidence-based scale, scheduling, collision and crossover rules; document decisions and measurements. Subdivide oversized work and discovered coverage gaps without dropping research scope. Deliver a reproducible local build/launch path and validate the complete playable behavior before claiming success.

This ticket publication records the plan; creating issues does not itself start or schedule an agent run.
