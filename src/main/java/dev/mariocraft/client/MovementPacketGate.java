package dev.mariocraft.client;

import dev.mariocraft.PlayerBridge;
import io.netty.channel.*;
import net.minecraft.network.play.client.*;
import java.util.UUID;

/** The integrated server samples native poses directly; vanilla movement packets must not resolve them a second time. */
final class MovementPacketGate extends ChannelOutboundHandlerAdapter {
    static final java.util.concurrent.atomic.AtomicLong denied=new java.util.concurrent.atomic.AtomicLong();
    private final UUID player;
    MovementPacketGate(UUID player) { this.player=player; }
    @Override public void write(ChannelHandlerContext context,Object packet,ChannelPromise promise) throws Exception {
        if(dev.mariocraft.ScopeRules.enabled() && (packet instanceof CPacketUseEntity || packet instanceof CPacketPlayerDigging || packet instanceof CPacketPlayerTryUseItem || packet instanceof CPacketPlayerTryUseItemOnBlock || packet instanceof CPacketClickWindow || packet instanceof CPacketCreativeInventoryAction || packet instanceof CPacketHeldItemChange || packet instanceof CPacketEnchantItem || packet instanceof CPacketRecipeInfo || packet instanceof CPacketPlaceRecipe || packet instanceof CPacketClientStatus && ((CPacketClientStatus)packet).getStatus()==CPacketClientStatus.State.REQUEST_STATS)) { denied.incrementAndGet();promise.setSuccess();return; }
        if((packet instanceof CPacketPlayer || packet instanceof CPacketUseEntity || packet instanceof CPacketPlayerDigging || packet instanceof CPacketPlayerTryUseItem || packet instanceof CPacketPlayerTryUseItemOnBlock) && (dev.mariocraft.persistence.RestoreBridge.HOLDS.containsKey(player) || PlayerBridge.POSES.containsKey(player) || dev.mariocraft.health.RespawnBridge.PENDING.containsKey(player))) { promise.setSuccess(); return; }
        context.write(packet,promise);
    }
}
