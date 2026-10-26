// 바닐라 VillagerTrades: 직업(VillagerProfession) × 레벨(1~5)별 거래 후보(ItemListing)와 방랑 상인 거래표.
// 거래표는 디컴파일 원본의 TRADES / WANDERING_TRADER_TRADES 를 그대로 옮겼다(trade_rebalance 실험용 표는 제외).
// 후보 한 개는 getOffer(주민 정보, 난수) 로 거래를 만든다 — 난수는 호출 순서까지 바닐라와 같다.
// TODO(지도): 지도 거래(바다/삼림 저택·전당 지도)는 항상 null(거래 없음). 구조물 탐색(StructureLocator)은 있지만 지도 만들기(map_id 발급·저장·
// 바이옴 미리보기 색)와 skipKnown 용 시작점 references 증가·저장이 아직 없다.
//   표의 칸은 그대로 두어 후보 뽑기(Villager.addOffersFromItemListings)의 난수 소비 순서는 유지한다.
package org.domi.furnace.server.trade;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.effect.Potions;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.EnchantTools;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.loot.Enchantment;
import org.domi.furnace.server.loot.Enchantments;
import org.domi.furnace.server.loot.LegacyNoiseRandom;
import org.domi.furnace.server.loot.LootData;
import org.domi.furnace.server.loot.LootRegistries;
import org.domi.furnace.server.loot.LootStack;
import org.domi.furnace.server.recipe.SpecialRecipes;

public final class VillagerTrades {
    private VillagerTrades() { }

    private static final String EMERALD = "minecraft:emerald", BOOK = "minecraft:book";

    /** 거래를 만들 때 필요한 주변 정보: 주민 종류(마을 사람 type — 주민이 아니면 null)와 데이터 팩 전리품 데이터(없으면 null). */
    public record TradeContext(String villagerType, LootData loot) { }

    /** 바닐라 VillagerTrades.ItemListing: 거래 하나를 만든다(만들 수 없으면 null). */
    @FunctionalInterface
    public interface ItemListing {
        MerchantOffer getOffer(TradeContext ctx, RandomSource random);
    }

    /** 직업 이름(예: "farmer") → 레벨(index 0 = 레벨 1) 별 후보. 거래가 없는 직업(무직·바보)은 없다. */
    public static final Map<String, ItemListing[][]> TRADES = new HashMap<>();

