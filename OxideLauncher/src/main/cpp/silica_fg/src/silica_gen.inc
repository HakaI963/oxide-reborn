// Part 5: generate+present, hook, configure, JNI.
//
// Flow per bridge swap (all under g_mu):
//   precapture: blit outgoing frame -> texRGBA[turn] (+ quarter luma).
//   real swap (bridge's backend pointer).
//   on_swap: count native/displayed.
//   postgenerate: ensure targets+programs, flow+median, warp x(mult-1) with
//     backend swaps, count generated only on success.
static bool pass_box(GLuint srcTex, GLuint dstTex);
static bool pass_flow();
static bool pass_median();

static double g_nativeDtMs = 0.0;
static long long g_lastWatchNs = 0;
static long long g_cfgMtime = 0;
static bool g_dirDiscovered = false;

static void discover_dir_locked() {
  if (g_dirDiscovered || !g_dataDir.empty()) return;
  g_dirDiscovered = true;
  char cmd[256] = {0};
  FILE* f = fopen("/proc/self/cmdline", "r");
  if (!f) return;
  size_t n = fread(cmd, 1, sizeof(cmd) - 1, f);
  fclose(f);
  if (n == 0) return;
  std::string proc(cmd);
  size_t colon = proc.find(':');
  std::string pkg = (colon == std::string::npos) ? proc : proc.substr(0, colon);
  if (pkg.empty()) return;
  std::string cand = "/data/user/0/" + pkg + "/files/silica";
  struct stat st;
  std::string p1 = cand + "/conf.toml";
  std::string p2 = cand + "/onigami.json";
  if (stat(p1.c_str(), &st) != 0 && stat(p2.c_str(), &st) != 0) return;
  g_dataDir = cand;
  char b[300];
  snprintf(b, sizeof(b), "silica-fg: data dir discovered: %s", cand.c_str());
  diag(b);
}

static void apply_cfg_values_locked(bool enabled, int mult, bool guard,
                                    const char* why) {
  bool want = enabled && mult >= 2 && mult <= 4;
  bool changed = (want != g_enabled) || (want && mult != g_mult) ||
                 (want && guard != g_guard);
  if (changed) {
    teardown_gl_locked();
    g_confails = 0;
    g_loggedReady = false;
    g_loggedGen = false;
  }
  g_enabled = want;
  g_mult = mult < 2 ? 2 : (mult > 4 ? 4 : mult);
  g_guard = guard;
  g_configured = true;
  g_reason = want ? "active" : "disabled by configuration";
  if (changed) {
    char b[256];
    snprintf(b, sizeof(b), "silica-fg: config %s enabled=%d mult=%dx guard=%d",
             why, g_enabled ? 1 : 0, g_mult, g_guard ? 1 : 0);
    diag(b);
  }
}

static void write_cfg_locked() {
  if (g_dataDir.empty()) return;
  std::string tmp = g_dataDir + "/fg_engine.cfg.tmp";
  std::string dst = g_dataDir + "/fg_engine.cfg";
  FILE* f = fopen(tmp.c_str(), "w");
  if (!f) return;
  fprintf(f, "enabled=%d\nmultiplier=%d\nguard=%d\n", g_enabled ? 1 : 0,
          g_mult, g_guard ? 1 : 0);
  fclose(f);
  rename(tmp.c_str(), dst.c_str());
}

static void watch_cfg_locked() {
  long long now = mono_ns();
  if (now - g_lastWatchNs < 1000000000LL) return;
  g_lastWatchNs = now;
  if (g_dataDir.empty()) return;
  std::string p = g_dataDir + "/fg_engine.cfg";
  struct stat st;
  if (stat(p.c_str(), &st) != 0) return;
  if ((long long)st.st_mtime == g_cfgMtime) return;
  g_cfgMtime = (long long)st.st_mtime;
  bool en = false;
  int mult = 2;
  bool guard = true;
  if (FILE* f = fopen(p.c_str(), "r")) {
    char line[128];
    while (fgets(line, sizeof(line), f)) {
      int v = 0;
      if (sscanf(line, "enabled=%d", &v) == 1) en = (v != 0);
      else if (sscanf(line, "multiplier=%d", &v) == 1) mult = v;
      else if (sscanf(line, "guard=%d", &v) == 1) guard = (v != 0);
    }
    fclose(f);
  }
  apply_cfg_values_locked(en, mult, guard, "live file");
  publish_locked();
}

