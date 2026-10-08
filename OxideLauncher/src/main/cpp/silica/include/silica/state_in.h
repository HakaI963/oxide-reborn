#pragma once
// Silica GL state tracking (internal). Own implementation.
// Every function returns true when the call must reach the driver, false when
// it was proven redundant and skipped. Skips are counted, never silent in
// diagnostics mode.
typedef unsigned int silica_enum_t;
typedef unsigned int silica_uint_t;
typedef int silica_int_t;
namespace silica::state {
void set_dedup(bool on);
bool use_program(silica_uint_t p);
bool active_unit(silica_enum_t unit);
bool bind_texture(silica_uint_t unit, silica_enum_t target, silica_uint_t id);
bool bind_buffer(silica_enum_t target, silica_uint_t id);
bool bind_framebuffer(silica_enum_t target, silica_uint_t id);
bool bind_renderbuffer(silica_enum_t target, silica_uint_t id);
bool bind_vertex_array(silica_uint_t id);
bool cap(silica_enum_t cap, bool on);
bool viewport(silica_int_t x, silica_int_t y, silica_int_t w, silica_int_t h);
unsigned long long hits();
unsigned long long skips();
} // namespace silica::state
