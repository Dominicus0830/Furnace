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
 * 빛, 하이트맵, 저장 표시는 바닐라대로). 블록 엔티티 칸 목록(getBlockEntitiesPos)은 있고, 블록 엔티티 객체(getBlockEntity)·엔티티·틱 상태
 * (getFullStatus)는 아직 없다. 다른 스레드에서 읽으면 공개 스냅샷과 자기 쓰기(N2b-3·4), 다른 스레드의 쓰기는 거울 일지로 주인이 재생한다(N2b-4,
 * {@link ChunkAccess} 참고). 청크가 내려가면 이 shim 의 섹션은 그 순간 내용으로 굳는다(바닐라: 떨어진 LevelChunk).
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
     * 높이 밖·공기뿐인 섹션은 minecraft:air(바닐라 LevelChunk.getBlockState — 섹션의 바닐라 hasOnlyAir(nonEmptyBlockCount == 0)를 본다. 동굴 공기만
     * 있는 섹션도 air 를 주고, 컨테이너 원시 쓰기로 개수가 0 인 채 블록이 든 섹션도 바닐라처럼 air 를 준다).
     */
    @Override
    public BlockState getBlockState(BlockPos pos) {
        int y = pos.getY();
        if (isOutsideBuildHeight(y)) return Blocks.AIR.defaultBlockState();
        if (!furnaceMirror.dead() && furnaceOwner()) return NmsChunks.state(furnaceVanillaStateIdAt(pos.getX(), y, pos.getZ()));   // 맞춘 뒤 살아 있는 값
        // 다른 스레드·내린 청크: 바닐라처럼 섹션 배열을 거친다 — 플러그인이 넣은 섹션·자기 쓰기·떼어 낸 내용을 그대로 읽는다(N2b-4)
        LevelChunkSection s = sections[getSectionIndex(y)];
        if (s == null || s.hasOnlyAir()) return Blocks.AIR.defaultBlockState();
        return s.getBlockState(pos.getX() & 15, y & 15, pos.getZ() & 15);
    }

    public FluidState getFluidState(int x, int y, int z) { return getBlockState(new BlockPos(x, y, z)).getFluidState(); }

    /**
     * 바닐라 LevelChunk.setBlockState 그대로(서버의 바닐라 포팅): 그 칸만 바꾸고 옛 상태를 돌려준다. 이웃·모양 갱신, 클라이언트 알림, POI 갱신은 없다.
     * 같은 상태·공기뿐인 섹션에 공기류·치우기 반응이 칸을 갈아 치움이면 null. 높이 밖이면 바닐라는 섹션 배열 밖 예외지만 여기서는 null.
     */
    public BlockState setBlockState(BlockPos pos, BlockState state, boolean moved) {
        int y = pos.getY();
        if (isOutsideBuildHeight(y)) return null;
        int slot = getSectionIndex(y);
        if (!furnaceMirror.dead() && furnaceOwner()) {
            furnaceMirror.ownerSync(slot);
            int old = NmsChunks.bridge().setBlockStateNow(furnaceWorld, pos.getX(), y, pos.getZ(), state.furnaceStateId(), moved);
            return old < 0 ? null : NmsChunks.state(old);
        }
        // 다른 스레드(N2b-4, 결정 D1): 바닐라처럼 그 자리 섹션을 본다. 서버 섹션의 보기면 일지에 쌓아 주인이 바닐라 LevelChunk.setBlockState 를 그대로 재생한다
        // (블록 엔티티·빛·하이트맵·저장 표시까지). 플러그인이 넣은 떨어진 섹션이면 그 섹션에 쓴다(들여오기가 가져간다 — 그 밖 부수 효과는 없다).
        LevelChunkSection s = sections[slot];
        if (s == null) return null;
        if (s.hasOnlyAir() && state.isAir()) return null;
        int x = pos.getX(), z = pos.getZ();
        if (!furnaceMirror.dead() && s.furnaceChunk() == this && s.furnaceIndex() == slot && s.getStates().furnaceLiveNow()) {
            BlockState old = s.getBlockState(x & 15, y & 15, z & 15);
            if (old == state) return null;
            furnaceMirror.chunkSet(s, slot, x, y, z, state.furnaceStateId(), old.furnaceStateId(), moved);
            return old;
        }
        BlockState old = s.setBlockState(x & 15, y & 15, z & 15, state);
        return old == state ? null : old;
    }
}
