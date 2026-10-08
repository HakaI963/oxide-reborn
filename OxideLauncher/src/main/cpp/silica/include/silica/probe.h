#pragma once
// Silica probe cache (driver facts, not per-context facts). Own code.
namespace silica {
// Cache backend renderer/version strings observed while a context is current.
void note_probe(const char* renderer, const char* version);
const char* probe_renderer();
const char* probe_version();
} // namespace silica
