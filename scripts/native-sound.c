/* Mod-owned audio boundary. Pinned synthesis retains locally loaded ROM banks.
 * All queued positions refer to this process-lifetime source, never Mario objects. */
#include "play_sound.h"
#include "load_audio_data.h"
#include "decomp/audio/external.h"
#include "decomp/audio/seqplayer.h"
#include "decomp/audio/load.h"
SM64PlaySoundFunctionPtr g_play_sound_func = NULL;
static float source[3]={0,0,0};
static int loaded;
void play_sound(uint32_t bits, f32 *ignored) {
    unsigned bank=bits>>28;
    /* Mario actions, movement loops and voice only. No objects, menus or music. */
    if(!g_is_audio_initialized || (bank!=0 && bank!=1 && bank!=2)) return;
    if(bank==1 && (((bits>>16)&255)==0x20 || ((bits>>16)&255)==0x28 || ((bits>>16)&255)==0x19)) return;
    if(sSoundRequestCount<255) {
        sSoundRequests[sSoundRequestCount].soundBits=bits;
        sSoundRequests[sSoundRequestCount++].position=source;
        if(g_play_sound_func) g_play_sound_func(bits,source);
    }
}
__declspec(dllexport) void mariocraft_audio_begin(const uint8_t *rom) {
    if(!loaded) { sm64_audio_init(rom);loaded=1; }
    else { g_is_audio_initialized=true;sound_reset(0); }
    sSoundRequestCount=0;
}
__declspec(dllexport) void mariocraft_audio_end(void) {
    if(!loaded) return;
    sound_reset(0);sSoundRequestCount=0;g_is_audio_initialized=false;
}
__declspec(dllexport) void mariocraft_audio_reset(void) {
    if(g_is_audio_initialized) { sound_reset(0);sSoundRequestCount=0; }
}
__declspec(dllexport) unsigned mariocraft_audio_tick(unsigned queued,unsigned desired,short *pcm) {
    if(!g_is_audio_initialized) return 0;
    update_game_sound();
    sequence_player_disable(&gSequencePlayers[SEQ_PLAYER_LEVEL]);
    sequence_player_disable(&gSequencePlayers[SEQ_PLAYER_ENV]);
    unsigned samples=queued<desired?544:528;
    for(unsigned i=0;i<2;i++) create_next_audio_buffer(pcm+i*(2*samples),samples);
    return samples;
}
