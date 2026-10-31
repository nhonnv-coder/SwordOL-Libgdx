# Legacy protocol: observed client contract

Evidence boundary and regeneration are in `README.md`. Sources are decompiled classes under `.tools/decompiled/`. All multibyte network fields below are big endian (`Utils.readshort`, `writeshort`, `readInt`, `writeInt`). Sprite/resource data uses mixed byte order; do not generalize network order to assets.

## Two channels: HTTP account and game transport

`Engine` holds two senders and every outbound packet picks one of them.

| Sender | Field | Class | Used by |
| --- | --- | --- | --- |
| account | `tcpAccountPoster` | always `HttpPoster` | `Engine.exec_A`: 1077 server list, 1051 login, 1055 registration, 1065 try-game |
| gameplay | `tcpPoster` | `TCPHandler`, or `HttpPoster` when `Engine.isCmNet == 0` | `Engine.exec`: everything after the handoff, including 1001 create character and 1097 in-game registration |

So the account side is always HTTP. The gameplay side is raw TCP in the normal case and falls back to
the same HTTP transport when `isCmNet` is zero; `Engine.receiveMsg` switches its decoder on
`bIsAccount || isCmNet == 0`, which is why one receive path has to handle both batch and single
packet framing.

`Engine.openPoster` disposes the current gameplay sender and builds the one the mode calls for, so a
session can change transport between the account phase and gameplay.

## Endpoint discovery

`Engine.getHttpStr` reads `connect_b.o` from `Const.pathsStr[0]` as a sequence of integers and joins
them as `a.b.c.d:port` — four dot-separated values then a colon before the fifth. `TCPBase`'s
constructor assigns that string to `serverIP`. The decoded file ships in the asset registry as
`legacy.config.connect-b`. The gameplay address is not taken from this file: `processLoginBackCmd`
reads it out of the 1052 response, so a hardcoded host is a bootstrap default, never the final
gameplay endpoint.

## Logical packet

`base.utils.Packet.init` writes the opcode as an untagged signed Java short. Each following field carries a one-byte tag:

| Tag | Outbound writer | Incoming reader (`PacketProcess.processDataInputStream`) |
| --- | --- | --- |
| 1 | No normal Packet writer | signed short; appended as MyShort |
| 2 | signed byte | signed byte |
| 3 | signed short | signed short |
| 4 | signed int | signed int |
| 5 | `writeLong` writes eight bytes | **reads four bytes as MyInteger** |
| 6 | unsigned-short UTF-8 byte length then UTF-8 bytes | unsigned-short byte length then UTF-8 bytes (`HttpPoster.readString`) |
| 7 | `writeMyData` has no payload implementation | unsigned-short byte length then byte buffer |
| 8 | no Packet writer | constant declared but no parsing branch |

`Packet.writeMyData` maps both type 4 and type 5 through `writeInt`. Therefore a faithful adapter must distinguish `writeLong` from generic MyDataType serialization. These are shipped inconsistencies, not a normal symmetric schema. `CNetPacketData` reads the initial opcode separately, then delegates the rest to PacketProcess. Incoming response strings use explicit UTF-8 in HttpPoster, unlike the unused/alternate `Packet.readString` platform-default constructor.

## TCP outbound envelope

`TCPHandler.sendRequest` constructs, for logical packet length N:

| Offset | Size | Value |
| --- | --- | --- |
| 0 | 2 | `FE FE` (65278, Java short -258) |
| 2 | 1 | key K, initially 1, increments modulo 256 after every envelope |
| 3 | 2 | N + 2 |
| 5 | N | logical packet XOR K per byte (`Utils.Simple_Encode`) |
| 5 + N | 4 | raw CRC accumulator of bytes at offsets 2 through N+4 inclusive |
| 9 + N | 2 | `AA AA` |

CRC uses reflected polynomial `0xEDB88320`, initial `0xFFFFFFFF`, with **no final complement** (`Utils.CRCChecksum`). A Python zlib CRC32 equivalent is `(~zlib.crc32(covered_bytes)) & 0xffffffff`. CRC covers key, length and encoded payload, excluding markers. XOR is obfuscation, with its key included in the packet; it does not protect secrets.

`TCPHandler.run` optionally sends two bytes from `bHeader` before its write queue if `bOpenHead != 0`; opcode 1052 supplies these values. It is not a universal fixed preface.

## TCP inbound framing

The receive loop reads two bytes as a signed short L, then reads L-2 bytes, passing these directly to `Engine.receiveMsg`. The ordinary socket mode passes one logical packet to `CNetPacketData`. The input direction has **no FEFE marker, XOR, CRC, or AAAA handling in this receive path**. It is incorrect to use the outbound envelope parser for responses.

The signed short cast imposes the original implementation's effective positive-length bound of 32767. The offline decoder refuses lengths below four (length field + opcode), values above the signed limit, and truncated frames. The original client instead reaches exceptions for invalid lengths.

