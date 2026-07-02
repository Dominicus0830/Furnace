package org.domi.furnace.server.entity;

public record ItemStack(int itemId, int count, byte[] components) {

    public static final ItemStack EMPTY = new ItemStack(0, 0, null);

    public ItemStack {
        components = components != null ? components.clone() : null;
    }

    @Override
    public byte[] components() {
        return components != null ? components.clone() : null;
    }

    public boolean isEmpty() {
        return itemId == 0 || count == 0;
    }
}
