# Entry flow, UI, resources and authority boundary

Source identity and static-analysis limits are in `README.md`. The method references below are sufficient to reproduce each trace with CFR; none is a claim of runtime observation.

## Startup and gameplay trace

1. Manifest selects `work.main.MyMidlet`. `startApp` stores the MIDlet/display singleton and sets the display to `MyGameCanvas.getInstance()`.
2. `MyGameCanvas.getInstance` constructs a fullscreen canvas, runs `businessInit` to wire Business/BusinessOne/BusinessTwo/Engine singletons, starts the Resources worker, opens control2, initializes EntityManager, loads system settings/RMS, initializes Engine, then starts the canvas thread.
3. `Engine.initialize` creates account `HttpPoster` and gameplay `TCPHandler`, each with Engine as listener. Connection mode can replace the latter with HttpPoster in `InitTcpPoster`.
4. `MyGameCanvas.run` updates focus/key/touch state and rendering on the game loop. `Engine` queues `CNetPacketData`; its response processing removes packets from that queue and dispatches through `CmdProcessor.processHttpResponse`.
5. `Business.LoginServer` resets account state and selects the account URL. `sendServerListCmdPacket_A` requests1077 with client version, client identifier, zero byte and empty string. Response1077 closes startup screen2, opens screen4, and calls `Business.loadServerList`.
6. `Business.longinGame` sends1051 with account/password and selected server data. Response1052 is read by `processLoginBackCmd`, then `Connect` stops account transport, stores account/authorization, configures the chosen game transport and sends1052 to it. The game server address is response supplied.
7. Response1080 `processLoginRoleCmd` opens screen11, builds three role slots, inserts blocks of30 parameters by slot, and calls `Business.SelRole`. `Business.selRoleMenu` sends1081 action0 to enter the selected character; BusinessOne sends action1 for deletion. These outgoing actions imply persistent operations and are never performed by the analysis tools.
8. User state arrives through1006 and1017; map1110 invokes `MapEx.setMap`. `CmdProcessor` filters most world packets until `EntityManager.s_pUser` exists. Portal1126 changes portal lists and map blocking/tiles. Other players1014, NPC2030/2031, item1008/1009, chat1004 and battle1040-family update their respective client objects.
9. `Engine.packetUserWalk` sends1005 with packed position/map ID. `processWalkCmd` handles nearby remote movement and server position correction for the local user. Client movement/AStar/path checking does not recover server movement authority.

The original uses direct singleton/business/UI coupling. A PureMVC reconstruction is a **new architecture decision**: retain transport and parsing in proxies, turn server responses into notifications/commands, keep screens in mediators, and store authoritative snapshot state separately from rendering/prediction. There is no evidence that the JAR itself used PureMVC or libGDX.

## Resource inventory

Original archive contains61 `.dat`,4 `.o`,150 `.p`,63 `.png`,72 `.ui`,1 manifest and69 class files. All351 nonclass members were byte-preserved with member hashes; `dev/assets/manifest.json` is the exact identity mapping. Generated output paths are ordinal-based because Windows names are case-insensitive. `res/LOGO.png` and `res/logo.png` collide by name but both are retained; their bytes are identical (SHA-256 `e836588490271c4d5b6efa3b2ce8162f210b9ad0dddd9f0b2d8e5501316f0d1c`). Standard PNG signatures were observed in all63 PNG entries. No assumption of license/ownership follows from their presence.

`Const.pathsStr` names `/res/`, `/res/ui/`, `/res/images/`, `/res/role/`, `/res/script/`; however the fifth array element is a display string, so do not assume every entry is a path.

### `.o` is not one universal format

- `res/connect_b.o`: compact line-based OFileReader configuration; exact16 bytes and decoded address are in `legacy-endpoints.json`.
- `res/start.o`: compact lines,16 bytes. `Engine.getClientStr` reads first value as client identifier `JT_N5800F`; second is key speed210. Hex `020bc0094a545f4e35383030460240d2` corroborates this interpretation.
- `res/mimg.o` (4797 bytes) and `res/rimg.o` (4461 bytes): image index tables loaded by `ImagePointer` using `Utils.loadScript(..., false, 2)`, **not OFileReader lines**. First two bytes are BE lengths4795/4459; remaining records are seven bytes: BE image ID int, pack index byte, BE pack offset short. `NewCreatePng` finds matching IDs and loads mpN.p or rpN.p at that offset. Observe method-specific use before applying a decoder based on extension.

