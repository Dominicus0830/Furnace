// 블록에 아이템 쓰기·설치(바닐라 ServerPlayerGameMode.useItemOn → ItemStack.useOn → BlockItem.useOn/place 와 그 변형들).
// 아이템 스택은 불변이라 "손의 스택을 바꾼다"는 행위자의 setItemInHand 로 돌려준다.
package org.domi.furnace.server.block;

import java.util.Map;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemPrototypes;
import org.domi.furnace.server.item.Val;

public final class BlockPlacement {
    private BlockPlacement() {}

    private static final Blocks BLOCKS = Blocks.get();
    private static final BlockInfo INFO = BlockInfo.get();

    // ------------------------------------------------------------------ ServerPlayerGameMode.useItemOn

    /** useItemOn(player, level, stack, hand, hit). 관전자는 메뉴가 있는 블록만(상자 엿보기) — 블록 엔티티 체계 전에는 PASS. */
    public static InteractionResult useItemOn(BlockLevel level, BlockActor player, int hand, Hit hit) {
        ItemStack stack = player.itemInHand(hand);
        Pos pos = hit.pos();
        int state = level.getBlockState(pos);
        if (player.isSpectator()) return InteractionResult.PASS;
        boolean haveItem = !player.itemInHand(BlockActor.MAIN_HAND).isEmpty() || !player.itemInHand(BlockActor.OFF_HAND).isEmpty();
        boolean suppressBlockUse = player.isSecondaryUseActive() && haveItem;
        ItemStack copy = stack;
        if (!suppressBlockUse) {
            BlockBehavior b = BlockBehaviors.of(state);
            ItemInteractionResult ir = b.useItemOn(player.itemInHand(hand), state, level, pos, player, hand, hit);
            if (ir.consumesAction()) {
                player.trigger("minecraft:item_used_on_block", pos, copy);
                return ir.result();
            }
            if (ir == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION && hand == BlockActor.MAIN_HAND) {
                InteractionResult r = b.useWithoutItem(state, level, pos, player, hit);
                if (r.consumesAction()) {
                    player.trigger("minecraft:default_block_use", pos, ItemStack.EMPTY);
                    return r;
                }
            }
        }
        if (!stack.isEmpty() && !player.isOnCooldown(stack.itemId())) {
            InteractionResult r = useOn(level, player, hand, hit);
            if (player.isCreative()) {
                // 바닐라는 원래 스택 객체의 개수를 되돌린다 — 손이 같은 스택(개수만 바뀐 것)을 들고 있을 때만 해당
                ItemStack cur = player.itemInHand(hand);
                if (cur != stack && cur.count() != stack.count() && cur.itemId() == stack.itemId() && cur.patch().equals(stack.patch())) {
                    player.setItemInHand(hand, stack);
                }
            }
            if (r.consumesAction()) player.trigger("minecraft:item_used_on_block", pos, copy);
            return r;
        }
        return InteractionResult.PASS;
    }

    /** ItemStack.useOn: 모험 모드는 can_place_on 이 클릭한 블록과 맞아야 한다. 쓰였으면(SUCCESS·CONSUME) 통계 used. */
    public static InteractionResult useOn(BlockLevel level, BlockActor player, int hand, Hit hit) {
        ItemStack stack = player.itemInHand(hand);
        if (player != null && !player.mayBuild() && !canPlaceOnInAdventure(stack, level, hit.pos())) return InteractionResult.PASS;
        InteractionResult r = itemUseOn(level, player, hand, hit, stack);
        if (r.shouldAwardStats()) player.awardStat("minecraft:used:" + org.domi.furnace.server.item.ItemTools.itemName(stack));
        return r;
    }

