# Data manager and renderers

User asks to manage/render data by type. Continue without approval questions under earlier explicit preference. Reuse installed Tk/Pillow and embed existing map preview. Add tabs for NPC, item, decoded image and sprite assets with search, details/source metadata, zoom, export, refresh and sprite frame/action playback. Read-only assets; no fabricated image for missing sprite mapping. NPC mapping only where original MapObject constructor establishes it; missing or equipment-dependent look stays explicitly unresolved. Item icons use original ItemEx atlas equation and horizontal22px slots, preserving signed type IDs as recorded.

- [x] Test atlas slicing, sprite module transforms/placement, missing data and NPC mapping; no raw lookId-as-image guessing.
- [x] Implement common read-only dataset/render functions and desktop tabs; embed map preview without spawning separate root window. Add double-click entry. Also recover7 server sprite packs from existing capture, without network access.
- [x] Run full suite, render actual data samples, GUI launch smoke, review, document coverage and limitations. Python69/69 pass,87/87 item icons,48/61 original sprite frame0 and7/7 server sprite frame0 have drawable data; missing parts remain warnings. Native UI screenshot geometry remains unavailable, startup verified through window inventory and error log.
