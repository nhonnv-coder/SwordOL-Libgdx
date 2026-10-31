# Demo protocol/server

This is a new local demo, not an implementation of the original SwordOL wire protocol or recovered combat formulas. No production accounts, authentication, TLS, persistence, shared world, quests, inventory or original economy are implemented. Unknown commands return `NOT_IMPLEMENTED` with an explicit TODO.

Run from the workspace after building: `java -jar server/target/swordol-server.jar`. Options: `--host 127.0.0.1 --port 7777`. Binding a different interface requires an explicit `--host`; do not expose this demo publicly.

UTF-8 NDJSON, at most 16,384 bytes before LF. Requests require nonempty string `requestId` (max128), string `type` (max64), object `payload`. Responses always include `requestId`, `type`, `ok`, `payload`, `error`; error payload is `{}`, error is `{code,message}`. Successful responses have `error:null`. Invalid JSON responds `BAD_REQUEST`; invalid UTF-8, oversized and truncated frames close the connection. LF is the frame delimiter.

Commands:

- `PING {}` -> `{pong:true}`; may be sent before login.
- `LOGIN {name}` -> `{sessionId}`. Demo name contains 1-24 printable characters. Each socket can login once.
- `ENTER_WORLD {}` -> full state. Requires login on the same socket.
- `MOVE {x,y}` -> full state. Requires entering world; both coordinates must be integers within map bounds. Movement is atomic. This demo permits any in-bounds destination.
- `ATTACK {targetId:"slime-1"}` -> full state. Requires entering world, living monster, Manhattan distance <=1 and 250ms cooldown. Each successful attack deals 10 damage; monster starts with30HP. Its death grants exactly10gold once.

Full state is `{player:{id,name,x,y,hp,gold},monster:{id,x,y,hp},map:{id,width,height}}`. Map `demo-field` is20x15, player starts(1,1) with100HP, monster `slime-1` starts(3,1). Each socket owns its private demo world. Client-supplied session IDs have no authority.

Request IDs cache the last256 responses per connection, including failures. An identical request replays its snapshot without mutation; the same ID with different type/payload returns `DUPLICATE_REQUEST`. After eviction an ID is fresh; monster death still cannot reward again. Reconnect starts a new demo world and gold balance.

Limits:8 socket workers,16 queued accepted sockets,16 OS listen backlog,15-second read inactivity timeout. Send `PING` periodically to retain an idle connection. Closing the server closes accepted sockets and joins workers. Workers and frames are bounded; no production denial-of-service protection is claimed.

Tests use real loopback sockets and cover session isolation, movement validation, malformed/oversized input, combat range/cooldown, replay conflicts/window, and one-time death rewards. Build uses Java8 API targeting. Mobile clients and deployment readiness are outside this module.