    static {
        TRADES.put("farmer", new ItemListing[][] {
            { // 레벨 1
                emeraldForItems("wheat", 20, 16, 2, 1),
                emeraldForItems("potato", 26, 16, 2, 1),
                emeraldForItems("carrot", 22, 16, 2, 1),
                emeraldForItems("beetroot", 15, 16, 2, 1),
                itemsForEmeralds("bread", 1, 6, 16, 1, 0.05F),
            },
            { // 레벨 2
                emeraldForItems("pumpkin", 6, 12, 10, 1),
                itemsForEmeralds("pumpkin_pie", 1, 4, 12, 5, 0.05F),
                itemsForEmeralds("apple", 1, 4, 16, 5, 0.05F),
            },
            { // 레벨 3
                itemsForEmeralds("cookie", 3, 18, 12, 10, 0.05F),
                emeraldForItems("melon", 4, 12, 20, 1),
            },
            { // 레벨 4
                itemsForEmeralds("cake", 1, 1, 12, 15, 0.05F),
                suspiciousStewForEmerald("night_vision", 100, 15),
                suspiciousStewForEmerald("jump_boost", 160, 15),
                suspiciousStewForEmerald("weakness", 140, 15),
                suspiciousStewForEmerald("blindness", 120, 15),
                suspiciousStewForEmerald("poison", 280, 15),
                suspiciousStewForEmerald("saturation", 7, 15),
            },
            { // 레벨 5
                itemsForEmeralds("golden_carrot", 3, 3, 12, 30, 0.05F),
                itemsForEmeralds("glistering_melon_slice", 4, 3, 12, 30, 0.05F),
            },
        });
        TRADES.put("fisherman", new ItemListing[][] {
            { // 레벨 1
                emeraldForItems("string", 20, 16, 2, 1),
                emeraldForItems("coal", 10, 16, 2, 1),
                itemsAndEmeraldsToItems("cod", 6, 1, "cooked_cod", 6, 16, 1, 0.05F),
                itemsForEmeralds("cod_bucket", 3, 1, 16, 1, 0.05F),
            },
            { // 레벨 2
                emeraldForItems("cod", 15, 16, 10, 1),
                itemsAndEmeraldsToItems("salmon", 6, 1, "cooked_salmon", 6, 16, 5, 0.05F),
                itemsForEmeralds("campfire", 2, 1, 12, 5, 0.05F),
            },
            { // 레벨 3
                emeraldForItems("salmon", 13, 16, 20, 1),
                enchantedItemForEmeralds("fishing_rod", 3, 3, 10, 0.2F),
            },
            { // 레벨 4
                emeraldForItems("tropical_fish", 6, 12, 30, 1),
            },
            { // 레벨 5
                emeraldForItems("pufferfish", 4, 12, 30, 1),
                emeraldForVillagerTypeItem(1, 12, 30, Map.of("plains", "oak_boat", "taiga", "spruce_boat", "snow", "spruce_boat", "desert", "jungle_boat", "jungle", "jungle_boat", "savanna", "acacia_boat", "swamp", "dark_oak_boat")),
            },
        });
        TRADES.put("shepherd", new ItemListing[][] {
            { // 레벨 1
                emeraldForItems("white_wool", 18, 16, 2, 1),
                emeraldForItems("brown_wool", 18, 16, 2, 1),
                emeraldForItems("black_wool", 18, 16, 2, 1),
                emeraldForItems("gray_wool", 18, 16, 2, 1),
                itemsForEmeralds("shears", 2, 1, 12, 1, 0.05F),
            },
            { // 레벨 2
                emeraldForItems("white_dye", 12, 16, 10, 1),
                emeraldForItems("gray_dye", 12, 16, 10, 1),
                emeraldForItems("black_dye", 12, 16, 10, 1),
                emeraldForItems("light_blue_dye", 12, 16, 10, 1),
                emeraldForItems("lime_dye", 12, 16, 10, 1),
                itemsForEmeralds("white_wool", 1, 1, 16, 5, 0.05F),
                itemsForEmeralds("orange_wool", 1, 1, 16, 5, 0.05F),
                itemsForEmeralds("magenta_wool", 1, 1, 16, 5, 0.05F),
                itemsForEmeralds("light_blue_wool", 1, 1, 16, 5, 0.05F),
                itemsForEmeralds("yellow_wool", 1, 1, 16, 5, 0.05F),
                itemsForEmeralds("lime_wool", 1, 1, 16, 5, 0.05F),
                itemsForEmeralds("pink_wool", 1, 1, 16, 5, 0.05F),
                itemsForEmeralds("gray_wool", 1, 1, 16, 5, 0.05F),
                itemsForEmeralds("light_gray_wool", 1, 1, 16, 5, 0.05F),
                itemsForEmeralds("cyan_wool", 1, 1, 16, 5, 0.05F),
                itemsForEmeralds("purple_wool", 1, 1, 16, 5, 0.05F),
                itemsForEmeralds("blue_wool", 1, 1, 16, 5, 0.05F),
                itemsForEmeralds("brown_wool", 1, 1, 16, 5, 0.05F),
                itemsForEmeralds("green_wool", 1, 1, 16, 5, 0.05F),
                itemsForEmeralds("red_wool", 1, 1, 16, 5, 0.05F),
                itemsForEmeralds("black_wool", 1, 1, 16, 5, 0.05F),
                itemsForEmeralds("white_carpet", 1, 4, 16, 5, 0.05F),
                itemsForEmeralds("orange_carpet", 1, 4, 16, 5, 0.05F),
                itemsForEmeralds("magenta_carpet", 1, 4, 16, 5, 0.05F),
                itemsForEmeralds("light_blue_carpet", 1, 4, 16, 5, 0.05F),
                itemsForEmeralds("yellow_carpet", 1, 4, 16, 5, 0.05F),
                itemsForEmeralds("lime_carpet", 1, 4, 16, 5, 0.05F),
                itemsForEmeralds("pink_carpet", 1, 4, 16, 5, 0.05F),
                itemsForEmeralds("gray_carpet", 1, 4, 16, 5, 0.05F),
                itemsForEmeralds("light_gray_carpet", 1, 4, 16, 5, 0.05F),
                itemsForEmeralds("cyan_carpet", 1, 4, 16, 5, 0.05F),
                itemsForEmeralds("purple_carpet", 1, 4, 16, 5, 0.05F),
                itemsForEmeralds("blue_carpet", 1, 4, 16, 5, 0.05F),
                itemsForEmeralds("brown_carpet", 1, 4, 16, 5, 0.05F),
                itemsForEmeralds("green_carpet", 1, 4, 16, 5, 0.05F),
                itemsForEmeralds("red_carpet", 1, 4, 16, 5, 0.05F),
                itemsForEmeralds("black_carpet", 1, 4, 16, 5, 0.05F),
            },
            { // 레벨 3
                emeraldForItems("yellow_dye", 12, 16, 20, 1),
                emeraldForItems("light_gray_dye", 12, 16, 20, 1),
                emeraldForItems("orange_dye", 12, 16, 20, 1),
                emeraldForItems("red_dye", 12, 16, 20, 1),
                emeraldForItems("pink_dye", 12, 16, 20, 1),
                itemsForEmeralds("white_bed", 3, 1, 12, 10, 0.05F),
                itemsForEmeralds("yellow_bed", 3, 1, 12, 10, 0.05F),
                itemsForEmeralds("red_bed", 3, 1, 12, 10, 0.05F),
                itemsForEmeralds("black_bed", 3, 1, 12, 10, 0.05F),
                itemsForEmeralds("blue_bed", 3, 1, 12, 10, 0.05F),
                itemsForEmeralds("brown_bed", 3, 1, 12, 10, 0.05F),
                itemsForEmeralds("cyan_bed", 3, 1, 12, 10, 0.05F),
                itemsForEmeralds("gray_bed", 3, 1, 12, 10, 0.05F),
                itemsForEmeralds("green_bed", 3, 1, 12, 10, 0.05F),
                itemsForEmeralds("light_blue_bed", 3, 1, 12, 10, 0.05F),
                itemsForEmeralds("light_gray_bed", 3, 1, 12, 10, 0.05F),
                itemsForEmeralds("lime_bed", 3, 1, 12, 10, 0.05F),
                itemsForEmeralds("magenta_bed", 3, 1, 12, 10, 0.05F),
                itemsForEmeralds("orange_bed", 3, 1, 12, 10, 0.05F),
                itemsForEmeralds("pink_bed", 3, 1, 12, 10, 0.05F),
                itemsForEmeralds("purple_bed", 3, 1, 12, 10, 0.05F),
            },
            { // 레벨 4
                emeraldForItems("brown_dye", 12, 16, 30, 1),
                emeraldForItems("purple_dye", 12, 16, 30, 1),
                emeraldForItems("blue_dye", 12, 16, 30, 1),
                emeraldForItems("green_dye", 12, 16, 30, 1),
                emeraldForItems("magenta_dye", 12, 16, 30, 1),
                emeraldForItems("cyan_dye", 12, 16, 30, 1),
                itemsForEmeralds("white_banner", 3, 1, 12, 15, 0.05F),
                itemsForEmeralds("blue_banner", 3, 1, 12, 15, 0.05F),
                itemsForEmeralds("light_blue_banner", 3, 1, 12, 15, 0.05F),
                itemsForEmeralds("red_banner", 3, 1, 12, 15, 0.05F),
                itemsForEmeralds("pink_banner", 3, 1, 12, 15, 0.05F),
                itemsForEmeralds("green_banner", 3, 1, 12, 15, 0.05F),
                itemsForEmeralds("lime_banner", 3, 1, 12, 15, 0.05F),
                itemsForEmeralds("gray_banner", 3, 1, 12, 15, 0.05F),
                itemsForEmeralds("black_banner", 3, 1, 12, 15, 0.05F),
                itemsForEmeralds("purple_banner", 3, 1, 12, 15, 0.05F),
                itemsForEmeralds("magenta_banner", 3, 1, 12, 15, 0.05F),
                itemsForEmeralds("cyan_banner", 3, 1, 12, 15, 0.05F),
                itemsForEmeralds("brown_banner", 3, 1, 12, 15, 0.05F),
                itemsForEmeralds("yellow_banner", 3, 1, 12, 15, 0.05F),
                itemsForEmeralds("orange_banner", 3, 1, 12, 15, 0.05F),
                itemsForEmeralds("light_gray_banner", 3, 1, 12, 15, 0.05F),
            },
            { // 레벨 5
                itemsForEmeralds("painting", 2, 3, 12, 30, 0.05F),
            },
        });
        TRADES.put("fletcher", new ItemListing[][] {
            { // 레벨 1
                emeraldForItems("stick", 32, 16, 2, 1),
                itemsForEmeralds("arrow", 1, 16, 12, 1, 0.05F),
                itemsAndEmeraldsToItems("gravel", 10, 1, "flint", 10, 12, 1, 0.05F),
            },
            { // 레벨 2
                emeraldForItems("flint", 26, 12, 10, 1),
                itemsForEmeralds("bow", 2, 1, 12, 5, 0.05F),
            },
            { // 레벨 3
                emeraldForItems("string", 14, 16, 20, 1),
                itemsForEmeralds("crossbow", 3, 1, 12, 10, 0.05F),
            },
            { // 레벨 4
                emeraldForItems("feather", 24, 16, 30, 1),
                enchantedItemForEmeralds("bow", 2, 3, 15, 0.05F),
            },
            { // 레벨 5
                emeraldForItems("tripwire_hook", 8, 12, 30, 1),
                enchantedItemForEmeralds("crossbow", 3, 3, 15, 0.05F),
                tippedArrowForItemsAndEmeralds("arrow", 5, "tipped_arrow", 5, 2, 12, 30),
            },
        });
        TRADES.put("librarian", new ItemListing[][] {
            { // 레벨 1
                emeraldForItems("paper", 24, 16, 2, 1),
                enchantBookForEmeralds(1),
                itemsForEmeralds("bookshelf", 9, 1, 12, 1, 0.05F),
            },
            { // 레벨 2
                emeraldForItems("book", 4, 12, 10, 1),
                enchantBookForEmeralds(5),
                itemsForEmeralds("lantern", 1, 1, 12, 5, 0.05F),
            },
            { // 레벨 3
                emeraldForItems("ink_sac", 5, 12, 20, 1),
                enchantBookForEmeralds(10),
                itemsForEmeralds("glass", 1, 4, 12, 10, 0.05F),
            },
            { // 레벨 4
                emeraldForItems("writable_book", 2, 12, 30, 1),
                enchantBookForEmeralds(15),
                itemsForEmeralds("clock", 5, 1, 12, 15, 0.05F),
                itemsForEmeralds("compass", 4, 1, 12, 15, 0.05F),
            },
            { // 레벨 5
                itemsForEmeralds("name_tag", 20, 1, 12, 30, 0.05F),
            },
        });
        TRADES.put("cartographer", new ItemListing[][] {
            { // 레벨 1
                emeraldForItems("paper", 24, 16, 2, 1),
                itemsForEmeralds("map", 7, 1, 12, 1, 0.05F),
            },
            { // 레벨 2
                emeraldForItems("glass_pane", 11, 16, 10, 1),
                treasureMap("filled_map.monument"),
            },
            { // 레벨 3
                emeraldForItems("compass", 1, 12, 20, 1),
                treasureMap("filled_map.mansion"),
                treasureMap("filled_map.trial_chambers"),
            },
            { // 레벨 4
                itemsForEmeralds("item_frame", 7, 1, 12, 15, 0.05F),
                itemsForEmeralds("white_banner", 3, 1, 12, 15, 0.05F),
                itemsForEmeralds("blue_banner", 3, 1, 12, 15, 0.05F),
                itemsForEmeralds("light_blue_banner", 3, 1, 12, 15, 0.05F),
                itemsForEmeralds("red_banner", 3, 1, 12, 15, 0.05F),
                itemsForEmeralds("pink_banner", 3, 1, 12, 15, 0.05F),
                itemsForEmeralds("green_banner", 3, 1, 12, 15, 0.05F),
                itemsForEmeralds("lime_banner", 3, 1, 12, 15, 0.05F),
                itemsForEmeralds("gray_banner", 3, 1, 12, 15, 0.05F),
                itemsForEmeralds("black_banner", 3, 1, 12, 15, 0.05F),
                itemsForEmeralds("purple_banner", 3, 1, 12, 15, 0.05F),
                itemsForEmeralds("magenta_banner", 3, 1, 12, 15, 0.05F),
                itemsForEmeralds("cyan_banner", 3, 1, 12, 15, 0.05F),
                itemsForEmeralds("brown_banner", 3, 1, 12, 15, 0.05F),
                itemsForEmeralds("yellow_banner", 3, 1, 12, 15, 0.05F),
                itemsForEmeralds("orange_banner", 3, 1, 12, 15, 0.05F),
                itemsForEmeralds("light_gray_banner", 3, 1, 12, 15, 0.05F),
            },
            { // 레벨 5
                itemsForEmeralds("globe_banner_pattern", 8, 1, 12, 30, 0.05F),
            },
        });
        TRADES.put("cleric", new ItemListing[][] {
            { // 레벨 1
                emeraldForItems("rotten_flesh", 32, 16, 2, 1),
                itemsForEmeralds("redstone", 1, 2, 12, 1, 0.05F),
            },
            { // 레벨 2
                emeraldForItems("gold_ingot", 3, 12, 10, 1),
                itemsForEmeralds("lapis_lazuli", 1, 1, 12, 5, 0.05F),
            },
            { // 레벨 3
                emeraldForItems("rabbit_foot", 2, 12, 20, 1),
                itemsForEmeralds("glowstone", 4, 1, 12, 10, 0.05F),
            },
            { // 레벨 4
                emeraldForItems("turtle_scute", 4, 12, 30, 1),
                emeraldForItems("glass_bottle", 9, 12, 30, 1),
                itemsForEmeralds("ender_pearl", 5, 1, 12, 15, 0.05F),
            },
            { // 레벨 5
                emeraldForItems("nether_wart", 22, 12, 30, 1),
                itemsForEmeralds("experience_bottle", 3, 1, 12, 30, 0.05F),
            },
        });
        TRADES.put("armorer", new ItemListing[][] {
            { // 레벨 1
                emeraldForItems("coal", 15, 16, 2, 1),
                itemsForEmeralds("iron_leggings", 7, 1, 12, 1, 0.2F),
                itemsForEmeralds("iron_boots", 4, 1, 12, 1, 0.2F),
                itemsForEmeralds("iron_helmet", 5, 1, 12, 1, 0.2F),
                itemsForEmeralds("iron_chestplate", 9, 1, 12, 1, 0.2F),
            },
            { // 레벨 2
                emeraldForItems("iron_ingot", 4, 12, 10, 1),
                itemsForEmeralds("bell", 36, 1, 12, 5, 0.2F),
                itemsForEmeralds("chainmail_boots", 1, 1, 12, 5, 0.2F),
                itemsForEmeralds("chainmail_leggings", 3, 1, 12, 5, 0.2F),
            },
            { // 레벨 3
                emeraldForItems("lava_bucket", 1, 12, 20, 1),
                emeraldForItems("diamond", 1, 12, 20, 1),
                itemsForEmeralds("chainmail_helmet", 1, 1, 12, 10, 0.2F),
                itemsForEmeralds("chainmail_chestplate", 4, 1, 12, 10, 0.2F),
                itemsForEmeralds("shield", 5, 1, 12, 10, 0.2F),
            },
            { // 레벨 4
                enchantedItemForEmeralds("diamond_leggings", 14, 3, 15, 0.2F),
                enchantedItemForEmeralds("diamond_boots", 8, 3, 15, 0.2F),
            },
            { // 레벨 5
                enchantedItemForEmeralds("diamond_helmet", 8, 3, 30, 0.2F),
                enchantedItemForEmeralds("diamond_chestplate", 16, 3, 30, 0.2F),
            },
        });
        TRADES.put("weaponsmith", new ItemListing[][] {
            { // 레벨 1
                emeraldForItems("coal", 15, 16, 2, 1),
                itemsForEmeralds("iron_axe", 3, 1, 12, 1, 0.2F),
                enchantedItemForEmeralds("iron_sword", 2, 3, 1, 0.05F),
            },
            { // 레벨 2
                emeraldForItems("iron_ingot", 4, 12, 10, 1),
                itemsForEmeralds("bell", 36, 1, 12, 5, 0.2F),
            },
            { // 레벨 3
                emeraldForItems("flint", 24, 12, 20, 1),
            },
            { // 레벨 4
                emeraldForItems("diamond", 1, 12, 30, 1),
                enchantedItemForEmeralds("diamond_axe", 12, 3, 15, 0.2F),
            },
            { // 레벨 5
                enchantedItemForEmeralds("diamond_sword", 8, 3, 30, 0.2F),
            },
        });
        TRADES.put("toolsmith", new ItemListing[][] {
            { // 레벨 1
                emeraldForItems("coal", 15, 16, 2, 1),
                itemsForEmeralds("stone_axe", 1, 1, 12, 1, 0.2F),
                itemsForEmeralds("stone_shovel", 1, 1, 12, 1, 0.2F),
                itemsForEmeralds("stone_pickaxe", 1, 1, 12, 1, 0.2F),
                itemsForEmeralds("stone_hoe", 1, 1, 12, 1, 0.2F),
            },
            { // 레벨 2
                emeraldForItems("iron_ingot", 4, 12, 10, 1),
                itemsForEmeralds("bell", 36, 1, 12, 5, 0.2F),
            },
            { // 레벨 3
                emeraldForItems("flint", 30, 12, 20, 1),
                enchantedItemForEmeralds("iron_axe", 1, 3, 10, 0.2F),
                enchantedItemForEmeralds("iron_shovel", 2, 3, 10, 0.2F),
                enchantedItemForEmeralds("iron_pickaxe", 3, 3, 10, 0.2F),
                itemsForEmeralds("diamond_hoe", 4, 1, 3, 10, 0.2F),
            },
            { // 레벨 4
                emeraldForItems("diamond", 1, 12, 30, 1),
                enchantedItemForEmeralds("diamond_axe", 12, 3, 15, 0.2F),
                enchantedItemForEmeralds("diamond_shovel", 5, 3, 15, 0.2F),
            },
            { // 레벨 5
                enchantedItemForEmeralds("diamond_pickaxe", 13, 3, 30, 0.2F),
            },
        });
        TRADES.put("butcher", new ItemListing[][] {
            { // 레벨 1
                emeraldForItems("chicken", 14, 16, 2, 1),
                emeraldForItems("porkchop", 7, 16, 2, 1),
                emeraldForItems("rabbit", 4, 16, 2, 1),
                itemsForEmeralds("rabbit_stew", 1, 1, 12, 1, 0.05F),
            },
            { // 레벨 2
                emeraldForItems("coal", 15, 16, 2, 1),
                itemsForEmeralds("cooked_porkchop", 1, 5, 16, 5, 0.05F),
                itemsForEmeralds("cooked_chicken", 1, 8, 16, 5, 0.05F),
            },
            { // 레벨 3
                emeraldForItems("mutton", 7, 16, 20, 1),
                emeraldForItems("beef", 10, 16, 20, 1),
            },
            { // 레벨 4
                emeraldForItems("dried_kelp_block", 10, 12, 30, 1),
            },
            { // 레벨 5
                emeraldForItems("sweet_berries", 10, 12, 30, 1),
            },
        });
        TRADES.put("leatherworker", new ItemListing[][] {
            { // 레벨 1
                emeraldForItems("leather", 6, 16, 2, 1),
                dyedArmorForEmeralds("leather_leggings", 3, 12, 1),
                dyedArmorForEmeralds("leather_chestplate", 7, 12, 1),
            },
            { // 레벨 2
                emeraldForItems("flint", 26, 12, 10, 1),
                dyedArmorForEmeralds("leather_helmet", 5, 12, 5),
                dyedArmorForEmeralds("leather_boots", 4, 12, 5),
            },
            { // 레벨 3
                emeraldForItems("rabbit_hide", 9, 12, 20, 1),
                dyedArmorForEmeralds("leather_chestplate", 7, 12, 1),
            },
            { // 레벨 4
                emeraldForItems("turtle_scute", 4, 12, 30, 1),
                dyedArmorForEmeralds("leather_horse_armor", 6, 12, 15),
            },
            { // 레벨 5
                itemsForEmeralds("saddle", 6, 1, 12, 30, 0.2F),
                dyedArmorForEmeralds("leather_helmet", 5, 12, 30),
            },
        });
        TRADES.put("mason", new ItemListing[][] {
            { // 레벨 1
                emeraldForItems("clay_ball", 10, 16, 2, 1),
                itemsForEmeralds("brick", 1, 10, 16, 1, 0.05F),
            },
            { // 레벨 2
                emeraldForItems("stone", 20, 16, 10, 1),
                itemsForEmeralds("chiseled_stone_bricks", 1, 4, 16, 5, 0.05F),
            },
            { // 레벨 3
                emeraldForItems("granite", 16, 16, 20, 1),
                emeraldForItems("andesite", 16, 16, 20, 1),
                emeraldForItems("diorite", 16, 16, 20, 1),
                itemsForEmeralds("dripstone_block", 1, 4, 16, 10, 0.05F),
                itemsForEmeralds("polished_andesite", 1, 4, 16, 10, 0.05F),
                itemsForEmeralds("polished_diorite", 1, 4, 16, 10, 0.05F),
                itemsForEmeralds("polished_granite", 1, 4, 16, 10, 0.05F),
            },
            { // 레벨 4
                emeraldForItems("quartz", 12, 12, 30, 1),
                itemsForEmeralds("orange_terracotta", 1, 1, 12, 15, 0.05F),
                itemsForEmeralds("white_terracotta", 1, 1, 12, 15, 0.05F),
                itemsForEmeralds("blue_terracotta", 1, 1, 12, 15, 0.05F),
                itemsForEmeralds("light_blue_terracotta", 1, 1, 12, 15, 0.05F),
                itemsForEmeralds("gray_terracotta", 1, 1, 12, 15, 0.05F),
                itemsForEmeralds("light_gray_terracotta", 1, 1, 12, 15, 0.05F),
                itemsForEmeralds("black_terracotta", 1, 1, 12, 15, 0.05F),
                itemsForEmeralds("red_terracotta", 1, 1, 12, 15, 0.05F),
                itemsForEmeralds("pink_terracotta", 1, 1, 12, 15, 0.05F),
                itemsForEmeralds("magenta_terracotta", 1, 1, 12, 15, 0.05F),
                itemsForEmeralds("lime_terracotta", 1, 1, 12, 15, 0.05F),
                itemsForEmeralds("green_terracotta", 1, 1, 12, 15, 0.05F),
                itemsForEmeralds("cyan_terracotta", 1, 1, 12, 15, 0.05F),
                itemsForEmeralds("purple_terracotta", 1, 1, 12, 15, 0.05F),
                itemsForEmeralds("yellow_terracotta", 1, 1, 12, 15, 0.05F),
                itemsForEmeralds("brown_terracotta", 1, 1, 12, 15, 0.05F),
                itemsForEmeralds("orange_glazed_terracotta", 1, 1, 12, 15, 0.05F),
                itemsForEmeralds("white_glazed_terracotta", 1, 1, 12, 15, 0.05F),
                itemsForEmeralds("blue_glazed_terracotta", 1, 1, 12, 15, 0.05F),
                itemsForEmeralds("light_blue_glazed_terracotta", 1, 1, 12, 15, 0.05F),
                itemsForEmeralds("gray_glazed_terracotta", 1, 1, 12, 15, 0.05F),
                itemsForEmeralds("light_gray_glazed_terracotta", 1, 1, 12, 15, 0.05F),
                itemsForEmeralds("black_glazed_terracotta", 1, 1, 12, 15, 0.05F),
                itemsForEmeralds("red_glazed_terracotta", 1, 1, 12, 15, 0.05F),
                itemsForEmeralds("pink_glazed_terracotta", 1, 1, 12, 15, 0.05F),
                itemsForEmeralds("magenta_glazed_terracotta", 1, 1, 12, 15, 0.05F),
                itemsForEmeralds("lime_glazed_terracotta", 1, 1, 12, 15, 0.05F),
                itemsForEmeralds("green_glazed_terracotta", 1, 1, 12, 15, 0.05F),
                itemsForEmeralds("cyan_glazed_terracotta", 1, 1, 12, 15, 0.05F),
                itemsForEmeralds("purple_glazed_terracotta", 1, 1, 12, 15, 0.05F),
                itemsForEmeralds("yellow_glazed_terracotta", 1, 1, 12, 15, 0.05F),
                itemsForEmeralds("brown_glazed_terracotta", 1, 1, 12, 15, 0.05F),
            },
            { // 레벨 5
                itemsForEmeralds("quartz_pillar", 1, 1, 12, 30, 0.05F),
                itemsForEmeralds("quartz_block", 1, 1, 12, 30, 0.05F),
            },
        });
    }