    /** Item.useOn — 블록 아이템은 설치, 아니면 아이템 종류별(ItemUse). */
    private static InteractionResult itemUseOn(BlockLevel level, BlockActor player, int hand, Hit hit, ItemStack stack) {
        BlockItems.Entry e = BlockItems.entry(stack.itemId());
        if (e == null) return ItemUse.useOn(level, player, hand, hit);
        return switch (e.kind()) {
            case PLACE_ON_WATER -> InteractionResult.PASS;   // 블록 면 클릭으로는 놓지 않는다(use 경로)
            case SOLID_BUCKET -> {
                InteractionResult r = blockItemUseOn(e, new PlaceContext(level, player, hand, stack, hit));
                if (r.consumesAction()) {
                    // BucketItem.getEmptySuccessItem: 무한 재료가 아니면 빈 양동이
                    if (!player.instabuild()) player.setItemInHand(hand, new ItemStack(itemId("minecraft:bucket"), 1));
                }
                yield r;
            }
            default -> blockItemUseOn(e, new PlaceContext(level, player, hand, stack, hit));
        };
    }

    /** BlockItem.useOn: 놓지 못했고 음식이면 먹기(아이템 쪽) — 먹기는 아이템 사용 체계에서. */
    private static InteractionResult blockItemUseOn(BlockItems.Entry e, PlaceContext ctx) {
        return place(e, ctx);
    }

    // ------------------------------------------------------------------ BlockItem.place

    public static InteractionResult place(BlockItems.Entry e, PlaceContext ctx0) {
        if (!ctx0.canPlace()) return InteractionResult.FAIL;
        PlaceContext ctx = updatePlacementContext(e, ctx0);
        if (ctx == null) return InteractionResult.FAIL;
        int s = getPlacementState(e, ctx);
        if (s < 0) return InteractionResult.FAIL;
        if (ctx.player() != null && !ctx.player().allowPlace(ctx.clickedPos(), s, ctx.hit().pos())) return InteractionResult.FAIL;
        if (!placeBlock(e, ctx, s)) return InteractionResult.FAIL;
        Pos pos = ctx.clickedPos();
        BlockLevel level = ctx.level();
        BlockActor player = ctx.player();
        ItemStack stack = ctx.itemInHand();
        int placed = level.getBlockState(pos);
        if (BLOCKS.blockOf(placed) == BLOCKS.blockOf(s)) {
            placed = updateBlockStateFromTag(pos, level, stack, placed);
            // updateCustomBlockEntityTag: TODO(블록 엔티티) block_entity_data 적용은 아직 없다
            if (e.kind() == BlockItems.Kind.SIGN || e.kind() == BlockItems.Kind.HANGING_SIGN)
                org.domi.furnace.server.block.types.SignBlock.afterPlaced(level, pos, player, stack);   // SignItem.updateCustomBlockEntityTag
            updateBlockEntityComponents(level, pos, stack);
            BlockBehaviors.of(placed).setPlacedBy(level, pos, placed, player, stack);
            if (player != null) player.trigger("minecraft:placed_block", pos, stack);
        }
        int pb = BLOCKS.blockOf(placed);
        String sound = e.kind() == BlockItems.Kind.SOLID_BUCKET ? "minecraft:item.bucket.empty_powder_snow" : INFO.placeSound(pb);
        level.playSound(player, pos, sound, "blocks", (INFO.soundVolume(pb) + 1.0F) / 2.0F, INFO.soundPitch(pb) * 0.8F);
        level.gameEvent("minecraft:block_place", pos, player, placed);
        if (player != null && !player.instabuild()) {
            ItemStack cur = player.itemInHand(ctx.hand());
            player.setItemInHand(ctx.hand(), cur.withCount(cur.count() - 1));
        }
        return InteractionResult.CONSUME;
    }

    /** BlockItem.updateBlockEntityComponents: 놓인 칸의 블록 엔티티가 아이템 컴포넌트를 받는다. */
    public static void updateBlockEntityComponents(BlockLevel level, Pos pos, ItemStack stack) {
        if (level.getBlockEntity(pos) instanceof org.domi.furnace.server.block.entity.BlockEntity be) {
            be.applyComponentsFromItemStack(stack);
            be.setChanged(level);
        }
    }

