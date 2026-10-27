/* Mod-owned entry adapters; ongoing actions remain pinned native code.
 * Flame policy: importer-pinned n64decomp/sm64 06ec56d interaction.c interact_flame.
 * No original object engine, object pointer or copied game asset is retained here. */
#include "libsm64.h"
#include "obj_pool.h"
#include "decomp/shim.h"
#include "decomp/include/object_fields.h"
#include "decomp/engine/surface_collision.h"
#include "decomp/include/sm64.h"
#include "decomp/game/mario.h"
#include "decomp/game/interaction.h"
#include "decomp/engine/math_util.h"

extern struct ObjPool s_mario_instance_pool;
struct MarioCraftInstance { struct GlobalState *globalState; };
extern s32 lava_boost_on_wall(struct MarioState *m);
__declspec(dllexport) int mariocraft_hazard_abi(void) { return 1; }
/* kind 1=fire body overlap; 2=lava body/side overlap. Surface floors handle feet. */
__declspec(dllexport) int mariocraft_hazard_contact(int id,int kind,float nx,float nz) {
    if(id<0 || (size_t)id>=s_mario_instance_pool.size || !s_mario_instance_pool.objects[id]) return 0;
    global_state_bind(((struct MarioCraftInstance *)s_mario_instance_pool.objects[id])->globalState);
    struct MarioState *m=gMarioState;
    if(m->health<0x100 || (m->action & ACT_FLAG_INTANGIBLE)) return 0;
    if(kind==1) {
        if((m->action & ACT_FLAG_INVULNERABLE) || m->invincTimer || (m->flags & (MARIO_METAL_CAP|MARIO_VANISH_CAP))) return 0;
        if((m->action & (ACT_FLAG_SWIMMING|ACT_FLAG_METAL_WATER)) || m->waterLevel-m->pos[1]>50.0f) return 0;
        m->marioObj->oMarioBurnTimer=0;
        update_mario_sound_and_camera(m);
        u32 action=(m->action & ACT_FLAG_AIR) && m->vel[1]<=0 ? ACT_BURNING_FALL : ACT_BURNING_JUMP;
        return drop_and_set_mario_action(m,action,1);
    }
    /* A fluid side is a crossover contact, using the original burning-wall entry.
     * Avoid a per-step restart while already bouncing. Native floor recontacts
     * independently own repeated exposure and intentionally bypass hit timers. */
    if(kind==2 && m->action!=ACT_LAVA_BOOST && !(m->action & ACT_FLAG_SWIMMING)) {
        struct SM64SurfaceCollisionData wall={0};wall.normal.x=nx;wall.normal.z=nz;
        struct SM64SurfaceCollisionData *previous=m->wall;m->wall=&wall;
        s32 result=lava_boost_on_wall(m);m->wall=previous;return result;
    }
    return 0;
}