    /** 방랑 상인 거래표(WANDERING_TRADER_TRADES): index 0 = 첫 묶음(addOffersFromItemListings 로 5개), index 1 = 둘째 묶음(nextInt(길이) 로 1개). 방랑 상인 엔티티는 아직 없다. */
    public static final ItemListing[][] WANDERING_TRADER_TRADES = {
            { // 거래 묶음 1
                itemsForEmeralds("sea_pickle", 2, 1, 5, 1, 0.05F),
                itemsForEmeralds("slime_ball", 4, 1, 5, 1, 0.05F),
                itemsForEmeralds("glowstone", 2, 1, 5, 1, 0.05F),
                itemsForEmeralds("nautilus_shell", 5, 1, 5, 1, 0.05F),
                itemsForEmeralds("fern", 1, 1, 12, 1, 0.05F),
                itemsForEmeralds("sugar_cane", 1, 1, 8, 1, 0.05F),
                itemsForEmeralds("pumpkin", 1, 1, 4, 1, 0.05F),
                itemsForEmeralds("kelp", 3, 1, 12, 1, 0.05F),
                itemsForEmeralds("cactus", 3, 1, 8, 1, 0.05F),
                itemsForEmeralds("dandelion", 1, 1, 12, 1, 0.05F),
                itemsForEmeralds("poppy", 1, 1, 12, 1, 0.05F),
                itemsForEmeralds("blue_orchid", 1, 1, 8, 1, 0.05F),
                itemsForEmeralds("allium", 1, 1, 12, 1, 0.05F),
                itemsForEmeralds("azure_bluet", 1, 1, 12, 1, 0.05F),
                itemsForEmeralds("red_tulip", 1, 1, 12, 1, 0.05F),
                itemsForEmeralds("orange_tulip", 1, 1, 12, 1, 0.05F),
                itemsForEmeralds("white_tulip", 1, 1, 12, 1, 0.05F),
                itemsForEmeralds("pink_tulip", 1, 1, 12, 1, 0.05F),
                itemsForEmeralds("oxeye_daisy", 1, 1, 12, 1, 0.05F),
                itemsForEmeralds("cornflower", 1, 1, 12, 1, 0.05F),
                itemsForEmeralds("lily_of_the_valley", 1, 1, 7, 1, 0.05F),
                itemsForEmeralds("wheat_seeds", 1, 1, 12, 1, 0.05F),
                itemsForEmeralds("beetroot_seeds", 1, 1, 12, 1, 0.05F),
                itemsForEmeralds("pumpkin_seeds", 1, 1, 12, 1, 0.05F),
                itemsForEmeralds("melon_seeds", 1, 1, 12, 1, 0.05F),
                itemsForEmeralds("acacia_sapling", 5, 1, 8, 1, 0.05F),
                itemsForEmeralds("birch_sapling", 5, 1, 8, 1, 0.05F),
                itemsForEmeralds("dark_oak_sapling", 5, 1, 8, 1, 0.05F),
                itemsForEmeralds("jungle_sapling", 5, 1, 8, 1, 0.05F),
                itemsForEmeralds("oak_sapling", 5, 1, 8, 1, 0.05F),
                itemsForEmeralds("spruce_sapling", 5, 1, 8, 1, 0.05F),
                itemsForEmeralds("cherry_sapling", 5, 1, 8, 1, 0.05F),
                itemsForEmeralds("mangrove_propagule", 5, 1, 8, 1, 0.05F),
                itemsForEmeralds("red_dye", 1, 3, 12, 1, 0.05F),
                itemsForEmeralds("white_dye", 1, 3, 12, 1, 0.05F),
                itemsForEmeralds("blue_dye", 1, 3, 12, 1, 0.05F),
                itemsForEmeralds("pink_dye", 1, 3, 12, 1, 0.05F),
                itemsForEmeralds("black_dye", 1, 3, 12, 1, 0.05F),
                itemsForEmeralds("green_dye", 1, 3, 12, 1, 0.05F),
                itemsForEmeralds("light_gray_dye", 1, 3, 12, 1, 0.05F),
                itemsForEmeralds("magenta_dye", 1, 3, 12, 1, 0.05F),
                itemsForEmeralds("yellow_dye", 1, 3, 12, 1, 0.05F),
                itemsForEmeralds("gray_dye", 1, 3, 12, 1, 0.05F),
                itemsForEmeralds("purple_dye", 1, 3, 12, 1, 0.05F),
                itemsForEmeralds("light_blue_dye", 1, 3, 12, 1, 0.05F),
                itemsForEmeralds("lime_dye", 1, 3, 12, 1, 0.05F),
                itemsForEmeralds("orange_dye", 1, 3, 12, 1, 0.05F),
                itemsForEmeralds("brown_dye", 1, 3, 12, 1, 0.05F),
                itemsForEmeralds("cyan_dye", 1, 3, 12, 1, 0.05F),
                itemsForEmeralds("brain_coral_block", 3, 1, 8, 1, 0.05F),
                itemsForEmeralds("bubble_coral_block", 3, 1, 8, 1, 0.05F),
                itemsForEmeralds("fire_coral_block", 3, 1, 8, 1, 0.05F),
                itemsForEmeralds("horn_coral_block", 3, 1, 8, 1, 0.05F),
                itemsForEmeralds("tube_coral_block", 3, 1, 8, 1, 0.05F),
                itemsForEmeralds("vine", 1, 1, 12, 1, 0.05F),
                itemsForEmeralds("brown_mushroom", 1, 1, 12, 1, 0.05F),
                itemsForEmeralds("red_mushroom", 1, 1, 12, 1, 0.05F),
                itemsForEmeralds("lily_pad", 1, 2, 5, 1, 0.05F),
                itemsForEmeralds("small_dripleaf", 1, 2, 5, 1, 0.05F),
                itemsForEmeralds("sand", 1, 8, 8, 1, 0.05F),
                itemsForEmeralds("red_sand", 1, 4, 6, 1, 0.05F),
                itemsForEmeralds("pointed_dripstone", 1, 2, 5, 1, 0.05F),
                itemsForEmeralds("rooted_dirt", 1, 2, 5, 1, 0.05F),
                itemsForEmeralds("moss_block", 1, 2, 5, 1, 0.05F),
            },
            { // 거래 묶음 2
                itemsForEmeralds("tropical_fish_bucket", 5, 1, 4, 1, 0.05F),
                itemsForEmeralds("pufferfish_bucket", 5, 1, 4, 1, 0.05F),
                itemsForEmeralds("packed_ice", 3, 1, 6, 1, 0.05F),
                itemsForEmeralds("blue_ice", 6, 1, 6, 1, 0.05F),
                itemsForEmeralds("gunpowder", 1, 1, 8, 1, 0.05F),
                itemsForEmeralds("podzol", 3, 3, 6, 1, 0.05F),
            },
    };