    /** updatePlacementContext — 비계만 바꾼다. */
    private static PlaceContext updatePlacementContext(BlockItems.Entry e, PlaceContext ctx) {
        if (e.kind() != BlockItems.Kind.SCAFFOLDING) return ctx;
        BlockLevel level = ctx.level();
        Pos pos = ctx.clickedPos();
        int s = level.getBlockState(pos);
        if (BLOCKS.blockOf(s) != e.block()) return scaffoldingDistance(level, pos) == 7 ? null : ctx;
        int dir;
        if (ctx.isSecondaryUseActive()) dir = ctx.isInside() ? Dir.opposite(ctx.clickedFace()) : ctx.clickedFace();
        else dir = ctx.clickedFace() == Dir.UP ? ctx.horizontalDirection() : Dir.UP;
        int i = 0;
        Pos m = pos.relative(dir);
        while (i < 7) {
            if (!level.isInWorldBounds(m)) {
                int maxY = level.maxBuildHeight();
                if (ctx.player() != null && m.y() >= maxY) {
                    level.sendActionBar(ctx.player(), tooHigh(maxY - 1));
                }
                break;
            }
            s = level.getBlockState(m);
            if (BLOCKS.blockOf(s) != e.block()) {
                if (BlockBehaviors.of(s).canBeReplaced(s, ctx)) return PlaceContext.at(ctx, m, dir);
                break;
            }
            m = m.relative(dir);
            if (Dir.horizontal(dir)) i++;
        }
        return null;
    }

    /** ScaffoldingBlock.getDistance. */
    public static int scaffoldingDistance(BlockLevel level, Pos pos) {
        int scaffolding = BLOCKS.block("minecraft:scaffolding");
        Prop distance = BLOCKS.prop("distance");
        Pos m = pos.below();
        int below = level.getBlockState(m);
        int d = 7;
        if (BLOCKS.blockOf(below) == scaffolding) d = distance.intValue(below);
        else if (BlockBehaviors.of(below).isFaceSturdy(below, level, m, Dir.UP, BlockInfo.FULL)) return 0;
        for (int dir : Dir.HORIZONTAL) {
            int s = level.getBlockState(pos.relative(dir));
            if (BLOCKS.blockOf(s) == scaffolding) {
                d = Math.min(d, distance.intValue(s) + 1);
                if (d == 1) break;
            }
        }
        return d;
    }

    /** getPlacementState(ctx) — 놓을 수 없으면 -1. */
    private static int getPlacementState(BlockItems.Entry e, PlaceContext ctx) {
        BlockLevel level = ctx.level();
        Pos pos = ctx.clickedPos();
        switch (e.kind()) {
            case STANDING_AND_WALL, SIGN, HANGING_SIGN, BANNER, PLAYER_HEAD -> {
                int wall = BlockBehaviors.ofBlock(e.wallBlock()).getStateForPlacement(ctx);
                int result = -1;
                int skip = Dir.opposite(e.attachment());
                for (int dir : ctx.nearestLookingDirections()) {
                    if (dir == skip) continue;
                    int cand = dir == e.attachment() ? BlockBehaviors.ofBlock(e.block()).getStateForPlacement(ctx) : wall;
                    if (cand >= 0 && canPlaceStandingAndWall(e, level, cand, pos)) {
                        result = cand;
                        break;
                    }
                }
                return result >= 0 && level.isUnobstructed(result, pos, null) ? result : -1;
            }
            case GAME_MASTER -> {
                if (ctx.player() != null && !ctx.player().canUseGameMasterBlocks()) return -1;
            }
            default -> {}
        }
        int s = BlockBehaviors.ofBlock(e.block()).getStateForPlacement(ctx);
        return s >= 0 && canPlace(e, ctx, s) ? s : -1;
    }

    private static boolean canPlaceStandingAndWall(BlockItems.Entry e, BlockLevel level, int state, Pos pos) {
        BlockBehavior b = BlockBehaviors.of(state);
        if (e.kind() == BlockItems.Kind.HANGING_SIGN && b instanceof WallHangingSignPlacement w && !w.canPlace(state, level, pos)) return false;
        return b.canSurvive(state, level, pos);
    }

