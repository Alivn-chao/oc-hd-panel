package dev.bec.hdpanel;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

@Mod(modid = HdPanelMod.ID, name = "OC HD Panel", version = "0.4.0-alpha2-html",
    dependencies = "required-after:OpenComputers@[1.12.61-GTNH,)", acceptedMinecraftVersions = "[1.7.10]")
public class HdPanelMod {
    public static final String ID = "ochdpanel";
    public static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(ID);
    public static PanelBlock panel;
    @SidedProxy(clientSide = "dev.bec.hdpanel.client.ClientProxy", serverSide = "dev.bec.hdpanel.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        panel = new PanelBlock();
        GameRegistry.registerBlock(panel, "panel");
        GameRegistry.registerTileEntity(PanelTile.class, ID + ":panel");
        GameRegistry.addRecipe(new ItemStack(panel), "GGG", "IRI", "III", 'G', Blocks.glass,
            'I', Items.iron_ingot, 'R', Items.redstone);
        NETWORK.registerMessage(ActionPacket.Handler.class, ActionPacket.class, 0, Side.SERVER);
        NETWORK.registerMessage(ActionReplyPacket.Handler.class, ActionReplyPacket.class, 1, Side.CLIENT);
        NETWORK.registerMessage(WebAssetRequest.Handler.class,WebAssetRequest.class,2,Side.SERVER);
        NETWORK.registerMessage(WebAssetPacket.Handler.class,WebAssetPacket.class,3,Side.CLIENT);
        proxy.init();
    }
}
