#include "libsm64.h"
#include <stddef.h>
__declspec(dllexport) int mariocraft_size_surface(void) { return sizeof(struct SM64Surface); }
__declspec(dllexport) int mariocraft_size_inputs(void) { return sizeof(struct SM64MarioInputs); }
__declspec(dllexport) int mariocraft_size_state(void) { return sizeof(struct SM64MarioState); }
__declspec(dllexport) int mariocraft_size_geometry(void) { return sizeof(struct SM64MarioGeometryBuffers); }
__declspec(dllexport) int mariocraft_offset_action(void) { return offsetof(struct SM64MarioState, action); }
__declspec(dllexport) int mariocraft_offset_health(void) { return offsetof(struct SM64MarioState, health); }
__declspec(dllexport) int mariocraft_offset_flags(void) { return offsetof(struct SM64MarioState, flags); }
__declspec(dllexport) int mariocraft_offset_invincTimer(void) { return offsetof(struct SM64MarioState, invincTimer); }