static bool run_flow_locked() {
  if (!pass_flow()) return false;
  if (!pass_median()) return false;
  unbind_images();
  return true;
}

bool postgenerate_locked(EGLDisplay d, EGLSurface s, int w, int h,
                         EGLBoolean (*real_swap)(EGLDisplay, EGLSurface)) {
  if (!g_enabled || g_have < 2) return false;  // warmup: nothing invented
  if (!ensure_gl_locked(w, h)) {
    // ensure fails hard only on real errors; not-ready returns silently.
    // Count a failure only when FG was mid-flight (targets existed before).
    publish_locked();
    return false;
  }
  if (g_genMs > 0.0 && g_nativeDtMs > 0.0 && g_genMs > 0.8 * g_nativeDtMs) {
    g_reason = "yielding (gpu-bound)";
    publish_locked();
    return false;
  }
  long long t0 = mono_ns();
  SavedState saved;
  save_state(saved);
  bool flowOk = run_flow_locked();
  bool presented_any = false;
  bool any_fail = !flowOk;
  int presented = 0;
  const int n = g_mult;
  if (flowOk) {
    GLuint older = g_tex[g_turn];      // frame N-1 (turn flipped at capture)
    GLuint newer = g_tex[g_turn ^ 1];  // frame N
    g_gl.BindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
    g_gl.Viewport(0, 0, g_w, g_h);
    g_gl.Disable(GL_BLEND);
    g_gl.Disable(GL_SCISSOR_TEST);
    g_gl.Disable(GL_DEPTH_TEST);
    g_gl.Disable(GL_STENCIL_TEST);
    g_gl.Disable(GL_CULL_FACE);
    g_gl.UseProgram(g_pWarp);
    g_gl.ActiveTexture(GL_TEXTURE0);
    g_gl.BindTexture(GL_TEXTURE_2D, older);
    g_gl.BindSampler(0, g_samp);
    g_gl.ActiveTexture(GL_TEXTURE1);
    g_gl.BindTexture(GL_TEXTURE_2D, newer);
    g_gl.BindSampler(1, g_samp);
    g_gl.ActiveTexture(GL_TEXTURE2);
    g_gl.BindTexture(GL_TEXTURE_2D, g_flow[1]);
    g_gl.BindSampler(2, g_sampFlow);
    if (g_wGuard >= 0) g_gl.Uniform1f(g_wGuard, g_guard ? 1.0f : 0.0f);
    g_gl.BindVertexArray(g_vao);
    for (int k = 1; k < n; ++k) {
      if (g_wT >= 0) g_gl.Uniform1f(g_wT, (float)k / (float)n);
      g_gl.DrawArrays(GL_TRIANGLES, 0, 3);
      if (g_gl.GetError() != GL_NO_ERROR) {
        any_fail = true;
        break;
      }
      if (g_nativeDtMs > 0.0) {
        double elapsed = (double)(mono_ns() - g_lastSwapNs) / 1000000.0;
        if (elapsed > 0.75 * g_nativeDtMs) break;
      }
      EGLBoolean ok = real_swap ? real_swap(d, s) : EGL_FALSE;
      if (ok == EGL_TRUE) {
        presented++;
      } else {
        any_fail = true;
        break;  // stop the chain: never present past a failure
      }
    }
  }
  restore_state(saved);
  unbind_images();
  long long t1 = mono_ns();
  double ms = (double)(t1 - t0) / 1000000.0;
  g_genMs = g_genMs == 0.0 ? ms : g_genMs * 0.9 + ms * 0.1;
  if (presented > 0) {
    g_reason = "active";
    g_gen += (unsigned long long)presented;
    g_displayed += (unsigned long long)presented;
    g_confails = 0;
    presented_any = true;
    if (!g_loggedGen) {
      g_loggedGen = true;
      char b[192];
      snprintf(b, sizeof(b), "silica-fg: generated frames live (%dx, +%d/frame)",
               n, n - 1);
      diag(b);
    }
  }
  if (any_fail) {
    g_drop++;
    note_fail_locked("present");
  }
  publish_locked();
  return presented_any;
}

