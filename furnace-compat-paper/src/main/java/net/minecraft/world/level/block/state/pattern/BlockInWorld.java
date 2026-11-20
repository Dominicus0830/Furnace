package net.minecraft.world.level.block.state.pattern;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 월드의 한 칸을 가리키는 값(Mojang 이름 shim) — 명령의 블록 조건이 시험하는 대상이다. 상태는 처음 읽을 때 월드에서 가져와 붙들고, 블록 엔티티·
 * 청크 불러오기 옵션은 두지 않았다(호출 위치에서 실패).
 */
public class BlockInWorld {

    private final LevelReader level;
    private final BlockPos pos;
    private BlockState state;

    public BlockInWorld(LevelReader level, BlockPos pos, boolean loadChunks) {
        this.level = level;
        this.pos = pos.immutable();
    }

    public BlockState getState() {
        if (this.state == null) this.state = this.level.getBlockState(this.pos);
        return this.state;
    }

    public LevelReader getLevel() { return this.level; }

    public BlockPos getPos() { return this.pos; }
}
