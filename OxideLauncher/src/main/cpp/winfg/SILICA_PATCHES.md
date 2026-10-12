# Silica patches to win-fg (upstream v0.3.0 @ fbc69ed6)

Upstream tree lives in `upstream/` verbatim (MIT, copyright The412Banner +
AMD FidelityFX notice retained) except the marked hunks below. Each hunk is
tagged `SILICA patch` in the source.

## P1 - FPS stats file (`upstream/src/layer.cpp`)

Upstream exposes no counters API (logcat only). Silica needs measured
native/displayed FPS for the in-game meter: counters at present entry
(native, exactly one real present per entry) and at the generated-present
site (gen/drop), plus a throttled `$WIN_FG_STATS_DIR/winfg_stats.json`
writer (tmp+rename, best-effort, never touches the present path).
Schema: enabled/multiplier/native_frames/displayed_frames(=native+gen)/
generated_frames/dropped/reason. Displayed is measured, never estimated.

## P2 - none yet

No logic changes to flow/synthesis/present/pacing/capture.
