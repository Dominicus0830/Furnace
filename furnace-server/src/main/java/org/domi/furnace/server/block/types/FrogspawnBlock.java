// 바닐라 FrogspawnBlock: 개구리알. 물 원천 위(그 칸은 비어 있음)에서만 산다. 3600~12000틱 뒤 부화(올챙이 2~6마리).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public class FrogspawnBlock extends BlockBehavior {

    public FrogspawnBlock(int block) { super(block); }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) { return mayPlaceOn(level, pos.below()); }

    @Override
    public void onPlace(int state, BlockLevel level, Pos pos, int oldState, boolean moved) {
        level.scheduleTick(pos, block, level.random().nextIntBetweenInclusive(3600, 12000));
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        return !canSurvive(state, level, pos) ? B.defaultState("air") : super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (!canSurvive(state, level, pos)) {
            level.destroyBlock(pos, false);
        } else {
            hatch(level, pos, random);
        }
    }

    /** entityInside: 떨어지는 블록 엔티티가 닿으면 부서진다(떨굼 없음). */
    @Override
    public void entityInside(int state, BlockLevel level, Pos pos, org.domi.furnace.server.entity.Entity entity) {
        if (entity instanceof org.domi.furnace.server.entity.FallingBlockEntity) level.destroyBlock(pos, false);
    }

    private static boolean mayPlaceOn(BlockLevel level, Pos pos) {
        int f = level.getFluidState(pos), above = level.getFluidState(pos.above());
        return Fluids.type(f) == Fluids.T_WATER && Fluids.isEmpty(above);
    }

    private static void hatch(BlockLevel level, Pos pos, RandomSource random) {
        level.destroyBlock(pos, false);
        level.playSound(null, pos, "minecraft:block.frogspawn.hatch", "blocks", 1.0F, 1.0F);
        int n = random.nextIntBetweenInclusive(2, 6);
        for (int i = 1; i <= n; i++) {
            // 바닐라 spawnTadpoles: x 오프셋 → z 오프셋 → 방향(1~361) 순서로 난수를 뽑는다.
            double xOffset = randomTadpolePositionOffset(random);
            double zOffset = randomTadpolePositionOffset(random);
            float yRot = random.nextIntBetweenInclusive(1, 361);
            level.spawnTadpole(pos, xOffset, zOffset, yRot);
        }
    }

    /** getRandomTadpolePositionOffset: clamp(nextDouble, 0.2, 0.8) — 바닐라 경계는 float 상수를 넓힌 값이다. */
    private static double randomTadpolePositionOffset(RandomSource random) {
        return Math.clamp(random.nextDouble(), (double) 0.2F, (double) 0.8F);
    }
}
