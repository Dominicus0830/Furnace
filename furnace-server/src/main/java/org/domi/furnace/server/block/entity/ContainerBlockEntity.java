// 칸에 아이템을 담는 블록 엔티티의 바탕(바닐라 BaseContainerBlockEntity + RandomizableContainerBlockEntity): 아이템 칸 배열·열지 않은 전리품 표·
// 이름·잠금·여는 사람 수. 저장 꼴은 바닐라 그대로(Items[{Slot, id, count, components?}], LootTable, LootTableSeed, CustomName, Lock).
package org.domi.furnace.server.block.entity;

import java.util.Arrays;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemNbt;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;

public abstract class ContainerBlockEntity extends OpenersBlockEntity {

    /** 칸 내용(빈 칸은 ItemStack.EMPTY). 길이는 고정. */
    public final ItemStack[] items;
    /** 아직 열지 않은 전리품 상자의 표(바닐라 RandomizableContainer LootTable) — 열면 채우고 null 로. */
    public String lootTable;
    public long lootSeed;
    /** 사용자 지정 이름(JSON 문자열 그대로 — 모루로 지은 이름). */
    public String customName;
    /** 잠금(1.21.1 LockCode: 열쇠 이름 문자열) — 그대로 보존. */
    public String lock;

    protected ContainerBlockEntity(String type, Pos pos, int blockState, int size) {
        this(type, pos, blockState, emptyItems(size));
    }

    protected ContainerBlockEntity(String type, Pos pos, int blockState, ItemStack[] items) {
        super(type, pos, blockState);
        this.items = items;
    }

    private static ItemStack[] emptyItems(int size) {
        ItemStack[] a = new ItemStack[size];
        Arrays.fill(a, ItemStack.EMPTY);
        return a;
    }

    public int size() { return items.length; }

    /** 메뉴/깔때기가 setItem 으로 칸을 바꿀 때의 부수 효과(바닐라 화로 setItem). 기본은 없음. */
    public void onItemSet(int index, ItemStack old, ItemStack now, BlockLevel level) { }

    /** 바닐라 containerMenu 의 제목 번역 키(이름이 없을 때). */
    public abstract String defaultTitleKey();

    @Override
    public void load(NbtCompound tag) {
        Arrays.fill(items, ItemStack.EMPTY);
        customName = tag.tags().get("CustomName") instanceof String s ? s : null;
        lock = tag.tags().get("Lock") instanceof String s ? s : null;
        lootTable = null;
        lootSeed = 0L;
        if (tag.tags().get("LootTable") instanceof String t) {   // 열지 않은 전리품 상자는 아이템 대신 표를 든다
            lootTable = t;
            lootSeed = tag.tags().get("LootTableSeed") instanceof Long seed ? seed : 0L;
            return;
        }
        if (!tag.contains("Items") || !ItemContext.installed()) return;
        for (Object o : tag.getList("Items").items()) {
            try {
                NbtCompound ie = (NbtCompound) o;
                int slot = ie.getByte("Slot") & 0xFF;
                if (slot >= items.length) continue;
                String name = ie.getString("id");
                Integer id = ItemContext.id("minecraft:item", name);
                int count = ie.contains("count") ? ie.getInt("count") : 1;
                if (id == null || id <= 0 || count <= 0) continue;
                var patch = ItemNbt.read(ie, name, ItemNbt.LEGACY_KEY);
                if (patch == null) continue;
                items[slot] = new ItemStack(id, count, patch);
            } catch (RuntimeException ex) {
                // 이상한 항목은 그 칸만 건너뛴다
            }
        }
    }

