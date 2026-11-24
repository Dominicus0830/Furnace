package org.domi.furnace.compat.nms;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.compat.paper.ItemNbtCodec;

/**
 * 용기 블록 엔티티(상자·통·셜커·깔때기·발사기·화로류·양조대·제작기·장식 단지·주크박스·장식 책장)의 {@code Container} 몸통.
 * 월드의 보기는 서버 용기 블록 엔티티의 칸을 그 칸의 주인 스레드에서 읽고 쓴다({@code ChunkAccess#furnaceBlockInventory} — 다른 스레드는 주인에게 부탁해 기다림).
 * 떨어진 블록 엔티티는 자기 NBT 의 {@code Items} 를 읽고 쓴다. 돌려주는 스택은 바닐라처럼 칸에 묶여 있어 고치면 칸이 바뀐다
 * ({@link ItemStack#liveContainerSlot}). 칸 쓰기는 서버의 칸 쓰기(바닐라 setItem 의 부수 효과 + 저장 표시·비교기 알림)로 이어진다.
 */
public final class NmsBlockContainer {

    private NmsBlockContainer() {}

    /** 블록 엔티티 종류 → 칸 수(바닐라 생성자·필드의 크기). */
    private static final Map<String, Integer> SIZES = Map.ofEntries(
            Map.entry("minecraft:chest", 27), Map.entry("minecraft:trapped_chest", 27), Map.entry("minecraft:barrel", 27),
            Map.entry("minecraft:shulker_box", 27), Map.entry("minecraft:hopper", 5), Map.entry("minecraft:dispenser", 9),
            Map.entry("minecraft:dropper", 9), Map.entry("minecraft:furnace", 3), Map.entry("minecraft:blast_furnace", 3),
            Map.entry("minecraft:smoker", 3), Map.entry("minecraft:brewing_stand", 5), Map.entry("minecraft:crafter", 9),
            Map.entry("minecraft:chiseled_bookshelf", 6), Map.entry("minecraft:decorated_pot", 1), Map.entry("minecraft:jukebox", 1));

    /** 전리품 표를 가질 수 있는 종류(바닐라 RandomizableContainerBlockEntity 하위). 읽기·쓰기 전에 표가 풀린다. */
    private static final java.util.Set<String> LOOTABLE = java.util.Set.of("minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel",
            "minecraft:shulker_box", "minecraft:hopper", "minecraft:dispenser", "minecraft:dropper", "minecraft:crafter");

    private static String key(BlockEntity be) { return net.minecraft.world.level.block.entity.BlockEntityType.getKey(be.getType()).toString(); }

    public static int size(BlockEntity be) {
        Integer n = SIZES.get(key(be));
        if (n == null) throw new UnsupportedNmsCall("Container#getContainerSize", "block entity " + key(be) + " has no item slots");
        return n;
    }

