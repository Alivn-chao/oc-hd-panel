package dev.bec.hdpanel.mcp;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

public final class McpBoxBlock extends BlockContainer {
    public McpBoxBlock(){super(Material.iron);setBlockName("ochd.mcpCase");setBlockTextureName("ochdpanel:panel_case");setHardness(3);setCreativeTab(CreativeTabs.tabRedstone);}
    public TileEntity createNewTileEntity(World world,int meta){return new McpBox();}
    @Override public void onBlockPlacedBy(World w,int x,int y,int z,EntityLivingBase e,ItemStack s){if(!w.isRemote&&e instanceof EntityPlayer)((McpBox)w.getTileEntity(x,y,z)).owner(((EntityPlayer)e).getUniqueID().toString());}
    @Override public boolean onBlockActivated(World w,int x,int y,int z,EntityPlayer p,int side,float a,float b,float c){
        if(w.isRemote)return true;
        McpBox box=(McpBox)w.getTileEntity(x,y,z);String id=p.getUniqueID().toString();
        if(!box.owner.isEmpty()&&!box.owner.equals(id)){p.addChatMessage(new ChatComponentText("OCHD MCP: another player's case."));return true;}
        if(p.isSneaking()){box.toggle();p.addChatMessage(new ChatComponentText(!box.enabled?"OCHD MCP: access disabled.":"OCHD MCP: access enabled."));}
        else p.addChatMessage(new ChatComponentText("OCHD MCP: connect OC cables. Shift-right-click toggles access. "+(!box.enabled?"DISABLED":"ENABLED")));
        return true;
    }
}
