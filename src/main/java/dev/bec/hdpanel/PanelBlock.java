package dev.bec.hdpanel;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

public class PanelBlock extends BlockContainer {
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
    @Override public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase entity, ItemStack stack) {
        int direction = MathHelper.floor_double(entity.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
        world.setBlockMetadataWithNotify(x, y, z, (direction + 2) & 3, 2);
    }
    @Override public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player,
            int side, float hitX, float hitY, float hitZ) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (world.isRemote && tile instanceof PanelTile) HdPanelMod.proxy.open((PanelTile) tile);
        return true;
    }
}