    private static <T> T await(java.util.concurrent.CompletableFuture<T> f) {
        try {
            return f.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException r) throw r;
            throw e;
        }
    }

    /** 아직 풀리지 않은 전리품 표가 있으면 바닐라는 읽기·쓰기 때 표를 풀어 채운다 — 서버의 푸는 길이 이 다리에 없어 정직하게 던진다. */
    private static void checkLoot(BlockEntity be, String what) {
        if (!LOOTABLE.contains(key(be))) return;
        boolean pending = be.furnaceViewChunk() != null
                ? be.saveWithoutMetadata(null).contains("LootTable")
                : be.furnaceDetachedSave().contains("LootTable");
        if (pending) {
            throw new UnsupportedNmsCall(what, "the container at " + be.getBlockPos() + " still has an unopened loot table; vanilla fills it on first access, the shim cannot yet");
        }
    }

    private static void checkSlot(BlockEntity be, int slot) {
        int n = size(be);
        if (slot < 0 || slot >= n) throw new IndexOutOfBoundsException("Index: " + slot + ", Size: " + n);
    }

    // ------------------------------------------------------------------ 한 칸 읽기·쓰기(월드의 보기 / 떨어진 것)

    private static org.domi.furnace.api.item.ItemStack readRaw(BlockEntity be, int slot, String what) {
        if (be.furnaceViewChunk() != null) return be.furnaceViewChunk().furnaceBlockInventory(be, what, inv -> await(inv.get(slot)));
        return ItemNbtCodec.readItems(be.furnaceDetachedSave(), size(be)).get(slot);
    }

    private static void writeRaw(BlockEntity be, int slot, org.domi.furnace.api.item.ItemStack value, String what) {
        if (be.furnaceViewChunk() != null) {
            be.furnaceViewChunk().furnaceBlockInventory(be, what, inv -> await(inv.set(slot, value)));
            return;
        }
        List<org.domi.furnace.api.item.ItemStack> items = new ArrayList<>(ItemNbtCodec.readItems(be.furnaceDetachedSave(), size(be)));
        items.set(slot, value);
        NbtCompound n = be.furnaceDetachedSave();
        n.putList("Items", ItemNbtCodec.writeItems(items));
        be.furnaceDetachedLoad(n);
    }

    /** 칸이 아직 expect 이면 next 로 바꾼다(아니면 false). */
    private static boolean swapRaw(BlockEntity be, int slot, org.domi.furnace.api.item.ItemStack expect, org.domi.furnace.api.item.ItemStack next, String what) {
        if (be.furnaceViewChunk() != null) {
            return be.furnaceViewChunk().furnaceBlockInventory(be, what, inv -> await(inv.update(slot, cur -> cur.equals(expect) ? next : null)));
        }
        if (!readRaw(be, slot, what).equals(expect)) return false;
        writeRaw(be, slot, next, what);
        return true;
    }

    private static ItemStack live(BlockEntity be, int slot, org.domi.furnace.api.item.ItemStack now, String what) {
        return ItemStack.liveContainerSlot((expect, next) -> swapRaw(be, slot, expect, next, what), now);
    }

    // ------------------------------------------------------------------ Container

    public static ItemStack getItem(BlockEntity be, int slot) {
        String what = "Container#getItem";
        checkSlot(be, slot);
        checkLoot(be, what);
        return live(be, slot, readRaw(be, slot, what), what);
    }

    public static void setItem(BlockEntity be, int slot, ItemStack stack, int maxStack) {
        String what = "Container#setItem";
        checkSlot(be, slot);
        checkLoot(be, what);
        stack.limitSize(Math.min(maxStack, stack.getMaxStackSize()));
        writeRaw(be, slot, stack.furnaceValue(), what);
        stack.furnaceBindLive((expect, next) -> swapRaw(be, slot, expect, next, what));
    }

    public static ItemStack removeItem(BlockEntity be, int slot, int amount) {
        String what = "Container#removeItem";
        if (slot < 0 || slot >= size(be) || amount <= 0) return ItemStack.EMPTY;
        checkLoot(be, what);
        var cur = readRaw(be, slot, what);
        if (cur.isEmpty()) return ItemStack.EMPTY;
        int n = Math.min(amount, cur.amount());
        var rest = n == cur.amount() ? org.domi.furnace.api.item.ItemStack.EMPTY
                : new org.domi.furnace.api.item.ItemStack(cur.type(), cur.amount() - n, cur.components(), cur.removedComponents());
        if (!swapRaw(be, slot, cur, rest, what)) return removeItem(be, slot, amount);   // 그새 칸이 바뀜 — 다시 읽어서
        return ItemStack.fromFurnace(new org.domi.furnace.api.item.ItemStack(cur.type(), n, cur.components(), cur.removedComponents()), n);
    }

    public static ItemStack removeItemNoUpdate(BlockEntity be, int slot) {
        String what = "Container#removeItemNoUpdate";
        if (slot < 0 || slot >= size(be)) return ItemStack.EMPTY;
        checkLoot(be, what);
        var cur = readRaw(be, slot, what);
        if (cur.isEmpty()) return ItemStack.EMPTY;
        if (!swapRaw(be, slot, cur, org.domi.furnace.api.item.ItemStack.EMPTY, what)) return removeItemNoUpdate(be, slot);
        return ItemStack.fromFurnace(cur, cur.amount());
    }

    public static boolean isEmpty(BlockEntity be) {
        for (ItemStack s : getContents(be)) if (!s.isEmpty()) return false;
        return true;
    }

    /** 칸마다 묶인 스택의 새 목록(바닐라는 칸 목록 그 자체 — 목록을 고쳐도 칸은 안 바뀐다). */
    public static List<ItemStack> getContents(BlockEntity be) {
        String what = "Container#getContents";
        checkLoot(be, what);
        List<org.domi.furnace.api.item.ItemStack> raw = be.furnaceViewChunk() != null
                ? be.furnaceViewChunk().furnaceBlockInventory(be, what, inv -> await(inv.contents()))
                : ItemNbtCodec.readItems(be.furnaceDetachedSave(), size(be));
        List<ItemStack> out = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) out.add(live(be, i, raw.get(i), what));
        return out;
    }
}
