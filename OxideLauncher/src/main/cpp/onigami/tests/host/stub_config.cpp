// Host-test stub: deterministic coalescing-ON config for state_cache tests.
#include "onigami/config.h"
namespace onigami {
const Config& current_config() {
    static Config c;
    c.state_coalescing = true;
    return c;
}
} // namespace onigami
