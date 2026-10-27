package dev.mariocraft.client;

import dev.mariocraft.health.*;
import dev.mariocraft.nativebridge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.*;
import net.minecraft.block.material.Material;
import org.apache.logging.log4j.Logger;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Opt-in actual-world death/GuiGameOver/vanilla respawn sequence. Setup never revives native Mario. */
final class RespawnProbe {
    private int completed,screens,screenFrames,requested=-1;
    private boolean inScreen,baseline,checks=true,serverCheckPending;
    private Object originalServer,originalOverworld;
    private UUID marker;
    private BlockPos markerBlock;
    private int markerBlockState;
    private long markerTick;
    private boolean transferring;
    private volatile boolean netherEntered;
    private boolean sawNether,returnedOverworld;
    private boolean transientClean=true,worldProgress=true,inventoryPreserved=true;
    private int maximumDrops;
    private final Set<UUID> baselineOrbs=new HashSet<>();
    private final List<double[]> deathPositions=new ArrayList<>();
    private static final String FIXTURE="mariocraftRespawnFixture";
    private static net.minecraft.item.ItemStack fixture(net.minecraft.item.Item item,int count) {
        net.minecraft.item.ItemStack stack=new net.minecraft.item.ItemStack(item,count);
        net.minecraft.nbt.NBTTagCompound tag=new net.minecraft.nbt.NBTTagCompound();tag.setBoolean(FIXTURE,true);stack.setTagCompound(tag);return stack;
    }
    private static boolean fixture(net.minecraft.item.ItemStack stack) { return stack.hasTagCompound() && stack.getTagCompound().getBoolean(FIXTURE); }
    private double[] designated;
    private UUID id;
    private volatile double[] lava;
    private volatile String failure;
    private boolean searching,hazardPlaced;
    private int lethalNativeAction;
    private final EnumSet<net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType> hiddenHud=EnumSet.noneOf(net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.class);
    void hud(net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType type) { hiddenHud.add(type); }
    void update(Minecraft mc,MarioPlayer owner,Logger log) {
        if(mc.world==null || mc.player==null || GameplayProbe.complete) return;
        if(failure!=null) { GameplayProbe.fail(failure);return; }
        if(originalServer==null) { originalServer=mc.getIntegratedServer();originalOverworld=mc.getIntegratedServer().getWorld(0);id=mc.player.getUniqueID(); }
        checks &= originalServer==mc.getIntegratedServer() && originalOverworld==mc.getIntegratedServer().getWorld(0) && id.equals(mc.player.getUniqueID());
        if(mc.player.dimension==-1) sawNether=true;
        if(sawNether && mc.player.dimension==0) returnedOverworld=true;
        boolean screen=mc.currentScreen instanceof net.minecraft.client.gui.GuiGameOver;
        if(screen && !inScreen) {
            screens++;screenFrames=0;deathPositions.add(new double[]{mc.player.posX,mc.player.posY,mc.player.posZ});
            MarioLives.Snapshot state=owner.lifeState();
            checks &= state.phase==MarioLives.Phase.AWAITING_RESPAWN && state.deaths==screens && state.lives==(screens==4?0:4-screens%4);
            log.info("MARIOCRAFT RESPAWN screen={} lives={} health={}",screens,state.lives,mc.player.getHealth());
        }
        inScreen=screen;
        if(screen && ++screenFrames==35) {
            // Same API as the enabled vanilla Respawn button, after observing the real screen.
            mc.player.respawnPlayer();mc.displayGuiScreen(null);
        }
    }
    void setup(int frame,MarioSession session,TerrainMesh terrain,Minecraft mc,MarioPlayer owner) {
        GameplayProbe.currentFrame=frame;
        MarioLives.Snapshot state=owner.lifeState();
        int cycle=(int)state.deaths;
        if(frame==30) {
            int health=Short.toUnsignedInt(session.state.health);
            org.apache.logging.log4j.LogManager.getLogger("MarioCraft").info("MARIOCRAFT RESPAWN clean-check cycle={} clean={} action={} invinc={} velocity={} {}",cycle,owner.cleanRespawnState(),Integer.toHexString(session.state.action),session.state.invincTimer,Arrays.toString(session.state.velocity),owner.respawnDiagnostics());
            checks &= health==MarioLives.FULL_HEALTH && session.state.invincTimer==0 && (session.state.action&0x20000)==0;
            checks &= state.phase==MarioLives.Phase.ALIVE && state.lives==(cycle==4?4:4-cycle%4);
            if(cycle>0 && mc.player.dimension==0) {
                if(designated==null) designated=new double[]{mc.player.posX,mc.player.posY,mc.player.posZ};
                else checks &= mc.player.getDistanceSq(designated[0],designated[1],designated[2])<0.000001;
            }
            if(cycle>0) { transientClean &= owner.cleanRespawnState();checks &= transientClean; }
            if(cycle>0 && cycle>completed && !serverCheckPending) {
                serverCheckPending=true;completed=cycle;
                mc.getIntegratedServer().addScheduledTask(()-> {
                    EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(id);
                    boolean ok=p!=null && p.inventory.getStackInSlot(0).getCount()==3 && p.inventory.getStackInSlot(0).getItem()==net.minecraft.init.Items.APPLE && fixture(p.inventory.getStackInSlot(0)) && p.experienceLevel==7 && p.experienceTotal==91 && p.getFoodStats().getFoodLevel()==14 && p.inventory.armorInventory.get(3).getItem()==net.minecraft.init.Items.IRON_HELMET && p.inventory.armorInventory.get(3).getItemDamage()==0 && fixture(p.inventory.armorInventory.get(3));
                    net.minecraft.world.WorldServer preserved=mc.getIntegratedServer().getWorld(0);
                    net.minecraft.entity.Entity entity=preserved.getEntityFromUuid(marker);
                    boolean markerPresent=entity!=null && entity.ticksExisted>markerTick;
                    boolean blockPreserved=net.minecraft.block.Block.getStateId(preserved.getBlockState(markerBlock))==markerBlockState;
                    worldProgress &= markerPresent && blockPreserved;
                    inventoryPreserved &= ok;
                    int drops=0;
                    for(net.minecraft.entity.Entity loaded:preserved.loadedEntityList) {
                        if(loaded instanceof net.minecraft.entity.item.EntityItem && fixture(((net.minecraft.entity.item.EntityItem)loaded).getItem())) drops++;
                        if(loaded instanceof net.minecraft.entity.item.EntityXPOrb && !baselineOrbs.contains(loaded.getUniqueID())) {
                            for(double[] death:deathPositions) if(loaded.getDistanceSq(death[0],death[1],death[2])<=9) { drops++;break; }
                        }
                    }
                    maximumDrops=Math.max(maximumDrops,drops);
                    checks &= ok && worldProgress && drops==0;
                    logScalar(mc,"{\"respawn\":"+cycle+",\"fullHealth\":"+(health==MarioLives.FULL_HEALTH)+",\"lives\":"+state.lives+",\"inventoryXpFoodPreserved\":"+ok+",\"markerEntityPreserved\":"+markerPresent+",\"markerBlockPreserved\":"+blockPreserved+",\"drops\":"+drops+"}\n",false);
                    serverCheckPending=false;
                    if(cycle==6) finish(mc);
                });
            }
        }
        if(!baseline && frame>=30) {
            baseline=true;
            mc.getIntegratedServer().addScheduledTask(()-> {
                EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(id);
                net.minecraft.world.WorldServer preserved=(net.minecraft.world.WorldServer)p.world;
                net.minecraft.entity.passive.EntityPig pig=new net.minecraft.entity.passive.EntityPig(preserved);
                pig.setNoAI(true);pig.setNoGravity(true);pig.setEntityInvulnerable(true);pig.setPosition(p.posX+2,p.posY,p.posZ);preserved.spawnEntity(pig);marker=pig.getUniqueID();markerTick=pig.ticksExisted;
                markerBlock=new BlockPos(preserved.getSpawnPoint().getX(),0,preserved.getSpawnPoint().getZ());markerBlockState=net.minecraft.block.Block.getStateId(preserved.getBlockState(markerBlock));
                p.inventory.setInventorySlotContents(0,fixture(net.minecraft.init.Items.APPLE,3));p.inventory.armorInventory.set(3,fixture(net.minecraft.init.Items.IRON_HELMET,1));p.experienceLevel=7;p.experienceTotal=91;p.getFoodStats().setFoodLevel(14);
                int ambientItems=0;
                for(net.minecraft.entity.Entity loaded:preserved.loadedEntityList) {
                    if(loaded instanceof net.minecraft.entity.item.EntityXPOrb) baselineOrbs.add(loaded.getUniqueID());
                    if(loaded instanceof net.minecraft.entity.item.EntityItem) ambientItems++;
                }
                logScalar(mc,"{\"setupInventoryXpFood\":true,\"taggedFixtureStacks\":2,\"baselineAmbientItems\":"+ambientItems+",\"baselineExperienceOrbs\":"+baselineOrbs.size()+",\"terrainEdits\":0}\n",true);
            });
        }
        if(frame>=60 && requested!=cycle && cycle<4) {
            requested=cycle;
            mc.getIntegratedServer().addScheduledTask(()-> {
                EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(id);
                p.attackEntityFrom(new net.minecraft.util.DamageSource("respawnProbe.external"),32);
            });
        }
        if(cycle==5 && frame>=60 && !transferring) {
            transferring=true;
            mc.getIntegratedServer().addScheduledTask(()-> {
                try {
                    EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(id);
                    net.minecraft.world.WorldServer w=mc.getIntegratedServer().getWorld(-1);
                    BlockPos selected=RespawnBridge.safeSpawn(w);
                    mc.getIntegratedServer().getPlayerList().transferPlayerToDimension(p,-1,new net.minecraft.world.Teleporter(w) {
                        @Override public void placeInPortal(net.minecraft.entity.Entity entity,float yaw) { entity.setLocationAndAngles(selected.getX()+.5,selected.getY()+.1,selected.getZ()+.5,0,0); }
                    });
                    p.connection.setPlayerLocation(selected.getX()+.5,selected.getY()+.1,selected.getZ()+.5,0,0);
                    netherEntered=true;
                } catch(RuntimeException e) { failure="dimension setup "+e.getClass().getSimpleName(); }
            });
        }
        if(cycle==5 && mc.player.dimension==-1 && netherEntered && frame>=60 && requested!=cycle) {
            requested=cycle;
            mc.getIntegratedServer().addScheduledTask(()-> {
                EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(id);
                p.attackEntityFrom(new net.minecraft.util.DamageSource("respawnProbe.dimension"),32);
            });
        }
        if(cycle==4 && !searching) {
            searching=true;
            mc.getIntegratedServer().addScheduledTask(()-> {
                try {
                    EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(id);
                    net.minecraft.world.WorldServer w=(net.minecraft.world.WorldServer)p.world;
                    BlockPos center=w.getSpawnPoint();
                    for(int r=0;r<=10;r++) for(int cx=-r;cx<=r;cx++) for(int cz=-r;cz<=r;cz++) {
                        if(Math.max(Math.abs(cx),Math.abs(cz))!=r) continue;
                        int ox=(center.getX()>>4)+cx,oz=(center.getZ()>>4)+cz;w.getChunkFromChunkCoords(ox,oz);
                        for(int x=ox*16;x<ox*16+16;x++) for(int z=oz*16;z<oz*16+16;z++) for(int y=5;y<100;y++) {
                            BlockPos b=new BlockPos(x,y,z);
                            if(w.getBlockState(b).getMaterial()==Material.LAVA && w.isAirBlock(b.up()) && w.isAirBlock(b.up(2)) && w.isAirBlock(b.up(3))) { lava=new double[]{x+.5,y+1.2,z+.5};return; }
                        }
                    }
                    failure="natural same-world lava search";
                } catch(RuntimeException e) { failure="lava setup "+e.getClass().getSimpleName(); }
            });
        }
        if(cycle==4 && lava!=null && frame>60 && !hazardPlaced) {
            hazardPlaced=true;
            // Test-only lethal starting health and relocation into an actual naturally generated lava block.
            session.configureHealthForProbe(0x180);
            mc.player.setPosition(lava[0],lava[1],lava[2]);session.position(terrain.x(lava[0]),terrain.y(lava[1]),terrain.z(lava[2]));
        }
    }
    void observe(Sm64Library.State nativeState,Minecraft mc,MarioPlayer owner,Logger log) {
        if(owner.lifeState().deaths==4 && Short.toUnsignedInt(nativeState.health)<0x100) lethalNativeAction=nativeState.action;
    }
    private void finish(Minecraft mc) {
        checks &= screens==6 && completed==6 && sawNether && returnedOverworld && lethalNativeAction==0x010208B7 && hiddenHud.size()==4;
        String result="{\"passed\":"+checks+",\"respawn\":true,\"deaths\":6,\"minecraftGameOverScreens\":"+screens+",\"completedRespawns\":"+completed+",\"zeroLifeReset\":true,\"worldPreserved\":"+worldProgress+",\"inventoryXpFoodPreserved\":"+inventoryPreserved+",\"nativeLavaDeathAction\":\""+Integer.toHexString(lethalNativeAction)+"\",\"terrainEdits\":0,\"vanillaHudElementsSuppressed\":"+hiddenHud.size()+",\"inventoryDrops\":"+maximumDrops+",\"transientStateClean\":"+transientClean+",\"dimensionRespawnPreservedLives\":"+(sawNether && returnedOverworld)+",\"testOnlyExternalDamageRequests\":5,\"testOnlyHealthSetups\":1,\"testOnlyRelocations\":2}";
        try { Files.write(new java.io.File(mc.mcDataDir,"gameplay-result.json").toPath(),result.getBytes(StandardCharsets.UTF_8)); }
        catch(java.io.IOException e) { throw new IllegalStateException(e); }
        GameplayProbe.complete=true;
    }
    private static void logScalar(Minecraft mc,String line,boolean first) {
        try { Files.write(new java.io.File(mc.mcDataDir,"respawn-trace.jsonl").toPath(),line.getBytes(StandardCharsets.UTF_8),first?new OpenOption[]{StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING}:new OpenOption[]{StandardOpenOption.CREATE,StandardOpenOption.APPEND}); }
        catch(java.io.IOException e) { throw new IllegalStateException(e); }
    }
}
