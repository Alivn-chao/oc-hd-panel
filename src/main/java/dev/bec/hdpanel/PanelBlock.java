package dev.bec.hdpanel;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.world.IBlockAccess;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.world.World;

public class PanelBlock extends BlockContainer {
    @SideOnly(Side.CLIENT) private IIcon[] fronts,backs;
    public PanelBlock() {
        super(Material.iron);
        setBlockName("ochdpanel.panel");
        setBlockTextureName("ochdpanel:panel");
        setHardness(2.0F);
        setCreativeTab(CreativeTabs.tabRedstone);
        setBlockBounds(0, 0, 0, 1, 1, 1);
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new PanelTile(); }
    @Override public int getRenderType() { return 0; }
    @Override public boolean isOpaqueCube() { return false; }
    @Override public boolean renderAsNormalBlock() { return false; }
    @Override @SideOnly(Side.CLIENT) public void registerBlockIcons(IIconRegister icons) {
        blockIcon=icons.registerIcon("ochdpanel:panel_case");fronts=new IIcon[16];backs=new IIcon[16];
        for(int i=0;i<16;i++){fronts[i]=icons.registerIcon("ochdpanel:panel_front_"+i);backs[i]=icons.registerIcon("ochdpanel:panel_back_"+i);}
    }
    @Override @SideOnly(Side.CLIENT) public IIcon getIcon(int side,int meta) {
        return side==PanelFacing.front(meta)?fronts[15]:side==PanelFacing.front(meta^2)?backs[15]:blockIcon;
    }
    @Override @SideOnly(Side.CLIENT) public IIcon getIcon(IBlockAccess world,int x,int y,int z,int side) {
        int meta=world.getBlockMetadata(x,y,z);boolean rear=side==PanelFacing.front(meta^2);
        if(side!=PanelFacing.front(meta)&&!rear)return blockIcon;
        TileEntity entity=world.getTileEntity(x,y,z);if(!(entity instanceof PanelTile))return getIcon(side,meta);
        PanelTile tile=(PanelTile)entity;
        int edges=PanelFacing.edges(rear?tile.wallWidth-1-tile.column:tile.column,tile.row,tile.wallWidth,tile.wallHeight);
        return rear?backs[edges]:fronts[edges];
    }
    @Override public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase entity, ItemStack stack) {
        world.setBlockMetadataWithNotify(x, y, z,PanelFacing.placed(entity.rotationYaw),2);
        if(!world.isRemote&&entity instanceof EntityPlayer&&cpw.mods.fml.common.Loader.isModLoaded("appliedenergistics2")){
            TileEntity tile=world.getTileEntity(x,y,z);if(tile instanceof PanelTile)((PanelTile)tile).aeOwner((EntityPlayer)entity);
        }
    }
    @Override public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player,
            int side, float hitX, float hitY, float hitZ) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (world.isRemote && tile instanceof PanelTile) {
            if(side==PanelFacing.front(world.getBlockMetadata(x,y,z)))HdPanelMod.proxy.open((PanelTile) tile);
            else HdPanelMod.proxy.openSettings((PanelTile)tile);
        }
        return true;
    }
}
