package dev.mariocraft.combat;
import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;
import dev.mariocraft.combat.MarioCombat.*;
public class MarioCombatTest {
    private Sample sample(int action,int flags,int anim,double y,double previous,double vy,double speed) { return new Sample(action,flags,anim,3,0,y,0,previous,0,vy,speed); }
    private Target ground=new Target(0,0,.55,.3,.9);
    @Test public void activeFlagsFacingAndVolumes() {
        assertNull(MarioCombat.classify(sample(0x800380,0,0x67,0,0,0,0),ground));
        Sample punch=sample(0x800380,0x100000,0x67,0,0,0,0);
        assertEquals(Attack.PUNCH,MarioCombat.classify(punch,ground));
        assertNull(MarioCombat.classify(punch,new Target(0,0,-.55,.3,.9)));
        assertNull(MarioCombat.classify(punch,new Target(0,0,.68,.3,.9)));
        assertNull(MarioCombat.classify(punch,new Target(0,2,.55,.3,.9)));
        assertEquals(Attack.KICK,MarioCombat.classify(sample(0x18008ac,0x200000,0x66,0,0,0,0),ground));
    }
    @Test public void topContactRequiresDescentAndTopCrossing() {
        assertEquals(Attack.STOMP,MarioCombat.classify(sample(0x3000880,0,0,1,1.3,-30,0),ground));
        assertNull(MarioCombat.classify(sample(0x3000880,0,0,.4,.5,-30,0),ground));
        assertNull(MarioCombat.classify(sample(0x3000880,0,0,1,1,30,0),ground));
        assertNull(MarioCombat.classify(sample(0x3000880,0,0,1.3,1.5,-30,0),ground));
        assertEquals(Attack.GROUND_POUND,MarioCombat.classify(sample(0x8008a9,0,0,.8,1.5,-75,0),ground));
        assertNull(MarioCombat.classify(sample(0x8008a9,0,0,.4,.5,-75,0),ground));
    }
    @Test public void shortTargetSweepAndSlowedDiveFallback() {
        Target shortMob=new Target(0,0,0,.2,.4);
        assertEquals(Attack.STOMP,MarioCombat.classify(sample(0x3000880,0,0,0,.75,-75,0),shortMob));
        assertEquals(Attack.GROUND_POUND,MarioCombat.classify(sample(0x8008a9,0,0,0,.75,-75,0),shortMob));
        assertEquals(Attack.STOMP,MarioCombat.classify(sample(0x188088a,0,0,.3,.8,-50,25),shortMob));
        assertNull(MarioCombat.classify(sample(0x188088a,0,0,.3,.8,50,25),shortMob));
        assertEquals(Attack.STOMP,MarioCombat.classify(sample(0x18008ac,0,79,.3,.8,-50,0),shortMob));
        // Active kick misses its cone behind Mario, but the native aerial fallback still applies.
        assertEquals(Attack.STOMP,MarioCombat.classify(sample(0x18008ac,0x200000,79,.3,.8,-50,0),new Target(0,0,-.3,.2,.4)));
    }
    @Test public void comboKickRecoveryAndAirborneContactEpoch() {
        Ledger ledger=new Ledger();UUID id=UUID.randomUUID();
        ledger.advance(sample(0x800380,0x100000,0x67,0,0,0,0));ledger.consume(id);
        ledger.advance(sample(0x800457,0,0x69,0,0,0,0));assertTrue(ledger.contains(id));
        ledger.advance(sample(0x800457,0x100000,0x68,0,0,0,0));assertFalse(ledger.contains(id));ledger.consume(id);
        ledger.advance(sample(0x800457,0,0x6a,0,0,0,0));assertTrue(ledger.contains(id));
        ledger.advance(sample(0x800457,0x200000,0x66,0,0,0,0));assertFalse(ledger.contains(id));ledger.consume(id);
        ledger.advance(sample(0x800457,0,0x66,0,0,0,0));assertTrue(ledger.contains(id));
        ledger.advance(sample(0x3000880,0,0,1,1.5,-50,0));ledger.consume(id);
        ledger.advance(sample(0x3000880,0,0,1,1.5,-50,0));assertTrue(ledger.contains(id));
        ledger.advance(sample(0x3000880,0,0,1,1,30,0));
        ledger.advance(sample(0x3000880,0,0,1,1.5,-50,0));assertFalse(ledger.contains(id));
    }
    @Test public void diveSpeedAndExplicitDamageRules() {
        assertNull(MarioCombat.classify(sample(0x188088a,0,0,0,0,0,25.9),ground));
        assertEquals(Attack.DIVE,MarioCombat.classify(sample(0x188088a,0,0,0,0,0,26),ground));
        assertEquals(3,Attack.PUNCH.damage,0);assertEquals(4,Attack.KICK.damage,0);assertEquals(5,Attack.STOMP.damage,0);assertEquals(6,Attack.DIVE.damage,0);assertEquals(8,Attack.GROUND_POUND.damage,0);
    }
    @Test public void ledgerKeepsRecoveryAndRecoilButAllowsNextComboStage() {
        Ledger ledger=new Ledger();UUID id=UUID.randomUUID();
        ledger.advance(sample(0x800380,0x100000,0x67,0,0,0,0));ledger.consume(id);
        ledger.advance(sample(0x800457,0,0x69,0,0,0,-40));assertTrue(ledger.contains(id));
        ledger.advance(sample(0x800457,0x100000,0x68,0,0,0,0));assertFalse(ledger.contains(id));
        ledger.consume(id);ledger.advance(sample(0x800457,0,0x6a,0,0,0,0));assertTrue(ledger.contains(id));
        ledger.advance(sample(0x0c400201,0,0,0,0,0,0));ledger.advance(sample(0x800380,0x100000,0x67,0,0,0,0));assertFalse(ledger.contains(id));
    }
    @Test public void jumpKickStartsItsOwnStageAndPauseDoesNotResetIt() {
        Ledger ledger=new Ledger();UUID id=UUID.randomUUID();
        ledger.advance(sample(0x800380,0x100000,0x67,0,0,0,0));ledger.consume(id);
        Sample kick=sample(0x18008ac,0x200000,79,1,1,20,0);
        ledger.advance(kick);assertFalse(ledger.contains(id));ledger.consume(id);
        ledger.advance(kick);assertTrue(ledger.contains(id));
        ledger.advance(sample(0x18008ac,0,79,1,1,-20,0));assertTrue(ledger.contains(id));
    }
    @Test public void ledgerKeepsDiveSlideAndPoundLandingContacts() {
        Ledger ledger=new Ledger();UUID id=UUID.randomUUID();
        ledger.advance(sample(0x188088a,0,0,0,0,0,40));ledger.consume(id);
        ledger.advance(sample(0x880456,0,0,0,0,0,30));assertTrue(ledger.contains(id));
        ledger.advance(sample(0x8008a9,0,0,1,1.5,-75,0));ledger.consume(id);
        ledger.advance(sample(0x80023c,0,0,0,1,-75,0));assertTrue(ledger.contains(id));
        ledger.clear();assertFalse(ledger.contains(id));
    }
}