## HTTP/WAP batching

`HttpPoster.sendRequest` prefixes each queued logical packet with total packet length (2 + N). `readySend` combines queued packets while their combined length remains below 1000 bytes. Outbound body has FEFE/key/length followed by session ID int, random int, packet-count byte and the concatenated prefixed packets. XOR covers the nine-byte session/count header and packet region. CRC then covers key, length and encoded region; AAAA ends the body. The outer length written is `9 + combined packet bytes`, which differs from the TCP outer length convention.

`HttpPoster.doSend` reads the HTTP response body as: signed short L; session ID int; random int; then L-8 bytes of batch data. Thus full body length is L+2. The batch begins with a packet-count byte, then packets with a two-byte length including that length field. `Engine.receiveMsg` applies this batch decoder when `bIsAccount` or `isCmNet == 0`; otherwise it treats the supplied stream as one packet. Empty/poll and retry requests in HttpPoster differ from ordinary batches; the offline tool decodes responses only.

### The HTTP request itself

`HttpPoster.doSend` posts through a WAP-style gateway rather than addressing the host directly:

| Property | Value |
| --- | --- |
| URL opened | the poster's own `url`, which the constructor sets to `http://10.0.0.172:80` |
| method | `POST` |
| `X-Online-Host` | `TCPBase.serverIP`, the real host and port from `connect_b.o` |
| `Accept` | `*/*` |
| `Content-Type` | `application/octet-stream` |
| `Content-Length` | the body length |

The body is the batch described above; there is no path, query string or form encoding, so this is a
binary tunnel over HTTP, not a REST resource API. A response code of 0 sets the resend flag. Code 200
is still rejected when `Content-Type` starts with `text/vnd.wap.wml` or `application/vnd.wap.wmlc`,
which is how the client detects a gateway error page instead of a game payload.

The response's first four-byte field after the length is a user/session id. When it is non-zero and
`TCPBase.usID` is still zero the client adopts it, which is how an HTTP session acquires the id that
later packets, including 1001, send back. `TCPBase.randdata` is cleared alongside `usID` when the
poster shuts down.

`base.math.ZipMe` is used for downloaded resources, not the core TCP receive envelope. It parses a ZIP local-file header `50 4B 03 04`, reads little-endian ZIP sizes and method, supports stored (0) and raw deflate (8), and extracts one entry. No evidence establishes zlib-compressed logical packets in this receive path.

## Basic opcode map and concrete schemas

Names from `work.api.Const` are corroborated by `CmdProcessor.processHttpResponse` dispatch, rather than guessed from numbers.

| Opcode | Observed behavior / method |
| --- | --- |
| 1077 | account server list; `Business.sendServerListCmdPacket_A`, response opens screen4 and calls `Business.loadServerList` |
| 1051 | account login; `Business.longinGame` around line779 writes account, password, server test string, int zero, connection-mode byte, zero byte, client string, client version int, fast-login byte |
| 1052 | account response chooses gameplay address/session, `processLoginBackCmd` → `Connect`; also gameplay connect request |
| 1055 / 1097 | account registration over the account channel / over the game channel, `Business.initRegisterPacket`; see below |
| 1065 | fast/guest game request/response, `Business.sendRegister_TryGame`, `processLoginFastGame` |
| 1001 | create character, `Business.NewRoleWnd_EditOK`; see below |
| 1080 | character list response, `processLoginRoleCmd` |
| 1081 | enter/delete/logout/choose character, `Business`/`BusinessOne`; response `processDelRoleCmd` |
| 1082 | response telling a guest/try-game account it has no character yet, `CmdProcessor.CreateRole`; the creation request is 1001 |
| 1006 | user info, `processUserInfoCmd` |
| 1017 | user attribute updates, `processUserAttriCmd` |
| 1110 | map information, `processMapInfoCmd` |
| 1126 | map portals and cell/tile updates, `processMapPortalCmd` |
| 1005 | walking, `Engine.packetUserWalk`, `processWalkCmd` |
| 1010 | action, `sendActionPacket`, `processActionCmd` |
| 1004 | chat, `sendTalkPacket`, `processTalkCmd` |
| 1008 / 1009 / 1032 | item info / item commands / item type info |
| 1031 / 1040 / 1041 / 1042 / 1049 | magic / battle / battle action / acknowledgment / battle end |
| 2030 / 2031 / 2032 | NPC info / NPC action or update / task dialog |
| 1311 | time/check/heartbeat family, subcommand byte distinguishes operations |

1052 account response parameters are recorded exactly in `legacy-endpoints.json`. `Connect` sends gameplay opcode1052 with int account ID, int authorization, byte server-line index, byte discretion setting, byte try-game flag, string account, string password.

