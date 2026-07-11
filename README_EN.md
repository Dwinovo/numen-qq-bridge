# Numen QQ Bridge (numen-qq-bridge)

English | [简体中文](README.md)

Bind a Minecraft [Numen](https://github.com/Dwinovo/minecraft-numen) AI companion to a QQ account, and command it from your phone.

DM the companion on QQ — "harvest the wheat back home" — and the companion in your world plans it, does it, and DMs you back when it's done: "Harvested 64 stacks of wheat, stored in the chest." You steer your Minecraft world without sitting at the keyboard. Add the companion to a group and it works the same way there.

```
Your QQ ──NapCatQQ (OneBot 11 forward WS)──▶ numen-qq ──NumenGateway.enqueue──▶ companion's queue ──▶ companion works
Your QQ ◀──send_private_msg / send_group_msg── numen-qq ◀──send_qq_message tool── companion reports back
```

- **Inbound** — a whitelisted private or group message is wrapped as `[QQ私聊 · name(QQ)] text` or `[QQ群123456 · name] text` and pushed into the companion's message queue. An idle companion starts immediately; a busy one picks the message up at its next action gap (shown as a ⌛ queued line in the in-game chat panel).
- **Outbound** — the bridge registers a `send_qq_message` tool with numen-api. Whether to reply, what to say, and when to say it are the companion's own decisions — sending a QQ message is the same kind of action to it as swinging a pickaxe. The numeric id rides along in the prefix, so the model routes its own reply: private-in → private-out, group-in → group-out.
- **Zero third-party dependencies** — JDK-built-in WebSocket plus Gson, no bot framework pulled in.

This project is also the reference integration for numen-api's public surface: the whole bridge uses just two open entry points — `NumenGateway.enqueue` (inbound) and `ToolRegistry.register` (outbound). To wire Numen to Discord, Telegram, or a livestream chat, copy this repo's shape.

---

## Requirements

| Component | Notes |
|---|---|
| Minecraft 1.21.1 | Fabric or NeoForge |
| Java 21 | Ships with the game |
| [Numen](https://github.com/Dwinovo/minecraft-numen) | The companion mod (the numen-api engine is bundled inside it); needs engine **0.0.4+**, which ships `NumenGateway` |
| [NapCatQQ](https://github.com/NapNeko/NapCatQQ) | QQ bot framework, runs on top of the official QQNT client |
| A bot QQ account | Register a dedicated alt — don't run your main account as the bot |
| Your own LLM API key | Configured in Numen itself; whitelisted people spend these tokens |

**Install:** drop this mod's jar and Numen's jar together into `mods/` (the Fabric build also needs Fabric API). This is a **client-side** mod — the companion is driven by the owner's client and burns the owner's API key, so nothing goes on the server.

---

## Step 1 — Deploy NapCatQQ

1. Install the official QQ (NT edition) on your PC.
2. Download `NapCat.Shell.zip` from [NapCatQQ Releases](https://github.com/NapNeko/NapCatQQ/releases) and extract it anywhere.
3. Run `launcher.bat` **as administrator** (`launcher-win10.bat` on Windows 10).
4. Open `http://127.0.0.1:6099` in a browser to reach the WebUI (the login token is in the terminal log, or set your own in `config/webui.json`), and scan to log in with the **bot QQ account**.
5. WebUI → **Network Config** → New → **Websocket Server** (pick "Server" — this mod is the client that dials in):

   | Field | Recommended |
   |---|---|
   | Host | `127.0.0.1` (same machine as the game; use `0.0.0.0` for a remote host) |
   | Port | `3001` |
   | Token | Set one, so other local programs can't connect at random |
   | Message format | array or string both work (the bridge reads `raw_message`, present in either) |

   If you prefer config files, edit `network.websocketServers` in `config/onebot11_<botQQ>.json` and restart — same effect.
6. Skip the QR scan next time: `launcher.bat <botQQ>` (quick login).

**Hard requirement for private-chat mode: add the bot account as a friend from your own QQ.** QQ won't deliver private messages between strangers, so without the friendship the message never reaches NapCat.

---

## Step 2 — Configure the bridge

Launch the game once to generate `config/numen/qq_bridge.json` (under `versions/<name>/config/numen/` if PCL/HMCL version isolation is on):

```json
{
  "ws_url": "ws://127.0.0.1:3001",
  "access_token": "the token from Step 1, blank if you set none",
  "group_whitelist": [],
  "user_whitelist": [your own QQ number],
  "companion_name": ""
}
```

| Field | Meaning |
|---|---|
| `ws_url` | NapCat forward-WS address. **Blank = bridge disabled** |
| `access_token` | Must match the token set in NapCat; a mismatch is rejected at the handshake |
| `group_whitelist` | Which **groups'** messages are accepted |
| `user_whitelist` | Which **QQ accounts** may command the companion over private chat — put your own number here |
| `companion_name` | Which companion messages are delivered to; blank = the first on the roster |

**Security model in one line: both whitelists are "empty = accept nothing" — deny by default, allow explicitly.** A message that isn't on a list is dropped silently at the bridge, never reaches the model, and costs zero tokens. Whitelisting someone authorizes them to spend your LLM API quota — only add people you trust.

---

## Step 3 — Use it

1. Enter a save, summon the companion, and **open its chat panel once** (press G) so its agent loop is live.
2. `[numen-qq] connected to ws://127.0.0.1:3001` in the log means the connection is up.
3. DM the bot from your QQ: "Where are you? What's nearby?"
   - A `⌛ [QQ私聊 · yourName(QQ)] …` queued line appears in the in-game chat panel.
   - The companion acts, and when done calls `send_qq_message` to DM you back.

From there it's open-ended: mine, build, tidy chests, report inventory — anything you can command in-game, you can command over QQ.

---

## How it works

- **Inbound goes through the message queue.** `NumenGateway.enqueue(companionUuid, message)` puts the string **verbatim** into the companion's owner-prompt queue — the same path as typing in the chat box — and the model sees it at a protocol-legal point (a tool-batch boundary). The message shape (source prefix and so on) is the bridge's own convention; the engine special-cases nothing.
- **Outbound goes through a tool.** The bridge registers `send_qq_message` (param `message` plus one of `group_id` / `user_id`); the model reads the reply target off the request prefix. No callback, no polling — the brain talks back with a hand.
- **The tool-call queue is never opened.** Every item in it is the product of some LLM decision, bound to a `tool_call_id` in the conversation protocol. Injecting a tool call from outside would be driving the body past the brain — that boundary is sealed on purpose.
- **Multi-loader layout.** All logic lives in `common/` (five classes: config, WS client, wiring, tool, constants); `fabric/` and `neoforge/` each hold one thin entry class.

---

## Config reference

| Field | Type | Default | Meaning |
|---|---|---|---|
| `ws_url` | string | `""` | NapCat forward-WS address (e.g. `ws://127.0.0.1:3001`). Blank disables the bridge |
| `access_token` | string | `""` | Bearer token; must match NapCat's. Blank = no auth header sent |
| `group_whitelist` | number[] | `[]` | Group numbers whose messages are accepted. Empty = accept none |
| `user_whitelist` | number[] | `[]` | QQ accounts allowed to command via private chat. Empty = accept none |
| `companion_name` | string | `""` | Target companion by name; blank = the roster's first entry |

The first group in `group_whitelist` is the default target for `send_qq_message` when the model passes neither `group_id` nor `user_id`.

---

## Troubleshooting

| Symptom | Check |
|---|---|
| Log repeats `connect ... failed, retrying` | Is NapCat's WS in "Server" mode? Right port? Does `access_token` match on both sides? |
| No response to private chat | ① Is your account a friend of the bot? ② Is `user_whitelist` **your** number (not the bot's)? ③ Is that WS server shown as started in NapCat? |
| Log: `QQ message dropped — no companion on the roster yet` | You haven't summoned a companion |
| Log: `enqueue ... refused — open the companion's chat once` | Open the companion's chat panel once after entering the game |
| Message reaches the game but the companion sits still | Check Numen's own API-key / model config (Settings page) — unrelated to the bridge |
| NapCat port won't bind | Check for multiple NapCat instances; kill all and start one |

Every game-side diagnostic is in the log's `[numen-qq]` lines: connect, reconnect, drop reasons, delivery results — one line, one cause.

---

## Build from source

```bash
./gradlew :fabric:build :neoforge:build
# artifacts in fabric/build/libs and neoforge/build/libs
```

Depends on `com.dwinovo.numen:numen-api-*-1.21.1:0.0.2-SNAPSHOT`, resolved from [numen-maven](https://github.com/Dwinovo/numen-maven).

---

## Ecosystem

**Numen** ([minecraft-numen](https://github.com/Dwinovo/minecraft-numen)) is the mod — the AI companion. It runs on the **[numen-api](https://github.com/Dwinovo/numen-api)** engine (published through **[numen-maven](https://github.com/Dwinovo/numen-maven)**), which exposes a small public API. Two things build on it:

**Extend a companion** — its own brain stays in charge:
- **Bridges** carry an outside channel into a companion: a message arrives, and the companion decides what to do. Built on `NumenGateway`. → **[numen-qq-bridge](https://github.com/Dwinovo/numen-qq-bridge)** (QQ), with more to come. *(this repo)*
- **Skills** teach a companion how to behave — markdown loaded into its context. Bundled with Numen, or community-written.

**Expose Numen** — hand the controls to an outside brain:
- **[numen-mcp](https://github.com/Dwinovo/numen-mcp)** is a Model Context Protocol server: any external agent (like Claude) drives companions directly. Built on `NumenActuator`.

---

## License

MIT. See [LICENSE](LICENSE).
