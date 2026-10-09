#pragma once
// ONIGAMI $ONIGAMI_DATA_DIR/onigami.json schema. Own schema, version 1.
#include <string>
namespace onigami {
struct Config {
  int onigami_version = 1;
  std::string profile = "compat";
  int program_vault_mb = 64;
  bool state_coalescing = true;
  bool diagnostics = true;
};
Config load_config();
const Config& current_config();
void apply_config(const Config& c);
std::string config_path();
} // namespace onigami
