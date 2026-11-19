package dev.bec.hdpanel.adapter;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public final class GroupAdapterContainer extends Container {
    public static final int WIDTH = 306;
    public static final int HEIGHT = 192;
    public static final int GROUP_SIZE = 16;
    public static final int GROUP_WIDTH = 74;
    public static final int PLAYER_X = 73;
    private final GroupAdapterTile adapter;

    public GroupAdapterContainer(InventoryPlayer player, GroupAdapterTile adapter) {
        this.adapter = adapter;
        for (int i = 0; i < GroupAdapterTile.SLOTS; i++) {
            int group = i / GROUP_SIZE;
            int position = i % GROUP_SIZE;
            addSlotToContainer(new Slot(adapter, i, 7 + group * GROUP_WIDTH + position % 4 * 18,
                    29 + position / 4 * 18) {
                @Override public boolean isItemValid(ItemStack stack) {
                    return adapter.isItemValidForSlot(getSlotIndex(), stack);
                }
            });
        }
        for (int i = 0; i < 27; i++) {
            addSlotToContainer(new Slot(player, i + 9, PLAYER_X + i % 9 * 18, 112 + i / 9 * 18));
        }
        for (int i = 0; i < 9; i++) addSlotToContainer(new Slot(player, i, PLAYER_X + i * 18, 170));
    }

    @Override public boolean canInteractWith(EntityPlayer player) {
        return adapter.isUseableByPlayer(player);
    }

    @Override public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        if (index < 0 || index >= inventorySlots.size()) return null;
        Slot slot = getSlot(index);
        ItemStack stack = slot.getStack();
        if (stack == null) return null;
        ItemStack original = stack.copy();
        if (index < GroupAdapterTile.SLOTS) {
            if (!mergeItemStack(stack, GroupAdapterTile.SLOTS, inventorySlots.size(), true)) return null;
        } else {
            // The old mergeItemStack stacks only one empty slot and ignores the destination slot limit.
            boolean moved = false;
            for (int i = 0; i < GroupAdapterTile.SLOTS && stack.stackSize > 0; i++) {
                Slot destination = getSlot(i);
                if (destination.getStack() == null && destination.isItemValid(stack)) {
                    destination.putStack(stack.splitStack(1));
                    moved = true;
                }
            }
            if (!moved) return null;
        }
        if (stack.stackSize == 0) slot.putStack(null);
        else slot.onSlotChanged();
        slot.onPickupFromSlot(player, stack);
        return original;
    }
}