void on_swap_locked(bool swap_ok, int w, int h) {
  long long now = mono_ns();
  if (!swap_ok) {
    g_drop++;
    publish_locked();
    return;
  }
  if (g_lastSwapNs != 0) {
    double dt = (double)(now - g_lastSwapNs) / 1000000.0;
    if (dt > 0.0 && dt < 1000.0)
      g_nativeDtMs = (g_nativeDtMs == 0.0) ? dt : g_nativeDtMs * 0.9 + dt * 0.1;
  }
  g_native++;
  g_displayed++;
  g_lastSwapNs = now;
  (void)w;
  (void)h;
  publish_locked();
}

// Explicit configuration from the game process over JNI. No env dependence.
void configure_locked(const char* dataDir, bool enabled, int mult, bool guard) {
  if (dataDir) g_dataDir = dataDir;
  bool want = enabled && mult >= 2 && mult <= 4;
  if (want != g_enabled || (want && mult != g_mult) ||
      (want && guard != g_guard)) {
    teardown_gl_locked();
    g_confails = 0;
    g_loggedReady = false;
    g_loggedGen = false;
  }
  g_enabled = want;
  g_mult = mult < 2 ? 2 : (mult > 4 ? 4 : mult);
  g_guard = guard;
  g_configured = true;
  g_reason = want ? "active" : "disabled by configuration";
  char b[256];
  snprintf(b, sizeof(b), "silica-fg: configure dir=%s enabled=%d mult=%dx guard=%d",
           g_dataDir.c_str(), g_enabled ? 1 : 0, g_mult, g_guard ? 1 : 0);
  diag(b);
  publish_locked();
}


// --- exported hook + JNI (C linkage) ---
extern "C" {

__attribute__((visibility("default"))) EGLBoolean silica_fg_present_hook(
    EGLDisplay d, EGLSurface s,
    EGLBoolean (*real_swap)(EGLDisplay, EGLSurface)) {
  int w = 0, h = 0;
  {
    std::lock_guard<std::mutex> l(g_mu);
    if (g_resolved && g_gl.QuerySurface) {
      EGLint ew = 0, eh = 0;
      if (g_gl.QuerySurface(d, s, EGL_WIDTH, &ew)) w = (int)ew;
      if (g_gl.QuerySurface(d, s, EGL_HEIGHT, &eh)) h = (int)eh;
    }
  }
  {
    std::lock_guard<std::mutex> l(g_mu);
    precapture_locked(d, w, h);
  }
  if (!real_swap) return EGL_FALSE;
  EGLBoolean ok = real_swap(d, s);
  bool gen = false;
  {
    std::lock_guard<std::mutex> l(g_mu);
    on_swap_locked(ok == EGL_TRUE, w, h);
    if (ok == EGL_TRUE) gen = postgenerate_locked(d, s, w, h, real_swap);
  }
  (void)gen;
  return ok;
}

__attribute__((visibility("default"))) jboolean
Java_dev_oxide_launcher_bridge_SilicaFgBridge_nativeConfigure(
    JNIEnv* env, jclass, jstring dataDir, jboolean enabled, jint mult,
    jboolean guard) {
  if (!env || !dataDir) return JNI_FALSE;
  const char* c = env->GetStringUTFChars(dataDir, nullptr);
  if (!c) return JNI_FALSE;
  {
    std::lock_guard<std::mutex> l(g_mu);
    configure_locked(c, enabled, mult, guard);
  }
  env->ReleaseStringUTFChars(dataDir, c);
  return JNI_TRUE;
}

__attribute__((visibility("default"))) jboolean
Java_dev_oxide_launcher_bridge_SilicaFgBridge_nativeReloadTranslation(
    JNIEnv* env, jclass, jstring dataDir) {
  if (!env || !dataDir) return JNI_FALSE;
  const char* c = env->GetStringUTFChars(dataDir, nullptr);
  if (!c) return JNI_FALSE;
  std::string dir(c);
  env->ReleaseStringUTFChars(dataDir, c);
  // libonigami exposes onigami_on_process_start(data_dir): reloads its
  // onigami.json config. Reuse it explicitly (the launcher never calls it).
  void* h = dlopen("libonigami.so", RTLD_LAZY | RTLD_LOCAL);
  if (!h) return JNI_FALSE;
  typedef int (*start_fn)(const char*);
  start_fn fn = reinterpret_cast<start_fn>(dlsym(h, "onigami_on_process_start"));
  if (!fn) return JNI_FALSE;
  fn(dir.c_str());
  return JNI_TRUE;
}

}  // extern "C"
}  // anonymous namespace
}  // namespace silica_fg
