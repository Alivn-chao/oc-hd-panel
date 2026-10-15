package dev.bec.hdpanel;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.*;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.util.*;
import java.util.EnumSet;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

/** Optional AE2 power-only attachment: no channel requirement or idle charge. */
public final class PanelAEGrid implements IGridBlock {
    private final PanelTile tile;
    private final IGridNode node;
    private boolean ready;
    public PanelAEGrid(PanelTile tile,NBTTagCompound saved){this.tile=tile;node=AEApi.instance().createGridNode(this);node.loadFromNBT("node",saved);}
    public IGridNode node(){return ready?node:null;}
    public void ready(){if(!ready){ready=true;node.updateState();}}
    public void owner(net.minecraft.entity.player.EntityPlayer player){node.setPlayerID(AEApi.instance().registries().players().getID(player));}
    public void save(NBTTagCompound data){node.saveToNBT("node",data);}
    public void destroy(){node.destroy();ready=false;}
    public int drawRF(int needed,double rfPerAE){
        if(!ready||needed<=0||rfPerAE<=0||!Double.isFinite(rfPerAE)||node.getGrid()==null)return 0;
        IEnergyGrid energy=node.getGrid().getCache(IEnergyGrid.class);double cost=needed/rfPerAE;
        if(energy.extractAEPower(cost,Actionable.SIMULATE,PowerMultiplier.ONE)+1e-8<cost)return 0;
        double extracted=energy.extractAEPower(cost,Actionable.MODULATE,PowerMultiplier.ONE);
        int rf=PanelPower.equivalentRF(extracted,rfPerAE,needed);
        double remainder=extracted-rf/rfPerAE;if(remainder>0)energy.injectPower(remainder,Actionable.MODULATE);
        return rf;
    }
    public double getIdlePowerUsage(){return 0;}
    public EnumSet<GridFlags> getFlags(){return EnumSet.of(GridFlags.CANNOT_CARRY);}
    public boolean isWorldAccessible(){return true;}
    public DimensionalCoord getLocation(){return new DimensionalCoord(tile);}
    public AEColor getGridColor(){return AEColor.Transparent;}
    public void onGridNotification(GridNotification n){}
    public void setNetworkStatus(IGrid grid,int channels){}
    public EnumSet<ForgeDirection> getConnectableSides(){EnumSet<ForgeDirection> sides=EnumSet.allOf(ForgeDirection.class);sides.remove(ForgeDirection.UNKNOWN);sides.remove(ForgeDirection.getOrientation(PanelFacing.front(tile.getBlockMetadata())));return sides;}
    public IGridHost getMachine(){return tile;}
    public void gridChanged(){}
    public ItemStack getMachineRepresentation(){return new ItemStack(HdPanelMod.panel);}
}
