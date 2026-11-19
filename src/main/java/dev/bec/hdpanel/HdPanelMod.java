package dev.bec.hdpanel;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import dev.bec.hdpanel.adapter.GroupAdapterBlock;
import dev.bec.hdpanel.adapter.GroupAdapterGuiHandler;

@Mod(modid = HdPanelMod.ID, name = "OC HD Panel", version = "0.5.0-alpha4-gtnh290",
    dependencies = "required-after:OpenComputers@[1.12.68-GTNH,)", acceptedMinecraftVersions = "[1.7.10]")
public class HdPanelMod {
    public static final String ID = "ochdpanel";
    public static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(ID);
    public static PanelBlock panel;
    @Mod.Instance(ID)
    public static HdPanelMod instance;
    @SidedProxy(clientSide = "dev.bec.hdpanel.client.ClientProxy", serverSide = "dev.bec.hdpanel.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        net.minecraftforge.common.config.Configuration config=new net.minecraftforge.common.config.Configuration(event.getSuggestedConfigurationFile());
        config.load();
        PanelPower.capacity=config.getInt("capacityPerBlock","power",100000,1000,10000000,"RF buffer in each physical screen block");
        PanelPower.maxReceive=config.getInt("maxReceive","power",4096,1,1000000,"Maximum RF accepted per connection call");
        PanelPower.fullBrightnessCost=config.getInt("rfPerBlockPerTick","power",16,1,10000,"RF/t per block at 100% brightness");
        PanelPower.euSinkTier=config.getInt("euSinkTier","power",4,1,14,"Maximum IC2 EU sink tier (4 = EV). OC converter input uses OC's own settings.");
        dev.bec.hdpanel.mcp.McpHardware.diskBytes = config.getInt("diskMiB", "mcp", 64, 1, 1024, "Capacity of newly created OCHD disks in MiB") * 1048576L;
        if(config.hasChanged())config.save();
        dev.bec.hdpanel.mcp.McpHardware.register();
        GroupAdapterBlock.register();
        NetworkRegistry.INSTANCE.registerGuiHandler(this, new GroupAdapterGuiHandler());
        panel = new PanelBlock();
        GameRegistry.registerBlock(panel, "panel");
        GameRegistry.registerTileEntity(PanelTile.class, ID + ":panel");
        GameRegistry.addRecipe(new ItemStack(panel), "GGG", "IRI", "III", 'G', Blocks.glass,
            'I', Items.iron_ingot, 'R', Items.redstone);
        NETWORK.registerMessage(ActionPacket.Handler.class, ActionPacket.class, 0, Side.SERVER);
        NETWORK.registerMessage(ActionReplyPacket.Handler.class, ActionReplyPacket.class, 1, Side.CLIENT);
        NETWORK.registerMessage(WebAssetRequest.Handler.class,WebAssetRequest.class,2,Side.SERVER);
        NETWORK.registerMessage(WebAssetPacket.Handler.class,WebAssetPacket.class,3,Side.CLIENT);
        NETWORK.registerMessage(PanelSettingsPacket.Handler.class,PanelSettingsPacket.class,4,Side.SERVER);
        NETWORK.registerMessage(dev.bec.hdpanel.mcp.McpPacket.ServerHandler.class, dev.bec.hdpanel.mcp.McpPacket.class, 5, Side.SERVER);
        NETWORK.registerMessage(dev.bec.hdpanel.mcp.McpPacket.ClientHandler.class, dev.bec.hdpanel.mcp.McpPacket.Reply.class, 6, Side.CLIENT);
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new dev.bec.hdpanel.mcp.McpPacket.ServerHandler());
        proxy.init();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        GroupAdapterBlock.registerRecipe();
    }
}
