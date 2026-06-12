//! Optional in-process libav decode backend, shipped as its own cdylib so the main
//! `dreamdisplays_native` library stays free of libav link dependencies (this one fails to
//! load on machines without the FFmpeg shared libraries, and the JVM treats that as
//! "feature unavailable" instead of losing the whole native pipeline).
//!
//! One session replaces the video `FFmpeg` process: libavformat reads the network stream,
//! libavcodec decodes (VideoToolbox / D3D11VA / VAAPI / CUDA where available, software otherwise),
//! libswscale aspect-fits into the target size, and the frame lands in the caller's direct
//! buffer as tightly packed I420 — the same wire format `dd_video_read_frame_i420` produces,
//! so the JVM render path is identical from there on.
//!
//! The additive surface ABI keeps decoder hardware frames alive and lets the render thread import
//! their planes into platform GL textures. macOS VideoToolbox is implemented through
//! CVPixelBuffer/IOSurface/CGLTexImageIOSurface2D; unsupported platforms/formats cleanly fall back
//! to the I420 path above.
//!
//! ABI mirrors the main library's conventions: panic-safe entry points, opaque `i64`
//! handles, blocking reads unblocked by `dd_lav_kill`.

pub mod session;
pub mod surface;

use session::{LavSessions, ERR_BAD_ARGS, ERR_IO, NO_PTS_NANOS};
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::sync::OnceLock;
use surface::{LavSurfaceDesc, SURFACE_ABI_VERSION};

/// Bumped on any breaking change of this ABI.
pub const LAV_ABI_VERSION: u32 = 2;

static SESSIONS: OnceLock<LavSessions> = OnceLock::new();

fn sessions() -> &'static LavSessions {
    SESSIONS.get_or_init(LavSessions::new)
}

/// Returns [`LAV_ABI_VERSION`]; the JVM bridge calls this first as a sanity check.
#[no_mangle]
pub extern "C" fn dd_lav_abi_version() -> u32 {
    LAV_ABI_VERSION
}

/// Returns the optional hardware-surface ABI version. This ABI is additive to the I420 path:
/// callers can probe it and fall back to [`dd_lav_read_frame_i420`] without reopening.
#[no_mangle]
pub extern "C" fn dd_lav_surface_abi_version() -> u32 {
    SURFACE_ABI_VERSION
}

/// Opens an in-process decode session for the UTF-8 `url` and returns a handle (0 on failure).
///
/// `w`/`h` are the target I420 dimensions (frames are aspect-fitted and padded with black),
/// `start_micros` is the initial seek position. `hw_accel` is a stable backend code:
/// 0 = software only, 1 = auto, 2 = VideoToolbox, 3 = D3D11VA, 4 = VAAPI, 5 = CUDA.
/// Hardware setup is best-effort; every backend falls back to software decode.
///
/// Safety: `url` must point to `url_len` readable bytes.
#[no_mangle]
pub unsafe extern "C" fn dd_lav_open(
    url: *const u8,
    url_len: u64,
    w: u32,
    h: u32,
    start_micros: i64,
    hw_accel: u32,
) -> i64 {
    if url.is_null() || url_len == 0 || w == 0 || h == 0 {
        return 0;
    }
    let bytes = std::slice::from_raw_parts(url, url_len as usize);
    catch_unwind(AssertUnwindSafe(|| {
        let url = String::from_utf8_lossy(bytes).into_owned();
        sessions().open(&url, w as usize, h as usize, start_micros, hw_accel)
    }))
    .unwrap_or(0)
}

/// Blocking decode of the next frame into `dst` as tightly packed I420 (Y, then U, then V),
/// aspect-fitted into the session's target size with black padding. No color conversion or
/// brightness is applied; both happen in the display's fragment shader.
///
/// Returns 0 on success, 1 on EOF, negative on error.
///
/// Safety: `dst` must point to `dst_len` writable bytes for the duration of the call.
/// Only one thread may read a given handle at a time.
#[no_mangle]
pub unsafe extern "C" fn dd_lav_read_frame_i420(handle: i64, dst: *mut u8, dst_len: u64) -> i32 {
    if dst.is_null() {
        return ERR_BAD_ARGS;
    }
    let dst = std::slice::from_raw_parts_mut(dst, dst_len as usize);
    catch_unwind(AssertUnwindSafe(|| sessions().read_frame(handle, dst))).unwrap_or(ERR_IO)
}

