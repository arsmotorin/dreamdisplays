# Native

Optional Rust native layer for hot media paths.

## Contents

- `dreamdisplays_native` are low-level helpers for Kotlin media code.
- `dreamdisplays_lav` is optional in-process video decode path through `FFmpeg` / `libav`.
- C ABI declarations consumed from Kotlin through `Project Panama`.

## Boundaries

- Kotlin orchestration stays in `media:player` and `platform:client:common`
- No Minecraft, `Fabric`, `NeoForge`, or `Paper` code belongs here
- Native code must expose a small stable C ABI and keep ownership / lifetime rules explicit

## Build

```sh
cd native
cargo build --release
cargo test
```

Gradle bundles local or CI-built native artifacts into mod jars under `dreamdisplays-natives/<os>-<arch>/`.
