# API

Public, experimental Dream Displays API. This module is the boundary external code, platform integrations, and sibling
modules can use without depending on `core` internals.

## Contents

- Public domain models: `DisplayId`, `Display`, `DisplayBounds`, `DisplaySettings`, `DisplayRuntimeState`, `DisplayEvent`
- Public playback and watch-party modes/actions: `PlaybackMode`, `PlaybackAction`, `WatchPartyAction`, `WatchPartySessionState`
- Service contracts: `DisplayService`, `PlaybackService`, `WatchPartyService`
- Media/render/platform integration contracts under `com.dreamdisplays.media.api`, `com.dreamdisplays.render.api`, and `com.dreamdisplays.platform.api`

## Boundaries

- `api` must not depend on `core` or import `com.dreamdisplays.core.*`
- Do not put implementations, caches, file IO, network IO, Minecraft / `Paper` / `Fabric` / `NeoForge` classes here
- If a type is meant for public consumers, it belongs here or in a small independent module such as `media`, not in `core`

## Dependents

`core`, `media:*`, and `platform:*` may depend on `api`. The reverse dependency is forbidden.