    @Override
    protected void saveAdditional(NbtCompound tag) {
        if (customName != null) tag.putString("CustomName", customName);
        if (lock != null) tag.putString("Lock", lock);
        if (lootTable != null) {   // 바닐라 trySaveLootTable: 표가 있으면 아이템은 쓰지 않는다
            tag.putString("LootTable", lootTable);
            if (lootSeed != 0L) tag.putLong("LootTableSeed", lootSeed);
            return;
        }
        NbtList list = new NbtList(NbtIo.TAG_COMPOUND);
        for (int i = 0; i < items.length && ItemContext.installed(); i++) {   // 아이템 레지스트리가 없으면(서버 밖 도구·시험) 이름을 알 수 없다
            ItemStack s = items[i];
            if (s == null || s.isEmpty()) continue;
            String name = ItemContext.name("minecraft:item", s.itemId());
            if (name == null) continue;
            NbtCompound e = new NbtCompound();
            e.putByte("Slot", (byte) i);
            e.putString("id", name);
            e.putInt("count", s.count());
            NbtCompound comps = ItemNbt.write(s.patch());
            if (comps != null) e.putCompound(ItemNbt.KEY, comps);
            list.add(e);
        }
        if (list.size() > 0 || alwaysWriteItems()) tag.putList("Items", list);
    }

    // ------------------------------------------------------------------ 컴포넌트(BaseContainerBlockEntity·RandomizableContainerBlockEntity)

    /** RandomizableContainerBlockEntity 인가(container_loot 를 주고받는다). 상자·통·발사기·공급기·깔때기·셜커·제작기. */
    protected boolean randomizable() { return false; }

    @Override
    protected void applyImplicitComponents(ComponentInput in) {
        super.applyImplicitComponents(in);
        customName = in.get("minecraft:custom_name") instanceof String n ? n : null;
        Object l = in.get("minecraft:lock");
        lock = l instanceof String k && !k.isEmpty() ? k : null;
        copyContainerInto(in.get("minecraft:container"), items);
        if (randomizable() && in.get("minecraft:container_loot") instanceof java.util.Map<?, ?> loot) {
            lootTable = (String) loot.get("loot_table");
            lootSeed = loot.get("seed") instanceof Number n ? n.longValue() : 0L;
        }
    }

    @Override
    protected void collectImplicitComponents(java.util.Map<String, Object> out) {
        super.collectImplicitComponents(out);
        out.put("minecraft:custom_name", customName);
        if (lock != null && !lock.isEmpty()) out.put("minecraft:lock", lock);
        out.put("minecraft:container", containerOf(items));
        if (randomizable() && lootTable != null) {
            java.util.Map<String, Object> loot = new java.util.LinkedHashMap<>();
            loot.put("loot_table", lootTable);
            loot.put("seed", lootSeed);
            out.put("minecraft:container_loot", loot);
        }
    }

    /** ItemContainerContents.fromItems: 비지 않은 칸만 {slot, item} 으로(정규 값). */
    public static java.util.List<Object> containerOf(ItemStack[] items) {
        java.util.List<Object> l = new java.util.ArrayList<>();
        for (int i = 0; i < items.length; i++) {
            ItemStack s = items[i];
            if (s == null || s.isEmpty()) continue;
            java.util.Map<String, Object> e = new java.util.LinkedHashMap<>();
            e.put("slot", i);
            e.put("item", org.domi.furnace.server.item.ItemStackCodecs.toCanonical(s));
            l.add(e);
        }
        return l;
    }

    /** ItemContainerContents.copyInto: 모든 칸을 덮어쓴다(없는 칸은 빈 칸). 값이 없으면 기본값(빈 용기)과 같다. */
    public static void copyContainerInto(Object container, ItemStack[] items) {
        Arrays.fill(items, ItemStack.EMPTY);
        if (!(container instanceof java.util.List<?> list)) return;
        for (Object o : list) {
            if (!(o instanceof java.util.Map<?, ?> m) || !(m.get("slot") instanceof Integer slot) || slot < 0 || slot >= items.length) continue;
            @SuppressWarnings("unchecked") var item = (java.util.Map<String, Object>) m.get("item");
            if (item != null) items[slot] = org.domi.furnace.server.item.ItemStackCodecs.fromCanonical(item);
        }
    }

    /** 칸이 모두 비어도 Items 목록을 쓰는가(바닐라 saveAllItems 의 alwaysPutTag — 셜커 상자만 거짓). */
    protected boolean alwaysWriteItems() { return true; }
}
