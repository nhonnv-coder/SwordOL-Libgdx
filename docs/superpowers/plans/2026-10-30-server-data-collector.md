# Passive server data collection

User explicitly requests opening emulator, autonomously monitoring and collecting available map/monster/item data, with separate summaries. Reuse inbound recorder; never inject requests, enumerate IDs or operate account/inventory actions. Heartbeat every15 minutes handles monitoring in this chat, quiet unless meaningful new data or action needed. Opening game is authorized; user handles credentials.

- [x] Extract selected confirmed fields from NPC2030 and item1008, resolve per-stream NPC template cache2030 actions1/2 exactly as client; retain unsupported schemas as diagnostics, not fabricated fields.
- [x] Reuse map extraction and publish immutable report generations under server-data, with separate JSON groups and a Markdown summary. Record capture snapshot hashes/offsets, distinguish runtime observations from database completeness. Exclude account/chat/keeper IDs.
- [x] Add one-shot collector command, tests for template expansion, boundaries, secret exclusion and summaries; run all tests, process old+new data, verify emulator and scheduled monitoring. Python64/64 pass; report7 files independently hash-verified; heartbeat thu-d-li-u-swordol ACTIVE every15 minutes with baseline monitor-state.json.

NPC classification stays numeric until an authoritative classifier is established. Item instances are observations, not complete drop/economy tables. No claim of autonomous gameplay without visible UI control evidence.
