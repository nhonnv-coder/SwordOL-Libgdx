# Native legacy login vertical slice

User rejects demo-only work. Implement actual original wire login/server-list/game handoff/role selection in native libGDX, within Java packages. Existing JSON demo remains explicitly optional. No live automatic login or endpoint probes; network actions require user input. Existing capture/game session stays untouched.

Sources: original Packet, HttpPoster, TCPHandler, Business.longinGame/loadServerList/SelRole and CmdProcessor.processLoginBackCmd/processLoginRoleCmd/Connect from original JAR SHA16024c3ed0536b29217a67f657f110abc78549eae75ef6ac0bfdd0dc205b0ccd. Client version250615, identifier JT_N5800F from start.o. Static evidence only until user-driven live acceptance.

Build logical typed packet writer + TCP outbound CRC/XOR + account HTTP batching and inbound batch decoding. Client session: disconnected → loading servers → login → game connect → roles → entering → map announcement. Parse strict field schema, bounded IO, cancel/disconnect invalidates queued callbacks and clears secrets, no sensitive logging. PureMVC Proxy/Command/Mediator connects UI. Render/UI never blocks on IO.

Local server implementation supports this narrow authentication/role/map flow through the same binary protocol, with injected account verifier and role store; no claim of old rules/full MMO. Local tests use synthetic identities only. Native UI starts disconnected, no credentials persisted, no auto registration/delete/trade. Default desktop opens native login, demo only via explicit property. Native world renderer and full gameplay remain next work; map announcement is not a completed playable world.

Acceptance: original Packet writer byte parity, envelope direction/CRC/length tests; real loopback HTTP→TCP integration native session with valid/invalid login and role slot handling; GL screenshot of actual native UI with local fixtures only, full reactor build, independent review.
