package dev.mariocraft.persistence;
import dev.mariocraft.health.MarioLives;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.UUID;
public class MarioSaveStateTest {
    private MarioSaveState state() { return new MarioSaveState(0,12.5,70.01,-8.5,0x450,new MarioLives().snapshot()); }
    @Test public void roundTripSemanticFieldsOnly() {
        MarioSaveState s=state(),r=MarioSaveState.read(s.write());assertNotNull(r);assertEquals(s.x,r.x,0);assertEquals(s.health,r.health);assertEquals(4,r.lives.lives);
        assertFalse(s.write().hasKey("action"));assertFalse(s.write().hasKey("velocity"));
    }
    @Test public void rejectsMissingOldFutureAndWrongTypes() {
        assertNull(MarioSaveState.read(new NBTTagCompound()));
        for(int version:new int[]{0,2,Integer.MAX_VALUE}) { NBTTagCompound n=state().write();n.setInteger("version",version);assertNull(MarioSaveState.read(n)); }
        NBTTagCompound n=state().write();n.setString("x","12.5");assertNull(MarioSaveState.read(n));
        n=state().write();n.removeTag("health");assertNull(MarioSaveState.read(n));
    }
    @Test public void rejectsNonfiniteBoundsAndInvalidLifeHealth() {
        for(double x:new double[]{Double.NaN,Double.POSITIVE_INFINITY,30000000}) { NBTTagCompound n=state().write();n.setDouble("x",x);assertNull(MarioSaveState.read(n)); }
        for(int h:new int[]{-1,0,255,2177}) { NBTTagCompound n=state().write();n.setInteger("health",h);assertNull(MarioSaveState.read(n)); }
        NBTTagCompound n=state().write();n.setLong("deaths",-1);assertNull(MarioSaveState.read(n));
        n=state().write();n.setInteger("lives",0);assertNull(MarioSaveState.read(n));
    }
    @Test public void finiteOutOfBuildHeightRemainsSemanticAndDoesNotBreakLivePublication() {
        for(double y:new double[]{-20,280,1024}) { MarioSaveState s=new MarioSaveState(0,0,y,0,2176,new MarioLives().snapshot());assertNotNull(MarioSaveState.read(s.write())); }
    }
    @Test public void pendingRestoresWithoutSecondLifeSubtraction() {
        MarioLives l=new MarioLives();l.die();MarioSaveState s=new MarioSaveState(-1,1,64,1,0,l.snapshot());
        MarioLives r=MarioLives.restore(MarioSaveState.read(s.write()).lives);assertEquals(3,r.snapshot().lives);assertFalse(r.die());assertTrue(r.respawn());assertEquals(3,r.snapshot().lives);assertEquals(1,r.snapshot().deaths);
    }
    @Test public void dataInstancesNeverShareOwnersAndBadRecordDoesNotPoisonNeighbor() {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();MarioSaveData first=new MarioSaveData(),second=new MarioSaveData();first.publish(a,state());assertNull(second.get(a));
        NBTTagCompound n=first.writeToNBT(new NBTTagCompound());n.getCompoundTag("players").setTag(b.toString(),new NBTTagCompound());n.setString("unrelated","preserve");
        second.readFromNBT(n);assertNotNull(second.get(a));assertNull(second.get(b));second.writeToNBT(n);assertEquals("preserve",n.getString("unrelated"));
    }
}
