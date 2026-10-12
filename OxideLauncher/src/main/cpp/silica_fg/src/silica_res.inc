// Part 3: teardown, compile, ensure (resources per context+size).
void teardown_gl_locked() {
  if (!g_resolved) {
    g_ctx = EGL_NO_CONTEXT;
    g_turn = 0;
    g_have = 0;
    return;
  }
  drain_errors();
  if (g_vao) g_gl.DeleteVertexArrays(1, &g_vao);
  if (g_vbo) g_gl.DeleteBuffers(1, &g_vbo);
  GLuint progs[4] = {g_pBox, g_pFlow, g_pMed, g_pWarp};
  for (int i = 0; i < 4; ++i)
    if (progs[i]) g_gl.DeleteProgram(progs[i]);
  if (g_fbo) g_gl.DeleteFramebuffers(1, &g_fbo);
  if (g_samp) g_gl.DeleteSamplers(1, &g_samp);
  if (g_sampFlow) g_gl.DeleteSamplers(1, &g_sampFlow);
  GLuint texs[6] = {g_tex[0], g_tex[1], g_luma[0], g_luma[1], g_flow[0],
                    g_flow[1]};
  g_gl.DeleteTextures(6, texs);
  drain_errors();
  g_tex[0] = g_tex[1] = g_luma[0] = g_luma[1] = g_flow[0] = g_flow[1] = 0;
  g_fbo = g_vbo = g_vao = g_samp = g_sampFlow = 0;
  g_pBox = g_pFlow = g_pMed = g_pWarp = 0;
  g_ctx = EGL_NO_CONTEXT;
  g_turn = 0;
  g_have = 0;
}

bool compile_stage(GLuint sh, const char* src, const char* tag) {
  g_gl.ShaderSource(sh, 1, &src, nullptr);
  g_gl.CompileShader(sh);
  GLint ok = 0;
  g_gl.GetShaderiv(sh, GL_COMPILE_STATUS, &ok);
  if (ok) return true;
  char log[256] = {0};
  GLsizei n = 0;
  g_gl.GetShaderInfoLog(sh, (GLsizei)sizeof(log) - 1, &n, log);
  log[sizeof(log) - 1] = 0;
  char b[300];
  snprintf(b, sizeof(b), "shader compile (%s): %.200s", tag, log);
  g_reason = b;
  return false;
}

static bool link_check(GLuint prog, const char* tag) {
  GLint st = 0;
  g_gl.GetProgramiv(prog, GL_LINK_STATUS, &st);
  if (st) return true;
  char log[256] = {0};
  GLsizei n = 0;
  g_gl.GetProgramInfoLog(prog, (GLsizei)sizeof(log) - 1, &n, log);
  log[sizeof(log) - 1] = 0;
  char b[300];
  snprintf(b, sizeof(b), "program link (%s): %.200s", tag, log);
  g_reason = b;
  return false;
}

GLuint link_compute(const char* src, const char* tag) {
  GLuint sh = g_gl.CreateShader(GL_COMPUTE_SHADER);
  if (!compile_stage(sh, src, tag)) {
    g_gl.DeleteShader(sh);
    return 0;
  }
  GLuint prog = g_gl.CreateProgram();
  g_gl.AttachShader(prog, sh);
  g_gl.LinkProgram(prog);
  g_gl.DeleteShader(sh);
  if (!link_check(prog, tag)) {
    g_gl.DeleteProgram(prog);
    return 0;
  }
  return prog;
}

GLuint link_raster(const char* vsrc, const char* fsrc, const char* tag) {
  GLuint vs = g_gl.CreateShader(GL_VERTEX_SHADER);
  GLuint fs = g_gl.CreateShader(GL_FRAGMENT_SHADER);
  bool ok = compile_stage(vs, vsrc, tag) && compile_stage(fs, fsrc, tag);
  GLuint prog = 0;
  if (ok) {
    prog = g_gl.CreateProgram();
    g_gl.AttachShader(prog, vs);
    g_gl.AttachShader(prog, fs);
    g_gl.LinkProgram(prog);
    if (!link_check(prog, tag)) {
      g_gl.DeleteProgram(prog);
      prog = 0;
    }
  }
  g_gl.DeleteShader(vs);
  g_gl.DeleteShader(fs);
  return prog;
}

static void tex_rgba(GLuint t, int w, int h) {
  g_gl.BindTexture(GL_TEXTURE_2D, t);
  g_gl.TexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
  g_gl.TexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
  g_gl.TexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
  g_gl.TexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
  g_gl.TexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA,
                  GL_UNSIGNED_BYTE, nullptr);
}

static void tex_r8(GLuint t, int w, int h) {
  g_gl.BindTexture(GL_TEXTURE_2D, t);
  g_gl.TexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
  g_gl.TexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
  g_gl.TexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
  g_gl.TexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
  g_gl.TexImage2D(GL_TEXTURE_2D, 0, GL_R8, w, h, 0, GL_RED, GL_UNSIGNED_BYTE,
                  nullptr);
}

static void tex_rg16f(GLuint t, int w, int h) {
  g_gl.BindTexture(GL_TEXTURE_2D, t);
  g_gl.TexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
  g_gl.TexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
  g_gl.TexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
  g_gl.TexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
  g_gl.TexImage2D(GL_TEXTURE_2D, 0, GL_RG16F, w, h, 0, GL_RG, GL_HALF_FLOAT,
                  nullptr);
}