    // ------------------------------------------------------------------ 후보 구현

    private static ItemStack stack(String item, int count) {
        Integer id = ItemContext.id("minecraft:item", item);
        return id == null ? ItemStack.EMPTY : new ItemStack(id, count);
    }

    private static String mc(String item) { return "minecraft:" + item; }

    /** EmeraldForItems: 아이템 count 개 → 에메랄드 emeralds 개(배율 0.05). */
    static ItemListing emeraldForItems(String item, int count, int maxUses, int xp, int emeralds) {
        return (ctx, r) -> new MerchantOffer(new ItemCost(mc(item), count), stack(EMERALD, emeralds), maxUses, xp, 0.05F);
    }

    /** ItemsForEmeralds: 에메랄드 → 아이템 count 개. */
    static ItemListing itemsForEmeralds(String item, int emeralds, int count, int maxUses, int xp, float mult) {
        return (ctx, r) -> new MerchantOffer(new ItemCost(EMERALD, emeralds), stack(mc(item), count), maxUses, xp, mult);
    }

    /** ItemsAndEmeraldsToItems: 에메랄드 + 아이템(B) → 아이템. */
    static ItemListing itemsAndEmeraldsToItems(String buy, int buyCount, int emeralds, String result, int resultCount, int maxUses, int xp, float mult) {
        return (ctx, r) -> new MerchantOffer(new ItemCost(EMERALD, emeralds), Optional.of(new ItemCost(mc(buy), buyCount)), stack(mc(result), resultCount),
                0, maxUses, true, 0, 0, mult, xp);
    }

