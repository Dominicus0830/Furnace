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
 * 용기 블록 엔티티(상자·통·셜커·깔때기·발사기·화로류·양조대·제작기·장식 단지)의 {@code Container} 몸통.
 * 월드의 보기는 서버 용기 블록 엔티티의 칸을 그 칸의 주인 스레드에서 읽고 쓴다({@code ChunkAccess#furnaceBlockInventory} — 다른 스레드는 주인에게 부탁해 기다림).
 * 떨어진 블록 엔티티는 자기 NBT 의 {@code Items} 를 읽고 쓴다. 돌려주는 스택은 바닐라처럼 칸에 묶여 있어 고치면 칸이 바뀐다
 * ({@link ItemStack#liveContainerSlot}). 열지 않은 전리품 표는 서버가 칸을 읽거나 쓰기 전에 푼다(바닐라 unpackLootTable(null); 떨어진 블록 엔티티는 레벨이 없어 풀지 않는다). 칸 쓰기는 서버의 칸 쓰기(바닐라 setItem 의 부수 효과 + 저장 표시·비교기 알림)로 이어진다.
 */
public final class NmsBlockContainer {

    private NmsBlockContainer() {}

    /** 블록 엔티티 종류 → 칸 수(바닐라 생성자·필드의 크기). */
    private static final Map<String, Integer> SIZES = Map.ofEntries(
            Map.entry("minecraft:chest", 27), Map.entry("minecraft:trapped_chest", 27), Map.entry("minecraft:barrel", 27),
            Map.entry("minecraft:shulker_box", 27), Map.entry("minecraft:hopper", 5), Map.entry("minecraft:dispenser", 9),
            Map.entry("minecraft:dropper", 9), Map.entry("minecraft:furnace", 3), Map.entry("minecraft:blast_furnace", 3),
            Map.entry("minecraft:smoker", 3), Map.entry("minecraft:brewing_stand", 5), Map.entry("minecraft:crafter", 9),
            Map.entry("minecraft:decorated_pot", 1));

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
        return live(be, slot, readRaw(be, slot, what), what);
    }

    public static void setItem(BlockEntity be, int slot, ItemStack stack, int maxStack) {
        String what = "Container#setItem";
        checkSlot(be, slot);
        stack.limitSize(Math.min(maxStack, stack.getMaxStackSize()));
        put(be, slot, stack, what);
    }

    /** 한도 없이 그 객체를 칸에 둔다(바닐라 NonNullList.set — 목록 보기의 set). */
    private static void put(BlockEntity be, int slot, ItemStack stack, String what) {
        writeRaw(be, slot, stack.furnaceValue(), what);
        stack.furnaceBindLive((expect, next) -> swapRaw(be, slot, expect, next, what));
    }

    public static ItemStack removeItem(BlockEntity be, int slot, int amount) {
        String what = "Container#removeItem";
        if (slot < 0 || slot >= size(be) || amount <= 0) return ItemStack.EMPTY;
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
        var cur = readRaw(be, slot, what);
        if (cur.isEmpty()) return ItemStack.EMPTY;
        if (!swapRaw(be, slot, cur, org.domi.furnace.api.item.ItemStack.EMPTY, what)) return removeItemNoUpdate(be, slot);
        return ItemStack.fromFurnace(cur, cur.amount());
    }

    public static boolean isEmpty(BlockEntity be) {
        for (ItemStack s : getContents(be)) if (!s.isEmpty()) return false;
        return true;
    }

    /**
     * 칸 목록의 보기(Paper 는 칸 목록 그 자체 — NonNullList): 읽기는 칸마다 묶인 스택, {@code set} 은 그 칸에 쓴다(한도는 칸의 최대 쌓기). 크기는 못 바꾼다.
     */
    public static List<ItemStack> getContents(BlockEntity be) {
        int n = size(be);
        return new java.util.AbstractList<>() {
            @Override public ItemStack get(int i) { return getItem(be, i); }

            @Override public int size() { return n; }

            @Override public ItemStack set(int i, ItemStack stack) {
                ItemStack old = getItem(be, i);
                checkSlot(be, i);
                put(be, i, stack == null ? ItemStack.EMPTY : stack, "Container#getContents");
                return old;
            }
        };
    }
}
