// 블록 동작 코드가 쓰는 바닐라 Block 정적 도우미(updateOrDestroy·updateFromNeighbourShapes·지지 판정·태그·속성 접근).
package org.domi.furnace.server.block;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class B {
    private B() {}

    public static final Blocks BLOCKS = Blocks.get();
    public static final BlockInfo INFO = BlockInfo.get();

    /** 상태의 블록 번호. */
    public static int block(int state) { return BLOCKS.blockOf(state); }

    /** 블록 이름 → 번호(없으면 -1). */
    public static int id(String name) { return BLOCKS.block(name.indexOf(':') < 0 ? "minecraft:" + name : name); }

    public static boolean is(int state, int block) { return BLOCKS.blockOf(state) == block; }

    public static boolean is(int state, String name) { return BLOCKS.blockOf(state) == id(name); }

    public static int defaultState(int block) { return BLOCKS.defaultState(block); }

    /** CraftEventFactory.handleBlockGrowEvent: 플러그인 BlockGrowEvent 가 허락하면 setBlock(flags) 하고 true. */
    public static boolean grow(BlockLevel level, Pos pos, int newState, int flags) {
        if (!level.gameEvents().blockGrow(level.dimension(), pos, level.getBlockState(pos), newState)) return false;
        level.setBlock(pos, newState, flags);
        return true;
    }

    /** CraftEventFactory.handleBlockFormEvent: 플러그인 BlockFormEvent 가 허락하면 setBlock(flags) 하고 true. */
    public static boolean form(BlockLevel level, Pos pos, int newState, int flags) {
        if (!level.gameEvents().blockForm(level.dimension(), pos, level.getBlockState(pos), newState)) return false;
        level.setBlock(pos, newState, flags);
        return true;
    }

    /** CraftEventFactory.handleBlockSpreadEvent: 플러그인 BlockSpreadEvent 가 허락하면 setBlock(flags) 하고 true. */
    public static boolean spread(BlockLevel level, Pos source, Pos pos, int newState, int flags) {
        if (!level.gameEvents().blockSpread(level.dimension(), pos, source, level.getBlockState(pos), newState)) return false;
        level.setBlock(pos, newState, flags);
        return true;
    }

    /** CraftEventFactory.callBlockFadeEvent: 플러그인 BlockFadeEvent 가 허락하는가(바꾸는 일은 부른 쪽이). */
    public static boolean fade(BlockLevel level, Pos pos, int newState) {
        return level.gameEvents().blockFade(level.dimension(), pos, level.getBlockState(pos), newState);
    }

    public static int defaultState(String name) { return BLOCKS.defaultState(id(name)); }

    public static Prop prop(String name) { return BLOCKS.prop(name); }

    public static BlockBehavior behavior(int state) { return BlockBehaviors.of(state); }

    public static boolean isAir(int state) { return INFO.isAir(state); }

    // ------------------------------------------------------------------ 태그(바닐라 데이터 팩 블록 태그)

    private static final Map<String, boolean[]> TAGS = new ConcurrentHashMap<>();

    /** state.is(#tag) — 태그 이름은 "minecraft:..."(# 없이). */
    public static boolean isTag(int state, String tag) {
        boolean[] t = TAGS.get(tag);
        if (t != null) return t[BLOCKS.blockOf(state)];
        t = TAGS.computeIfAbsent(tag.indexOf(':') < 0 ? "minecraft:" + tag : tag, k -> {
            boolean[] arr = new boolean[BLOCKS.blockCount()];
            for (String n : org.domi.furnace.server.world.gen.feature.BlockTags.get().blocksOf(k)) {
                int b = BLOCKS.block(n);
                if (b >= 0) arr[b] = true;
            }
            return arr;
        });
        TAGS.putIfAbsent(tag, t);
        return t[BLOCKS.blockOf(state)];
    }

    // ------------------------------------------------------------------ 갱신(바닐라 Block 정적)

    /** Block.updateOrDestroy(old, new, level, pos, flags, recursionLeft). */
    public static void updateOrDestroy(int oldState, int newState, BlockLevel level, Pos pos, int flags, int recursionLeft) {
        if (newState != oldState) {
            if (INFO.isAir(newState)) level.destroyBlock(pos, (flags & BlockLevel.UPDATE_SUPPRESS_DROPS) == 0, null, recursionLeft);
            else level.setBlock(pos, newState, flags & ~BlockLevel.UPDATE_SUPPRESS_DROPS, recursionLeft);
        }
    }

    /** Block.updateFromNeighbourShapes(state, level, pos) — 모양 갱신 순서로 이웃마다 updateShape. */
    public static int updateFromNeighbourShapes(int state, BlockLevel level, Pos pos) {
        int s = state;
        for (int d : Dir.SHAPE_ORDER) {
            Pos n = pos.relative(d);
            s = behavior(s).updateShape(s, d, level.getBlockState(n), level, pos, n);
        }
        return s;
    }

    /** BlockState.updateNeighbourShapes(level, pos, flags, recursionLeft). */
    public static void updateNeighbourShapes(int state, BlockLevel level, Pos pos, int flags, int recursionLeft) {
        for (int d : Dir.SHAPE_ORDER) level.neighborShapeChanged(Dir.opposite(d), state, pos.relative(d), pos, flags, recursionLeft);
    }

    public static void updateNeighbourShapes(int state, BlockLevel level, Pos pos, int flags) {
        updateNeighbourShapes(state, level, pos, flags, BlockLevel.UPDATE_LIMIT);
    }

    // ------------------------------------------------------------------ 지지(바닐라 SupportType)

    public static boolean isFaceSturdy(int state, BlockLevel level, Pos pos, int dir, int type) {
        return behavior(state).isFaceSturdy(state, level, pos, dir, type);
    }

    public static boolean isFaceSturdy(int state, BlockLevel level, Pos pos, int dir) { return isFaceSturdy(state, level, pos, dir, BlockInfo.FULL); }

    /** Block.canSupportRigidBlock(level, pos) — 그 칸의 윗면이 RIGID. */
    public static boolean canSupportRigidBlock(BlockLevel level, Pos pos) {
        return isFaceSturdy(level.getBlockState(pos), level, pos, Dir.UP, BlockInfo.RIGID);
    }

    /** Block.canSupportCenter(level, pos, dir) — 아랫면이고 #unstable_bottom_center 면 거짓. */
    public static boolean canSupportCenter(BlockLevel level, Pos pos, int dir) {
        int s = level.getBlockState(pos);
        if (dir == Dir.DOWN && isTag(s, "minecraft:unstable_bottom_center")) return false;
        return isFaceSturdy(s, level, pos, dir, BlockInfo.CENTER);
    }

    /** BlockState.isSolidRender 근사: 투명하지 않은 완전한 블록(occlusion 모양이 꽉 참). */
    public static boolean isFullOcclusion(int state) { return INFO.occlusionShape(state).sameAs(Shape.BLOCK) && INFO.canOcclude(block(state)); }

    /** BlockState.isCollisionShapeFullBlock(빈 세계 기준). */
    public static boolean isCollisionShapeFullBlock(int state) { return INFO.collisionShape(state).sameAs(Shape.BLOCK); }

    /** Block.withPropertiesOf(state): 이 블록의 기본 상태에 state 의 같은 이름 속성 값을 옮긴다. */
    public static int withPropertiesOf(int block, int state) {
        int out = BLOCKS.defaultState(block);
        int from = BLOCKS.blockOf(state);
        for (Prop p : BLOCKS.propsOf(block)) {
            for (Prop q : BLOCKS.propsOf(from)) {
                if (q == p) {
                    out = p.with(out, p.valueName(state));
                    break;
                }
            }
        }
        return out;
    }

    /** Level.getTimeOfDay(1.0F) = DimensionType.timeOfDay(dayTime) — 네더·엔드는 고정 시각(18000·6000). */
    public static float timeOfDay(BlockLevel level) {
        long fixed = level.dimensionType().fixedTime();
        long t = fixed >= 0 ? fixed : level.dayTime();
        double d0 = t / 24000.0 - 0.25;
        d0 -= Math.floor(d0);
        double d1 = 0.5 - Math.cos(d0 * Math.PI) / 2.0;
        return (float) (d0 * 2.0 + d1) / 3.0F;
    }

    /** 아이템 이름 → ItemStack(count). */
    public static org.domi.furnace.server.entity.ItemStack stack(String item, int count) {
        return new org.domi.furnace.server.entity.ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", item), count);
    }

    /** 손에 든 것이 그 아이템인가(ItemStack.is(item)). */
    public static boolean holds(org.domi.furnace.server.entity.ItemStack s, String item) {
        return !s.isEmpty() && s.itemId() == org.domi.furnace.server.item.ItemContext.id("minecraft:item", item);
    }

    /** ItemStack.is(#아이템 태그). */
    public static boolean holdsTag(org.domi.furnace.server.entity.ItemStack s, String tag) {
        return !s.isEmpty() && org.domi.furnace.server.item.ItemContext.isInTag("minecraft:item", tag, s.itemId());
    }

    /**
     * 위 칸이 아래 칸으로 오는 빛을 완전히 막는가 — LightEngine.getLightBlockInto(…, UP, …) >= 최대 빛(15) 의 근사.
     * TODO(빛): 빛 차단량(getLightBlock) 표가 생기면 정확히. 지금은 가리는 블록의 아랫면 가림 모양이 꽉 찼으면 막힘.
     */
    public static boolean lightBlockedAbove(int aboveState) {
        return INFO.canOcclude(block(aboveState)) && INFO.occlusionShape(aboveState).isFaceFull(Dir.DOWN);
    }

    private static final int BARRIER = id("barrier"), CARVED_PUMPKIN = id("carved_pumpkin"), JACK_O_LANTERN = id("jack_o_lantern"),
            MELON = id("melon"), PUMPKIN = id("pumpkin");

    /** Block.isExceptionForConnection: 나뭇잎·차단벽·깎은 호박·잭오랜턴·수박·호박·셜커 상자에는 울타리·벽이 붙지 않는다. */
    public static boolean isExceptionForConnection(int state) {
        int b = block(state);
        return behavior(state) instanceof org.domi.furnace.server.block.types.LeavesBlock || b == BARRIER || b == CARVED_PUMPKIN
                || b == JACK_O_LANTERN || b == MELON || b == PUMPKIN || isTag(state, "minecraft:shulker_boxes");
    }

    /** BlockState.canBeReplaced(ctx). */
    public static boolean canBeReplaced(int state, PlaceContext ctx) { return behavior(state).canBeReplaced(state, ctx); }

    /** BlockState.canSurvive(level, pos). */
    public static boolean canSurvive(int state, BlockLevel level, Pos pos) { return behavior(state).canSurvive(state, level, pos); }

    /** 설치 칸에 물 원천이 있는가(getStateForPlacement 의 waterlogged = fluid.getType() == WATER). */
    public static boolean waterAt(PlaceContext ctx) { return Fluids.isWaterSource(ctx.level().getFluidState(ctx.clickedPos())); }

    /** updateShape 첫머리의 "물이 잠겨 있으면 물 틱 예약"(scheduleTick(pos, WATER, WATER.getTickDelay)). */
    public static void tickWaterlogged(int state, BlockLevel level, Pos pos) {
        if (BlockBehavior.WATERLOGGED.has(state) && BlockBehavior.WATERLOGGED.is(state)) level.scheduleFluidTick(pos, Fluids.T_WATER, 5);
    }

    /** level.getRandom().nextFloat() * 0.1F + 0.9F — 문·울타리 문 소리 높이. */
    public static float openPitch(BlockLevel level) { return level.random().nextFloat() * 0.1F + 0.9F; }

    /** BlockState.liquid(): 액체 블록(물·용암)인가. */
    public static boolean isLiquid(int state) { int b = block(state); return b == WATER || b == LAVA; }

    private static final int WATER = id("water"), LAVA = id("lava");

    /** BlockState.isSolid()(legacySolid). */
    public static boolean isSolid(int state) { return INFO.legacySolid(state); }

    /** 상태의 유체. */
    public static int fluid(int state) { return behavior(state).getFluidState(state); }
}