    /** DyedArmorForEmeralds: 가죽 방어구 — 염색 가능하면 무작위 염료 1~3 개로 물들인다(nextInt(16), nextFloat 순서). */
    static ItemListing dyedArmorForEmeralds(String item, int emeralds, int maxUses, int xp) {
        return (ctx, r) -> {
            ItemStack s = stack(mc(item), 1);
            Integer id = ItemContext.id("minecraft:item", mc(item));
            if (id != null && ItemContext.isInTag("minecraft:item", "minecraft:dyeable", id)) {
                List<Integer> dyes = new java.util.ArrayList<>();
                dyes.add(r.nextInt(16));
                if (r.nextFloat() > 0.7F) dyes.add(r.nextInt(16));
                if (r.nextFloat() > 0.8F) dyes.add(r.nextInt(16));
                s = SpecialRecipes.applyDyes(s, dyes);
            }
            return new MerchantOffer(new ItemCost(EMERALD, emeralds), s, maxUses, xp, 0.2F);
        };
    }

    /** EmeraldForVillagerTypeItem: 주민 종류별 아이템(배) → 에메랄드. 주민이 아니면 null. */
    static ItemListing emeraldForVillagerTypeItem(int count, int maxUses, int xp, Map<String, String> byType) {
        return (ctx, r) -> {
            if (ctx.villagerType() == null) return null;
            return new MerchantOffer(new ItemCost(mc(byType.get(ctx.villagerType())), count), stack(EMERALD, 1), maxUses, xp, 0.05F);
        };
    }