1080 response field0 is role count byte; each role has 30 typed parameters; slot is parameter6 in that role block. Client initializes three slots and sets each received role at its slot. This is evidence of the client UI layout, not proof that the server permits exactly three in every configuration.

1081 enter request (`Business`, around line1170): int role ID, byte0, int client version, string client identifier. Delete (`BusinessOne`, around line222): int role ID, byte1. Logout uses byte2; choose-role uses byte3 and additional client/account fields (`Business`, around line3085).

1110 response: int map ID, int document/resource ID, int map type, string map name → `MapEx.setMap(type, id, doc, name)`.

1005 outbound: int player ID, int packed position, int map ID. Packed position = direction*1,000,000 + X*1000 + Y (`Engine.packetUserWalk`). Incoming `processWalkCmd` reads player ID and packed position; action = packed/10,000,000, X = packed/1000%1000, Y = packed%1000, direction = packed/1,000,000%10. Actions0/3 are handled; action3 corrects the local user's position. An unknown nearby remote player triggers an opcode1014 info request. No client observation proves the server's speed/position validation rules.

1010 outbound: int0, int user ID, short X, short Y, short direction, int data, short action type. 1004 outbound: short message type, string content, string sender, string receiver. These schemas are observed at `CmdProcessor.sendActionPacket` and `sendTalkPacket`; new-server semantics still require explicit implementation.

## Full inbound dispatch

Every opcode `CmdProcessor` routes to a handler of its own, read straight from the dispatch
switch. 85 in total. A handler named here exists; its field layout is only established where this
document states one.

| Opcode | Handler | Opcode | Handler |
| --- | --- | --- | --- |
| 1004 | `processTalkCmd` | 1142 | `processActNpcInfo` |
| 1005 | `processWalkCmd` | 1145 | `processAutoFindRoad` |
| 1006 | `processUserInfoCmd` | 1146 | `processNpcAttrCmd` |
| 1008 | `processItemInfoCmd` | 1147 | `processNpcDialogCmd` |
| 1009 | `processItemCmd` | 1157 | `processRehearseCmd` |
| 1010 | `processActionCmd` | 1158 | `processBattleModeCmd` |
| 1012 | `processTickInfo` | 1170 | `FuBen_InFo` |
| 1014 | `processOtherPlayerCmd` | 1171 | `FuBen_Item` |
| 1015 | `processMarryNameCmd` | 1176 | `processSynWarCmd` |
| 1017 | `processUserAttriCmd` | 1207 | `processActEffectFrameCmd` |
| 1023 | `processTeamCmd` | 1303 | `processPlayerInfoCmd` |
| 1026 | `processTeamMemberCmd` | 1311 | `processTestSpeed` |
| 1031 | `processMagicCmd` | 1312 | `processBuild` |
| 1032 | `processItemTypeInfoCmd` | 1313 | `processUpdateItemInfoCmd` |
| 1033 | `processShopCmd` | 1314 | `processAchievementCmd` |
| 1037 | `processStageHelpCmd` | 1315 | `processReWardCmd` |
| 1038 | `processTeamWalk` | 1403 | `processNpcActquest` |
| 1040 | `processBattleCmd` | 1407 | `processMapDataCmd` |
| 1042 | `processBattleAckCmd` | 1415 | `processSkillBreakCmd` |
| 1049 | `processBattleEndCmd` | 1416 | `processMagicAttrCmd` |
| 1052 | `processLoginBackCmd` | 1500 | `processMailCtrl` |
| 1055 | `SmsFenbaoCmd` | 1504 | `processUpdateClient` |
| 1056 | `processTradeCmd` | 1506 | `processPetScoreCmd` |
| 1059 | `processBattleAckEffectCmd` | 1508 | `processVipShopCmd` |
| 1060 | `processBattleEffectCmd` | 1510 | `processgStatusExTimeCmd` |
| 1061 | `processGodBlessCmd` | 1518 | `processTeamPK` |
| 1065 | `processLoginFastGame` | 1519 | `processEmoneyExchange` |
| 1066 | `processNewAccountIpCmd` | 1521 | `processDisconnectCmd` |
| 1080 | `processLoginRoleCmd` | 1523 | `processShortCutCmd` |
| 1081 | `processDelRoleCmd` | 1527 | `processShowUpdateCmd` |
| 1082 | `CreateRole` | 1531 | `processSysHelpCmd` |
| 1101 | `processMapItemCmd` | 1534 | `processEquipChangCmd` |
| 1103 | `processMagicInfoCmd` | 1535 | `processOpenBagCmd` |
| 1107 | `processSyndicateCmd` | 1602 | `processSeachCmd` |
| 1110 | `processMapInfoCmd` | 1603 | `processEquipPhoto` |
| 1126 | `processMapPortalCmd` | 1605 | `processSplitEquipTwice` |
| 1127 | `processPetInfoCmd` | 1610 | `processWorldRankCmd` |
| 1129 | `processLeaveUp` | 2030 | `processNpcInfoCmd` |
| 1130 | `processPetEudemon` | 2031 | `processNpcUpdateCmd` |
| 1132 | `procss_MSG_TUTOR` | 2032 | `processTaskDialogCmd` |
| 1134 | `processPetAttribCmd` | 2402 | `processCalendarAction` |
| 1138 | `processStallageCmd` | 2500 | `processLoadCtrlCmd` |
| 1141 | `processFindRoadCmd` |  |  |

