// playerdata/<uuid>.dat (gzip NBT) 위치 read/write. M4c가 Inventory/Health 확장.
package org.domi.furnace.server.entity;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;

public final class PlayerData {

    public record Snapshot(double x, double y, double z, float yaw, float pitch,
                           boolean onGround, ItemStack[] slots) {}

    private PlayerData() {}

    private static Path file(Path worldDir, UUID id) {
        return worldDir.resolve("playerdata").resolve(id + ".dat");
    }

    public static void save(Path worldDir, UUID id, double x, double y, double z,
                            float yaw, float pitch, boolean onGround) {
        ItemStack[] slots = null;
        Snapshot existing = load(worldDir, id);
        if (existing != null) slots = existing.slots();
        save(worldDir, id, x, y, z, yaw, pitch, onGround, slots);
    }

    public static void save(Path worldDir, UUID id, double x, double y, double z,
                            float yaw, float pitch, boolean onGround, ItemStack[] slots) {
        NbtCompound root = new NbtCompound();
        NbtList pos = new NbtList(NbtIo.TAG_DOUBLE);
        pos.add(x); pos.add(y); pos.add(z);
        root.putList("Pos", pos);
        NbtList rot = new NbtList(NbtIo.TAG_FLOAT);
        rot.add(yaw); rot.add(pitch);
        root.putList("Rotation", rot);
        root.putByte("OnGround", (byte) (onGround ? 1 : 0));
        root.putList("Inventory", inventoryToNbt(slots));
        try {
            Path f = file(worldDir, id);
            Files.createDirectories(f.getParent());
            try (var os = Files.newOutputStream(f)) {
                NbtIo.writeGzip(os, root, "");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("playerdata 저장 실패 " + id, e);
        }
    }

    public static Snapshot load(Path worldDir, UUID id) {
        Path f = file(worldDir, id);
        if (!Files.exists(f)) return null;
        try (var is = Files.newInputStream(f)) {
            NbtCompound root = NbtIo.readGzip(is);
            NbtList pos = root.getList("Pos");
            NbtList rot = root.getList("Rotation");
            return new Snapshot(
                    (Double) pos.get(0), (Double) pos.get(1), (Double) pos.get(2),
                    (Float) rot.get(0), (Float) rot.get(1),
                    root.getByte("OnGround") != 0,
                    inventoryFromNbt(root.contains("Inventory") ? root.getList("Inventory") : null)
            );
        } catch (IOException e) {
            throw new UncheckedIOException("playerdata 로드 실패 " + id, e);
        }
    }

    private static NbtList inventoryToNbt(ItemStack[] slots) {
        NbtList inventory = new NbtList(NbtIo.TAG_COMPOUND);
        if (slots == null) return inventory;
        for (int i = 0; i < slots.length; i++) {
            ItemStack stack = slots[i];
            if (stack == null || stack.isEmpty()) continue;
            NbtCompound entry = new NbtCompound();
            entry.putInt("Slot", i);
            entry.putInt("Id", stack.itemId());
            entry.putInt("Count", stack.count());
            byte[] components = stack.components();
            if (components != null && components.length > 0) entry.putByteArray("Comp", components);
            inventory.add(entry);
        }
        return inventory;
    }

    private static ItemStack[] inventoryFromNbt(NbtList inventory) {
        ItemStack[] slots = emptySlots();
        if (inventory == null) return slots;
        for (Object entryObj : inventory.items()) {
            NbtCompound entry = (NbtCompound) entryObj;
            int slot = entry.getInt("Slot");
            if (slot < 0 || slot >= slots.length) continue;
            byte[] components = entry.contains("Comp") ? entry.getByteArray("Comp") : null;
            slots[slot] = new ItemStack(entry.getInt("Id"), entry.getInt("Count"), components);
        }
        return slots;
    }

    private static ItemStack[] emptySlots() {
        ItemStack[] slots = new ItemStack[46];
        Arrays.fill(slots, ItemStack.EMPTY);
        return slots;
    }
}
