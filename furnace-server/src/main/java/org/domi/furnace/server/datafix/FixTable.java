// 고침 단계 표: 바닐라 1.21.1 DataFixers.addFixers 의 순서(스키마 버전·하위 버전, 같은 스키마 안에서는 addFixer 순서)를 그대로 따른다.
// 구조물 템플릿(팔레트·블록 엔티티·엔티티·아이템)에 닿지 않는 고침(청크·월드 설정·통계·옵션·POI 등)은 넣지 않았다.
package org.domi.furnace.server.datafix;

import static org.domi.furnace.server.datafix.FixUtil.renamer;
import static org.domi.furnace.server.datafix.StructureDataFixer.map;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

final class FixTable {
    private FixTable() {}

    static final List<FixStep> STEPS;

    private static final List<FixStep> list = new ArrayList<>();

    private static FixStep step(int v, String name) { return step(v, 0, name); }

    private static FixStep step(int v, int sub, String name) {
        FixStep s = new FixStep(v, sub, name);
        list.add(s);
        return s;
    }

    /** 블록과 아이템을 같은 표로 바꾸는 흔한 꼴. */
    private static void blockAndItem(int v, String name, UnaryOperator<String> f) {
        step(v, name).blockState(BlockStateFixes.rename(f)).item(ItemStackFixes.rename(f));
    }

    /** 바닐라 createRenamer(Map) 이 아닌 무이름공간 표(AttributesRenameFix 2523). */
    private static UnaryOperator<String> plain(Map<String, String> m) { return s -> m.getOrDefault(s, s); }

