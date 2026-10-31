# Desktop map preview

User requests a map preview tool; prior instruction explicitly delegates choices and says not to ask again. Use installed Python Tkinter + Pillow, no browser/server/install step. Read existing catalog, map.json/map-ref.bin and original/ downloaded PNGs; render a static first animation frame. Show searchable map list, zoom/pan, grid/block/portal overlays, hover cell information and PNG export. Keep source captures and exported assets read-only.

- [x] Test bounded reference parser, row-major render placement, downloaded image priority, animation cycle handling, mirroring and portal overlays.
- [x] Implement renderer and Tk desktop viewer; add double-click launcher.
- [x] Run full analysis suite, render real collected maps, inspect PNGs, smoke launch GUI and document limitations. All25 maps rendered with zero missing images/warnings. Original-client comparison is static; native window screenshot capture times out, so input interactions are not independently verified through UI automation.

Static preview excludes characters/NPCs and animated timing. Report missing images explicitly; do not invent replacement assets.
