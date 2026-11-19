package dev.bec.hdpanel.client;

import dev.bec.hdpanel.adapter.GroupAdapterBlock;
import dev.bec.hdpanel.adapter.GroupAdapterContainer;
import dev.bec.hdpanel.adapter.GroupAdapterTile;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Slot;

final class GroupAdapterGui extends GuiContainer {
    GroupAdapterGui(GroupAdapterContainer container) {
        super(container);
        xSize = GroupAdapterContainer.WIDTH;
        ySize = GroupAdapterContainer.HEIGHT;
    }

    @Override protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xff60748a);
        drawRect(guiLeft + 2, guiTop + 2, guiLeft + xSize - 2, guiTop + ySize - 2, 0xff202b3a);
        for (int i = 0; i < inventorySlots.inventorySlots.size(); i++) {
            Slot slot = inventorySlots.getSlot(i);
            int x = guiLeft + slot.xDisplayPosition;
            int y = guiTop + slot.yDisplayPosition;
            drawRect(x - 1, y - 1, x + 17, y + 17, 0xff475a70);
            drawRect(x, y, x + 16, y + 16, 0xff111923);
            if (i < GroupAdapterTile.SLOTS && !slot.getHasStack()) {
                String number = Integer.toString(i + 1);
                fontRendererObj.drawString(number, x + (16 - fontRendererObj.getStringWidth(number)) / 2, y + 4, 0x8a9aac);
            }
        }
    }

    @Override protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRendererObj.drawString(I18n.format(GroupAdapterBlock.TITLE), 8, 7, 0xe2edf8);
        for (int group = 0; group < GroupAdapterTile.SLOTS / GroupAdapterContainer.GROUP_SIZE; group++) {
            int first = group * GroupAdapterContainer.GROUP_SIZE + 1;
            String range = first + "–" + (first + GroupAdapterContainer.GROUP_SIZE - 1);
            fontRendererObj.drawString(range, 7 + group * GroupAdapterContainer.GROUP_WIDTH, 18, 0xabbdd0);
        }
        fontRendererObj.drawString(I18n.format("container.inventory"), GroupAdapterContainer.PLAYER_X, 101, 0xe2edf8);
    }
}