    static {
        step(1456, "EntityItemFrameDirectionFix").entity(EntityFixes.named("minecraft:item_frame", EntityFixes::itemFrameFacing));
        step(1458, "CustomNameToComponent")
            .entity(EntityFixes::customNameExceptCommandMinecart)
            .item(ItemStackFixes.tag(ItemStackFixes::customName))
            .blockEntity(BlockEntityFixes::customNameExceptCommandBlock);
        step(1460, "EntityPaintingMotiveFix").entity(EntityFixes.named("minecraft:painting", EntityFixes::paintingMotive));
        step(1474, "Colorless shulker")
            .entity(EntityFixes.named("minecraft:shulker", EntityFixes::colorlessShulker))
            .blockState(BlockStateFixes.rename(renamer("minecraft:purple_shulker_box", "minecraft:shulker_box")))
            .item(ItemStackFixes.rename(renamer("minecraft:purple_shulker_box", "minecraft:shulker_box")));
        step(1475, "Flowing fixer").blockState(BlockStateFixes.rename(renamer(map(
            "minecraft:flowing_water", "minecraft:water", "minecraft:flowing_lava", "minecraft:lava"))));
        blockAndItem(1480, "Rename coral", renamer(map(
            "minecraft:blue_coral", "minecraft:tube_coral_block", "minecraft:pink_coral", "minecraft:brain_coral_block",
            "minecraft:purple_coral", "minecraft:bubble_coral_block", "minecraft:red_coral", "minecraft:fire_coral_block",
            "minecraft:yellow_coral", "minecraft:horn_coral_block", "minecraft:blue_coral_plant", "minecraft:tube_coral",
            "minecraft:pink_coral_plant", "minecraft:brain_coral", "minecraft:purple_coral_plant", "minecraft:bubble_coral",
            "minecraft:red_coral_plant", "minecraft:fire_coral", "minecraft:yellow_coral_plant", "minecraft:horn_coral",
            "minecraft:blue_coral_fan", "minecraft:tube_coral_fan", "minecraft:pink_coral_fan", "minecraft:brain_coral_fan",
            "minecraft:purple_coral_fan", "minecraft:bubble_coral_fan", "minecraft:red_coral_fan", "minecraft:fire_coral_fan",
            "minecraft:yellow_coral_fan", "minecraft:horn_coral_fan", "minecraft:blue_dead_coral", "minecraft:dead_tube_coral",
            "minecraft:pink_dead_coral", "minecraft:dead_brain_coral", "minecraft:purple_dead_coral", "minecraft:dead_bubble_coral",
            "minecraft:red_dead_coral", "minecraft:dead_fire_coral", "minecraft:yellow_dead_coral", "minecraft:dead_horn_coral")));
        step(1483, "EntityPufferfishRenameFix")
            .entity(EntityFixes.renameId(renamer("minecraft:puffer_fish", "minecraft:pufferfish")))
            .item(ItemStackFixes.rename(renamer("minecraft:puffer_fish_spawn_egg", "minecraft:pufferfish_spawn_egg")));
        blockAndItem(1484, "Rename seagrass", renamer(map(
            "minecraft:sea_grass", "minecraft:seagrass", "minecraft:tall_sea_grass", "minecraft:tall_seagrass")));
        step(1486, "EntityCodSalmonFix")
            .entity(EntityFixes.renameId(renamer(map("minecraft:salmon_mob", "minecraft:salmon", "minecraft:cod_mob", "minecraft:cod"))))
            .item(ItemStackFixes.rename(renamer(map(
                "minecraft:salmon_mob_spawn_egg", "minecraft:salmon_spawn_egg", "minecraft:cod_mob_spawn_egg", "minecraft:cod_spawn_egg"))));
        blockAndItem(1487, "Rename prismarine_brick(s)_*", renamer(map(
            "minecraft:prismarine_bricks_slab", "minecraft:prismarine_brick_slab",
            "minecraft:prismarine_bricks_stairs", "minecraft:prismarine_brick_stairs")));
        step(1488, "Rename kelp, command block names")
            .blockState(BlockStateFixes.rename(renamer(map("minecraft:kelp_top", "minecraft:kelp", "minecraft:kelp", "minecraft:kelp_plant"))))
            .item(ItemStackFixes.rename(renamer("minecraft:kelp_top", "minecraft:kelp")))
            .blockEntity(BlockEntityFixes.named("minecraft:command_block", BlockEntityFixes::customNameToComponent))
            .entity(EntityFixes.named("minecraft:commandblock_minecart", BlockEntityFixes::customNameToComponent));
        step(1490, "Rename melon")
            .blockState(BlockStateFixes.rename(renamer("minecraft:melon_block", "minecraft:melon")))
            .item(ItemStackFixes.rename(renamer(map(
                "minecraft:melon_block", "minecraft:melon", "minecraft:melon", "minecraft:melon_slice",
                "minecraft:speckled_melon", "minecraft:glistering_melon_slice"))));
        step(1494, "ItemStackEnchantmentNamesFix").item(ItemStackFixes.tag(ItemStackFixes::enchantmentNames));
        Map<String, String> barks = map(
            "minecraft:portal", "minecraft:nether_portal", "minecraft:oak_bark", "minecraft:oak_wood", "minecraft:spruce_bark", "minecraft:spruce_wood",
            "minecraft:birch_bark", "minecraft:birch_wood", "minecraft:jungle_bark", "minecraft:jungle_wood", "minecraft:acacia_bark", "minecraft:acacia_wood",
            "minecraft:dark_oak_bark", "minecraft:dark_oak_wood", "minecraft:stripped_oak_bark", "minecraft:stripped_oak_wood",
            "minecraft:stripped_spruce_bark", "minecraft:stripped_spruce_wood", "minecraft:stripped_birch_bark", "minecraft:stripped_birch_wood",
            "minecraft:stripped_jungle_bark", "minecraft:stripped_jungle_wood", "minecraft:stripped_acacia_bark", "minecraft:stripped_acacia_wood",
            "minecraft:stripped_dark_oak_bark", "minecraft:stripped_dark_oak_wood", "minecraft:mob_spawner", "minecraft:spawner");
        Map<String, String> barkItems = new LinkedHashMap<>(barks);
        barkItems.putAll(map(
            "minecraft:clownfish", "minecraft:tropical_fish", "minecraft:chorus_fruit_popped", "minecraft:popped_chorus_fruit",
            "minecraft:evocation_illager_spawn_egg", "minecraft:evoker_spawn_egg", "minecraft:vindication_illager_spawn_egg", "minecraft:vindicator_spawn_egg"));
        step(1510, "The Renamening")
            .blockState(BlockStateFixes.rename(renamer(barks)))
            .item(ItemStackFixes.rename(renamer(barkItems)))
            .entity(EntityFixes.renameId(EntityFixes::renamening));
        step(1515, "Rename coral fan blocks").blockState(BlockStateFixes.rename(renamer(map(
            "minecraft:tube_coral_fan", "minecraft:tube_coral_wall_fan", "minecraft:brain_coral_fan", "minecraft:brain_coral_wall_fan",
            "minecraft:bubble_coral_fan", "minecraft:bubble_coral_wall_fan", "minecraft:fire_coral_fan", "minecraft:fire_coral_wall_fan",
            "minecraft:horn_coral_fan", "minecraft:horn_coral_wall_fan"))));
        step(1800, "Rename dye items").item(ItemStackFixes.rename(renamer(map(
            "minecraft:cactus_green", "minecraft:green_dye", "minecraft:rose_red", "minecraft:red_dye", "minecraft:dandelion_yellow", "minecraft:yellow_dye"))));
        step(1802, "Rename sign & stone slab")
            .blockState(BlockStateFixes.rename(renamer(map(
                "minecraft:stone_slab", "minecraft:smooth_stone_slab", "minecraft:sign", "minecraft:oak_sign", "minecraft:wall_sign", "minecraft:oak_wall_sign"))))
            .item(ItemStackFixes.rename(renamer(map("minecraft:stone_slab", "minecraft:smooth_stone_slab", "minecraft:sign", "minecraft:oak_sign"))));
        step(1803, "ItemLoreFix").item(ItemStackFixes.tag(ItemStackFixes::lore));
        step(1904, "EntityCatSplitFix").entity(EntityFixes::catSplit);
        step(1914, "WeaponSmithChestLootTableFix").blockEntity(BlockEntityFixes.named("minecraft:chest", BlockEntityFixes::weaponsmithLoot));
        step(1917, "CatTypeFix").entity(EntityFixes.named("minecraft:cat", EntityFixes::catType));
        step(1928, "EntityRavagerRenameFix")
            .entity(EntityFixes.renameId(renamer("minecraft:illager_beast", "minecraft:ravager")))
            .item(ItemStackFixes.rename(renamer("minecraft:illager_beast_spawn_egg", "minecraft:ravager_spawn_egg")));
        step(1948, "OminousBannerRenameFix").item(ItemStackFixes.tagIf(Set.of("minecraft:white_banner"), ItemStackFixes::ominousBannerName));
        step(1953, "OminousBannerBlockEntityRenameFix").blockEntity(BlockEntityFixes.named("minecraft:banner", BlockEntityFixes::ominousBannerName));
        blockAndItem(2209, "Rename bee_hive", renamer("minecraft:bee_hive", "minecraft:beehive"));
        step(2503, "WallPropertyFix").blockState(BlockStateFixes::wallProperty);
        blockAndItem(2508, "Renamed fungi to fungus", renamer(map(
            "minecraft:warped_fungi", "minecraft:warped_fungus", "minecraft:crimson_fungi", "minecraft:crimson_fungus")));
        step(2509, "EntityZombifiedPiglinRenameFix")
            .entity(EntityFixes.renameId(renamer("minecraft:zombie_pigman", "minecraft:zombified_piglin")))
            .item(ItemStackFixes.rename(renamer("minecraft:zombie_pigman_spawn_egg", "minecraft:zombified_piglin_spawn_egg")));
        step(2514, "UUID fixes")
            .entity(EntityFixes::attributeModifierUuids)
            .blockEntity(BlockEntityFixes.named("minecraft:conduit", BlockEntityFixes::conduitUuid))
            .blockEntity(BlockEntityFixes.named("minecraft:skull", BlockEntityFixes::skullUuid))
            .item(ItemStackFixes::uuids);
        step(2518, "Jigsaw properties & rotation")
            .blockEntity(BlockEntityFixes.named("minecraft:jigsaw", BlockEntityFixes::jigsawProperties))
            .blockState(BlockStateFixes::jigsawRotation);
        UnaryOperator<String> attrs2523 = plain(map(
            "generic.maxHealth", "minecraft:generic.max_health", "Max Health", "minecraft:generic.max_health",
            "zombie.spawnReinforcements", "minecraft:zombie.spawn_reinforcements", "Spawn Reinforcements Chance", "minecraft:zombie.spawn_reinforcements",
            "horse.jumpStrength", "minecraft:horse.jump_strength", "Jump Strength", "minecraft:horse.jump_strength",
            "generic.followRange", "minecraft:generic.follow_range", "Follow Range", "minecraft:generic.follow_range",
            "generic.knockbackResistance", "minecraft:generic.knockback_resistance", "Knockback Resistance", "minecraft:generic.knockback_resistance",
            "generic.movementSpeed", "minecraft:generic.movement_speed", "Movement Speed", "minecraft:generic.movement_speed",
            "generic.flyingSpeed", "minecraft:generic.flying_speed", "Flying Speed", "minecraft:generic.flying_speed",
            "generic.attackDamage", "minecraft:generic.attack_damage", "generic.attackKnockback", "minecraft:generic.attack_knockback",
            "generic.attackSpeed", "minecraft:generic.attack_speed", "generic.armorToughness", "minecraft:generic.armor_toughness"));
        step(2523, "Attribute renames").item(ItemStackFixes.renameAttributes(attrs2523)).entity(EntityFixes.renameAttributes(attrs2523));
        step(2528, "Rename soul fire torch and lantern")
            .item(ItemStackFixes.rename(renamer(map(
                "minecraft:soul_fire_torch", "minecraft:soul_torch", "minecraft:soul_fire_lantern", "minecraft:soul_lantern"))))
            .blockState(BlockStateFixes.rename(renamer(map(
                "minecraft:soul_fire_torch", "minecraft:soul_torch", "minecraft:soul_fire_wall_torch", "minecraft:soul_wall_torch",
                "minecraft:soul_fire_lantern", "minecraft:soul_lantern"))));
        step(2531, "RedstoneWireConnectionsFix").blockState(BlockStateFixes::redstoneConnections);
        step(2679, "CauldronRenameFix").blockState(BlockStateFixes::cauldron);
        blockAndItem(2680, "Renamed grass path to dirt path", renamer("minecraft:grass_path", "minecraft:dirt_path"));
        blockAndItem(2690, "Renamed copper blocks to new oxidized terms", renamer(map(
            "minecraft:weathered_copper_block", "minecraft:oxidized_copper_block", "minecraft:semi_weathered_copper_block", "minecraft:weathered_copper_block",
            "minecraft:lightly_weathered_copper_block", "minecraft:exposed_copper_block", "minecraft:weathered_cut_copper", "minecraft:oxidized_cut_copper",
            "minecraft:semi_weathered_cut_copper", "minecraft:weathered_cut_copper", "minecraft:lightly_weathered_cut_copper", "minecraft:exposed_cut_copper",
            "minecraft:weathered_cut_copper_stairs", "minecraft:oxidized_cut_copper_stairs",
            "minecraft:semi_weathered_cut_copper_stairs", "minecraft:weathered_cut_copper_stairs",
            "minecraft:lightly_weathered_cut_copper_stairs", "minecraft:exposed_cut_copper_stairs",
            "minecraft:weathered_cut_copper_slab", "minecraft:oxidized_cut_copper_slab",
            "minecraft:semi_weathered_cut_copper_slab", "minecraft:weathered_cut_copper_slab",
            "minecraft:lightly_weathered_cut_copper_slab", "minecraft:exposed_cut_copper_slab",
            "minecraft:waxed_semi_weathered_copper", "minecraft:waxed_weathered_copper", "minecraft:waxed_lightly_weathered_copper", "minecraft:waxed_exposed_copper",
            "minecraft:waxed_semi_weathered_cut_copper", "minecraft:waxed_weathered_cut_copper",
            "minecraft:waxed_lightly_weathered_cut_copper", "minecraft:waxed_exposed_cut_copper",
            "minecraft:waxed_semi_weathered_cut_copper_stairs", "minecraft:waxed_weathered_cut_copper_stairs",
            "minecraft:waxed_lightly_weathered_cut_copper_stairs", "minecraft:waxed_exposed_cut_copper_stairs",
            "minecraft:waxed_semi_weathered_cut_copper_slab", "minecraft:waxed_weathered_cut_copper_slab",
            "minecraft:waxed_lightly_weathered_cut_copper_slab", "minecraft:waxed_exposed_cut_copper_slab")));
        blockAndItem(2691, "Rename copper suffixes", renamer(map(
            "minecraft:waxed_copper", "minecraft:waxed_copper_block", "minecraft:oxidized_copper_block", "minecraft:oxidized_copper",
            "minecraft:weathered_copper_block", "minecraft:weathered_copper", "minecraft:exposed_copper_block", "minecraft:exposed_copper")));
        blockAndItem(2696, "Renamed grimstone to deepslate", renamer(map(
            "minecraft:grimstone", "minecraft:deepslate", "minecraft:grimstone_slab", "minecraft:cobbled_deepslate_slab",
            "minecraft:grimstone_stairs", "minecraft:cobbled_deepslate_stairs", "minecraft:grimstone_wall", "minecraft:cobbled_deepslate_wall",
            "minecraft:polished_grimstone", "minecraft:polished_deepslate", "minecraft:polished_grimstone_slab", "minecraft:polished_deepslate_slab",
            "minecraft:polished_grimstone_stairs", "minecraft:polished_deepslate_stairs", "minecraft:polished_grimstone_wall", "minecraft:polished_deepslate_wall",
            "minecraft:grimstone_tiles", "minecraft:deepslate_tiles", "minecraft:grimstone_tile_slab", "minecraft:deepslate_tile_slab",
            "minecraft:grimstone_tile_stairs", "minecraft:deepslate_tile_stairs", "minecraft:grimstone_tile_wall", "minecraft:deepslate_tile_wall",
            "minecraft:grimstone_bricks", "minecraft:deepslate_bricks", "minecraft:grimstone_brick_slab", "minecraft:deepslate_brick_slab",
            "minecraft:grimstone_brick_stairs", "minecraft:deepslate_brick_stairs", "minecraft:grimstone_brick_wall", "minecraft:deepslate_brick_wall",
            "minecraft:chiseled_grimstone", "minecraft:chiseled_deepslate")));
        step(2700, "Renamed cave vines blocks").blockState(BlockStateFixes.rename(renamer(map(
            "minecraft:cave_vines_head", "minecraft:cave_vines", "minecraft:cave_vines_body", "minecraft:cave_vines_plant"))));
        blockAndItem(2717, "Rename azalea_leaves_flowers", renamer("minecraft:azalea_leaves_flowers", "minecraft:flowering_azalea_leaves"));
        step(2831, "UntaggedSpawnerFix")
            .blockEntity(BlockEntityFixes.named("minecraft:mob_spawner", BlockEntityFixes::untaggedSpawner))
            .entity(EntityFixes.named("minecraft:spawner_minecart", BlockEntityFixes::untaggedSpawner));
        step(3086, "Change cat variant type").entity(EntityFixes.named("minecraft:cat", EntityFixes.variant("CatType", EntityFixes::catVariant)));
        step(3087, "Change frog variant type").entity(EntityFixes.named("minecraft:frog", EntityFixes.variant("Variant", EntityFixes::frogVariant)));
        step(3090, "EntityPaintingFieldsRenameFix").entity(EntityFixes.named("minecraft:painting", EntityFixes::paintingFields));
        step(3097, "Filtered text, british shorthair")
            .item(ItemStackFixes.tagIf(Set.of("minecraft:writable_book", "minecraft:written_book"), ItemStackFixes::removeFilteredBook))
            .blockEntity(BlockEntityFixes.named("minecraft:sign", BlockEntityFixes::removeFilteredSignText))
            .entity(EntityFixes.named("minecraft:cat", EntityFixes::britishShorthair));
        step(3438, "Brushable block, pottery shards")
            .blockEntity(BlockEntityFixes.renameId(renamer("minecraft:suspicious_sand", "minecraft:brushable_block")))
            .blockEntity(BlockEntityFixes.named("minecraft:brushable_block", BlockEntityFixes::brushableFields))
            .item(ItemStackFixes.rename(renamer(map(
                "minecraft:pottery_shard_archer", "minecraft:archer_pottery_shard", "minecraft:pottery_shard_prize", "minecraft:prize_pottery_shard",
                "minecraft:pottery_shard_arms_up", "minecraft:arms_up_pottery_shard", "minecraft:pottery_shard_skull", "minecraft:skull_pottery_shard"))));
        step(3439, "Sign text format")
            .blockEntity(BlockEntityFixes.named("minecraft:sign", BlockEntityFixes::signDoubleSided))
            .blockEntity(BlockEntityFixes.named("minecraft:hanging_sign", BlockEntityFixes::signDoubleSided));
        Map<String, String> sherds = new LinkedHashMap<>();
        for (String s : new String[] {"angler", "archer", "arms_up", "blade", "brewer", "burn", "danger", "explorer", "friend", "heart",
            "heartbreak", "howl", "miner", "mourner", "plenty", "prize", "sheaf", "shelter", "skull", "snort"}) {
            sherds.put("minecraft:" + s + "_pottery_shard", "minecraft:" + s + "_pottery_sherd");
        }
        step(3447, "Pottery shard to sherd").item(ItemStackFixes.rename(renamer(sherds)));
        step(3448, "DecoratedPotFieldRenameFix").blockEntity(BlockEntityFixes.named("minecraft:decorated_pot", BlockEntityFixes::decoratedPotFields));
        step(3564, "Drop invalid sign datafix data")
            .blockEntity(BlockEntityFixes.named("minecraft:sign", BlockEntityFixes::dropInvalidSignData))
            .blockEntity(BlockEntityFixes.named("minecraft:hanging_sign", BlockEntityFixes::dropInvalidSignData));
        step(3568, "MobEffectIdFix")
            .blockEntity(BlockEntityFixes.named("minecraft:beacon", MobEffectFixes::beacon))
            .entity(MobEffectFixes::entity)
            .item(MobEffectFixes::item);
        blockAndItem(3692, "Rename grass to short_grass", renamer("minecraft:grass", "minecraft:short_grass"));
        step(3800, "Rename scute").item(ItemStackFixes.rename(renamer("minecraft:scute", "minecraft:turtle_scute")));
        step(3803, "Rename sweeping enchant").item(ItemStackFixes.tag(ItemStackFixes::sweepingEdge));
        step(3813, "BlockPosFormatAndRenamesFix")
            .entity(EntityFixes::blockPosFields)
            .blockEntity(BlockEntityFixes.named("minecraft:beehive", BlockEntityFixes::beehivePos))
            .blockEntity(BlockEntityFixes.named("minecraft:end_gateway", BlockEntityFixes::endGatewayPos))
            .item(ItemStackFixes.tagIf(Set.of("minecraft:compass"), ItemStackFixes::lodestonePos));
        UnaryOperator<String> jump = renamer("minecraft:horse.jump_strength", "minecraft:generic.jump_strength");
        step(3814, "Rename jump strength attribute").item(ItemStackFixes.renameAttributes(jump)).entity(EntityFixes.renameAttributes(jump));
        step(3818, "BeehiveFieldRenameFix").blockEntity(BlockEntityFixes.named("minecraft:beehive", BlockEntityFixes::beehiveFields));
        step(3818, 1, "BannerPatternFormatFix").blockEntity(BlockEntityFixes.named("minecraft:banner", BlockEntityFixes::bannerPatterns));
        step(3818, 5, "ItemStackComponentizationFix").item(ItemStackFixes::componentize);
        step(3820, "PlayerHeadBlockProfileFix").blockEntity(BlockEntityFixes.named("minecraft:skull", BlockEntityFixes::skullProfile));
        step(3825, "Custom name to item name")
            .item(ItemStackFixes::customNameToItemName)
            .blockEntity(BlockEntityFixes.named("minecraft:banner", BlockEntityFixes::bannerOminousName));
        step(3833, "RemoveEmptyItemInBrushableBlockFix")
            .blockEntity(BlockEntityFixes.named("minecraft:brushable_block", BlockEntityFixes::brushableEmptyItem));
        step(3945, "AttributeIdFix, JukeboxTicksSinceSongStartedFix")
            .item(ItemStackFixes::attributeModifierIds)
            .entity(EntityFixes::attributeIds)
            .blockEntity(BlockEntityFixes.named("minecraft:jukebox", BlockEntityFixes::jukeboxTicks));
        STEPS = List.copyOf(StructureDataFixer.sorted(list));
    }
}
