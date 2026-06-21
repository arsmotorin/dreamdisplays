# Core

Internal domain implementation for Dream Displays. `core` knows product rules, but it does not know Minecraft loader-specific APIs.

## Contents

- Implementations of public services from `api`: display, playback, and watch-party
- Internal ports: `DisplaySystem`, `DisplayLookup`, `DisplayMutationPort`, `PlaybackPort`, `WatchPartyPort`, `DisplayCommandExecutor`
- Playback logic: permissions and timeline
- Protocol v2 wire classes and serialization
- Storage models and security policies / guards

## Boundaries

- `core` depends on `api`, not the other way around
- Do not put `Fabric` / `NeoForge` / `Paper` APIs, Minecraft classes, UI, rendering, or media decoder logic here
- Do not add public DTOs or service interfaces here. They belong in `api`
- Platform code talks to `core` through public API and internal ports only where it is actually doing wiring/adapters

## Dependencies

Allowed: `api`, `media`, serialization, compile-only `slf4j`
Forbidden: `platform:*`, `media:player`, `media:source`, loader-specific libraries
