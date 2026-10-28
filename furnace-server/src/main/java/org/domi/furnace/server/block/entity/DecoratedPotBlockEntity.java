// 바닐라 DecoratedPotBlockEntity: 아이템 한 종류(스택 한도까지)와 네 면의 도자기 조각(sherds, 벽돌 = 장식 없음). 저장 꼴은 바닐라 그대로:
// "sherds"(기본이 아닐 때만 아이템 이름 4개), "item"(비어 있지 않을 때), 열지 않은 전리품 표면 LootTable·LootTableSeed.
// 넣거나 두드리면 블록 이벤트 1 로 흔들림을 알린다(wobble 정류는 클라이언트 몫 — 서버는 시각만 기억한다).
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

public class DecoratedPotBlockEntity extends ContainerBlockEntity {

    public static final String BRICK = "minecraft:brick";
    /** WobbleStyle: POSITIVE(7틱) · NEGATIVE(10틱) — 블록 이벤트 data 는 ordinal. */
    public static final int WOBBLE_POSITIVE = 0, WOBBLE_NEGATIVE = 1;

    /** 뒤·왼·오른·앞 순서의 조각 아이템 이름(장식 없음 = 벽돌). */
    public final String[] sherds = {BRICK, BRICK, BRICK, BRICK};
    public long lastWobbleTime;
    public int lastWobbleStyle = -1;

    public DecoratedPotBlockEntity(Pos pos, int state) { super("minecraft:decorated_pot", pos, state, 1); }

    @Override public boolean isValid(int state) { return org.domi.furnace.server.block.B.is(state, "decorated_pot"); }

    @Override public String defaultTitleKey() { return "block.minecraft.decorated_pot"; }

    /** 바닐라 DecoratedPotBlockEntity: pot_decorations ↔ 조각, container 의 첫 아이템 ↔ 담긴 아이템(이름·잠금 없음). */
    @Override
    protected void applyImplicitComponents(ComponentInput in) {
        setSherds(in.getOrDefault("minecraft:pot_decorations", java.util.List.of()) instanceof java.util.List<?> l ? l : java.util.List.of());
        items[0] = firstOf(in.get("minecraft:container"));   // ItemContainerContents.copyOne: 0번 칸
    }

    private static ItemStack firstOf(Object container) {
        if (container instanceof java.util.List<?> l && !l.isEmpty() && l.get(0) instanceof java.util.Map<?, ?> m && Integer.valueOf(0).equals(m.get("slot")) && m.get("item") instanceof java.util.Map<?, ?> it) {
            @SuppressWarnings("unchecked") var item = (java.util.Map<String, Object>) it;
            return org.domi.furnace.server.item.ItemStackCodecs.fromCanonical(item);
        }
        return ItemStack.EMPTY;
    }

    @Override
    protected void collectImplicitComponents(java.util.Map<String, Object> out) {
        out.put("minecraft:pot_decorations", new java.util.ArrayList<Object>(java.util.Arrays.asList(sherds)));
        out.put("minecraft:container", containerOf(new ItemStack[] {items[0]}));
    }

    public ItemStack theItem() { return items[0]; }

    public boolean isEmpty() { return items[0].isEmpty(); }

    /** 바닐라 getTheItem: 열지 않은 전리품이 있으면 먼저 채운다. */
    public ItemStack theItem(BlockLevel level) {
        if (lootTable != null) level.unpackContainerLoot(this);
        return items[0];
    }

    public void setTheItem(ItemStack s, BlockLevel level) {
        if (lootTable != null) level.unpackContainerLoot(this);
        items[0] = s;
    }

    /** 바닐라 wobble: 서버에서만 블록 이벤트 1 을 보낸다(클라이언트가 흔든다). */
    public void wobble(int style, BlockLevel level) {
        level.blockEvent(pos, org.domi.furnace.server.block.B.block(level.getBlockState(pos)), 1, style);
    }

    @Override
    public boolean triggerEvent(int type, int data) {
        if (type == 1 && data >= 0 && data < 2) {
            lastWobbleStyle = data;
            return true;
        }
        return false;
    }

    /** 장식 아이템 이름 4개를 그대로 넣는다(없거나 모자라면 벽돌). */
    public void setSherds(java.util.List<?> names) {
        for (int i = 0; i < 4; i++) sherds[i] = i < names.size() && names.get(i) instanceof String s ? s : BRICK;
    }

    private boolean defaultSherds() {
        for (String s : sherds) if (!BRICK.equals(s)) return false;
        return true;
    }

    @Override
    public void load(NbtCompound tag) {
        Arrays.fill(items, ItemStack.EMPTY);
        Arrays.fill(sherds, BRICK);
        customName = null;
        lock = null;
        lootTable = null;
        lootSeed = 0L;
        if (tag.tags().get("sherds") instanceof NbtList l) setSherds(l.items());
        if (tag.tags().get("LootTable") instanceof String t) {
            lootTable = t;
            lootSeed = tag.tags().get("LootTableSeed") instanceof Long seed ? seed : 0L;
            return;
        }
        if (tag.tags().get("item") instanceof NbtCompound ie && ItemContext.installed()) {
            try {
                String name = ie.getString("id");
                Integer id = ItemContext.id("minecraft:item", name);
                int count = ie.contains("count") ? ie.getInt("count") : 1;
                var patch = id == null ? null : ItemNbt.read(ie, name, ItemNbt.LEGACY_KEY);
                if (id != null && id > 0 && count > 0 && patch != null) items[0] = new ItemStack(id, count, patch);
            } catch (RuntimeException e) {
                // 이상한 아이템은 없는 것으로
            }
        }
    }

    @Override
    protected void saveAdditional(NbtCompound tag) {
        if (!defaultSherds()) {
            NbtList l = new NbtList(NbtIo.TAG_STRING);
            for (String s : sherds) l.add(s);
            tag.putList("sherds", l);
        }
        if (lootTable != null) {
            tag.putString("LootTable", lootTable);
            if (lootSeed != 0L) tag.putLong("LootTableSeed", lootSeed);
            return;
        }
        ItemStack s = items[0];
        if (!s.isEmpty() && ItemContext.installed()) {
            NbtCompound e = new NbtCompound();
            e.putString("id", ItemContext.name("minecraft:item", s.itemId()));
            e.putInt("count", s.count());
            NbtCompound comps = ItemNbt.write(s.patch());
            if (comps != null) e.putCompound(ItemNbt.KEY, comps);
            tag.putCompound("item", e);
        }
    }

    /** getUpdateTag(saveCustomOnly): 클라이언트가 면 무늬를 그리도록 sherds·item 을 그대로. */
    @Override
    public NbtCompound updateTag() { return saveCustomOnly(); }
}
