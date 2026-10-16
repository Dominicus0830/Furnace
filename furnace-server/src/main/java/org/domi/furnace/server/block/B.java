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

    public static int defaultState(String name) { return BLOCKS.defaultState(id(name)); }

    public static Prop prop(String name) { return BLOCKS.prop(name); }

    public static BlockBehavior behavior(int state) { return BlockBehaviors.of(state); }

    public static boolean isAir(int state) { return INFO.isAir(state); }

    // ------------------------------------------------------------------ 태그(바닐라 데이터 팩 블록 태그)

    private static final Map<String, boolean[]> TAGS = new ConcurrentHashMap<>();

    /** state.is(#tag) — 태그 이름은 "minecraft:..."(# 없이). */
    public static boolean isTag(int state, String tag) {
        boolean[] t = TAGS.computeIfAbsent(tag.indexOf(':') < 0 ? "minecraft:" + tag : tag, k -> {
            boolean[] arr = new boolean[BLOCKS.blockCount()];
            for (String n : org.domi.furnace.server.world.gen.feature.BlockTags.get().blocksOf(k)) {
                int b = BLOCKS.block(n);
                if (b >= 0) arr[b] = true;
            }
            return arr;
        });
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

    /** 상태의 유체. */
    public static int fluid(int state) { return behavior(state).getFluidState(state); }
}
