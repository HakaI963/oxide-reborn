#pragma once
// ONIGAMI persistent program vault. Own design: FNV-1a64 keys, byte budget,
// oldest-first eviction. Failures are silent fallbacks (recompile path).
#include <stddef.h>
#include <string>
#include <vector>
namespace onigami {
std::string vault_key_hex(const char* a, const char* b);
bool vault_lookup(const std::string& key_hex, std::vector<char>& out);
void vault_store(const std::string& key_hex, const void* data, size_t len);
} // namespace onigami
