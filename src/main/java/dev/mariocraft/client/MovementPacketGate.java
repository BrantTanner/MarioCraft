package dev.mariocraft.client;

import dev.mariocraft.PlayerBridge;
import io.netty.channel.*;
import net.minecraft.network.play.client.CPacketPlayer;
import java.util.UUID;

/** The integrated server samples native poses directly; vanilla movement packets must not resolve them a second time. */
final class MovementPacketGate extends ChannelOutboundHandlerAdapter {
    private final UUID player;
    MovementPacketGate(UUID player) { this.player=player; }
    @Override public void write(ChannelHandlerContext context,Object packet,ChannelPromise promise) throws Exception {
        if(packet instanceof CPacketPlayer && PlayerBridge.POSES.containsKey(player)) { promise.setSuccess(); return; }
        context.write(packet,promise);
    }
}
