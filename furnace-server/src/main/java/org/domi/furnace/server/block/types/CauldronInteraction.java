// 바닐라 CauldronInteraction: 가마솥 종류(empty/water/lava/powder_snow)마다 손 아이템 → 동작 표. 없는 아이템은 PASS_TO_DEFAULT.
package org.domi.furnace.server.block.types;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntPredicate;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.ItemInteractionResult;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ComponentPatch;
import org.domi.furnace.server.item.ItemContext;

@FunctionalInterface
public interface CauldronInteraction {

    ItemInteractionResult interact(int state, BlockLevel level, Pos pos, BlockActor player, int hand, ItemStack stack);

    /** 아이템 번호 → 동작. */
    final class Table {
        final String name;
        final Map<Integer, CauldronInteraction> map = new HashMap<>();

        Table(String name) { this.name = name; }

        CauldronInteraction get(ItemStack stack) {
            CauldronInteraction i = stack.isEmpty() ? null : map.get(stack.itemId());
            return i != null ? i : (s, l, p, pl, h, st) -> ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        void put(String item, CauldronInteraction i) { map.put(item(item), i); }
    }

    Table EMPTY = Builder.build("empty"), WATER = Builder.build("water"), LAVA = Builder.build("lava"), POWDER_SNOW = Builder.build("powder_snow");

    static Table byName(String name) {
        return switch (name) {
            case "water" -> WATER;
            case "lava" -> LAVA;
            case "powder_snow" -> POWDER_SNOW;
            default -> EMPTY;
        };
    }

    private static int item(String name) { return ItemContext.id("minecraft:item", name); }

    final class Builder {
        private Builder() {}

        private static final int CAULDRON = B.id("cauldron");
        private static final Prop_ LEVEL = new Prop_();

        /** LayeredCauldronBlock.LEVEL 을 늦게 잡는다(정적 초기화 순서). */
        private static final class Prop_ {
            int get(int s) { return LayeredCauldronBlock.LEVEL.intValue(s); }
            int with(int s, int v) { return LayeredCauldronBlock.LEVEL.withInt(s, v); }
            int cycle(int s) { return LayeredCauldronBlock.LEVEL.cycle(s); }
        }

