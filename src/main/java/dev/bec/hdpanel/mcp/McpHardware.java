package dev.bec.hdpanel.mcp;

import li.cil.oc.api.Driver;
import li.cil.oc.api.FileSystem;
import li.cil.oc.api.driver.item.HostAware;
import li.cil.oc.api.driver.item.Slot;
import li.cil.oc.api.machine.MachineHost;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.prefab.DriverItem;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import java.util.UUID;

public final class McpHardware {
    public static long diskBytes = 64L * 1024 * 1024;
    public static final McpBoxBlock BOX = new McpBoxBlock();
    public static final Item DISK = new Item().setUnlocalizedName("ochd.disk").setTextureName("minecraft:record_11").setMaxStackSize(1).setCreativeTab(CreativeTabs.tabRedstone);
    public static void register() {
        GameRegistry.registerBlock(BOX, "mcp_case");
        GameRegistry.registerTileEntity(McpBox.class, "ochdpanel:mcp_case");
        GameRegistry.registerItem(DISK, "large_disk");
        Driver.add(new HardwareDriver(DISK));
        GameRegistry.addRecipe(new ItemStack(BOX), "IRI", "RER", "IRI", 'I', Items.iron_ingot, 'R', Items.redstone, 'E', Items.ender_pearl);
        GameRegistry.addRecipe(new ItemStack(DISK), "III", "DED", "III", 'I', Items.iron_ingot, 'D', Items.diamond, 'E', Items.ender_pearl);
    }
    private static class HardwareDriver extends DriverItem implements HostAware {
        HardwareDriver(Item item) { super(new ItemStack(item)); }
        @Override public boolean worksWith(ItemStack stack, Class<? extends EnvironmentHost> host) { return worksWith(stack) && MachineHost.class.isAssignableFrom(host); }
        @Override public String slot(ItemStack stack) { return Slot.HDD; }
        @Override public int tier(ItemStack stack) { return 0; }
        @Override public ManagedEnvironment createEnvironment(ItemStack stack, EnvironmentHost host) {
            if (!(host instanceof MachineHost) || host.world().isRemote) return null;
            NBTTagCompound data = dataTag(stack);
            if (!data.hasKey("ochdDisk")) data.setString("ochdDisk", UUID.randomUUID().toString());
            if (!data.hasKey("ochdCapacity")) data.setLong("ochdCapacity", diskBytes);
            return FileSystem.asManagedEnvironment(FileSystem.fromSaveDirectory("ochd-" + data.getString("ochdDisk"), Math.max(1048576L, Math.min(1073741824L, data.getLong("ochdCapacity")))), "OCHD storage", host, null, 1);
        }
    }
}
