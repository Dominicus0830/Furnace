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

    private static final java.util.concurrent.atomic.AtomicLong SEQ = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.ConcurrentHashMap<UUID, Long> LAST_SEQ = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<UUID, Object> LOCKS = new java.util.concurrent.ConcurrentHashMap<>();

    private static Object lock(UUID id) {
        return LOCKS.computeIfAbsent(id, k -> new Object());
    }

    /** 저장 요청 순서표. 스냅샷을 뜨는 시점에 받아 saveSequenced에 넘기면 늦게 도착한 옛 스냅샷이 최신을 덮어쓰지 못한다. */
    public static long nextSeq() {
        return SEQ.incrementAndGet();
    }

    /** 같은 플레이어의 더 새로운 시퀀스가 이미 기록됐으면 건너뛰는 저장(자동저장·퇴장 저장이 풀에서 뒤바뀌어도 안전). */
    public static void saveSequenced(long seq, Path worldDir, UUID id, double x, double y, double z,
                                     float yaw, float pitch, boolean onGround, ItemStack[] slots) {
        synchronized (lock(id)) {
            Long last = LAST_SEQ.get(id);
            if (last != null && last > seq) return;
            LAST_SEQ.put(id, seq);
            save(worldDir, id, x, y, z, yaw, pitch, onGround, slots);
        }
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
            // 임시 파일에 쓰고 원자적으로 교체 — 쓰는 도중 크래시/동시 읽기에서도 잘린 파일이 보이지 않는다.
            synchronized (lock(id)) {
                Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
                try (var os = Files.newOutputStream(tmp)) {
                    NbtIo.writeGzip(os, root, "");
                }
                try {
                    Files.move(tmp, f, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("playerdata 저장 실패 " + id, e);
        }
    }

    /**
     * 위치/인벤토리 로드. 파일이 없으면 null. 손상됐거나 알 수 없는 형식이면 경고 후 원본을 `.corrupt`로 보존하고 null
     * (바닐라 PlayerDataStorage처럼 새 플레이어로 시작 — 예전엔 예외로 접속이 영구히 막혔다).
     */
    public static Snapshot load(Path worldDir, UUID id) {
        Path f = file(worldDir, id);
        synchronized (lock(id)) {
        if (!Files.exists(f)) return null;
        try (var is = Files.newInputStream(f)) {
            NbtCompound root = NbtIo.readGzip(is);
            NbtList pos = root.getList("Pos");
            NbtList rot = root.getList("Rotation");
            return new Snapshot(
                    (Double) pos.get(0), (Double) pos.get(1), (Double) pos.get(2),
                    (Float) rot.get(0), (Float) rot.get(1),
                    root.contains("OnGround") && root.getByte("OnGround") != 0,
                    inventoryFromNbt(root.contains("Inventory") ? root.getList("Inventory") : null)
            );
        } catch (IOException | RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger(PlayerData.class).warn("playerdata 읽기 실패 {} — 새 플레이어로 시작하고 원본은 .corrupt로 보존", id, e);
            try {
                Files.move(f, f.resolveSibling(f.getFileName() + ".corrupt"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveFail) {
                org.slf4j.LoggerFactory.getLogger(PlayerData.class).warn("손상된 playerdata 백업 실패 {}", id, moveFail);
            }
            return null;
        }
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
            // 자체 형식({Slot:int, Id:int, Count:int})만 읽는다. 바닐라 형식(Slot:byte, id:string)은 아직 변환하지 않고 건너뛴다.
            if (!(entry.tags().get("Slot") instanceof Integer slotObj)
                    || !(entry.tags().get("Id") instanceof Integer itemId)
                    || !(entry.tags().get("Count") instanceof Integer count)) continue;
            int slot = slotObj;
            if (slot < 0 || slot >= slots.length) continue;
            byte[] components = entry.tags().get("Comp") instanceof byte[] c ? c : null;
            slots[slot] = new ItemStack(itemId, count, components);
        }
        return slots;
    }

    private static ItemStack[] emptySlots() {
        ItemStack[] slots = new ItemStack[46];
        Arrays.fill(slots, ItemStack.EMPTY);
        return slots;
    }
}