    /** SuspiciousStewForEmerald: 에메랄드 1 → 효과 하나가 든 수상한 스튜(지속 틱). */
    static ItemListing suspiciousStewForEmerald(String effect, int duration, int xp) {
        return (ctx, r) -> {
            Map<String, Object> entry = new java.util.LinkedHashMap<>();
            entry.put("id", mc(effect));
            entry.put("duration", duration);
            ItemStack stew = org.domi.furnace.server.item.ItemTools.with(stack("minecraft:suspicious_stew", 1), "minecraft:suspicious_stew_effects", List.of(entry));
            return new MerchantOffer(new ItemCost(EMERALD, 1), stew, 12, xp, 0.05F);
        };
    }

    /** TippedArrowForItemsAndEmeralds: 에메랄드 + 화살 → 무작위 물약 화살(효과 있는 물약, 레지스트리 순서에서 nextInt). */
    static ItemListing tippedArrowForItemsAndEmeralds(String buy, int buyCount, String result, int resultCount, int emeralds, int maxUses, int xp) {
        return (ctx, r) -> {
            List<String> potions = Potions.names().stream().filter(p -> !Potions.effectsOf(p).isEmpty()).toList();
            String potion = potions.get(r.nextInt(potions.size()));
            Map<String, Object> contents = new java.util.LinkedHashMap<>();
            contents.put("potion", potion);
            ItemStack out = org.domi.furnace.server.item.ItemTools.with(stack(mc(result), resultCount), "minecraft:potion_contents", contents);
            return new MerchantOffer(new ItemCost(EMERALD, emeralds), Optional.of(new ItemCost(mc(buy), buyCount)), out, 0, maxUses, true, 0, 0, 0.05F, xp);
        };
    }

