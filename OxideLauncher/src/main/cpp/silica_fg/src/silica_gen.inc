// Part 5: generate+present, hook, JNI configuration.
static EGLDisplay g_eglD = EGL_NO_DISPLAY;

static bool run_flow_locked() {
  if (!pass_flow()) return false;
  if (!pass_median()) return false;
  unbind_images();
  return true;
}

bool postgenerate_locked(EGLDisplay d, EGLSurface s, int w, int h) {
  if (!g_enabled || g_have < 2) return false;  // warmup: nothing invented
  EGLContext cur = EGL_NO_CONTEXT;
  // current context via raw EGL is unavailable here (no egl procs in this
  // TU); the backend resolves its own display for queries below. Context
  // tracking piggybacks on successful GL calls instead.
  (void)cur;
  if (!ensure_gl_locked(d, w, h)) {
    // ensure() only fails hard on real errors (resolve/ES/FBO/shader);
    // not-ready cases return silently inside.
    if (g_enabled) note_fail_locked("setup");
    publish_locked();
    return false;
  }
  long long t0 = mono_ns();
  SavedState saved;
  save_state(saved);
  if (!run_flow_locked()) {
    restore_state(saved);
    unbind_images();
    note_fail_locked("flow");
    publish_locked();
    return false;
  }
  GLuint older = g_tex[g_turn];      // frame N-1
  GLuint newer = g_tex[g_turn ^ 1];  // frame N
  const int n = g_mult;
  bool any_fail = false;
  int presented = 0;
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
    EGLBoolean ok = EGL_FALSE;
    if (g_realSwap) ok = g_realSwap(d, s);
    if (ok == EGL_TRUE) {
      presented++;
    } else {
      any_fail = true;
      break;  // stop the chain: never present past a failure
    }
  }
  restore_state(saved);
  unbind_images();
  g_gl.ActiveTexture(GL_TEXTURE0);
  long long t1 = mono_ns();
  double ms = (double)(t1 - t0) / 1000000.0;
  g_genMs = g_genMs == 0.0 ? ms : g_genMs * 0.9 + ms * 0.1;
  if (presented > 0) {
    g_gen += (unsigned long long)presented;
    g_displayed += (unsigned long long)presented;
    g_confails = 0;
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
  return presented > 0;
}

// Set from JNI (explicit, no env dependence).
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

}  // namespace

// Exported present hook (C linkage, see header).
// NOTE: real_swap is stashed per-call (bridge passes it every swap).
static EGLBoolean (*g_bridgeSwap)(EGLDisplay, EGLSurface) = nullptr;

void silica_fg_set_real_swap(EGLBoolean (*fn)(EGLDisplay, EGLSurface)) {
  std::lock_guard<std::mutex> l(g_mu);
  g_realSwap = fn;
}

bool silica_fg_on_swap(bool swap_ok, int w, int h) {
  std::lock_guard<std::mutex> l(g_mu);
  long long now = mono_ns();
  if (!swap_ok) {
    g_drop++;
    return false;
  }
  g_native++;
  g_displayed++;
  g_lastSwapNs = now;
  if (w > 0) g_wq = w;
  if (h > 0) g_hq = h;
  publish_locked();
  return false;
}

}  // namespace silica_fg