/// Blocking decode of the next frame into `dst` as I420 and writes the frame's normalized
/// playback timestamp in nanoseconds to `pts_nanos`. When libav cannot provide a timestamp,
/// `pts_nanos` is set to `i64::MIN` and the caller should keep its synthetic FPS clock.
///
/// This is an additive ABI entry point; [`dd_lav_read_frame_i420`] stays available for older
/// JVM bridges and simple callers.
///
/// Safety: `dst` must point to `dst_len` writable bytes for the duration of the call. If
/// `pts_nanos` is non-null, it must point to one writable `i64`.
#[no_mangle]
pub unsafe extern "C" fn dd_lav_read_frame_i420_pts(
    handle: i64,
    dst: *mut u8,
    dst_len: u64,
    pts_nanos: *mut i64,
) -> i32 {
    if dst.is_null() {
        return ERR_BAD_ARGS;
    }
    if !pts_nanos.is_null() {
        *pts_nanos = NO_PTS_NANOS;
    }
    let dst = std::slice::from_raw_parts_mut(dst, dst_len as usize);
    catch_unwind(AssertUnwindSafe(|| {
        let mut pts = NO_PTS_NANOS;
        let rc = sessions().read_frame_with_pts(handle, dst, &mut pts);
        if !pts_nanos.is_null() {
            *pts_nanos = pts;
        }
        rc
    }))
    .unwrap_or(ERR_IO)
}

/// Blocking decode of the next hardware frame as a retained GPU-importable surface.
///
/// On success, `desc->handle` is a surface handle owned by the caller. Release it with
/// [`dd_lav_release_surface`] after the render thread has imported/bound the planes it needs.
/// Returns 0 on success, 1 on EOF, negative on error or unsupported platform/format.
///
/// Safety: `desc` must point to writable memory for one [`LavSurfaceDesc`].
#[no_mangle]
pub unsafe extern "C" fn dd_lav_read_surface(handle: i64, desc: *mut LavSurfaceDesc) -> i32 {
    if desc.is_null() {
        return ERR_BAD_ARGS;
    }
    catch_unwind(AssertUnwindSafe(|| {
        sessions().read_surface(handle, &mut *desc)
    }))
    .unwrap_or(ERR_IO)
}

/// Imports one retained surface plane into an existing OpenGL texture object.
///
/// The call must run on the render thread with the destination OpenGL context current. The
/// texture object must match the descriptor's `texture_target` (currently GL_TEXTURE_RECTANGLE
/// for macOS IOSurface).
#[no_mangle]
pub extern "C" fn dd_lav_bind_surface_plane_gl(
    surface_handle: i64,
    plane: u32,
    texture_id: u32,
) -> i32 {
    catch_unwind(AssertUnwindSafe(|| {
        sessions().bind_surface_plane_gl(surface_handle, plane, texture_id)
    }))
    .unwrap_or(ERR_IO)
}

/// Releases a surface returned by [`dd_lav_read_surface`]. Safe to call with 0 or stale handles.
#[no_mangle]
pub extern "C" fn dd_lav_release_surface(surface_handle: i64) {
    let _ = catch_unwind(AssertUnwindSafe(|| {
        sessions().release_surface(surface_handle)
    }));
}

/// Copies the session's last error description (UTF-8) into `dst`; returns bytes written
/// or a negative error code.
///
/// Safety: `dst` must point to `dst_len` writable bytes.
#[no_mangle]
pub unsafe extern "C" fn dd_lav_error(handle: i64, dst: *mut u8, dst_len: u64) -> i32 {
    if dst.is_null() {
        return ERR_BAD_ARGS;
    }
    let dst = std::slice::from_raw_parts_mut(dst, dst_len as usize);
    catch_unwind(AssertUnwindSafe(|| sessions().error(handle, dst))).unwrap_or(ERR_IO)
}

/// Interrupts the session's network/decode loop, unblocking any reader stuck in
/// [`dd_lav_read_frame_i420`]. The handle stays valid until [`dd_lav_close`].
#[no_mangle]
pub extern "C" fn dd_lav_kill(handle: i64) {
    let _ = catch_unwind(AssertUnwindSafe(|| sessions().kill(handle)));
}

/// Frees the session. Must not be called while another thread is inside
/// [`dd_lav_read_frame_i420`] for the same handle (join the reader thread first).
#[no_mangle]
pub extern "C" fn dd_lav_close(handle: i64) {
    let _ = catch_unwind(AssertUnwindSafe(|| sessions().close(handle)));
}
