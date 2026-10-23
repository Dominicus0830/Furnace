// 바닐라 BrushableBlock: 수상한 모래·자갈. 놓이거나 이웃이 바뀌면 2틱 뒤 틱에서 아래가 비었으면 떨어진다(솔질은 블록 엔티티 동작).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public class BrushableBlock extends BaseEntityBlock {

    private final int turnsInto;
    private final String brushSound, brushCompletedSound;

    public BrushableBlock(int block) {
        super(block);
        var p = Blocks.get().params(block);
        this.turnsInto = B.id(p.get("turns_into").getAsString());
        this.brushSound = p.get("brush_sound").getAsString();
        this.brushCompletedSound = p.get("brush_comleted_sound").getAsString();
    }

    public int turnsInto() { return turnsInto; }

    public String brushSound() { return brushSound; }

    public String brushCompletedSound() { return brushCompletedSound; }

    @Override
    public void onPlace(int state, BlockLevel level, Pos pos, int oldState, boolean moved) { level.scheduleTick(pos, block, 2); }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        level.scheduleTick(pos, block, 2);
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        // TODO(블록 엔티티): BrushableBlockEntity.checkReset — 솔질이 멈춘 뒤 dusted 를 되돌린다.
        if (FallingBlock.isFree(level.getBlockState(pos.below())) && pos.y() >= level.minBuildHeight()) {
            // TODO(엔티티): FallingBlockEntity.fall — 떨어지는 블록 엔티티(그 자리는 유체의 레거시 블록으로).
        }
    }
}
