// 바닐라 ComposterBlock: 퇴비통. 퇴비 아이템을 넣으면 확률로 한 단계(level 0..7) 오르고, 7 이 되면 20틱 뒤 8(완성), 비우면 뼛가루.
package org.domi.furnace.server.block.types;

import java.util.HashMap;
import java.util.Map;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.ItemInteractionResult;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemContext;

public class ComposterBlock extends BlockBehavior {

    static final Prop LEVEL = B.prop("level");
    public static final int READY = 8, MAX_LEVEL = 7;

    /** COMPOSTABLES: 아이템 번호 → 성공 확률. */
    private static final Map<Integer, Float> COMPOSTABLES = new HashMap<>();
    static {
        add(0.3F, "jungle_leaves oak_leaves spruce_leaves dark_oak_leaves acacia_leaves cherry_leaves birch_leaves azalea_leaves mangrove_leaves "
                + "oak_sapling spruce_sapling birch_sapling jungle_sapling acacia_sapling cherry_sapling dark_oak_sapling mangrove_propagule "
                + "beetroot_seeds dried_kelp short_grass kelp melon_seeds pumpkin_seeds seagrass sweet_berries glow_berries wheat_seeds moss_carpet "
                + "pink_petals small_dripleaf hanging_roots mangrove_roots torchflower_seeds pitcher_pod");
        add(0.5F, "dried_kelp_block tall_grass flowering_azalea_leaves cactus sugar_cane vine nether_sprouts weeping_vines twisting_vines melon_slice glow_lichen");
        add(0.65F, "sea_pickle lily_pad pumpkin carved_pumpkin melon apple beetroot carrot cocoa_beans potato wheat brown_mushroom red_mushroom "
                + "mushroom_stem crimson_fungus warped_fungus nether_wart crimson_roots warped_roots shroomlight dandelion poppy blue_orchid allium "
                + "azure_bluet red_tulip orange_tulip white_tulip pink_tulip oxeye_daisy cornflower lily_of_the_valley wither_rose fern sunflower "
                + "lilac rose_bush peony large_fern spore_blossom azalea moss_block big_dripleaf");
        add(0.85F, "hay_block brown_mushroom_block red_mushroom_block nether_wart_block warped_wart_block flowering_azalea bread baked_potato cookie "
                + "torchflower pitcher_plant");
        add(1.0F, "cake pumpkin_pie");
    }

    private static void add(float chance, String items) {
        for (String i : items.split(" ")) COMPOSTABLES.put(ItemContext.id("minecraft:item", i), chance);
    }

    /** 퇴비가 되는 아이템인가. */
    public static boolean isCompostable(ItemStack stack) { return !stack.isEmpty() && COMPOSTABLES.containsKey(stack.itemId()); }

    public ComposterBlock(int block) { super(block); }

    @Override
    public void onPlace(int state, BlockLevel level, Pos pos, int oldState, boolean moved) {
        if (LEVEL.intValue(state) == MAX_LEVEL) level.scheduleTick(pos, block, 20);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, int state, BlockLevel level, Pos pos, BlockActor player, int hand, Hit hit) {
        int lvl = LEVEL.intValue(state);
        if (lvl < READY && isCompostable(stack)) {
            if (lvl < MAX_LEVEL) {
                int ns = addItem(player, state, level, pos, stack);
                level.levelEvent(null, 1500, pos, state != ns ? 1 : 0);
                player.awardStat("minecraft:used");
                if (!player.instabuild()) player.setItemInHand(hand, stack.withCount(stack.count() - 1));
            }
            return ItemInteractionResult.sidedSuccess(false);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        if (LEVEL.intValue(state) == READY) {
            extractProduce(player, state, level, pos);
            return InteractionResult.sidedSuccess(false);
        }
        return InteractionResult.PASS;
    }

    /** insertItem(호퍼 등): 7 미만이고 퇴비 아이템이면 넣고 하나 줄인다(돌려준 상태가 새 상태). */
    public static int insertItem(BlockActor entity, int state, BlockLevel level, ItemStack stack, Pos pos) {
        if (LEVEL.intValue(state) < MAX_LEVEL && isCompostable(stack)) return addItem(entity, state, level, pos, stack);
        return state;
    }

    /** extractProduce: 뼛가루 아이템을 위로 내놓고 비운다. */
    public static int extractProduce(BlockActor entity, int state, BlockLevel level, Pos pos) {
        RandomSource r = level.random();
        // Vec3.atLowerCornerWithOffset(pos, 0.5, 1.01, 0.5).offsetRandom(random, 0.7F) — 난수 3번.
        r.nextFloat();
        r.nextFloat();
        r.nextFloat();
        // new ItemEntity(level, x, y, z, stack) 가 처음 속도로 난수 2번(nextDouble).
        r.nextDouble();
        r.nextDouble();
        // TODO(엔티티): 그 자리에 뼛가루 ItemEntity(setDefaultPickUpDelay) 를 만든다.
        int ns = empty(entity, state, level, pos);
        level.playSound(null, pos, "minecraft:block.composter.empty", "blocks", 1.0F, 1.0F);
        return ns;
    }

    static int empty(BlockActor entity, int state, BlockLevel level, Pos pos) {
        int ns = LEVEL.withInt(state, 0);
        level.setBlock(pos, ns, 3);
        level.gameEvent("minecraft:block_change", pos, entity, ns);
        return ns;
    }

    static int addItem(BlockActor entity, int state, BlockLevel level, Pos pos, ItemStack stack) {
        int lvl = LEVEL.intValue(state);
        float chance = COMPOSTABLES.getOrDefault(stack.itemId(), -1.0F);
        if ((lvl != 0 || !(chance > 0.0F)) && !(level.random().nextDouble() < chance)) return state;
        int nl = lvl + 1;
        int ns = LEVEL.withInt(state, nl);
        level.setBlock(pos, ns, 3);
        level.gameEvent("minecraft:block_change", pos, entity, ns);
        if (nl == MAX_LEVEL) level.scheduleTick(pos, B.block(state), 20);
        return ns;
    }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (LEVEL.intValue(state) == MAX_LEVEL) {
            level.setBlock(pos, LEVEL.cycle(state), 3);
            level.playSound(null, pos, "minecraft:block.composter.ready", "blocks", 1.0F, 1.0F);
        }
    }

    @Override
    public int getAnalogOutputSignal(int state, BlockLevel level, Pos pos) { return LEVEL.intValue(state); }

    // TODO(3-4e 호퍼): getContainer — 7 미만이면 위에서 넣는 칸(insertItem), 8 이면 아래에서 뼛가루를 빼는 칸(extractProduce 와 같은 비우기, 아이템 엔티티 없이).
}
