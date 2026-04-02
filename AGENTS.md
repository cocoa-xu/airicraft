# Airicraft Agent Notes

## Current Project State
- This repo is a Fabric mod for Minecraft `1.21.11`.
- It currently uses Yarn mappings, not Mojang official mappings.
- Java target is `21`.
- The build is a multi-project Gradle build with:
  - root project: Fabric mod
  - `wrapper/`: standalone Java CLI for agent-driven control

## Build And Run
- Before running build or test verification commands, source `.envrc` first: `source .envrc`
- Full build: `./gradlew build`
- Run Minecraft client in dev: `./gradlew runClient`
- `runClient` starts JDWP by default on `127.0.0.1:5005` with `suspend=n`
- Attach a debugger with `jdb -attach 127.0.0.1:5005` or any JDWP client
- Override JDWP settings with Gradle properties, for example:
  - `./gradlew runClient -Pairicraft.jdwp.port=5006`
  - `./gradlew runClient -Pairicraft.jdwp.suspend=y`
- CLI entrypoint: `wrapper/src/main/java/ai/moeru/airicraft/wrapper/AiricraftCliMain.java`
- CLI artifact is built by the `wrapper` subproject as a runnable jar and application distribution.

## Architecture
- The public control surface is the standalone `wrapper` CLI.
- The Fabric mod exposes an internal localhost HTTP bridge.
- Bridge discovery is via `~/.airicraft/bridge-state.json`.
- The CLI reads the state file, calls the localhost bridge, and deletes stale state if the bridge is unreachable.

## Key Mod-Side Files
- `src/client/java/ai/moeru/airicraft/ModBridgeServer.java`
  - localhost bridge entrypoint
  - bridge auth, routing, error mapping
- `src/client/java/ai/moeru/airicraft/ClientRuntimeController.java`
  - bridge lifecycle and highlight ticking/rendering
- `src/client/java/ai/moeru/airicraft/HighlightManager.java`
  - block/region highlight registry and rendering
- `src/client/java/ai/moeru/airicraft/SingleplayerWorldService.java`
  - list and join saved singleplayer worlds
- `src/client/java/ai/moeru/airicraft/SavedServerService.java`
  - list and join saved multiplayer servers

## Key Wrapper Files
- `wrapper/src/main/java/ai/moeru/airicraft/wrapper/AiricraftCliMain.java`
  - CLI command tree, text output, error handling
- `wrapper/src/main/java/ai/moeru/airicraft/wrapper/HttpBridgeTransport.java`
  - bridge HTTP client and stale-state handling

## Current CLI Commands
- `airicraft status`
- `airicraft worlds list`
- `airicraft worlds join --world-id <id>`
- `airicraft servers list`
- `airicraft servers join --server-id <id>`
- `airicraft player focus`
- `airicraft player look-at --x <x> --y <y> --z <z>`
- `airicraft world snapshot [--x <x> --y <y> --z <z>] [--radius <0-4>]`
- `airicraft highlights block --x <x> --y <y> --z <z> [--color <hex>] [--duration-seconds <1-86400>] [--overlay-text <text>]`
- `airicraft highlights region --x1 <x> --y1 <y> --z1 <z> --x2 <x> --y2 <y> --z2 <z> [--color <hex>] [--duration-seconds <1-86400>] [--overlay-text <text>]`
- `airicraft highlights list`
- `airicraft highlights clear --highlight-id <id>`
- `airicraft highlights clear-all`
- `airicraft help [command...]`

## CLI Output Contract
- Operational commands print deterministic plain text to `stdout`.
- Success starts with:
  - `status: ok`
  - `command: <command path>`
- Failure starts with:
  - `status: error`
  - `command: <command path>`
  - `error_code: <stable_code>`
  - `message: <text>`
- `help` and `--help` are text-only usage output.
- Exit codes:
  - `0` success
  - `2` CLI parse or validation failure
  - `3` bridge discovery or transport failure
  - `4` bridge/domain/state failure
  - `1` unexpected internal failure

## Current Bridge Endpoints
- `GET /v1/status`
- `GET /v1/worlds`
- `POST /v1/worlds/join`
- `GET /v1/servers`
- `POST /v1/servers/join`
- `GET /v1/focus`
- `GET /v1/world-snapshot`
- `GET|POST|DELETE /v1/highlights`

## Behavior Notes
- The bridge is tied to the Minecraft client process, not world load state.
- `airicraft status` is a probe command and still exits `0` when Minecraft is unavailable, reporting `available: false`.
- World-bound read/action commands still return `world_not_loaded` when no world is active.
- `airicraft worlds join` and `airicraft servers join` return `already_in_world` if a world is already loaded.
- `airicraft worlds list` is intended to return `already_in_world` once the client is restarted onto the latest code.
- `airicraft servers list` can still safely enumerate saved servers while out of world.
- Highlights support:
  - persistent by default
  - optional timeout
  - custom `overlayText`
  - block and region highlights
  - list, clear-one, clear-all

## Verified So Far
- `./gradlew build` passes.
- Wrapper bridge initialization and stale discovery cleanup logic work.
- Bridge stale discovery handling works.
- Focus, world snapshot, and highlight flows were previously tested end-to-end.
- Out-of-world world listing and `join_world` were tested against the bridge:
  - listing saved worlds worked
  - joining a saved world worked
  - repeated join while already in world returned `already_in_world`

## Important Caveat
- If behavior changes in bridge handlers do not appear in a running dev client, restart `runClient`.
- A running Minecraft dev process keeps the old classes loaded even if the repo has already been rebuilt.
- The in-mod verification scenarios are stateful. Running multiple planner/follow scenarios back to back in one client session can produce cross-scenario interference.
- In particular, `llm.degradation_goal_preserved` intentionally drives the runtime into degraded mode before reset, so later planner/follow scenarios should be run individually or after restarting `runClient`.