    /** TreasureMapForEmeralds: 지도 만들기가 없어 거래를 만들지 못한다(바닐라도 탐색 실패면 null). TODO(지도) */
    static ItemListing treasureMap(String name) { return (ctx, r) -> null; }

    /** EnchantedItemForEmeralds: 에메랄드 → 마법 부여된 장비. 수준 5+nextInt(15), 비용 base+수준(최대 64). */
    static ItemListing enchantedItemForEmeralds(String item, int baseCost, int maxUses, int xp, float mult) {
        return (ctx, r) -> {
            int level = 5 + r.nextInt(15);
            ItemStack s = stack(mc(item), 1);
            if (ctx.loot() != null) {
                LootStack picked = Enchantments.enchantItemFromTag(new LegacyNoiseRandom(r), new LootStack(mc(item), 1), level,
                        "minecraft:on_traded_equipment", ctx.loot().registries());
                s = withEnchantments(s, picked);
            }
            return new MerchantOffer(new ItemCost(EMERALD, Math.min(baseCost + level, 64)), s, maxUses, xp, mult);
        };
    }

    /** 고른 인챈트(LootStack 의 enchantments)를 스택의 컴포넌트로 옮긴다. 고른 것이 없으면 그대로. */
    private static ItemStack withEnchantments(ItemStack s, LootStack picked) {
        if (picked.get(Enchantments.ENCHANTMENTS) instanceof Map<?, ?> m && m.get("levels") instanceof Map<?, ?> l && !l.isEmpty()) {
            return EnchantTools.update(s, levels -> {
                for (var e : l.entrySet()) levels.put(String.valueOf(e.getKey()), ((Number) e.getValue()).intValue());
            });
        }
        return s;
    }

