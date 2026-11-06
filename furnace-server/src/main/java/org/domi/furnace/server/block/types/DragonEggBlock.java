// 바닐라 DragonEggBlock: 쓰거나 때리면 주변(±15, ±7) 빈칸으로 순간 이동한다. 떨어지는 블록(5틱).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public class DragonEggBlock extends FallingBlock {

    public DragonEggBlock(int block) { super(block); }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        teleport(state, level, pos);
        return InteractionResult.CONSUME;
    }

    @Override
    public void attack(int state, BlockLevel level, Pos pos, BlockActor player) { teleport(state, level, pos); }

    private void teleport(int state, BlockLevel level, Pos pos) {
        RandomSource r = level.random();
        for (int i = 0; i < 1000; i++) {
            Pos p = pos.offset(r.nextInt(16) - r.nextInt(16), r.nextInt(8) - r.nextInt(8), r.nextInt(16) - r.nextInt(16));
            if (B.isAir(level.getBlockState(p)) && level.withinBorder(p)) {
                level.setBlock(p, state, 2);
                level.removeBlock(pos, false);
                return;
            }
        }
    }

    @Override
    protected int delayAfterPlace() { return 5; }
}