        static Table build(String name) {
            Table t = new Table(name);
            switch (name) {
                case "empty" -> {
                    addDefaultInteractions(t);
                    t.put("potion", (state, level, pos, player, hand, stack) -> {
                        if (!isWaterPotion(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                        player.setItemInHand(hand, player.createFilledResult(stack, B.stack("glass_bottle", 1)));
                        player.awardStat("minecraft:use_cauldron");
                        player.awardStat("minecraft:used");
                        level.setBlockAndUpdate(pos, B.defaultState("water_cauldron"));
                        level.playSound(null, pos, "minecraft:item.bottle.empty", "blocks", 1.0F, 1.0F);
                        level.gameEvent("minecraft:fluid_place", pos, null, -1);
                        return ItemInteractionResult.sidedSuccess(false);
                    });
                }
                case "water" -> {
                    addDefaultInteractions(t);
                    t.put("bucket", (state, level, pos, player, hand, stack) ->
                            fillBucket(state, level, pos, player, hand, stack, B.stack("water_bucket", 1), s -> LEVEL.get(s) == 3, "minecraft:item.bucket.fill"));
                    t.put("glass_bottle", (state, level, pos, player, hand, stack) -> {
                        player.setItemInHand(hand, player.createFilledResult(stack, waterPotion()));
                        player.awardStat("minecraft:use_cauldron");
                        player.awardStat("minecraft:used");
                        LayeredCauldronBlock.lowerFillLevel(state, level, pos);
                        level.playSound(null, pos, "minecraft:item.bottle.fill", "blocks", 1.0F, 1.0F);
                        level.gameEvent("minecraft:fluid_pickup", pos, null, -1);
                        return ItemInteractionResult.sidedSuccess(false);
                    });
                    t.put("potion", (state, level, pos, player, hand, stack) -> {
                        if (LEVEL.get(state) == 3 || !isWaterPotion(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                        player.setItemInHand(hand, player.createFilledResult(stack, B.stack("glass_bottle", 1)));
                        player.awardStat("minecraft:use_cauldron");
                        player.awardStat("minecraft:used");
                        level.setBlockAndUpdate(pos, LEVEL.cycle(state));
                        level.playSound(null, pos, "minecraft:item.bottle.empty", "blocks", 1.0F, 1.0F);
                        level.gameEvent("minecraft:fluid_place", pos, null, -1);
                        return ItemInteractionResult.sidedSuccess(false);
                    });
                    // TODO(컴포넌트): 물든 가죽 갑옷·말 갑옷·늑대 갑옷 씻기(DYED_COLOR 지움, clean_armor), 깃발 무늬 한 겹 지우기(clean_banner),
                    // 색 셜커 상자 → 흰 셜커 상자(clean_shulker_box). 셋 다 성공하면 lowerFillLevel. 지금은 PASS_TO_DEFAULT.
                }
                case "lava" -> {
                    t.put("bucket", (state, level, pos, player, hand, stack) ->
                            fillBucket(state, level, pos, player, hand, stack, B.stack("lava_bucket", 1), s -> true, "minecraft:item.bucket.fill_lava"));
                    addDefaultInteractions(t);
                }
                case "powder_snow" -> {
                    t.put("bucket", (state, level, pos, player, hand, stack) ->
                            fillBucket(state, level, pos, player, hand, stack, B.stack("powder_snow_bucket", 1), s -> LEVEL.get(s) == 3, "minecraft:item.bucket.fill_powder_snow"));
                    addDefaultInteractions(t);
                }
                default -> throw new IllegalArgumentException(name);
            }
            return t;
        }

        /** addDefaultInteractions: 용암·물·가루눈 양동이를 비워 그 가마솥(가득)으로. */
        private static void addDefaultInteractions(Table t) {
            t.put("lava_bucket", (state, level, pos, player, hand, stack) ->
                    emptyBucket(level, pos, player, hand, stack, B.defaultState("lava_cauldron"), "minecraft:item.bucket.empty_lava"));
            t.put("water_bucket", (state, level, pos, player, hand, stack) ->
                    emptyBucket(level, pos, player, hand, stack, LayeredCauldronBlock.waterCauldronFull(), "minecraft:item.bucket.empty"));
            t.put("powder_snow_bucket", (state, level, pos, player, hand, stack) ->
                    emptyBucket(level, pos, player, hand, stack, LEVEL.with(B.defaultState("powder_snow_cauldron"), 3), "minecraft:item.bucket.empty_powder_snow"));
        }

        /** fillBucket: 조건이 맞으면 양동이를 채우고 빈 가마솥으로. */
        static ItemInteractionResult fillBucket(int state, BlockLevel level, Pos pos, BlockActor player, int hand, ItemStack empty, ItemStack filled,
                                                IntPredicate full, String sound) {
            if (!full.test(state)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            player.setItemInHand(hand, player.createFilledResult(empty, filled));
            player.awardStat("minecraft:use_cauldron");
            player.awardStat("minecraft:used");
            level.setBlockAndUpdate(pos, B.defaultState(CAULDRON));
            level.playSound(null, pos, sound, "blocks", 1.0F, 1.0F);
            level.gameEvent("minecraft:fluid_pickup", pos, null, -1);
            return ItemInteractionResult.sidedSuccess(false);
        }

        /** emptyBucket: 양동이를 비우고 그 가마솥 상태로. */
        static ItemInteractionResult emptyBucket(BlockLevel level, Pos pos, BlockActor player, int hand, ItemStack filled, int newState, String sound) {
            player.setItemInHand(hand, player.createFilledResult(filled, B.stack("bucket", 1)));
            player.awardStat("minecraft:fill_cauldron");
            player.awardStat("minecraft:used");
            level.setBlockAndUpdate(pos, newState);
            level.playSound(null, pos, sound, "blocks", 1.0F, 1.0F);
            level.gameEvent("minecraft:fluid_place", pos, null, -1);
            return ItemInteractionResult.sidedSuccess(false);
        }

        /** PotionContents.is(Potions.WATER). */
        static boolean isWaterPotion(ItemStack s) {
            return s.patch().entries().get("minecraft:potion_contents") instanceof Map<?, ?> m && "minecraft:water".equals(m.get("potion"));
        }

        /** PotionContents.createItemStack(POTION, WATER). */
        static ItemStack waterPotion() {
            Map<String, Object> pc = new LinkedHashMap<>();
            pc.put("potion", "minecraft:water");
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("minecraft:potion_contents", pc);
            return new ItemStack(item("potion"), 1, ComponentPatch.of(raw));
        }
    }
}
