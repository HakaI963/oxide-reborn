#pragma once
// ONIGAMI $ONIGAMI_DATA_DIR/onigami.json schema. Own schema, version 1.
#include <string>
namespace onigami {
struct Config {
  int onigami_version = 1;
  std::string profile = "compat";
  int program_vault_mb = 0;
  bool state_coalescing = false;
  bool diagnostics = true;
  int swap_interval = 1;
  float render_scale = 1.0f;
  int upscale_mode = 0;

};
Config load_config();
const Config& current_config();
void apply_config(const Config& c);
std::string config_path();
} // namespace onigami
