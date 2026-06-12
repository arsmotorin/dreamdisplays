# Dream Displays Native

Optional Rust library that takes over the hottest CPU paths of the media pipeline. Consumed from Kotlin via the Project
Panama (yes, we would better drop support for natives on Java 21 or earlier than use silly JNI).

## Build

```sh
cd native
cargo build --release
cargo test
```

If `native/target/release/` contains `dreamdisplays_native` and/or `dreamdisplays_lav` when the mod jars are built,
Gradle bundles them into the jar at `dreamdisplays-natives/<os>-<arch>/`. At runtime `NativeMedia` extracts them to
`./dreamdisplays/native/<os>-<arch>/` in the game directory.

`dreamdisplays_lav` is optional and replaces only the video `FFmpeg` process. It uses `FFmpeg`'s shared libraries through
`ffmpeg-next`, probes the requested decoder for VideoToolbox, D3D11VA/DXVA2, VAAPI, or CUDA support, and falls back to
software decode when hardware setup fails. The external `FFmpeg` binary is still required for audio until the audio path is
moved in-process too. If `FFmpeg` shared libraries are bundled next to `dreamdisplays_lav`, `NativeMedia` preloads them
before loading the LAV backend.

Runtime toggles:

```sh
-Ddreamdisplays.native.libav=true             # Enable the in-process video path
-Ddreamdisplays.native.libav.hw=auto          # Auto (default), videotoolbox, d3d11va, vaapi, cuda, none
-Ddreamdisplays.native.libav.zeroCopy=true    # Enable the additive hardware-surface ABI once a renderer is wired
```

## ABI

C ABI, declared in `src/lib.rs`.
