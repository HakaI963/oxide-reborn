// ONIGAMI state-cache regression test (host-runnable, real code under test).
//
// Pins the framebuffer/vertex-state coalescing contract against the defects
// seen as diagonal streaks, slivers and covering triangles on-device:
// ELEMENT_ARRAY_BUFFER backend state is VAO-scoped, so a global last-value
// cache cannot mirror it across VAO switches and must never dedupe it;
// TEXTURE_2D bindings are per-texture-unit; DRAW/READ framebuffer bindings
// are independent. Uses the real state_cache.cpp with a stub config.
#include "state_cache.h"
#include <cstdio>
namespace {
int g_fail = 0;
void check(bool cond, const char* name) {
    if (cond) printf("ok: %s\n", name);
    else { printf("FAIL: %s\n", name); g_fail++; }
}
} // namespace
int main() {
    using onigami::state_cache;
    // 1. ARRAY_BUFFER is context-global: dedup is correct here.
    check(!state_cache().check_bind_buffer(GL_ARRAY_BUFFER, 1), "array bind forwards");
    check(state_cache().check_bind_buffer(GL_ARRAY_BUFFER, 1), "array rebind deduped");
    check(!state_cache().check_bind_buffer(GL_ARRAY_BUFFER, 2), "array new id forwards");
    // 2. ELEMENT_ARRAY_BUFFER must NEVER dedupe (VAO-scoped backend state).
    check(!state_cache().check_bind_buffer(GL_ELEMENT_ARRAY_BUFFER, 5), "index bind forwards");
    check(!state_cache().check_bind_buffer(GL_ELEMENT_ARRAY_BUFFER, 5), "index rebind STILL forwards");
    check(!state_cache().check_bind_vertex_array(7), "vao switch forwards");
    check(!state_cache().check_bind_buffer(GL_ELEMENT_ARRAY_BUFFER, 5), "index bind after VAO switch forwards");
    check(!state_cache().check_bind_vertex_array(8), "second vao switch forwards");
    check(!state_cache().check_bind_buffer(GL_ELEMENT_ARRAY_BUFFER, 5), "index bind after second VAO switch forwards");
    // 3. VAO binds dedupe (context-global binding).
    check(!state_cache().check_bind_vertex_array(3), "vao bind forwards");
    check(state_cache().check_bind_vertex_array(3), "vao rebind deduped");
    check(state_cache().bound_vertex_array() == 3, "vao getter tracks");
    // 4. DRAW/READ framebuffer bindings are independent.
    check(!state_cache().check_bind_framebuffer(GL_DRAW_FRAMEBUFFER, 5), "draw fbo forwards");
    check(!state_cache().check_bind_framebuffer(GL_READ_FRAMEBUFFER, 5), "same id on READ still forwards");
    check(state_cache().check_bind_framebuffer(GL_DRAW_FRAMEBUFFER, 5), "draw rebind deduped");
    check(state_cache().check_bind_framebuffer(GL_READ_FRAMEBUFFER, 5), "read rebind deduped");
    check(!state_cache().check_bind_framebuffer(GL_READ_FRAMEBUFFER, 0), "read default forwards");
    check(state_cache().bound_framebuffer() == 5, "draw binding getter tracks");
    // 5. TEXTURE_2D is per-unit: same id on two units both forward.
    onigami::state_cache().note_active_unit(GL_TEXTURE0 + 0);
    check(!state_cache().check_bind_texture(GL_TEXTURE_2D, 10), "tex bind unit0 forwards");
    check(state_cache().check_bind_texture(GL_TEXTURE_2D, 10), "tex rebind unit0 deduped");
    onigami::state_cache().note_active_unit(GL_TEXTURE0 + 1);
    check(!state_cache().check_bind_texture(GL_TEXTURE_2D, 10), "same tex on unit1 STILL forwards");
    check(state_cache().check_bind_texture(GL_TEXTURE_2D, 10), "tex rebind unit1 deduped");
    onigami::state_cache().note_active_unit(GL_TEXTURE0 + 0);
    check(state_cache().check_bind_texture(GL_TEXTURE_2D, 10), "unit0 cache intact");
    // 6. Invalidate drops everything (recycled names after deletes).
    state_cache().invalidate();
    check(state_cache().bound_framebuffer() == 0, "fbo getter reset");
    check(state_cache().bound_vertex_array() == 0, "vao getter reset");
check(!state_cache().check_bind_buffer(GL_ARRAY_BUFFER, 2), "array forwards after invalidate");
    check(!state_cache().check_bind_vertex_array(3), "vao forwards after invalidate");
    check(!state_cache().check_bind_framebuffer(GL_DRAW_FRAMEBUFFER, 5), "fbo forwards after invalidate");
    check(!state_cache().check_bind_texture(GL_TEXTURE_2D, 10), "tex forwards after invalidate");
    if (g_fail == 0) { printf("ALL STATE-CACHE TESTS PASSED\n"); return 0; }
    printf("%d TEST(S) FAILED\n", g_fail);
    return 1;
}