## Account registration and character creation

Read from the CFR decompilation of `VoLamHoiTu.jar` (sha256 `16024c3e…`). Both flows exist in the
original client; neither was implemented in the native rebuild before this note.

### Registration — opcodes 1055 and 1097

`Business.openRegisterWnd(status)` opens control screen 114. `CustomScreen` (around line 1749)
collects three edit controls, `114007`/`114008`/`114009`, and **refuses any value shorter than six
characters** (`Const.other_str[583]`). They are stored as `Business.m_loginStr[0..2]`; the response
path fills the login screen's account and password fields from `[0]` and `[1]`, which is what
identifies the first two. The third field's meaning is not established by the client code.

Which opcode is sent depends on the screen's status variable: status `0` calls
`LoginServer(false, null)` then sends **1055** over `exec_A` (the HTTP account channel) and starts
`tcpAccountPoster`; any other status sends **1097** over `exec` (the TCP game channel). Both bodies
come from `Business.initRegisterPacket(cmd)`:

| Order | Type | Source |
| --- | --- | --- |
| 1 | byte | `0` |
| 2 | string | `m_loginStr[0]` (account) |
| 3 | string | `m_loginStr[1]` (password) |
| 4 | string | `m_loginStr[2]` |
| 5 | string | `""` |
| 6 | int | `Business.getFenBaoID(0)`, read from `PackageID.o` |
| 7 | int | `getBaoUserID()` |
| 8 | int | `9` |

1055 response (`CmdProcessor`, around line 1035) switches on byte 0. Case `0` opens control screen 2,
reads three parameters from offset 1, and shows "Đăng ký tài khoản thành công!" when the first is not
empty. Case `2` carries a name and password back to the screen. The server's own validation rules are
not observable from the client.

### Character creation — opcode 1001

`Business.NewRoleWnd_EditOK` builds the request. The name comes from the edit box, limited to ten
characters with input type 2. `ScanType` is the profession button's saved id modulo 1000 and
`SexmType` is the sex button's saved id modulo 1000 minus 15, so `10015` is 0 and `10016` is 1.

| Order | Type | Source |
| --- | --- | --- |
| 1 | string | character name |
| 2 | int | `SexmType` |
| 3 | byte | `ScanType * 10 + 1` |
| 4 | byte | `0` |
| 5 | int | `TCPBase.usID` |
| 6 | int | `0` |
| 7 | string | `""` |
| 8 | byte | slot index, `selRoleCs.getfocusedID() - 11001` |
| 9 | int | `0` |
| 10 | byte | `Business.ServerLineIndex` |
| 11 | byte | `GameScreen.m_Mdiscretion` |
| 12 | byte | `CmdProcessor.ucTryGame`, taken from parameter 11 of the 1052 response |

It is sent with `m_Engine.exec`, the TCP game channel, not the account channel.

The selectable looks are the table `Business.newRoleLookFaceDatID`, ten professions by two sexes:
`{110000,210000} {0,220000} {130000,0} {140000,240000} {150000,0} {0,260000} {170000,270000}
{180000,280000} {0,0} {0,0}`. A zero means that sex is unavailable for that profession, and
`NewRoleWnd_Flush` deactivates the corresponding button; professions 9 and 10 are zero in both
columns and `NewRoleWnd_OK` returns without sending for saved ids `10009` and `10010`. Browsing a
profession sends `1602` with byte `1` and the profession index, which is how its preview data is
requested.

### 1082 is the response, not the request

`CmdProcessor.CreateRole` handles inbound 1082: string at 0 becomes `Business.TryGameUserName`, byte
at 1 is `haveRole`, and when `haveRole == 0` the client opens control screen 10, the creation window,
and sends `1602` with byte `1` and int `1`. So 1082 announces that a guest or try-game account has no
character yet; the creation request itself is 1001. The earlier opcode table entry calling 1082
"guest-role/create handling" should be read that way.

## Unknowns

There are no authenticated network recordings in the input. Endpoint reachability, actual response sequences, negotiation timing, authoritative combat calculations, balances, drop tables, account persistence and anti-cheat rules are unknown. Static parser coverage does not constitute interoperability verification. The new server should use explicit versioned fixtures; preserve these legacy facts only where compatibility is deliberately implemented.
