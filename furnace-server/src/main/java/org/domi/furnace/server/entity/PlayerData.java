// playerdata/<uuid>.dat (gzip NBT) read/write — 바닐라 플레이어 NBT 형식(Pos·Rotation·UUID·Inventory{Slot:byte,id,count}).
// 저장 시 기존 파일을 바탕으로 서버가 아는 필드만 덮어써서 아직 다루지 않는 바닐라 필드(체력·허기·경험치 등)를 보존하고,
// 바닐라 PlayerDataStorage 처럼 이전 파일을 <uuid>.dat_old 로 남긴다.
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

    /** @param root 읽은 NBT 전체(생존 상태 등 다른 필드를 읽는 데 쓴다) */
    public record Snapshot(double x, double y, double z, float yaw, float pitch,
                           boolean onGround, ItemStack[] slots, NbtCompound root) {
        public Snapshot(double x, double y, double z, float yaw, float pitch, boolean onGround, ItemStack[] slots) {
            this(x, y, z, yaw, pitch, onGround, slots, null);
        }
    }

    private PlayerData() {}

    /** 바닐라 문자열 아이템 id 변환용. null 이면(일부 테스트) 초기 Furnace 형식으로 쓴다. */
    private static volatile org.domi.furnace.server.world.ItemRegistry items;

    public static void itemRegistry(org.domi.furnace.server.world.ItemRegistry registry) { items = registry; }

    private static final int DATA_VERSION = 3955;
    private static final String FURNACE_COMPONENTS = "furnace:components";
    /** 바닐라 인벤토리에 자리가 없는 제작 칸(창 슬롯 1..4) 아이템 — 바닐라는 접속 종료 때 인벤토리로 돌려준다. */
    private static final String FURNACE_CRAFTING = "furnace:crafting";

    // 플레이어 데이터 읽기·쓰기 전용 단일 스레드(D-14). 청크 IO 풀과 분리해 대기열 지연을 받지 않고,
    // 제출 순서대로 직렬이라 퇴장 저장 → 같은 UUID 재접속 읽기 순서가 보장된다. 종료 경로는 awaitIo 로 비운다.
    private static final java.util.concurrent.ExecutorService IO = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "furnace-playerdata-io");
        t.setDaemon(true);
        return t;
    });

    public static java.util.concurrent.Executor io() { return IO; }

    /** 지금까지 io() 에 제출된 작업이 끝날 때까지 기다린다(서버 종료·테스트). 시간 안에 끝나면 true. */
    public static boolean awaitIo(long timeout, java.util.concurrent.TimeUnit unit) throws InterruptedException {
        try {
            IO.submit(() -> { }).get(timeout, unit);
            return true;
        } catch (java.util.concurrent.TimeoutException e) {
            return false;
        } catch (java.util.concurrent.ExecutionException e) {
            return true;
        }
    }

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
        saveSequenced(seq, worldDir, id, x, y, z, yaw, pitch, onGround, slots, null);
    }

    /** @param extra 스냅샷을 뜰 때 함께 떠 둔 나머지 필드(체력·허기·경험치·게임 모드 등). null 이면 기존 값 유지. */
    public static void saveSequenced(long seq, Path worldDir, UUID id, double x, double y, double z,
                                     float yaw, float pitch, boolean onGround, ItemStack[] slots, NbtCompound extra) {
        synchronized (lock(id)) {
            Long last = LAST_SEQ.get(id);
            if (last != null && last > seq) return;
            LAST_SEQ.put(id, seq);
            save(worldDir, id, x, y, z, yaw, pitch, onGround, slots, extra);
        }
    }

    public static void save(Path worldDir, UUID id, double x, double y, double z,
                            float yaw, float pitch, boolean onGround, ItemStack[] slots) {
        save(worldDir, id, x, y, z, yaw, pitch, onGround, slots, null);
    }

    public static void save(Path worldDir, UUID id, double x, double y, double z,
                            float yaw, float pitch, boolean onGround, ItemStack[] slots, NbtCompound extra) {
        synchronized (lock(id)) {
            NbtCompound root = readExisting(file(worldDir, id));
            if (extra != null) {
                for (String k : VITALS_KEYS) root.tags().remove(k);   // 없어진 선택 필드(이전 게임 모드·스폰 지점 등)를 지운다
                root.tags().putAll(extra.tags());
            }
            fill(root, id, x, y, z, yaw, pitch, onGround, slots);
            write(worldDir, id, root);
        }
    }

    /** PlayerVitals.save 가 쓰는 선택 필드 — 값이 없어지면 파일에서도 지워야 하는 것. */
    private static final String[] VITALS_KEYS = {"previousPlayerGameType", "SpawnX", "SpawnY", "SpawnZ", "SpawnForced",
            "SpawnAngle", "SpawnDimension", "LastDeathLocation", "raid_omen_position", "Tags", "BukkitValues"};

    private static NbtCompound readExisting(Path f) {
        if (!Files.exists(f)) return new NbtCompound();
        try (var is = Files.newInputStream(f)) {
            return NbtIo.readGzip(is);
        } catch (IOException | RuntimeException e) {
            return new NbtCompound();
        }
    }

    private static void fill(NbtCompound root, UUID id, double x, double y, double z,
                             float yaw, float pitch, boolean onGround, ItemStack[] slots) {
        NbtList pos = new NbtList(NbtIo.TAG_DOUBLE);
        pos.add(x); pos.add(y); pos.add(z);
        root.putList("Pos", pos);
        NbtList rot = new NbtList(NbtIo.TAG_FLOAT);
        rot.add(yaw); rot.add(pitch);
        root.putList("Rotation", rot);
        root.putByte("OnGround", (byte) (onGround ? 1 : 0));
        root.putIntArray("UUID", uuidToIntArray(id));
        root.putInt("DataVersion", DATA_VERSION);
        if (!root.contains("Dimension")) root.putString("Dimension", "minecraft:overworld");
        root.tags().remove(FURNACE_CRAFTING);
        if (items != null) {
            root.putList("Inventory", vanillaInventoryToNbt(slots));
            NbtList crafting = craftingToNbt(slots);
            if (crafting.size() > 0) root.putList(FURNACE_CRAFTING, crafting);
        } else {
            root.putList("Inventory", inventoryToNbt(slots));
        }
    }

    private static void write(Path worldDir, UUID id, NbtCompound root) {
        try {
            Path f = file(worldDir, id);
            Files.createDirectories(f.getParent());
            // 임시 파일에 쓰고 교체 — 쓰는 도중 크래시/동시 읽기에서도 잘린 파일이 보이지 않는다. 이전 파일은 .dat_old 로.
            Path tmp = Files.createTempFile(f.getParent(), id + "-", ".dat");
            try (var os = Files.newOutputStream(tmp)) {
                NbtIo.writeGzip(os, root, "");
            } catch (IOException | RuntimeException e) {
                Files.deleteIfExists(tmp);   // 쓰다 실패하면 임시 파일을 남기지 않는다
                throw e;
            }
            if (Files.exists(f)) {
                Files.move(f, f.resolveSibling(id + ".dat_old"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(tmp, f, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
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
                    readInventory(root),
                    root
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

    /** 바닐라 Entity UUID 저장 형식(int[4], 상위 비트부터). */
    static int[] uuidToIntArray(UUID id) {
        long m = id.getMostSignificantBits(), l = id.getLeastSignificantBits();
        return new int[]{(int) (m >> 32), (int) m, (int) (l >> 32), (int) l};
    }

    /** 창 슬롯(InventoryMenu 0..45) → 바닐라 Inventory Slot. 제작 칸(0..4)은 -1(바닐라 인벤토리에 없음). */
    static int windowToVanillaSlot(int window) {
        if (window >= 36 && window <= 44) return window - 36;   // 핫바 0..8
        if (window >= 9 && window <= 35) return window;         // 주 인벤토리 9..35
        if (window >= 5 && window <= 8) return 103 - (window - 5); // 머리 103 · 가슴 102 · 다리 101 · 발 100
        if (window == 45) return -106;                          // 왼손
        return -1;
    }

    public static int vanillaToWindowSlot(int slot) {
        if (slot >= 0 && slot <= 8) return slot + 36;
        if (slot >= 9 && slot <= 35) return slot;
        if (slot >= 100 && slot <= 103) return 5 + (103 - slot);
        if (slot == -106) return 45;
        return -1;
    }

    private static NbtCompound vanillaItem(ItemStack stack) {
        String name = items.nameOf(stack.itemId());
        if (name == null || name.equals("minecraft:air")) return null;
        NbtCompound it = new NbtCompound();
        it.putString("id", name);
        it.putInt("count", stack.count());
        NbtCompound components = org.domi.furnace.server.item.ItemNbt.write(stack.patch());
        if (components != null) it.putCompound(org.domi.furnace.server.item.ItemNbt.KEY, components);
        return it;
    }

    private static NbtList vanillaInventoryToNbt(ItemStack[] slots) {
        NbtList inventory = new NbtList(NbtIo.TAG_COMPOUND);
        if (slots == null) return inventory;
        // 바닐라 Inventory.save 순서: 주 인벤토리(0..35) → 방어구(100..103) → 왼손(-106)
        int[] order = new int[41];
        int n = 0;
        for (int v = 0; v <= 35; v++) order[n++] = vanillaToWindowSlot(v);
        for (int v = 100; v <= 103; v++) order[n++] = vanillaToWindowSlot(v);
        order[n] = 45;
        for (int window : order) {
            if (window < 0 || window >= slots.length) continue;
            ItemStack stack = slots[window];
            if (stack == null || stack.isEmpty()) continue;
            NbtCompound it = vanillaItem(stack);
            if (it == null) continue;
            NbtCompound entry = new NbtCompound();
            entry.putByte("Slot", (byte) windowToVanillaSlot(window));
            entry.tags().putAll(it.tags());
            inventory.add(entry);
        }
        return inventory;
    }

    /**
     * 접속 중인 플레이어의 저장 꼴 NBT(/data get entity 용, 바닐라 saveWithoutId 근사): 저장 때 쓰는 필드 + SelectedItem.
     * 차이: 저장 경로와 같은 조각만 담는다(Motion·Fire 같은 엔티티 공통 필드 일부는 없다).
     */
    public static NbtCompound snapshotNbt(Player p) {
        NbtCompound root = p.vitals().saveData();
        ItemStack[] slots = p.inventory().snapshot();
        int selected = p.inventory().selected();
        root.putInt("SelectedItemSlot", selected);
        if (items != null) root.putList("EnderItems", enderItemsToNbt(p.enderChest().snapshot()));
        root.putCompound("recipeBook", p.recipeBook().save());
        root.putString("Dimension", p.dimension());
        root.putBoolean("seenCredits", p.seenCredits);
        p.saveTags(root);
        p.savePersistentData(root);
        p.saveShoulders(root);
        p.saveAttributes(root);
        fill(root, p.uuid(), p.x(), p.y(), p.z(), p.yaw(), p.pitch(), p.onGround(), slots);
        root.tags().remove("DataVersion");
        root.tags().remove(FURNACE_CRAFTING);
        if (items != null) {
            ItemStack held = slots[PlayerInventory.windowSlot(selected)];
            NbtCompound it = held == null || held.isEmpty() ? null : vanillaItem(held);
            if (it != null) root.putCompound("SelectedItem", it);   // NbtPredicate.getEntityTagToCompare
        }
        return root;
    }

    /** 아이템 레지스트리가 있는가(없으면 EnderItems 를 읽거나 쓰지 않고 파일의 것을 그대로 둔다). */
    public static boolean hasItemRegistry() { return items != null; }

    /** 엔더 상자 27칸 → 바닐라 EnderItems 목록(Slot:byte, id, count, components). 레지스트리가 없으면 빈 목록. */
    public static NbtList enderItemsToNbt(ItemStack[] slots) {
        NbtList list = new NbtList(NbtIo.TAG_COMPOUND);
        if (slots == null || items == null) return list;
        for (int i = 0; i < slots.length; i++) {
            ItemStack stack = slots[i];
            if (stack == null || stack.isEmpty()) continue;
            NbtCompound it = vanillaItem(stack);
            if (it == null) continue;
            NbtCompound entry = new NbtCompound();
            entry.putByte("Slot", (byte) i);
            entry.tags().putAll(it.tags());
            list.add(entry);
        }
        return list;
    }

    /** 바닐라 EnderItems 읽기(없거나 레지스트리가 없으면 빈 27칸). */
    public static ItemStack[] enderItemsFromNbt(NbtCompound root, int size) {
        ItemStack[] out = new ItemStack[size];
        Arrays.fill(out, ItemStack.EMPTY);
        if (root == null || items == null || !root.contains("EnderItems")) return out;
        for (Object o : root.getList("EnderItems").items()) {
            if (o instanceof NbtCompound e && e.tags().get("Slot") instanceof Byte b && (b & 0xFF) < size) {
                ItemStack s = vanillaStack(e);
                if (s != null) out[b & 0xFF] = s;
            }
        }
        return out;
    }

    private static NbtList craftingToNbt(ItemStack[] slots) {
        NbtList list = new NbtList(NbtIo.TAG_COMPOUND);
        if (slots == null) return list;
        for (int window = 1; window <= 4 && window < slots.length; window++) {
            ItemStack stack = slots[window];
            if (stack == null || stack.isEmpty()) continue;
            NbtCompound it = vanillaItem(stack);
            if (it == null) continue;
            NbtCompound entry = new NbtCompound();
            entry.putByte("Slot", (byte) window);
            entry.tags().putAll(it.tags());
            list.add(entry);
        }
        return list;
    }

    /** 바닐라 형식(Slot:byte, id:string)과 초기 Furnace 형식(Slot:int, Id:int)을 모두 읽는다. */
    private static ItemStack[] readInventory(NbtCompound root) {
        NbtList inv = root.contains("Inventory") ? root.getList("Inventory") : null;
        ItemStack[] slots = inventoryFromNbt(inv);
        if (items == null) return slots;
        if (inv != null) {
            for (Object o : inv.items()) {
                if (o instanceof NbtCompound e && e.tags().get("Slot") instanceof Byte b) {
                    ItemStack s = vanillaStack(e);
                    int window = vanillaToWindowSlot(b);
                    if (s != null && window >= 0) slots[window] = s;
                }
            }
        }
        if (root.contains(FURNACE_CRAFTING)) {
            for (Object o : root.getList(FURNACE_CRAFTING).items()) {
                if (o instanceof NbtCompound e && e.tags().get("Slot") instanceof Byte b && b >= 1 && b <= 4) {
                    ItemStack s = vanillaStack(e);
                    if (s != null) slots[b] = s;
                }
            }
        }
        return slots;
    }

    private static ItemStack vanillaStack(NbtCompound e) {
        if (!(e.tags().get("id") instanceof String name)) return null;
        int itemId = items.idOf(name);
        int count = e.tags().get("count") instanceof Number c ? c.intValue() : 1;
        if (itemId <= 0 || count <= 0) return null;
        var patch = org.domi.furnace.server.item.ItemNbt.read(e, name, FURNACE_COMPONENTS);
        return patch == null ? null : new ItemStack(itemId, count, patch);
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
            NbtCompound components = org.domi.furnace.server.item.ItemNbt.write(stack.patch());
            if (components != null) entry.putCompound(org.domi.furnace.server.item.ItemNbt.KEY, components);
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
            var patch = org.domi.furnace.server.item.ItemNbt.read(entry, items != null ? items.nameOf(itemId) : null, "Comp");
            if (patch == null) continue;
            slots[slot] = new ItemStack(itemId, count, patch);
        }
        return slots;
    }

    private static ItemStack[] emptySlots() {
        ItemStack[] slots = new ItemStack[46];
        Arrays.fill(slots, ItemStack.EMPTY);
        return slots;
    }
}
