package dev.mariocraft;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.world.BlockEvent;

/** Player-specific MVP boundaries. World simulation and existing inventory data stay intact. */
public final class ScopeRules {
    private final java.util.Map<EntityPlayer,net.minecraft.nbt.NBTTagCompound> food=new java.util.WeakHashMap<>();
    public static boolean enabled() { return Boolean.parseBoolean(System.getProperty("mariocraft.gameplay","true")); }
    /** Deliberate fixtures bypass portal entry without opening a production travel path. */
    public static void fixtureTransfer(net.minecraft.entity.player.EntityPlayerMP player,int dimension,net.minecraft.world.Teleporter teleporter,String fixture) {
        if(!("hazard".equals(fixture)&&Boolean.getBoolean("mariocraft.hazardProbe")) && !("respawn".equals(fixture)&&Boolean.getBoolean("mariocraft.respawnProbe"))) throw new IllegalStateException("Dimension fixture requires its explicit probe");
        player.getServer().getPlayerList().transferPlayerToDimension(player,dimension,teleporter);
    }
    public static boolean player(EntityPlayer p) { return enabled() && p!=null && p.world!=null && (!p.world.isRemote ? p.getServer()!=null && !p.getServer().isDedicatedServer() : true); }
    @SubscribeEvent public void interact(PlayerInteractEvent e) { if(player(e.getEntityPlayer()) && e.isCancelable()) e.setCanceled(true); }
    @SubscribeEvent public void attack(AttackEntityEvent e) { if(player(e.getEntityPlayer())) e.setCanceled(true); }
    @SubscribeEvent public void pickup(EntityItemPickupEvent e) { if(player(e.getEntityPlayer())) e.setCanceled(true); }
    @SubscribeEvent public void xp(PlayerPickupXpEvent e) { if(player(e.getEntityPlayer())) e.setCanceled(true); }
    @SubscribeEvent public void breakBlock(BlockEvent.BreakEvent e) { if(player(e.getPlayer())) e.setCanceled(true); }
    @SubscribeEvent public void place(BlockEvent.EntityPlaceEvent e) { if(e.getEntity() instanceof EntityPlayer && player((EntityPlayer)e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public void travel(net.minecraftforge.event.entity.EntityTravelToDimensionEvent e) {
        if(e.getEntity() instanceof EntityPlayer && player((EntityPlayer)e.getEntity())) e.setCanceled(true);
    }
    @SubscribeEvent public void command(net.minecraftforge.event.CommandEvent e) {
        if(e.getSender() instanceof EntityPlayer && player((EntityPlayer)e.getSender())) e.setCanceled(true);
    }
    @SubscribeEvent public void container(PlayerContainerEvent.Open e) {
        if(player(e.getEntityPlayer()) && !e.getEntityPlayer().world.isRemote) e.getEntityPlayer().closeScreen();
    }
    @SubscribeEvent public void tick(net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent e) {
        if(!player(e.player) || e.player.world.isRemote) return;
        if(e.phase==net.minecraftforge.fml.common.gameevent.TickEvent.Phase.START) {
            net.minecraft.nbt.NBTTagCompound n=new net.minecraft.nbt.NBTTagCompound();e.player.getFoodStats().writeNBT(n);food.put(e.player,n);
        } else {
            net.minecraft.nbt.NBTTagCompound n=food.remove(e.player);if(n!=null) e.player.getFoodStats().readNBT(n);
        }
    }
}