// Full rebuild for (context, size). Returns false on any error (reason set).
bool ensure_gl_locked(EGLContext cur, int w, int h) {
  if (g_ctx == cur && g_w == w && g_h == h && g_pWarp != 0) return true;
  teardown_gl_locked();
  if (!resolve_gl_locked()) return false;
  // GLES 3.1+ required for compute.
  const GLubyte* ver = g_gl.GetString(GL_VERSION);
  int major = 0, minor = 0;
  if (ver) sscanf((const char*)ver, "OpenGL ES %d.%d", &major, &minor);
  if (major < 3 || (major == 3 && minor < 1)) {
    g_reason = "GLES 3.1+ required for compute flow";
    return false;
  }
  g_ctx = cur;
  g_w = w;
  g_h = h;
  g_qw = (w + 3) / 4;
  g_qh = (h + 3) / 4;
  g_fw = (g_qw + 7) / 8;
  g_fh = (g_qh + 7) / 8;
  drain_errors();
  g_gl.GenTextures(2, g_tex);
  for (int i = 0; i < 2; ++i) tex_rgba(g_tex[i], w, h);
  g_gl.GenTextures(2, g_luma);
  for (int i = 0; i < 2; ++i) tex_r8(g_luma[i], g_qw, g_qh);
  g_gl.GenTextures(2, g_flow);
  for (int i = 0; i < 2; ++i) tex_rg16f(g_flow[i], g_fw, g_fh);
  g_gl.BindTexture(GL_TEXTURE_2D, 0);
  g_gl.GenFramebuffers(1, &g_fbo);
  g_gl.BindFramebuffer(GL_DRAW_FRAMEBUFFER, g_fbo);
  g_gl.FramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                            GL_TEXTURE_2D, g_tex[0], 0);
  bool fboOk = g_gl.CheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) ==
               GL_FRAMEBUFFER_COMPLETE;
  g_gl.BindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
  if (!fboOk || g_gl.GetError() != GL_NO_ERROR) {
    g_reason = "retention FBO incomplete";
    teardown_gl_locked();
    return false;
  }
  g_pBox = link_compute(kBox, "box");
  g_pFlow = link_compute(kFlow, "flow");
  g_pMed = link_compute(kMedian, "med");
  g_pWarp = link_raster(kWarpVert, kWarpFrag, "warp");
  if (!g_pBox || !g_pFlow || !g_pMed || !g_pWarp) {
    teardown_gl_locked();
    return false;
  }
  g_boxSize = g_gl.GetUniformLocation(g_pBox, "uDstSize");
  g_gl.GetUniformLocation(g_pBox, "uSrc");  // image units are fixed by layout
  g_flowSize = g_gl.GetUniformLocation(g_pFlow, "uSize");
  g_flowGrid = g_gl.GetUniformLocation(g_pFlow, "uFlowSize");
  g_medSize = g_gl.GetUniformLocation(g_pMed, "uSize");
  g_wPrev = g_gl.GetUniformLocation(g_pWarp, "uPrev");
  g_wCurr = g_gl.GetUniformLocation(g_pWarp, "uCurr");
  g_wFlow = g_gl.GetUniformLocation(g_pWarp, "uFlow");
  g_wPx = g_gl.GetUniformLocation(g_pWarp, "uPx2Uv");
  g_wT = g_gl.GetUniformLocation(g_pWarp, "uT");
  g_wGuard = g_gl.GetUniformLocation(g_pWarp, "uGuard");
  static const GLfloat tri[6] = {-1.f, -1.f, 3.f, -1.f, -1.f, 3.f};
  g_gl.GenBuffers(1, &g_vbo);
  g_gl.GenVertexArrays(1, &g_vao);
  g_gl.BindVertexArray(g_vao);
  g_gl.BindBuffer(GL_ARRAY_BUFFER, g_vbo);
  g_gl.BufferData(GL_ARRAY_BUFFER, sizeof(tri), tri, GL_STATIC_DRAW);
  g_gl.EnableVertexAttribArray(0);
  g_gl.VertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, nullptr);
  g_gl.BindVertexArray(0);
  g_gl.BindBuffer(GL_ARRAY_BUFFER, 0);
  g_gl.GenSamplers(1, &g_samp);
  g_gl.SamplerParameteri(g_samp, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
  g_gl.SamplerParameteri(g_samp, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
  g_gl.SamplerParameteri(g_samp, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
  g_gl.SamplerParameteri(g_samp, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
  g_gl.GenSamplers(1, &g_sampFlow);
  g_gl.SamplerParameteri(g_sampFlow, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
  g_gl.SamplerParameteri(g_sampFlow, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
  g_gl.SamplerParameteri(g_sampFlow, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
  g_gl.SamplerParameteri(g_sampFlow, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
  // Fixed sampler units for warp.
  g_gl.UseProgram(g_pWarp);
  if (g_wPrev >= 0) g_gl.Uniform1i(g_wPrev, 0);
  if (g_wCurr >= 0) g_gl.Uniform1i(g_wCurr, 1);
  if (g_wFlow >= 0) g_gl.Uniform1i(g_wFlow, 2);
  if (g_wPx >= 0)
    g_gl.Uniform2f(g_wPx, 4.0f / (float)w, 4.0f / (float)h);
  g_gl.UseProgram(0);
  if (g_gl.GetError() != GL_NO_ERROR) {
    g_reason = "resource setup GL error";
    teardown_gl_locked();
    return false;
  }
  if (!g_loggedReady) {
    g_loggedReady = true;
    char b[192];
    snprintf(b, sizeof(b), "silica-fg: engine ready (%dx%d, flow %dx%d, ES %s)",
             w, h, g_fw, g_fh, ver ? (const char*)ver : "?");
    diag(b);
  }
  return true;
}