    /**
     * EnchantBookForEmeralds(tradeable 태그): 거래 가능한 인챈트 하나(nextInt(태그 크기)), 수준 nextIntBetweenInclusive(1, 최대),
     * 비용 2 + nextInt(5 + 수준*10) + 3*수준(두 배 태그면 ×2, 최대 64). 태그가 비면 에메랄드 1 + 책 → 책.
     */
    static ItemListing enchantBookForEmeralds(int xp) {
        return (ctx, r) -> {
            LootRegistries regs = ctx.loot() == null ? null : ctx.loot().registries();
            List<String> tradeable = regs == null ? List.of() : regs.tag("minecraft:enchantment", "minecraft:tradeable");
            int cost;
            ItemStack book;
            if (!tradeable.isEmpty()) {
                String id = tradeable.get(r.nextInt(tradeable.size()));
                Enchantment e = regs.enchantment(id);
                int level = r.nextIntBetweenInclusive(1, e.maxLevel());
                book = EnchantTools.update(stack("minecraft:enchanted_book", 1), levels -> levels.put(id, level));
                cost = 2 + r.nextInt(5 + level * 10) + 3 * level;
                if (regs.tag("minecraft:enchantment", "minecraft:double_trade_price").contains(id)) cost *= 2;
                if (cost > 64) cost = 64;
            } else {
                cost = 1;
                book = stack(BOOK, 1);
            }
            return new MerchantOffer(new ItemCost(EMERALD, cost), Optional.of(new ItemCost(BOOK, 1)), book, 0, 12, true, 0, 0, 0.2F, xp);
        };
    }
}
