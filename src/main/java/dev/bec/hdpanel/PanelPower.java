package dev.bec.hdpanel;

/** One physical battery per block: joining/splitting walls never copies stored RF. */
public final class PanelPower {
    public static int capacity=100000, maxReceive=4096, fullBrightnessCost=16,euSinkTier=4;
    public interface ExternalPower { boolean tryConsume(double amount); }
    private int energy;
    public int stored(){return energy;}
    public void restore(int value){energy=Math.max(0,Math.min(capacity,value));}
    public int receive(int amount,boolean simulate){
        int accepted=Math.max(0,Math.min(Math.min(amount,maxReceive),capacity-energy));
        if(!simulate)energy+=accepted;return accepted;
    }
    public static int brightness(int value){return Math.max(10,Math.min(100,value));}
    public static int cost(int blocks,int brightness){return blocks*((fullBrightnessCost*brightness(brightness)+99)/100);}
    public static int total(PanelPower[] batteries){int sum=0;for(PanelPower b:batteries)sum+=b.energy;return sum;}
    public static int receive(PanelPower[] batteries,int amount,boolean simulate){
        int remaining=Math.max(0,Math.min(amount,maxReceive)),accepted=0;
        for(PanelPower b:batteries){int added=b.receive(remaining,simulate);accepted+=added;remaining-=added;if(remaining==0)break;}return accepted;
    }
    public static boolean consume(PanelPower[] batteries,int amount){
        if(amount<0||total(batteries)<amount)return false;
        for(PanelPower b:batteries){int used=Math.min(b.energy,amount);b.energy-=used;amount-=used;if(amount==0)break;}return true;
    }
    public static boolean consumeMixed(PanelPower[] batteries,int amount,double ocPerRF,ExternalPower source){
        if(amount<0)return false;
        int rf=Math.min(total(batteries),amount),shortfall=amount-rf;
        if(shortfall>0&&(!Double.isFinite(ocPerRF)||ocPerRF<=0||!source.tryConsume(shortfall*ocPerRF)))return false;
        return consume(batteries,rf);
    }
    public static double rfPerEU(double ocPerEU,double ocPerRF){
        if(!Double.isFinite(ocPerEU)||!Double.isFinite(ocPerRF)||ocPerEU<=0||ocPerRF<=0)return 0;
        return ocPerEU/ocPerRF;
    }
    public static int equivalentRF(double eu,double rfPerEU,int limit){
        if(!Double.isFinite(eu)||eu<=0||!Double.isFinite(rfPerEU)||rfPerEU<=0||limit<=0)return 0;
        return (int)Math.min(limit,Math.floor(eu*rfPerEU));
    }
}
