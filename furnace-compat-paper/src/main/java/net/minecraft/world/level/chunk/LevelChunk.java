package net.minecraft.world.level.chunk;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.domi.furnace.compat.nms.NmsChunks;

/**
 * 로드된 청크 shim(Mojang 이름, N2a): 서버 청크의 <b>보기</b>({@link ChunkAccess} 참고). {@code ServerLevel#getChunk} 등이 돌려준다.
 * 블록 쓰기 {@link #setBlockState} 는 바닐라처럼 그 칸만 바꾼다(이웃·모양 갱신, 클라이언트 알림, POI 없음 — 블록 자체의 놓기·치우기 반응, 블록 엔티티,
 * 빛, 하이트맵, 저장 표시는 바닐라대로). 블록 엔티티 읽기(getBlockEntity)·엔티티·틱 상태(getFullStatus)는 아직 없다.
 */
public class LevelChunk extends ChunkAccess {

    private final Level level;

    private LevelChunk(Level level, int chunkX, int chunkZ) {
        super(level.getWorld().furnaceHandle(), chunkX, chunkZ);
        this.level = level;
    }

    /** 호환 모듈 전용: 로드된 청크의 새 보기(로드 여부는 부르는 쪽이 확인한다). 정체성 캐시({@code NmsChunks.loadedChunk})만 부른다. */
    public static LevelChunk furnaceView(Level level, int chunkX, int chunkZ) { return new LevelChunk(level, chunkX, chunkZ); }

    public Level getLevel() { return level; }

    /**
     * 높이 밖·공기뿐인 섹션은 minecraft:air(바닐라 LevelChunk.getBlockState — 동굴 공기만 있는 섹션도 hasOnlyAir 라 air 를 준다).
     */
    @Override
    public BlockState getBlockState(BlockPos pos) {
        int y = pos.getY();
        if (isOutsideBuildHeight(y)) return Blocks.AIR.defaultBlockState();
        if (furnaceSectionNonAir(getSectionIndex(y)) == 0) return Blocks.AIR.defaultBlockState();
        return NmsChunks.state(furnaceStateIdAt(pos.getX(), y, pos.getZ()));
    }

    public FluidState getFluidState(int x, int y, int z) { return getBlockState(new BlockPos(x, y, z)).getFluidState(); }

    /**
     * 바닐라 LevelChunk.setBlockState 그대로(서버의 바닐라 포팅): 그 칸만 바꾸고 옛 상태를 돌려준다. 이웃·모양 갱신, 클라이언트 알림, POI 갱신은 없다.
     * 같은 상태·공기뿐인 섹션에 공기류·치우기 반응이 칸을 갈아 치움이면 null. 높이 밖이면 바닐라는 섹션 배열 밖 예외지만 여기서는 null.
     */
    public BlockState setBlockState(BlockPos pos, BlockState state, boolean moved) {
        int y = pos.getY();
        if (isOutsideBuildHeight(y)) return null;
        int old = NmsChunks.bridge().setBlockStateNow(furnaceWorld, pos.getX(), y, pos.getZ(), state.furnaceStateId(), moved);
        return old < 0 ? null : NmsChunks.state(old);
    }
}
