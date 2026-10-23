// 이식 묶음 8 의 블록 종류 등록(묶음마다 파일을 나눠 병렬 이식이 서로 부딪히지 않게).
package org.domi.furnace.server.block.types;

final class Batch8 {
    private Batch8() {}

    static void register() {
        Reg.type("minecraft:glow_lichen", GlowLichenBlock::new);
        Reg.type("minecraft:sculk_vein", SculkVeinBlock::new);
        Reg.type("minecraft:comparator", ComparatorBlock::new);
        Reg.type("minecraft:repeater", RepeaterBlock::new);
        Reg.type("minecraft:candle", CandleBlock::new);
        Reg.type("minecraft:candle_cake", CandleCakeBlock::new);
        Reg.type("minecraft:chorus_flower", ChorusFlowerBlock::new);
        Reg.type("minecraft:mangrove_propagule", MangrovePropaguleBlock::new);
        Reg.type("minecraft:sapling", SaplingBlock::new);
        Reg.type("minecraft:amethyst", AmethystBlock::new);
        Reg.type("minecraft:amethyst_cluster", AmethystClusterBlock::new);
        Reg.type("minecraft:budding_amethyst", BuddingAmethystBlock::new);
        Reg.type("minecraft:ladder", LadderBlock::new);
        Reg.type("minecraft:enchantment_table", EnchantingTableBlock::new);
        Reg.type("minecraft:end_portal", EndPortalBlock::new);
        Reg.type("minecraft:crafting_table", CraftingTableBlock::new);
        Reg.type("minecraft:fletching_table", FletchingTableBlock::new);
        Reg.type("minecraft:smithing_table", SmithingTableBlock::new);
        Reg.type("minecraft:carpet", CarpetBlock::new);
        Reg.type("minecraft:wool_carpet", WoolCarpetBlock::new);
        Reg.type("minecraft:coral", CoralBlock::new);
        Reg.type("minecraft:loom", LoomBlock::new);
        Reg.type("minecraft:crying_obsidian", CryingObsidianBlock::new);
        Reg.type("minecraft:bubble_column", BubbleColumnBlock::new);
    }
}
