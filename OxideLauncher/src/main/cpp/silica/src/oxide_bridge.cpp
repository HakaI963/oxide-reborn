// Oxide <-> Silica lifecycle/mode stubs. Real stage wiring lands incrementally.
#include "silica/oxide_bridge.h"
#include <stddef.h>
extern "C" {
int silica_on_process_start(const char* data_dir) { (void)data_dir; return 0; }
int silica_on_surface(const SilicaSurface* s) { (void)s; return 0; }
int silica_on_surface_lost(void) { return 0; }
int silica_on_process_end(void) { return 0; }
int silica_set_mode(int mode, int cache_mb, int upscale) { (void)mode; (void)cache_mb; (void)upscale; return 0; }
}
