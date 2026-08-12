package com.arxyt.customnpcsysmcompat;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;
import noppes.npcs.entity.EntityNPCInterface;

public final class NpcAmmoItemHandler implements IItemHandlerModifiable {
    private static final int SLOT_COUNT = 9;
    private final EntityNPCInterface npc;

    public NpcAmmoItemHandler(EntityNPCInterface npc) {
        this.npc = npc;
    }

    static int inventorySlot(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            throw new RuntimeException("Slot " + slot + " not in valid range [0," + SLOT_COUNT + ")");
        }
        return slot == 0 ? 5 : slot + 6;
    }

    @Override
    public int getSlots() {
        return SLOT_COUNT;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return npc.inventory.getItem(inventorySlot(slot));
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack existing = getStackInSlot(slot);
        int limit = Math.min(getSlotLimit(slot), stack.getMaxStackSize());
        if (!existing.isEmpty() && !ItemHandlerHelper.canItemStacksStack(existing, stack)) {
            return stack;
        }
        int accepted = Math.min(limit - existing.getCount(), stack.getCount());
        if (accepted <= 0) {
            return stack;
        }
        if (!simulate) {
            ItemStack replacement = existing.isEmpty() ? stack.copy() : existing.copy();
            replacement.setCount(existing.getCount() + accepted);
            setStackInSlot(slot, replacement);
        }
        ItemStack remainder = stack.copy();
        remainder.shrink(accepted);
        return remainder;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        ItemStack existing = getStackInSlot(slot);
        if (existing.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        int extracted = Math.min(amount, existing.getCount());
        ItemStack result = existing.copy();
        result.setCount(extracted);
        if (!simulate) {
            ItemStack replacement = existing.copy();
            replacement.shrink(extracted);
            setStackInSlot(slot, replacement);
        }
        return result;
    }

    @Override
    public int getSlotLimit(int slot) {
        inventorySlot(slot);
        return 64;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        inventorySlot(slot);
        return true;
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        npc.inventory.setItem(inventorySlot(slot), stack.copy());
        npc.updateClient = true;
    }
}
