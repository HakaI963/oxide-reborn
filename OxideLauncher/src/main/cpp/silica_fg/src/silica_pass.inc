// Part 4: compute passes, present path, stats, hook, JNI.
static void unbind_images() {
  for (GLuint u = 0; u < 8; ++u)
    g_gl.BindImageTexture(u, 0, 0, GL_FALSE, 0, GL_READ_ONLY, GL_RGBA8);
}

static bool pass_box(GLuint srcTex, GLuint dstTex) {
  g_gl.BindImageTexture(0, srcTex, 0, GL_FALSE, 0, GL_READ_ONLY, GL_RGBA8);
  g_gl.BindImageTexture(3, dstTex, 0, GL_FALSE, 0, GL_WRITE_ONLY, GL_R8);
  g_gl.UseProgram(g_pBox);
  if (g_boxSize >= 0) g_gl.Uniform2i(g_boxSize, g_qw, g_qh);
  g_gl.DispatchCompute((GLuint)((g_qw + 7) / 8), (GLuint)((g_qh + 7) / 8), 1);
  g_gl.MemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
  return g_gl.GetError() == GL_NO_ERROR;
}

static bool pass_flow() {
  // prev = Q[turn^1] (older), curr = Q[turn] (just captured)
  g_gl.BindImageTexture(2, g_luma[g_turn ^ 1], 0, GL_FALSE, 0, GL_READ_ONLY,
                        GL_R8);
  g_gl.BindImageTexture(3, g_luma[g_turn], 0, GL_FALSE, 0, GL_READ_ONLY, GL_R8);
  g_gl.BindImageTexture(4, g_flow[0], 0, GL_FALSE, 0, GL_WRITE_ONLY, GL_RG16F);
  g_gl.UseProgram(g_pFlow);
  if (g_flowSize >= 0) g_gl.Uniform2i(g_flowSize, g_qw, g_qh);
  if (g_flowGrid >= 0) g_gl.Uniform2i(g_flowGrid, g_fw, g_fh);
  g_gl.DispatchCompute((GLuint)((g_fw + 7) / 8), (GLuint)((g_fh + 7) / 8), 1);
  g_gl.MemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
  return g_gl.GetError() == GL_NO_ERROR;
}

static bool pass_median() {
  g_gl.BindImageTexture(4, g_flow[0], 0, GL_FALSE, 0, GL_READ_ONLY, GL_RG16F);
  g_gl.BindImageTexture(5, g_flow[1], 0, GL_FALSE, 0, GL_WRITE_ONLY, GL_RG16F);
  g_gl.UseProgram(g_pMed);
  if (g_medSize >= 0) g_gl.Uniform2i(g_medSize, g_fw, g_fh);
  g_gl.DispatchCompute((GLuint)((g_fw + 7) / 8), (GLuint)((g_fh + 7) / 8), 1);
  g_gl.MemoryBarrier(
      (GLbitfield)(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT |
                   GL_TEXTURE_FETCH_BARRIER_BIT));
  return g_gl.GetError() == GL_NO_ERROR;
}

struct SavedState {
  GLint prog = 0, vao = 0, vbo = 0, active = GL_TEXTURE0;
  GLint vp[4] = {0, 0, 0, 0};
  GLint rfbo = 0, dfbo = 0;
  GLint tex[3] = {0, 0, 0};
  GLint samp[3] = {0, 0, 0};
  GLboolean en[5] = {GL_FALSE, GL_FALSE, GL_FALSE, GL_FALSE, GL_FALSE};
  GLint imgName[8] = {0, 0, 0, 0, 0, 0, 0, 0};
  GLint imgLevel[8] = {0, 0, 0, 0, 0, 0, 0, 0};
  GLint imgLayered[8] = {0, 0, 0, 0, 0, 0, 0, 0};
  GLint imgLayer[8] = {0, 0, 0, 0, 0, 0, 0, 0};
  GLint imgAccess[8] = {0, 0, 0, 0, 0, 0, 0, 0};
  GLint imgFormat[8] = {0, 0, 0, 0, 0, 0, 0, 0};
};

static void save_state(SavedState& s) {
  g_gl.GetIntegerv(GL_CURRENT_PROGRAM, &s.prog);
  g_gl.GetIntegerv(GL_VERTEX_ARRAY_BINDING, &s.vao);
  g_gl.GetIntegerv(GL_ARRAY_BUFFER_BINDING, &s.vbo);
  g_gl.GetIntegerv(GL_ACTIVE_TEXTURE, &s.active);
  g_gl.GetIntegerv(GL_VIEWPORT, s.vp);
  g_gl.GetIntegerv(GL_READ_FRAMEBUFFER_BINDING, &s.rfbo);
  g_gl.GetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, &s.dfbo);
  for (int i = 0; i < 3; ++i) {
    g_gl.ActiveTexture((GLenum)(GL_TEXTURE0 + i));
    g_gl.GetIntegerv(GL_TEXTURE_BINDING_2D, &s.tex[i]);
    g_gl.GetIntegerv(GL_SAMPLER_BINDING, &s.samp[i]);
  }
  s.en[0] = g_gl.IsEnabled(GL_BLEND);
  s.en[1] = g_gl.IsEnabled(GL_SCISSOR_TEST);
  s.en[2] = g_gl.IsEnabled(GL_DEPTH_TEST);
  s.en[3] = g_gl.IsEnabled(GL_STENCIL_TEST);
  s.en[4] = g_gl.IsEnabled(GL_CULL_FACE);
  for (int i = 0; i < 8; ++i) {
    g_gl.GetIntegerv((GLenum)(GL_IMAGE_BINDING_NAME + i), &s.imgName[i]);
    g_gl.GetIntegerv((GLenum)(GL_IMAGE_BINDING_LEVEL + i), &s.imgLevel[i]);
    g_gl.GetIntegerv((GLenum)(GL_IMAGE_BINDING_LAYERED + i), &s.imgLayered[i]);
    g_gl.GetIntegerv((GLenum)(GL_IMAGE_BINDING_LAYER + i), &s.imgLayer[i]);
    g_gl.GetIntegerv((GLenum)(GL_IMAGE_BINDING_ACCESS + i), &s.imgAccess[i]);
    g_gl.GetIntegerv((GLenum)(GL_IMAGE_BINDING_FORMAT + i), &s.imgFormat[i]);
  }
  drain_errors();  // game may have left stale errors; ours start clean
}

