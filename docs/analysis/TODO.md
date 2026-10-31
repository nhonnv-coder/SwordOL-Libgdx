# Remaining recovery and new-server work

## Evidence status

- Answered: artifact identity; startup trace; transport split; endpoint selection; basic logical field encoding; TCP request framing; response framing/batching; resource families and loader references; collision-safe original asset export; offline response decoder.
- Partial: character/map/walk/chat/action schemas; sprite and packed-PNG layouts; UI binary layouts; gameplay feature coverage. Large Business/CmdProcessor classes were inspected by relevant methods, not exhaustively annotated.
- Unresolved: authentic response fixtures, server reachability/availability, complete world assets, authoritative rules, compatibility across server versions, full runtime parity.

## Prioritized follow-up

1. Retain authorized saved responses for1077,1052,1080,1006,1110,1005 and1126. Decode using the provided offline tool. Preserve session context and original hashes; create sanitized fixtures for automated regression. Do not enable historical endpoints as defaults.
2. Implement fixture-driven adapters for the deliberate compatibility subset. Resolve type5 reader/writer inconsistency per direction; enforce bounded lengths/counts and Unicode round trips in the new protocol. Avoid silently accepting unknown tags.
3. Done: offline PNG reconstruction for indexed `.p` records with dimension/palette/CRC validation (1321 images, every stored CRC matching), sprite/action metadata export with per-record bounds checks, and source member/hash lineage kept in every derived asset. See `docs/legacy-assets.md`.
4. Done for the readers: all five control types are ported and all 72 layouts decode byte-exactly (`docs/legacy-assets.md`). Still open: map control IDs to callbacks. Compare login/role/game screens against actual MIDlet screenshots if a compatible runtime is later available. Static layout inference alone cannot validate visual parity.
5. Recover map/skill data from supplied response fixtures or separately authorized saved caches. Build a world-asset completeness report listing unresolved IDs and fallback usage.
6. New server: add persistent account/character storage; session/token authorization; authoritative movement/collision; authoritative combat and skills; spawn/NPC/quest scripts; inventory/equipment/trade transactions; chat/social/guild systems; map transfers; observability and administration. Formulas and rates remain explicit design decisions until backed by evidence.
7. New-client parity: animations/sprites/tint, maps/pathfinding, input/focus, dialogs, item/tooltips, chat/social, NPC/quests, skills/combat, pets, trades/guild/economy. Track each feature as observed, reconstructed, fixture-tested, runtime-compared or unknown.
8. Validate Android/Windows/iOS builds on their actual toolchains and devices, then release signing, crash mapping, resource packaging and obfuscation. A Java ME decompilation is not a buildable cross-platform product.

No item above authorizes live production requests or changes. The existing server is a response-comparison source only; a new server implementation cannot be described as recovered original server code.
