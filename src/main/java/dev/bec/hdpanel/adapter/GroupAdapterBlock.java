package dev.bec.hdpanel.adapter;

import cpw.mods.fml.common.registry.GameRegistry;
import dev.bec.hdpanel.HdPanelMod;
import li.cil.oc.api.detail.ItemInfo;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

public final class GroupAdapterBlock extends BlockContainer {
    private static final GroupAdapterBlock BLOCK = new GroupAdapterBlock();
    public static final String TITLE = "tile.ochd.groupAdapter.name";

    private GroupAdapterBlock() {
        super(Material.iron);
        setBlockName("ochd.groupAdapter");
        setBlockTextureName("ochdpanel:panel_case");
        setHardness(3);
        setCreativeTab(CreativeTabs.tabRedstone);
    }

    public static void register() {
        GameRegistry.registerBlock(BLOCK, "group_adapter");
        GameRegistry.registerTileEntity(GroupAdapterTile.class, "ochdpanel:group_adapter");
    }

    public static void registerRecipe() {
        ItemInfo adapter = li.cil.oc.api.Items.get("adapter");
        if (adapter != null) {
            GameRegistry.addRecipe(new ItemStack(BLOCK), "IRI", "RAR", "IRI",
                'I', Items.iron_ingot, 'R', Items.redstone, 'A', adapter.createItemStack(1));
        }
    }

    @Override public TileEntity createNewTileEntity(World world, int meta) {
        return new GroupAdapterTile();
    }

    @Override public int getRenderType() { return 0; }

    @Override public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase entity, ItemStack stack) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!world.isRemote && tile instanceof GroupAdapterTile && entity instanceof EntityPlayer) {
            ((GroupAdapterTile) tile).setOwner(entity.getUniqueID().toString());
        }
    }

    @Override public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player,
            int side, float hitX, float hitY, float hitZ) {
        if (player.isSneaking()) return false;
        if (world.isRemote) return true;
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof GroupAdapterTile && ((GroupAdapterTile) tile).isUseableByPlayer(player)) {
            player.openGui(HdPanelMod.instance, GroupAdapterGuiHandler.ID, world, x, y, z);
        } else {
            player.addChatMessage(new ChatComponentTranslation("ochd.groupAdapter.denied"));
        }
        return true;
    }

    @Override public void breakBlock(World world, int x, int y, int z, net.minecraft.block.Block block, int meta) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!world.isRemote && tile instanceof GroupAdapterTile) {
            GroupAdapterTile adapter = (GroupAdapterTile) tile;
            for (int slot = 0; slot < adapter.getSizeInventory(); slot++) {
                ItemStack stack = adapter.getStackInSlotOnClosing(slot);
                if (stack != null) world.spawnEntityInWorld(new EntityItem(world, x + .5, y + .5, z + .5, stack));
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
    }
}