static void restore_state(const SavedState& s) {
  for (int i = 0; i < 3; ++i) {
    g_gl.ActiveTexture((GLenum)(GL_TEXTURE0 + i));
    g_gl.BindTexture(GL_TEXTURE_2D, (GLuint)s.tex[i]);
    g_gl.BindSampler((GLuint)i, (GLuint)s.samp[i]);
  }
  g_gl.ActiveTexture((GLenum)s.active);
  g_gl.BindVertexArray((GLuint)s.vao);
  g_gl.BindBuffer(GL_ARRAY_BUFFER, (GLuint)s.vbo);
  g_gl.UseProgram((GLuint)s.prog);
  g_gl.BindFramebuffer(GL_READ_FRAMEBUFFER, (GLuint)s.rfbo);
  g_gl.BindFramebuffer(GL_DRAW_FRAMEBUFFER, (GLuint)s.dfbo);
  g_gl.Viewport(s.vp[0], s.vp[1], s.vp[2], s.vp[3]);
  if (s.en[0]) g_gl.Enable(GL_BLEND);
  if (s.en[1]) g_gl.Enable(GL_SCISSOR_TEST);
  if (s.en[2]) g_gl.Enable(GL_DEPTH_TEST);
  if (s.en[3]) g_gl.Enable(GL_STENCIL_TEST);
  if (s.en[4]) g_gl.Enable(GL_CULL_FACE);
  for (int i = 0; i < 8; ++i) {
    g_gl.BindImageTexture(
        (GLuint)i, (GLuint)s.imgName[i], s.imgLevel[i],
        (GLboolean)s.imgLayered[i], s.imgLayer[i], (GLenum)s.imgAccess[i],
        (GLenum)(s.imgFormat[i] ? s.imgFormat[i] : GL_RGBA8));
  }
}

// Retain outgoing frame (pre-swap). Fast path only: blit. Never blocks on
// shader builds; if targets are not ready the frame is skipped silently.
void precapture_locked(EGLDisplay d, int w, int h) {
  (void)d;
  if (!g_enabled) return;
  if (!g_resolved && !resolve_gl_locked()) return;  // backend down: skip
  const GLubyte* ver = g_gl.GetString(GL_VERSION);
  if (!ver) return;  // no current context yet: skip silently
  int major = 0, minor = 0;
  sscanf((const char*)ver, "OpenGL ES %d.%d", &major, &minor);
  if (major < 3 || (major == 3 && minor < 1)) {
    if (g_enabled) {
      g_enabled = false;
      g_reason = "GLES 3.1+ required for compute flow";
      diag("silica-fg: DISABLED: GLES 3.1+ required for compute flow");
    }
    return;
  }
  if (w <= 0 || h <= 0) return;
  if (g_ctx == EGL_NO_CONTEXT || g_w != w || g_h != h || g_fbo == 0 ||
      g_pBox == 0) {
    return;  // targets/programs not built yet: postgenerate() builds them
  }
  EGLContext cur = EGL_NO_CONTEXT;
  // context identity is rechecked in postgenerate; blit needs current ctx
  drain_errors();
  GLint saveR = 0, saveD = 0;
  g_gl.GetIntegerv(GL_READ_FRAMEBUFFER_BINDING, &saveR);
  g_gl.GetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, &saveD);
  (void)cur;
  g_gl.BindFramebuffer(GL_READ_FRAMEBUFFER, 0);
  g_gl.BindFramebuffer(GL_DRAW_FRAMEBUFFER, g_fbo);
  g_gl.FramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                            GL_TEXTURE_2D, g_tex[g_turn], 0);
  g_gl.BlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL_COLOR_BUFFER_BIT,
                       GL_NEAREST);
  bool ok = g_gl.GetError() == GL_NO_ERROR;
  g_gl.BindFramebuffer(GL_READ_FRAMEBUFFER, (GLuint)saveR);
  g_gl.BindFramebuffer(GL_DRAW_FRAMEBUFFER, (GLuint)saveD);
  if (!ok) {
    note_fail_locked("retain-blit");
    publish_locked();
    return;
  }
  // quarter luma of the just-retained frame (compute, still pre-swap: cheap)
  g_gl.BindImageTexture(0, g_tex[g_turn], 0, GL_FALSE, 0, GL_READ_ONLY,
                        GL_RGBA8);
  g_gl.BindImageTexture(3, g_luma[g_turn], 0, GL_FALSE, 0, GL_WRITE_ONLY,
                        GL_R8);
  g_gl.UseProgram(g_pBox);
  if (g_boxSize >= 0) g_gl.Uniform2i(g_boxSize, g_qw, g_qh);
  g_gl.DispatchCompute((GLuint)((g_qw + 7) / 8), (GLuint)((g_qh + 7) / 8), 1);
  g_gl.MemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
  ok = g_gl.GetError() == GL_NO_ERROR;
  g_gl.UseProgram(0);
  unbind_images();
  if (!ok) {
    note_fail_locked("retain-luma");
    publish_locked();
    return;
  }
  g_turn ^= 1;
  if (g_have < 2) g_have++;
}
