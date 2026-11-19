package dev.bec.hdpanel.adapter;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.detail.ItemInfo;
import li.cil.oc.api.driver.SidedBlock;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.SidedEnvironment;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.TileEntityEnvironment;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

public final class GroupAdapterTile extends TileEntityEnvironment implements IInventory {
    public static final int SLOTS = 64;
    private static final int RETRY_TICKS = 20;
    private final ItemStack[] items = new ItemStack[SLOTS];
    private final Link[] links = new Link[SLOTS];
    private String owner = "";
    private boolean bindingsChanged = true;

    public GroupAdapterTile() {
        node = Network.newNode(this, Visibility.Network).withComponent("ochd_adapter", Visibility.Network).create();
        for (int i = 0; i < SLOTS; i++) links[i] = new Link();
    }

    private static final class Link {
        int[] target;
        TileEntity tile;
        ManagedEnvironment driver;
        Component component;
        NBTTagCompound saved;
        long nextProbe;
        String status = "unbound";
        boolean duplicate;
    }

    private static int[] target(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) return null;
        int[] coord = stack.getTagCompound().getIntArray("oc:coord");
        // The native MFU stores x, y, z, dimension and Forge face in that order.
        return coord.length == 5 && coord[4] >= 0 && coord[4] < ForgeDirection.VALID_DIRECTIONS.length ? coord : null;
    }

    void setOwner(String value) { owner = value; markDirty(); }

    @Override public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) return;
        super.updateEntity();
        if (node.network() == null) return;

        if (bindingsChanged) updateDuplicates();
        long tick = worldObj.getTotalWorldTime();
        for (Link link : links) {
            if (link.target != null && !link.duplicate) updateLink(link, tick);
        }
    }

    private void updateDuplicates() {
        bindingsChanged = false;
        for (int i = 0; i < SLOTS; i++) {
            Link link = links[i];
            link.duplicate = false;
            if (link.target == null) continue;
            for (int earlier = 0; earlier < i; earlier++) {
                if (Arrays.equals(links[earlier].target, link.target)) {
                    link.duplicate = true;
                    break;
                }
            }
            if (link.duplicate) {
                detach(link);
                link.status = "duplicate";
            } else if ("duplicate".equals(link.status)) {
                link.status = "connecting";
                link.nextProbe = 0;
            }
        }
    }

    private void updateLink(Link link, long tick) {
        if (link.component == null && tick < link.nextProbe) return;
        int[] pos = link.target;
        if (pos[3] != worldObj.provider.dimensionId) {
            offline(link, "other_dimension", tick);
            return;
        }
        if (!worldObj.blockExists(pos[0], pos[1], pos[2])) {
            offline(link, "unloaded", tick);
            return;
        }
        TileEntity tile = worldObj.getTileEntity(pos[0], pos[1], pos[2]);
        if (tile == null || tile.isInvalid() || tile == this) {
            offline(link, "no_target", tick);
            return;
        }
        if (link.tile != tile || link.component == null || link.component.network() != node.network()) {
            detach(link);
            try {
                attach(link, tile, ForgeDirection.getOrientation(pos[4]));
            } catch (RuntimeException | LinkageError failure) {
                offline(link, "driver_error", tick);
                return;
            }
        }
        if (link.component == null) {
            link.nextProbe = tick + RETRY_TICKS;
            return;
        }
        if (link.driver != null && link.driver.canUpdate()) {
            try {
                link.driver.update();
            } catch (RuntimeException | LinkageError failure) {
                offline(link, "driver_error", tick);
            }
        }
    }

    private void attach(Link link, TileEntity tile, ForgeDirection side) {
        link.tile = tile;
        if (tile instanceof Environment || tile instanceof SidedEnvironment) {
            Node existing = tile instanceof SidedEnvironment ? ((SidedEnvironment) tile).sidedNode(side)
                : ((Environment) tile).node();
            // A remote cable or computer must not merge two separate OC networks.
            if (existing instanceof Component && existing.network() == node.network()) {
                link.component = (Component) existing;
                link.status = "online";
            } else {
                link.status = "native_not_connected";
            }
            return;
        }
        int[] pos = link.target;
        SidedBlock factory = Driver.driverFor(worldObj, pos[0], pos[1], pos[2], side);
        ManagedEnvironment driver = factory == null ? null : factory.createEnvironment(worldObj, pos[0], pos[1], pos[2], side);
        if (driver == null || !(driver.node() instanceof Component)) {
            link.status = "no_driver";
            return;
        }
        link.driver = driver;
        String driverClass = driver.getClass().getName();
        if (link.saved != null && driverClass.equals(link.saved.getString("driver"))) {
            driver.load(link.saved.getCompoundTag("data"));
        }
        Node component = driver.node();
        node.connect(component);
        link.component = (Component) component;
        link.status = "online";
        markDirty();
    }

    private void offline(Link link, String status, long tick) {
        detach(link);
        link.status = status;
        link.nextProbe = tick + RETRY_TICKS;
    }

    private static void saveDriver(Link link) {
        if (link.driver == null) return;
        NBTTagCompound data = new NBTTagCompound();
        link.driver.save(data);
        NBTTagCompound saved = new NBTTagCompound();
        saved.setString("driver", link.driver.getClass().getName());
        saved.setTag("data", data);
        link.saved = saved;
    }

    private static void detach(Link link) {
        ManagedEnvironment driver = link.driver;
        if (driver != null) {
            try {
                saveDriver(link);
            } catch (RuntimeException | LinkageError ignored) {
                // A broken third-party driver must not prevent its node from being removed.
            } finally {
                link.driver = null;
                if (driver.node() != null) driver.node().remove();
            }
        }
        link.component = null;
        link.tile = null;
    }

    @Override public void invalidate() {
        for (Link link : links) detach(link);
        super.invalidate();
    }

    @Override public void onChunkUnload() {
        for (Link link : links) detach(link);
        super.onChunkUnload();
    }

    private Map<Integer, Map<String, Object>> listRows(Context context, String filter) {
        Map<Integer, Map<String, Object>> result = new LinkedHashMap<>();
        for (int i = 0; i < SLOTS; i++) {
            ItemStack stack = items[i];
            if (stack == null) continue;
            String label = stack.hasDisplayName() ? stack.getDisplayName().trim() : Integer.toString(i + 1);
            String group = "default";
            int slash = label.indexOf('/');
            if (slash > 0 && slash < label.length() - 1) {
                group = label.substring(0, slash).trim();
                label = label.substring(slash + 1).trim();
            }
            if (!filter.isEmpty() && !filter.equals(group)) continue;

            Link link = links[i];
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("slot", i + 1);
            row.put("group", group);
            row.put("label", label);
            row.put("status", link.status);
            if (link.target != null) {
                row.put("x", link.target[0]);
                row.put("y", link.target[1]);
                row.put("z", link.target[2]);
                row.put("dimension", link.target[3]);
                row.put("side", link.target[4]);
            }
            if (link.component != null && link.component.network() == context.node().network()
                    && link.component.canBeSeenFrom(context.node())) {
                row.put("address", link.component.address());
                row.put("type", link.component.name());
            } else if ("online".equals(link.status)) {
                row.put("status", "not_visible");
            }
            result.put(i + 1, row);
        }
        return result;
    }

    @Callback(doc = "function([group:string]):table -- List installed MFUs by slot, with target status and OC address.")
    public Object[] list(Context context, Arguments args) {
        return new Object[] {listRows(context, args.optString(0, ""))};
    }

    private BecNodeAccess becNode(Context context, int slot, String expectedAddress) throws ReflectiveOperationException {
        if (slot < 1 || slot > SLOTS) throw new IllegalArgumentException("slot must be 1–64");
        Link link = links[slot - 1];
        Component component = link.component;
        if (component == null || !"bec_io_node".equals(component.name())
                || component.network() == null || component.network() != context.node().network()
                || !component.canBeSeenFrom(context.node()) || !expectedAddress.equals(component.address())) {
            throw new IllegalStateException("slot no longer exposes the expected BEC node");
        }
        int[] pos = link.target;
        if (pos == null || pos[3] != worldObj.provider.dimensionId || !worldObj.blockExists(pos[0], pos[1], pos[2])
                || link.tile == null || link.tile.isInvalid() || worldObj.getTileEntity(pos[0], pos[1], pos[2]) != link.tile) {
            throw new IllegalStateException("BEC node is unloaded or has changed");
        }
        return BecNodeAccess.open(link.tile);
    }

    @Callback(doc = "function([group:string]):table -- Read BEC state, work permission, parallel limits and progress by slot.")
    public Object[] becStatus(Context context, Arguments args) {
        Map<Integer, Map<String, Object>> rows = listRows(context, args.optString(0, ""));
        for (Map.Entry<Integer, Map<String, Object>> entry : rows.entrySet()) {
            Map<String, Object> row = entry.getValue();
            if (!"bec_io_node".equals(row.get("type"))) continue;
            try {
                row.putAll(becNode(context, entry.getKey(), (String) row.get("address")).snapshot());
            } catch (Exception | LinkageError failure) {
                row.put("error", BecNodeAccess.message(failure));
            }
        }
        return new Object[] {rows};
    }

    @Callback(doc = "function(plan:table):table -- Apply and read back parallel limits on idle, closed BEC nodes.")
    public Object[] becConfigure(Context context, Arguments args) {
        return new Object[] {BecBatch.configure(args.checkTable(0), SLOTS,
            (slot, address) -> becNode(context, slot, address))};
    }

    @Callback(doc = "function(slot:number, address:string, allowed:boolean[, quota:number]):table -- Change BEC admission and read it back.")
    public Object[] becSetAdmission(Context context, Arguments args) throws ReflectiveOperationException {
        BecNodeAccess access = becNode(context, args.checkInteger(0), args.checkString(1));
        boolean allowed = args.checkBoolean(2);
        if (allowed) access.openAdmission(args.checkInteger(3));
        else access.closeAdmission();
        return new Object[] {access.snapshot()};
    }

    @Override public int getSizeInventory() { return SLOTS; }
    @Override public int getInventoryStackLimit() { return 1; }
    @Override public String getInventoryName() { return GroupAdapterBlock.TITLE; }
    @Override public boolean hasCustomInventoryName() { return false; }
    @Override public ItemStack getStackInSlot(int slot) { return items[slot]; }
    @Override public boolean isItemValidForSlot(int slot, ItemStack stack) {
        ItemInfo item = stack == null ? null : li.cil.oc.api.Items.get(stack);
        return item != null && "mfu".equals(item.name());
    }

    @Override public void setInventorySlotContents(int slot, ItemStack stack) {
        items[slot] = stack;
        Link link = links[slot];
        int[] next = isItemValidForSlot(slot, stack) ? target(stack) : null;
        if (!Arrays.equals(next, link.target)) {
            detach(link);
            link.saved = null;
            link.target = next;
            link.status = next == null ? "unbound" : "connecting";
            link.nextProbe = 0;
            bindingsChanged = true;
        }
        markDirty();
    }

    @Override public ItemStack decrStackSize(int slot, int count) {
        return count > 0 ? getStackInSlotOnClosing(slot) : null;
    }

    @Override public ItemStack getStackInSlotOnClosing(int slot) {
        ItemStack stack = items[slot];
        if (stack != null) setInventorySlotContents(slot, null);
        return stack;
    }

    @Override public boolean isUseableByPlayer(EntityPlayer player) {
        return worldObj != null && (worldObj.isRemote || owner.isEmpty() || owner.equals(player.getUniqueID().toString()))
            && worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
            && player.getDistanceSq(xCoord + .5, yCoord + .5, zCoord + .5) <= 64;
    }

    @Override public void openInventory() { }
    @Override public void closeInventory() { }

    @Override public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        owner = nbt.getString("owner");
        bindingsChanged = true;
        NBTTagList entries = nbt.getTagList("links", 10);
        for (int i = 0; i < entries.tagCount(); i++) {
            NBTTagCompound entry = entries.getCompoundTagAt(i);
            int slot = entry.getInteger("slot");
            if (slot < 0 || slot >= SLOTS) continue;
            items[slot] = ItemStack.loadItemStackFromNBT(entry.getCompoundTag("item"));
            links[slot].target = isItemValidForSlot(slot, items[slot]) ? target(items[slot]) : null;
            if (links[slot].target != null) {
                links[slot].saved = entry.getCompoundTag("environment");
                links[slot].status = "connecting";
            }
        }
    }

    @Override public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setString("owner", owner);
        NBTTagList entries = new NBTTagList();
        for (int i = 0; i < SLOTS; i++) {
            if (items[i] == null) continue;
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger("slot", i);
            entry.setTag("item", items[i].writeToNBT(new NBTTagCompound()));
            Link link = links[i];
            try {
                saveDriver(link);
            } catch (RuntimeException | LinkageError ignored) {
                // Keep the last good driver snapshot if the driver cannot save its current state.
            }
            if (link.saved != null) entry.setTag("environment", link.saved.copy());
            entries.appendTag(entry);
        }
        nbt.setTag("links", entries);
    }
}
