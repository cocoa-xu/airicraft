# Airicraft Agent Notes

## Current Project State
- This repo is a Fabric mod for Minecraft `1.21.11`.
- It currently uses Yarn mappings, not Mojang official mappings.
- Java target is `21`.
- The build is a multi-project Gradle build with:
  - root project: Fabric mod
  - `wrapper/`: standalone Java MCP wrapper over `stdio`

## Build And Run
- Full build: `./gradlew build`
- Run Minecraft client in dev: `./gradlew runClient`
- Wrapper entrypoint: `wrapper/src/main/java/ai/moeru/airicraft/wrapper/AiricraftWrapperMain.java`
- Wrapper artifact is built by the `wrapper` subproject as a runnable jar.

## Architecture
- The MCP server is not inside the mod.
- The public MCP surface is the standalone `wrapper` process over `stdio`.
- The Fabric mod exposes an internal localhost HTTP bridge.
- Bridge discovery is via `~/.airicraft/bridge-state.json`.
- The wrapper reads the state file, calls the localhost bridge, and deletes stale state if the bridge is unreachable.

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
- `wrapper/src/main/java/ai/moeru/airicraft/wrapper/AiricraftWrapperMain.java`
  - MCP tool registration
- `wrapper/src/main/java/ai/moeru/airicraft/wrapper/BridgeClient.java`
  - bridge HTTP client and stale-state handling

## Current MCP Tools
- `minecraft_get_status`
- `minecraft_list_worlds`
- `minecraft_join_world`
- `minecraft_list_servers`
- `minecraft_join_server`
- `minecraft_get_focus`
- `minecraft_get_world_snapshot`
- `minecraft_highlight_block`
- `minecraft_highlight_region`
- `minecraft_list_highlights`
- `minecraft_clear_highlight`
- `minecraft_clear_highlights`

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
- `minecraft_get_status` reports `sessionState`, `currentScreen`, and `canJoinWorldOrServer`.
- World-bound read/action tools still return `world_not_loaded` when no world is active.
- `minecraft_join_world` and `minecraft_join_server` return `already_in_world` if a world is already loaded.
- `minecraft_list_worlds` is intended to return `already_in_world` once the client is restarted onto the latest code.
- `minecraft_list_servers` can still safely enumerate saved servers while out of world.
- Highlights support:
  - persistent by default
  - optional timeout
  - custom `overlayText`
  - block and region highlights
  - list, clear-one, clear-all

## Verified So Far
- `./gradlew build` passes.
- Wrapper MCP initialization works.
- Bridge stale discovery handling works.
- Focus, world snapshot, and highlight flows were previously tested end-to-end.
- Out-of-world world listing and `join_world` were tested against the bridge:
  - listing saved worlds worked
  - joining a saved world worked
  - repeated join while already in world returned `already_in_world`

## Important Caveat
- If behavior changes in bridge handlers do not appear in a running dev client, restart `runClient`.
- A running Minecraft dev process keeps the old classes loaded even if the repo has already been rebuilt.
