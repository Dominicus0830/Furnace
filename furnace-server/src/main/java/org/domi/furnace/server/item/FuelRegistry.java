// 바닐라 AbstractFurnaceBlockEntity.getFuel(): 연료 아이템 → 태울 수 있는 틱 수. 디컴파일 원본의 표를 순서대로 옮겼다(뒤 항목이 앞을 덮어쓴다).
// non_flammable_wood 태그 아이템은 연료가 아니다.
package org.domi.furnace.server.item;

import java.util.List;
import org.domi.furnace.server.entity.ItemStack;

public final class FuelRegistry {
    private FuelRegistry() { }

    private record Entry(String id, int ticks) { }   // id 가 '#' 로 시작하면 아이템 태그

    private static final List<Entry> TABLE = List.of(
            new Entry("minecraft:lava_bucket", 20000), new Entry("minecraft:coal_block", 16000), new Entry("minecraft:blaze_rod", 2400),
            new Entry("minecraft:coal", 1600), new Entry("minecraft:charcoal", 1600), new Entry("#minecraft:logs", 300),
            new Entry("#minecraft:bamboo_blocks", 300), new Entry("#minecraft:planks", 300), new Entry("minecraft:bamboo_mosaic", 300),
            new Entry("#minecraft:wooden_stairs", 300), new Entry("minecraft:bamboo_mosaic_stairs", 300), new Entry("#minecraft:wooden_slabs", 150),
            new Entry("minecraft:bamboo_mosaic_slab", 150), new Entry("#minecraft:wooden_trapdoors", 300),
            new Entry("#minecraft:wooden_pressure_plates", 300), new Entry("#minecraft:wooden_fences", 300), new Entry("#minecraft:fence_gates", 300),
            new Entry("minecraft:note_block", 300), new Entry("minecraft:bookshelf", 300), new Entry("minecraft:chiseled_bookshelf", 300),
            new Entry("minecraft:lectern", 300), new Entry("minecraft:jukebox", 300), new Entry("minecraft:chest", 300),
            new Entry("minecraft:trapped_chest", 300), new Entry("minecraft:crafting_table", 300), new Entry("minecraft:daylight_detector", 300),
            new Entry("#minecraft:banners", 300), new Entry("minecraft:bow", 300), new Entry("minecraft:fishing_rod", 300),
            new Entry("minecraft:ladder", 300), new Entry("#minecraft:signs", 200), new Entry("#minecraft:hanging_signs", 800),
            new Entry("minecraft:wooden_shovel", 200), new Entry("minecraft:wooden_sword", 200), new Entry("minecraft:wooden_hoe", 200),
            new Entry("minecraft:wooden_axe", 200), new Entry("minecraft:wooden_pickaxe", 200), new Entry("#minecraft:wooden_doors", 200),
            new Entry("#minecraft:boats", 1200), new Entry("#minecraft:wool", 100), new Entry("#minecraft:wooden_buttons", 100),
            new Entry("minecraft:stick", 100), new Entry("#minecraft:saplings", 100), new Entry("minecraft:bowl", 100),
            new Entry("#minecraft:wool_carpets", 67), new Entry("minecraft:dried_kelp_block", 4001), new Entry("minecraft:crossbow", 300),
            new Entry("minecraft:bamboo", 50), new Entry("minecraft:dead_bush", 100), new Entry("minecraft:scaffolding", 50),
            new Entry("minecraft:loom", 300), new Entry("minecraft:barrel", 300), new Entry("minecraft:cartography_table", 300),
            new Entry("minecraft:fletching_table", 300), new Entry("minecraft:smithing_table", 300), new Entry("minecraft:composter", 300),
            new Entry("minecraft:azalea", 100), new Entry("minecraft:flowering_azalea", 100), new Entry("minecraft:mangrove_roots", 300));

    /** 이 아이템이 태울 수 있는 틱(연료가 아니면 0). */
    public static int burnTime(ItemStack s) {
        if (s == null || s.isEmpty()) return 0;
        Integer id = s.itemId();
        if (ItemContext.isInTag("minecraft:item", "minecraft:non_flammable_wood", id)) return 0;
        String name = ItemTools.itemName(s);
        int result = 0;
        for (Entry e : TABLE) {
            boolean match = e.id().startsWith("#") ? ItemContext.isInTag("minecraft:item", e.id().substring(1), id) : e.id().equals(name);
            if (match) result = e.ticks();
        }
        return result;
    }

    public static boolean isFuel(ItemStack s) { return burnTime(s) > 0; }
}
