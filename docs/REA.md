# REA setup

REA 6.7.0 is installed locally in `.tools/rea`, with its exact dependency versions recorded in `package-lock.json`. The project Codex connection is in `.codex/config.toml`; matching workflow instructions are in `.agents/skills/reverse-engineer-anything`.

Restart Codex and reopen this trusted project to load the connection and skill.

From the project directory in PowerShell:

```powershell
# Show the installed version
node .tools/rea/node_modules/rea-agents/scripts/rea.mjs --version

# Verify MCP startup and tool discovery
node .tools/rea/verify.mjs

# Restore dependencies on this machine
npm.cmd ci --prefix .tools/rea --ignore-scripts --no-audit --no-fund
```

The connection uses absolute paths for this Windows checkout. Update `.codex/config.toml` if the checkout moves or Node.js is installed elsewhere.

Installation does not include optional analysis engines such as Ghidra, IDA or JADX. Java decompilation of `docs/files/VoLamHoiTu.jar` may require a separate compatible Java analysis tool. Native dependencies whose install scripts were skipped may need additional setup before runtime instrumentation is available.

Setup verification completed the MCP initialization handshake and discovered 207 tools. A new Codex session must load the configuration before those tools are available in chat.

Upstream: https://github.com/morluto/rea
