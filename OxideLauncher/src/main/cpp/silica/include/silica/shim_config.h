#pragma once
// Silica shim configuration, read from $SILICA_DATA_DIR/silica.json.
// Own schema (silica_version/profile/program_vault_mb/state_coalescing/
// diagnostics). Parsed once per process; missing file means safe defaults.
namespace silica::config {
void load_once();
int program_vault_mb();   // 0 disables the program cache
bool dedup_enabled();     // state coalescing toggle
bool diagnostics();       // verbose logging toggle
const char* profile();    // AUTO/BALANCED/PERFORMANCE/QUALITY
} // namespace silica::config