OFileReader integer tags use the top2 bits to choose1/2/4/5-byte forms and bit0x20 for sign; lower6 bits carry leading value bits. `read()` distinguishes strings with top bits0xC0 and reads a14-bit byte length. File header gives line count; each line has a compact byte length. The same bit pattern has different meaning in `read()` versus `readInteger()` contexts.

### `.p` packed indexed images

`ImagePointer.NewCreatePng` reads22-byte records from `/res/images/mpN.p` or `rpN.p` using the index-table offset. These files do not have PNG signatures; renaming them to `.png` is incorrect.

| Record offset | Field |
| --- | --- |
| 0 / 2 | unsigned-short width / height |
| 4 | PNG bit depth byte |
| 5 | transparency palette index signed byte; -1 means no tRNS |
| 6 / 10 / 14 | precomputed PLTE / tRNS / IHDR CRC ints |
| 18 | palette byte length short (RGB565, two bytes/color) |
| 20 | full following data length unsigned short |
| 22 onward | RGB565 palette, image data and stored IDAT CRC |

`createPLTEChunkEx` expands RGB565 to RGB888, with optional per-channel OR tint. `NewCreatePngEx` reconstructs PNG signature, IHDR (indexed color type3), PLTE, optional tRNS, IDAT and IEND. `createIDATChunkEx` copies the trailing compressed image data **including its stored CRC**; it does not decode pixels. PNG reconstruction can be implemented offline, but this export currently preserves original packs rather than claiming a verified full graphical conversion. Palette sign handling and tint/CRC behavior must be checked against the bytecode when porting.

Fallback `NewCreatePng` tries `/res/p<ID>.png` and RMS `<ID>.png` when an image ID is absent from index tables. Downloaded map images use separate memory buffers/indexes.

### `.dat` sprites

`ISpriteEx.readSpriteEx` loads `/res/role/d<ID>.dat`; first2 bytes are a **little-endian** payload length. The payload uses mixed-order fields: byte image count at0, LE module count at1-2, LE frame count at3-4, byte action count at5, LE frame-section length at6-7, then BE image IDs (4 bytes each). Modules have5-byte records. Frame lists have a count byte followed by4 bytes/module; actions have a count,4-byte action ID and3 bytes/frame. See `getModulesPos`, `getFramesPos`, `getActionsPos`, `loadActionIndex`, and drawing accessors for semantic transforms and signed offsets. Do not decode these as network packets or generic archives.

Missing sprites fall back to cached `<ID>.zip` or packaged `d1000000.dat`. Downloaded boss sprites and cache ZIPs use `ZipMe`; this is a separate loader branch. The61 packaged `.dat` headers do not begin with ZIP signatures.

### `.ui` layouts

`CtrlManager.loadData` loads `/res/ui/u<window-ID>.ui` directly as DataInputStream. `CustomScreen.newCtrl` reads BE int ID, byte class/type flag, then common `ScreenBase.newCtrl` properties: four signed-byte neighbor IDs, short X/Y/width/height, int text color, int position/index, short mode, byte optimization flags. It then reads focus and soft-control IDs. CtrlManager reads a control-count byte and typed child controls, each with ID/type plus common and type-specific fields.

`CtrlManager.newScreen` chooses concrete edit/button/grid/text/etc. Controls have additional data in their own `newCtrl` implementations. A generic JSON layout export requires those per-type readers; merely scanning common records is insufficient. Screens contain IDs driving Business callbacks, so reproducing appearance without ID/action mapping will omit behavior.

### Server-delivered map data

`MapEx.updateArray...` assembles server-provided buffers for map PNG/index, boss sprite/image, map reference, map tiles and skill data. `loadZipData` unpacks map data using ZipMe, reads a5-byte header, dimensions from header bytes1/2, then cols*rows tile bytes and three ceil(cols*rows/8) bitsets (blocking, rotation, flight blocking). `updateArrayBoosImages` also unpacks ZIP bytes. These buffers demonstrate that the packaged archive alone may omit world assets. A complete map catalog cannot be asserted from packaged files.

## Authority and unresolved behavior

Observed client classes implement UI, local caches, animations, pathfinding, predictive movement, outgoing requests and response interpretation. They expose message IDs and some field ordering, plus client-side display limits. They do not establish authoritative combat damage, critical/drop probability, loot tables, item economy, quest rewards, progression, account authorization, persistence transactions, server scheduling or cheat checks. Server responses can inform comparison; they are not server source. A replacement server must implement and document its own rules, with any legacy compatibility backed by captured fixtures.
