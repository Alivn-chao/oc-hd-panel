package dev.bec.hdpanel.adapter;

import cpw.mods.fml.common.network.IGuiHandler;
import dev.bec.hdpanel.HdPanelMod;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public final class GroupAdapterGuiHandler implements IGuiHandler {
    public static final int ID = 1;

    @Override public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id != ID) return null;
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof GroupAdapterTile) || !((GroupAdapterTile) tile).isUseableByPlayer(player)) return null;
        return new GroupAdapterContainer(player.inventory, (GroupAdapterTile) tile);
    }

    @Override public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id != ID) return null;
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof GroupAdapterTile)) return null;
        return HdPanelMod.proxy.groupAdapterGui(new GroupAdapterContainer(player.inventory, (GroupAdapterTile) tile));
    }
}
