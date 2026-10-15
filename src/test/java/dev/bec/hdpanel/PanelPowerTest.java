package dev.bec.hdpanel;

import org.junit.Test;
import static org.junit.Assert.*;

public class PanelPowerTest {
    @Test public void rfSimulationNeverChargesAndReceiveIsBounded(){
        PanelPower b=new PanelPower();assertEquals(0,b.receive(-100,false));assertEquals(4096,b.receive(10000,true));assertEquals(0,b.stored());
        b.restore(99990);assertEquals(10,b.receive(1000,false));assertEquals(100000,b.stored());assertEquals(0,b.receive(1,false));
    }
    @Test public void theWholeWallMustHaveEnoughRfBeforeAnythingIsDebited(){
        PanelPower left=new PanelPower(),right=new PanelPower();left.restore(10);right.restore(21);
        PanelPower[] wall={left,right};assertFalse(PanelPower.consume(wall,32));assertEquals(10,left.stored());assertEquals(21,right.stored());
        right.receive(1,false);assertTrue(PanelPower.consume(wall,32));assertEquals(0,PanelPower.total(wall));
    }
    @Test public void oneCableFillsTheSharedWallWithoutCopyingCharge(){
        PanelPower a=new PanelPower(),b=new PanelPower();a.restore(100000);PanelPower[] wall={a,b};
        assertEquals(4096,PanelPower.receive(wall,100000,true));assertEquals(100000,PanelPower.total(wall));
        assertEquals(4096,PanelPower.receive(wall,100000,false));assertEquals(4096,b.stored());
        b.restore(99999);assertEquals(1,PanelPower.receive(wall,100,false));assertEquals(200000,PanelPower.total(wall));
    }
    @Test public void splittingAndRejoiningOnlyRegroupsPhysicalBatteries(){
        PanelPower a=new PanelPower(),b=new PanelPower();a.restore(500);b.restore(1000);
        assertEquals(1500,PanelPower.total(new PanelPower[]{a,b}));assertTrue(PanelPower.consume(new PanelPower[]{a},16));
        assertEquals(1484,PanelPower.total(new PanelPower[]{a,b}));assertEquals(1000,b.stored());
    }
    @Test public void brightnessLowersCostWithoutFreePowerOrInvalidLevels(){
        assertEquals(16,PanelPower.cost(1,100));assertEquals(8,PanelPower.cost(1,50));assertEquals(2,PanelPower.cost(1,10));
        assertEquals(480,PanelPower.cost(30,100));assertEquals(60,PanelPower.cost(30,10));
        assertEquals(10,PanelPower.brightness(-1));assertEquals(100,PanelPower.brightness(10000));
        PanelPower b=new PanelPower();b.restore(-1);assertEquals(0,b.stored());b.restore(Integer.MAX_VALUE);assertEquals(100000,b.stored());
    }
    @Test public void ocPowerSuppliesTheExactDeficitWithoutSpendingRfOnFailure(){
        PanelPower b=new PanelPower();b.restore(6);final double[] debited={0};
        PanelPower.ExternalPower source=amount->{debited[0]=amount;return true;};
        assertTrue(PanelPower.consumeMixed(new PanelPower[]{b},16,.1,source));
        assertEquals(1,debited[0],1e-12);assertEquals(0,b.stored());
        b.restore(6);assertFalse(PanelPower.consumeMixed(new PanelPower[]{b},16,.1,amount->false));assertEquals(6,b.stored());
    }
    @Test public void aFullyChargedRfBufferNeverDrawsOcPower(){
        PanelPower b=new PanelPower();b.restore(32);
        assertTrue(PanelPower.consumeMixed(new PanelPower[]{b},16,.1,amount->{fail("unnecessary OC withdrawal");return false;}));assertEquals(16,b.stored());
    }
    @Test public void conversionsFollowOcRatiosAndNeverCreditMoreEnergyThanSupplied(){
        assertEquals(4,PanelPower.rfPerEU(.4,.1),1e-12);assertEquals(2,PanelPower.rfPerEU(.2,.1),1e-12);
        assertEquals(10,PanelPower.equivalentRF(2.5,4,4096));
        assertEquals(4096,PanelPower.equivalentRF(100000,4,4096));assertEquals(0,PanelPower.equivalentRF(.1,4,4096));
        for(int n=1;n<1000;n++){double input=n/17.0;int rf=PanelPower.equivalentRF(input,4,4096);assertTrue(rf/4.0<=input);}
        assertEquals(0,PanelPower.equivalentRF(Double.NaN,4,4096));assertEquals(0,PanelPower.rfPerEU(.4,0),0);
    }
}
