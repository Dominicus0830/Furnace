// 바닐라 ChorusFlowerBlock: 후렴화(age 0..5). 무작위 틱마다 위로 자라거나 옆으로 가지를 치고, 다 자라면(5) 멈춘다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class ChorusFlowerBlock extends BlockBehavior {

    static final Prop AGE = B.prop("age");
    public static final int DEAD_AGE = 5;
    private static final int END_STONE = B.id("end_stone"), CHORUS_PLANT = B.id("chorus_plant"), CHORUS_FLOWER = B.id("chorus_flower");

    private final int plant;

    public ChorusFlowerBlock(int block) {
        super(block);
        this.plant = B.id(Blocks.get().params(block).get("plant").getAsString());
    }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (!canSurvive(state, level, pos)) level.destroyBlock(pos, true);
    }

    @Override
    public boolean isRandomlyTicking(int state) { return AGE.intValue(state) < DEAD_AGE; }

    @Override
    public void randomTick(int state, BlockLevel level, Pos pos, RandomSource random) {
        Pos above = pos.above();
        if (!level.isEmptyBlock(above) || above.y() >= level.maxBuildHeight()) return;
        int age = AGE.intValue(state);
        if (age >= DEAD_AGE) return;
        boolean grow = false, endStoneBelowStem = false;
        int below = level.getBlockState(pos.below());
        if (B.is(below, END_STONE)) {
            grow = true;
        } else if (B.is(below, plant)) {
            int height = 1;
            for (int i = 0; i < 4; i++) {
                int s = level.getBlockState(pos.below(height + 1));
                if (!B.is(s, plant)) {
                    if (B.is(s, END_STONE)) endStoneBelowStem = true;
                    break;
                }
                height++;
            }
            if (height < 2 || height <= random.nextInt(endStoneBelowStem ? 5 : 4)) grow = true;
        } else if (B.isAir(below)) {
            grow = true;
        }
        if (grow && allNeighborsEmpty(level, above, -1) && level.isEmptyBlock(pos.above(2))) {
            level.setBlock(pos, ChorusPlantBlock.stateWithConnections(level, pos, B.defaultState(plant)), 2);
            placeGrownFlower(level, above, age);
        } else if (age < 4) {
            int branches = random.nextInt(4);
            if (endStoneBelowStem) branches++;
            boolean grew = false;
            for (int i = 0; i < branches; i++) {
                int dir = Dir.HORIZONTAL[random.nextInt(4)];
                Pos p = pos.relative(dir);
                if (level.isEmptyBlock(p) && level.isEmptyBlock(p.below()) && allNeighborsEmpty(level, p, Dir.opposite(dir))) {
                    placeGrownFlower(level, p, age + 1);
                    grew = true;
                }
            }
            if (grew) level.setBlock(pos, ChorusPlantBlock.stateWithConnections(level, pos, B.defaultState(plant)), 2);
            else placeDeadFlower(level, pos);
        } else {
            placeDeadFlower(level, pos);
        }
    }

    private void placeGrownFlower(BlockLevel level, Pos pos, int age) {
        level.setBlock(pos, AGE.withInt(defaultState(), age), 2);
        level.levelEvent(null, 1033, pos, 0);
    }

    private void placeDeadFlower(BlockLevel level, Pos pos) {
        level.setBlock(pos, AGE.withInt(defaultState(), DEAD_AGE), 2);
        level.levelEvent(null, 1034, pos, 0);
    }

    /** allNeighborsEmpty: except(-1 이면 없음) 쪽을 뺀 수평 이웃이 모두 빈칸. */
    private static boolean allNeighborsEmpty(BlockLevel level, Pos pos, int except) {
        for (int d : Dir.HORIZONTAL) {
            if (d != except && !level.isEmptyBlock(pos.relative(d))) return false;
        }
        return true;
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        if (dir != Dir.UP && !canSurvive(state, level, pos)) level.scheduleTick(pos, block, 1);
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        int below = level.getBlockState(pos.below());
        if (B.is(below, plant) || B.is(below, END_STONE)) return true;
        if (!B.isAir(below)) return false;
        boolean hasPlant = false;
        for (int d : Dir.HORIZONTAL) {
            int s = level.getBlockState(pos.relative(d));
            if (B.is(s, plant)) {
                if (hasPlant) return false;
                hasPlant = true;
            } else if (!B.isAir(s)) {
                return false;
            }
        }
        return hasPlant;
    }

    /** generatePlant(level, pos, random, maxHorizontalSpread): 후렴 식물 지형 생성. */
    public static void generatePlant(BlockLevel level, Pos pos, RandomSource random, int maxSpread) {
        level.setBlock(pos, ChorusPlantBlock.stateWithConnections(level, pos, B.defaultState(CHORUS_PLANT)), 2);
        growTreeRecursive(level, pos, random, pos, maxSpread, 0);
    }

    private static void growTreeRecursive(BlockLevel level, Pos branchPos, RandomSource random, Pos origin, int maxSpread, int depth) {
        int plantState = B.defaultState(CHORUS_PLANT);
        int height = random.nextInt(4) + 1;
        if (depth == 0) height++;
        for (int i = 0; i < height; i++) {
            Pos p = branchPos.above(i + 1);
            if (!allNeighborsEmpty(level, p, -1)) return;
            level.setBlock(p, ChorusPlantBlock.stateWithConnections(level, p, plantState), 2);
            level.setBlock(p.below(), ChorusPlantBlock.stateWithConnections(level, p.below(), plantState), 2);
        }
        boolean branched = false;
        if (depth < 4) {
            int branches = random.nextInt(4);
            if (depth == 0) branches++;
            for (int i = 0; i < branches; i++) {
                int dir = Dir.HORIZONTAL[random.nextInt(4)];
                Pos p = branchPos.above(height).relative(dir);
                if (Math.abs(p.x() - origin.x()) < maxSpread && Math.abs(p.z() - origin.z()) < maxSpread && level.isEmptyBlock(p)
                        && level.isEmptyBlock(p.below()) && allNeighborsEmpty(level, p, Dir.opposite(dir))) {
                    branched = true;
                    level.setBlock(p, ChorusPlantBlock.stateWithConnections(level, p, plantState), 2);
                    Pos back = p.relative(Dir.opposite(dir));
                    level.setBlock(back, ChorusPlantBlock.stateWithConnections(level, back, plantState), 2);
                    growTreeRecursive(level, p, random, origin, maxSpread, depth + 1);
                }
            }
        }
        if (!branched) level.setBlock(branchPos.above(height), AGE.withInt(B.defaultState(CHORUS_FLOWER), DEAD_AGE), 2);
    }

    /** onProjectileHit: 투사체가 mayInteract·mayBreak 면 부수고 떨군다(부순 엔티티는 투사체). */
    @Override
    public void onProjectileHit(BlockLevel level, int state, org.domi.furnace.server.entity.Projectile.Hit hit,
                                org.domi.furnace.server.entity.Projectile projectile) {
        Pos pos = hit.blockPos();
        // CraftBukkit(+Paper 새 상태): EntityChangeBlockEvent 를 막으면 부수지 않는다
        if (projectile.mayInteract(level, pos) && projectile.mayBreak(level)
                && org.domi.furnace.server.block.B.entityChange(level, projectile, pos, org.domi.furnace.server.block.B.fluidLeftover(state))) {
            level.destroyBlockByEntity(pos, true, projectile);
        }
    }
}
