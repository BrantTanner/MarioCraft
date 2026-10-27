package dev.mariocraft.client;

import dev.mariocraft.health.*;
import dev.mariocraft.persistence.*;
import dev.mariocraft.nativebridge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Explicit test-only save/reopen, relocation, health, input and malformed-record fixtures.
 * Uses disposable ordinary generated saves; no terrain edits, keyboard or desktop automation. */
final class PersistenceProbe {
    static final PersistenceProbe INSTANCE=Boolean.getBoolean("mariocraft.persistenceProbe")?new PersistenceProbe():null;
    private static final String[] WORLDS={"A","A","A","A","A","A","B","A","B","A","A","A","A"};
    private final String prefix="mariocraft-persistence-"+System.currentTimeMillis();
    private int cycle=-1,frame,lastAction,firstHealth;
    private long deadline,pauseStarted,unloadStarted;
    private boolean closing,loading,finish,setup,capture,first,verified,deathRequested;
    private volatile boolean fixtureDone;
    private volatile String failure;
    private volatile double[] water,dry;
    private volatile MarioSaveState expected;
    private int expectedHealth=2176,expectedLives=4;
    private final Map<String,MarioSaveState> savedStates=new HashMap<>();
    private MarioSaveState positionExpected;
    private final Set<String> initialized=new HashSet<>();
    private final Map<String,UUID> markers=new HashMap<>();
    private final Map<String,BlockPos> blocks=new HashMap<>();
    private final Map<String,Integer> blockStates=new HashMap<>();
    private int verifiedLoads,closed,swimResumes,idleResumes,pendingResumes;
    private boolean all=true;
    private void trace(String line) {
        try { Files.write(new java.io.File(Minecraft.getMinecraft().mcDataDir,"persistence-trace.jsonl").toPath(),(line+"\n").getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE,StandardOpenOption.APPEND); }
        catch(java.io.IOException e) { throw new IllegalStateException(e); }
    }
    @SubscribeEvent public void lifecycle(TickEvent.RenderTickEvent event) {
        if(event.phase!=TickEvent.Phase.END) return;
        Minecraft mc=Minecraft.getMinecraft();org.lwjgl.opengl.Display.setLocation(-20000,-20000);
        mc.gameSettings.pauseOnLostFocus=false;mc.gameSettings.renderDistanceChunks=4;
        if(deadline==0) {
            deadline=System.nanoTime()+900000000000L;
            try { Files.write(new java.io.File(mc.mcDataDir,"persistence-trace.jsonl").toPath(),new byte[0]); } catch(java.io.IOException e) { throw new IllegalStateException(e); }
        }
        if(System.nanoTime()>deadline) failure="persistence timeout cycle "+cycle;
        if(failure!=null) { GameplayProbe.fail(failure);mc.shutdown();return; }
        if(finish) { mc.shutdown();return; }
        if(mc.world==null && mc.currentScreen instanceof GuiMainMenu && !mc.isIntegratedServerRunning() && !loading) {
            if(closing) {
                boolean empty=dev.mariocraft.PlayerBridge.POSES.isEmpty()&&HealthBridge.OWNERS.isEmpty()&&RespawnBridge.PENDING.isEmpty()&&RestoreBridge.HOLDS.isEmpty();
                boolean resources=owner.persistenceClosed();all &= empty && resources;closed++;closing=false;
                trace("{\"unload\":"+closed+",\"registriesEmpty\":"+empty+",\"resourcesClosed\":"+resources+"}");
            }
            if(++cycle>=WORLDS.length) {
                boolean passed=all&&verifiedLoads==WORLDS.length&&closed==WORLDS.length&&swimResumes==1&&pendingResumes==1;
                String result="{\"passed\":"+passed+",\"loads\":"+verifiedLoads+",\"unloads\":"+closed+",\"swimResumes\":"+swimResumes+",\"idleResumes\":"+idleResumes+",\"pendingResumes\":"+pendingResumes+",\"terrainEdits\":0}";
                try { Files.write(new java.io.File(mc.mcDataDir,"gameplay-result.json").toPath(),result.getBytes(StandardCharsets.UTF_8)); } catch(java.io.IOException e) { throw new IllegalStateException(e); }
                finish=true;return;
            }
            positionExpected=savedStates.get(WORLDS[cycle]);
            loading=true;frame=0;setup=capture=first=verified=deathRequested=false;fixtureDone=false;pauseStarted=0;
            expectedLives=cycle==5||cycle==7||cycle==9?3:4;
            expectedHealth=cycle==1?0x500:cycle==2&&expected!=null?expected.health:cycle==3?0x600:cycle==4?0x100:2176;
            mc.launchIntegratedServer(prefix+WORLDS[cycle],"MarioCraft persistence probe",new WorldSettings(20261026L,GameType.SURVIVAL,true,false,WorldType.DEFAULT));return;
        }
        if(mc.world!=null) loading=false;
        if(cycle==4 && mc.currentScreen instanceof net.minecraft.client.gui.GuiGameOver) capture=true;
        if(closing && System.nanoTime()-unloadStarted>30000000000L) { failure="bounded unload timeout cycle "+cycle+" screen "+(mc.currentScreen==null?"null":mc.currentScreen.getClass().getSimpleName());return; }
        if(capture && !closing && fixtureDone) {
            if(pauseStarted==0) { mc.displayGuiScreen(new net.minecraft.client.gui.GuiIngameMenu());pauseStarted=System.nanoTime();return; }
            if(!mc.isGamePaused() || System.nanoTime()-pauseStarted<500000000L) return;
            // Read the exact immutable value production MapStorage will serialize on shutdown.
            expected=owner.semanticSave();
            if(expected!=null) savedStates.put(WORLDS[cycle],expected);
            if(cycle==4) all &= expected!=null && expected.lives.phase==MarioLives.Phase.AWAITING_RESPAWN && expected.lives.lives==3;
            trace("{\"save\":"+cycle+",\"health\":"+(expected==null?-1:expected.health)+",\"lives\":"+(expected==null?-1:expected.lives.lives)+",\"nativeAction\":\""+Integer.toHexString(lastAction)+"\"}");
            if(cycle>=9 && cycle<=11) {
                // Fixture modifies only mod-owned data, after native capture; original vanilla data is untouched.
                final net.minecraft.server.integrated.IntegratedServer server=mc.getIntegratedServer();final UUID id=mc.player.getUniqueID();
                server.addScheduledTask(()-> {
                    MarioSaveData data=MarioSaveData.load(server);net.minecraft.nbt.NBTTagCompound n=data.writeToNBT(new net.minecraft.nbt.NBTTagCompound());
                    net.minecraft.nbt.NBTTagCompound players=n.getCompoundTag("players");
                    if(cycle==9) players.getCompoundTag(id.toString()).setDouble("x",Double.NaN);
                    if(cycle==10) players.getCompoundTag(id.toString()).setInteger("version",0);
                    if(cycle==11) players.removeTag(id.toString());
                    data.readFromNBT(n);
                });
                try { server.addScheduledTask(()->{}).get(); } catch(Exception e) { throw new IllegalStateException(e); }
            }
            closing=true;unloadStarted=System.nanoTime();mc.world.sendQuittingDisconnectingPacket();mc.loadWorld(null);mc.displayGuiScreen(new GuiMainMenu());
        }
    }
    private MarioPlayer owner;
    void setup(int frame,MarioSession session,TerrainMesh terrain,Minecraft mc,MarioPlayer owner) {
        this.owner=owner;
        if(frame>=40 && !setup && fixtureDone) {
            setup=true;
            if(cycle==0) session.configureHealthForProbe(0x500);
            if(cycle==1) { session.configureHealthForProbe(0x700);session.position(terrain.x(water[0]),terrain.y(water[1]),terrain.z(water[2]));mc.player.setPosition(water[0],water[1],water[2]); }
            if(cycle==2) { session.configureHealthForProbe(0x600);session.position(terrain.x(dry[0]),terrain.y(dry[1]) + 10,terrain.z(dry[2]));mc.player.setPosition(dry[0],dry[1],dry[2]); }
            if(cycle==3) session.configureHealthForProbe(0x100);

        }
    }
    ControlState controls(int frame) { ControlState s=new ControlState();if(cycle==2 && frame>=65&&frame<71) s.a=true;return s; }
    void observe(int frame,Sm64Library.State state,Minecraft mc,MarioPlayer owner) {
        this.owner=owner;this.frame=frame;lastAction=state.action;
        if(frame>300) { failure="bounded phase timeout cycle "+cycle;return; }
        if(cycle==4 && frame>=100 && !deathRequested) {
            deathRequested=true;UUID id=mc.player.getUniqueID();
            mc.getIntegratedServer().addScheduledTask(()-> { EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(id);p.attackEntityFrom(new net.minecraft.util.DamageSource("persistenceProbe.external"),32); });
        }
        if(cycle==1 && setup && frame>=44) capture=true;
        int h=Short.toUnsignedInt(state.health);
        if(!first) {
            first=true;firstHealth=h;
            boolean ok=cycle==2?Math.abs(h-expectedHealth)<=2:h==expectedHealth;
            boolean position=true;
            if(positionExpected!=null && cycle!=5 && cycle<10) {
                MarioSaveState anchor=owner.resumeAnchor();
                position=anchor!=null && Math.abs(anchor.x-positionExpected.x)<.00001 && Math.abs(anchor.z-positionExpected.z)<.00001
                    && (cycle==2?Math.abs(anchor.y-positionExpected.y)<.00001:anchor.y<=positionExpected.y+.1 && anchor.y>=positionExpected.y-160);
            }
            all &= ok && position && owner.lifeState().lives==expectedLives;
            trace("{\"positionRestored\":"+cycle+",\"valid\":"+position+"}");
            trace("{\"firstFrame\":"+cycle+",\"health\":"+h+",\"expectedHealth\":"+expectedHealth+",\"lives\":"+owner.lifeState().lives+",\"valid\":"+ok+"}");
        }
        if(frame==30 && !verified) {
            verified=true;verifiedLoads++;
            boolean swim=(state.action&0x2000)!=0;
            boolean clean=cycle==2?owner.cleanSwimResume():owner.cleanIdleResume();
            boolean ownership=owner.persistenceOwnership();all &= clean && ownership;trace("{\"ownership\":"+cycle+",\"valid\":"+ownership+"}");if(cycle==2) swimResumes++;else idleResumes++;if(cycle==5) { pendingResumes++;all &= owner.lifeState().deaths==1; }
            trace("{\"resume\":"+cycle+",\"world\":\""+WORLDS[cycle]+"\",\"swimming\":"+swim+",\"clean\":"+clean+",\"action\":\""+Integer.toHexString(state.action)+"\"}");
            fixture(mc);
        }
        if(setup && frame>=80 && cycle!=4) {
            if(cycle==2) { /* capture during jump below, not after settling */ }
            else capture=true;
        }
        if(cycle==2 && setup && frame==70) { all &= (state.action&0x800)!=0;capture=true; }
    }
    private void fixture(Minecraft mc) {
        final String world=WORLDS[cycle];final UUID id=mc.player.getUniqueID();final net.minecraft.server.integrated.IntegratedServer server=mc.getIntegratedServer();
        server.addScheduledTask(()-> {
            try {
                EntityPlayerMP p=server.getPlayerList().getPlayerByUUID(id);
                boolean single=p.world.playerEntities.size()==1 && p.world.playerEntities.get(0)==p;
                all &= single;trace("{\"singleMinecraftPlayer\":"+cycle+",\"valid\":"+single+"}");
                if(initialized.add(world)) {
                    net.minecraft.item.ItemStack stack=new net.minecraft.item.ItemStack(net.minecraft.init.Items.APPLE,world.equals("A")?7:9);
                    net.minecraft.nbt.NBTTagCompound tag=new net.minecraft.nbt.NBTTagCompound();tag.setString("MarioPersistenceFixture",world);stack.setTagCompound(tag);p.inventory.setInventorySlotContents(35,stack);
                    p.getEntityData().setString("MarioUnrelatedFixture",world);p.addExperienceLevel(7);p.getFoodStats().setFoodLevel(14);
                    net.minecraft.entity.passive.EntityPig pig=new net.minecraft.entity.passive.EntityPig(p.world);pig.setPosition(p.posX+3,p.posY,p.posZ);pig.setNoAI(true);pig.setNoGravity(true);pig.setEntityInvulnerable(true);p.world.spawnEntity(pig);markers.put(world,pig.getUniqueID());
                    BlockPos marker=new BlockPos(p.posX,0,p.posZ);blocks.put(world,marker);blockStates.put(world,net.minecraft.block.Block.getStateId(p.world.getBlockState(marker)));
                } else {
                    p.world.getChunkFromBlockCoords(blocks.get(world));
                    net.minecraft.item.ItemStack stack=p.inventory.getStackInSlot(35);
                    boolean ok=stack.getItem()==net.minecraft.init.Items.APPLE && stack.getCount()==(world.equals("A")?7:9)&&stack.hasTagCompound()&&world.equals(stack.getTagCompound().getString("MarioPersistenceFixture"))
                        && world.equals(p.getEntityData().getString("MarioUnrelatedFixture"))&&p.experienceLevel==7&&p.getFoodStats().getFoodLevel()==14
                        &&p.getServerWorld().getEntityFromUuid(markers.get(world))!=null&&net.minecraft.block.Block.getStateId(p.world.getBlockState(blocks.get(world)))==blockStates.get(world);
                    all &= ok;trace("{\"vanillaPreserved\":"+cycle+",\"valid\":"+ok+"}");
                }
                if(dry==null) { BlockPos spawn=RespawnBridge.safeSpawn(p.getServerWorld());dry=new double[]{spawn.getX()+.5,spawn.getY()+.1,spawn.getZ()+.5}; }
                if(cycle==1) {
                    int sx=(int)p.posX,sz=(int)p.posZ;
                    search:for(int r=0;r<=256;r+=8) for(int dx=-r;dx<=r;dx+=4) for(int dz=-r;dz<=r;dz+=4) {
                        if(Math.max(Math.abs(dx),Math.abs(dz))!=r) continue;
                        int x=sx+dx,z=sz+dz,top=p.world.getHeight(new BlockPos(x,0,z)).getY()-1;
                        WaterVolume.Sample w=WaterVolume.sample(p.world,x+.5,top+.1,z+.5);
                        if(w.wet && w.surface-w.bottom>=6 && p.world.getCollisionBoxes(null,new AxisAlignedBB(x+.13,w.surface-4,z+.13,x+.87,w.surface-2.4,z+.87)).isEmpty()) { water=new double[]{x+.5,w.surface-4,z+.5};break search; }
                    }
                    if(water==null) throw new IllegalStateException("No natural deep water");
                }
                fixtureDone=true;
            } catch(RuntimeException e) { failure="fixture "+e; }
        });
    }
}