    /** BlockItem.canPlace: (mustSurvive 면) 살아남고 행위자 충돌 문맥으로 막히지 않아야. 비계는 mustSurvive = false. */
    private static boolean canPlace(BlockItems.Entry e, PlaceContext ctx, int s) {
        boolean mustSurvive = e.kind() != BlockItems.Kind.SCAFFOLDING;
        return (!mustSurvive || BlockBehaviors.of(s).canSurvive(s, ctx.level(), ctx.clickedPos()))
                && ctx.level().isUnobstructed(s, ctx.clickedPos(), ctx.player());
    }

    /** placeBlock: 기본 11, 두 칸 높이는 위를 먼저 비우고(27), 침대는 26. */
    private static boolean placeBlock(BlockItems.Entry e, PlaceContext ctx, int s) {
        BlockLevel level = ctx.level();
        Pos pos = ctx.clickedPos();
        return switch (e.kind()) {
            case DOUBLE_HIGH -> {
                Pos above = pos.above();
                int fill = level.isWaterAt(above) ? BLOCKS.defaultState("minecraft:water") : BLOCKS.defaultState("minecraft:air");
                level.setBlock(above, fill, 27);
                yield level.setBlock(pos, s, 11);
            }
            case BED -> level.setBlock(pos, s, 26);
            default -> level.setBlock(pos, s, 11);
        };
    }

    /** updateBlockStateFromTag: block_state 구성 요소의 (이름, 값)을 있는 속성에만 적용하고, 바뀌면 플래그 2로 다시 놓는다. */
    private static int updateBlockStateFromTag(Pos pos, BlockLevel level, ItemStack stack, int state) {
        Object v = component(stack, "minecraft:block_state");
        if (v == null) return state;
        Map<String, Object> props = Val.map(v);
        if (props.isEmpty()) return state;
        int n = state;
        for (var p : props.entrySet()) {
            Prop prop = BLOCKS.prop(p.getKey());
            if (prop == null || !prop.has(n)) continue;
            String value = Val.string(p.getValue());
            if (prop.indexOf(n, value) >= 0) n = prop.with(n, value);
        }
        if (n != state) level.setBlock(pos, n, 2);
        return n;
    }

    // ------------------------------------------------------------------ 도우미

    /** 스택의 구성 요소 값(패치 → 아이템 기본값). 지워졌으면 null. */
    public static Object component(ItemStack stack, String name) {
        Map<String, Object> patch = stack.patch().entries();
        if (patch.containsKey(name)) return patch.get(name);
        String item = ItemContext.installed() ? ItemContext.name("minecraft:item", stack.itemId()) : null;
        return item == null ? null : ItemPrototypes.defaultValue(item, name);
    }

    /** 모험 모드 can_place_on — 술어 평가는 블록 술어 체계가 들어오면. 지금은 구성 요소가 없으면 거부. */
    static boolean canPlaceOnInAdventure(ItemStack stack, BlockLevel level, Pos pos) {
        Object c = component(stack, "minecraft:can_place_on");
        if (c == null) return false;
        return AdventurePredicate.test(c, level, pos);
    }

    static int itemId(String name) {
        Integer id = ItemContext.installed() ? ItemContext.id("minecraft:item", name) : null;
        return id == null ? 0 : id;
    }

    /** build.tooHigh(빨강) 액션바. */
    public static org.domi.furnace.api.text.Text tooHigh(int maxY) {
        return org.domi.furnace.api.text.Text.translatable("build.tooHigh", maxY).color(org.domi.furnace.api.text.NamedTextColor.RED);
    }

    /** 바닐라 wasBlockPlacementAttempt: 블록 아이템이나 양동이이고 재사용 대기가 아니면. */
    public static boolean wasBlockPlacementAttempt(BlockActor player, ItemStack stack) {
        if (stack.isEmpty() || player.isOnCooldown(stack.itemId())) return false;
        if (BlockItems.entry(stack.itemId()) != null) return true;
        String n = ItemContext.installed() ? ItemContext.name("minecraft:item", stack.itemId()) : null;
        return n != null && n.endsWith("_bucket") && !n.equals("minecraft:milk_bucket") || "minecraft:bucket".equals(n);
    }

    /** WallHangingSignBlock.canPlace 를 가진 동작. */
    public interface WallHangingSignPlacement {
        boolean canPlace(int state, BlockLevel level, Pos pos);
    }
}
