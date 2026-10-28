// 바닐라 SaplingBlock: 묘목(stage 0/1). 밝은 곳에서 무작위 틱 1/7 로 한 단계, 두 번째에 나무(TreeGrower)를 키운다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class SaplingBlock extends BushBlock implements BonemealableBlock {

    static final Prop STAGE = B.prop("stage");

    /** TreeGrower 이름(oak/spruce/…/mangrove/azalea). */
    protected final String tree;
    private final TreeGrower.Spec grower;

    public SaplingBlock(int block) {
        super(block);
        this.tree = Blocks.get().params(block).get("tree").getAsString();
        this.grower = TreeGrower.of(tree);
    }

    @Override
    public void randomTick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (level.getMaxLocalRawBrightness(pos.above()) >= 9 && random.nextInt(7) == 0) advanceTree(level, pos, state, random);
    }

    /** advanceTree: 0 단계면 1 로(플래그 4), 아니면 나무를 키운다. */
    public void advanceTree(BlockLevel level, Pos pos, int state, RandomSource random) {
        if (STAGE.intValue(state) == 0) {
            level.setBlock(pos, STAGE.cycle(state), 4);
        } else {
            TreeGrower.growTree(grower, level, pos, state, random);
        }
    }

    @Override
    public boolean isValidBonemealTarget(BlockLevel level, Pos pos, int state) { return true; }

    @Override
    public boolean isBonemealSuccess(BlockLevel level, RandomSource random, Pos pos, int state) { return level.random().nextFloat() < 0.45; }

    @Override
    public void performBonemeal(BlockLevel level, RandomSource random, Pos pos, int state) { advanceTree(level, pos, state, random); }
}
