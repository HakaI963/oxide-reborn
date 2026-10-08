package dev.oxide.launcher.game.renderer.silica

/**
 * Oxide <-> Silica pipeline contracts.
 *
 * Silica is optimized around the Oxide Launcher environment instead of every
 * launcher generically. These interfaces are the seam: the launcher owns
 * process/lifecycle/surface/scheduling, Silica owns GL translation policy.
 * Phase 1 provides the Kotlin contracts + lifecycle/EGL/scheduling/cache
 * implementation; framebuffer/program/texture/sync/memory/resolution/pacing
 * stages are specified here and filled incrementally with measurements.
 *
 * Iris shaderpacks keep working because Silica stays OpenGL/OpenGL-ES based;
 * no Vulkan-only path. Compute shaders are not assumed faster (they add
 * overhead); every stage must prove lower frame time or lower variance.
 */
interface SilicaGameLifecycle {
    fun onProcessStart()
    fun onSurfaceAvailable()
    fun onSurfaceLost()
    fun onProcessEnd()
}

interface SilicaEglContext {
    /** Create backend context; on failure LOG the backend error (ported fcdf914: never silent). */
    fun createContext(display: Any?, attributes: Map<String, Int>): Result<Any?>
    fun makeCurrent(display: Any?, draw: Any?, read: Any?, context: Any?): Boolean
    fun lastErrorRearmed(): Int
}

interface SilicaSwapchain {
    fun onSurfaceChanged(width: Int, height: Int)
    fun beginFrame(): Boolean
    fun endFrame(): Boolean
}

interface SilicaRenderScheduler {
    fun submit(draw: () -> Unit)
    fun flush()
}

/** Resource lifetime: explicit acquire/release, no leaks across surface loss. */
interface SilicaResources {
    fun acquireFramebuffer(id: Int)
    fun releaseFramebuffer(id: Int)
    fun acquireProgram(id: Int)
    fun releaseProgram(id: Int)
    fun acquireTexture(id: Int)
    fun releaseTexture(id: Int)
    fun onContextLost()
}

/** Memory/cache: bounded shader/program binary cache resembling GLSL cache policy. */
interface SilicaMemoryCache {
    fun maxBytes(): Long
    fun evictIfNeeded()
    fun clear()
}

/** Resolution/upscaling: render below native + spatial upscale only when it reduces cost. */
interface SilicaResolution {
    /** 1.0 = native. <1.0 must reduce total GPU cost or stay off. */
    fun renderScale(): Float
    fun upscaleEnabled(): Boolean
}

/** Frame pacing: smooths presentation; never fakes FPS. */
interface SilicaFramePacer {
    fun targetFrameTimeMs(): Float?
}

/** Frame generation: capability-driven, optional, off unless measured-positive. */
interface SilicaFrameGeneration {
    fun supported(): Boolean
    fun reason(): String
    fun enabled(): Boolean
}

/** Runtime controls: live-applied vs restart-required is explicit per key. */
enum class SilicaApplyPolicy { LIVE, RESTART_REQUIRED }

interface SilicaControls {
    fun policy(key: String): SilicaApplyPolicy
}
