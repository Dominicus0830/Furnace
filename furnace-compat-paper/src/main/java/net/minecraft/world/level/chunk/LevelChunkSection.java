package net.minecraft.world.level.chunk;

import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.domi.furnace.compat.nms.NmsChunks;

/**
 * 청크 섹션 shim(Mojang 이름, N2a): 로드된 청크의 섹션(16³) <b>보기</b>. 읽을 때마다 서버 청크의 지금 값을 본다 — 그 청크를 가진
 * region 스레드(이벤트 처리기·entityScheduler·전역 빌림 구간)에서만 되고, 아니면 ThreadOwnershipException. 칸 좌표는 섹션 안(0~15).
 * <p>섹션 쓰기(setBlockState)·새 섹션 만들기·필드 접근은 N2b(설계 먼저)라 없다 — 그 호출은 UnsupportedNmsCall.
 * 블록은 {@code Level#setBlock}·{@code LevelChunk#setBlockState} 로 바꾼다.
 */
public class LevelChunkSection {

    public static final int SECTION_WIDTH = 16;
    public static final int SECTION_HEIGHT = 16;
    public static final int SECTION_SIZE = 4096;
    public static final int BIOME_CONTAINER_BITS = 2;

    private final ChunkAccess chunk;
    private final int index;

    private LevelChunkSection(ChunkAccess chunk, int index) {
        this.chunk = chunk;
        this.index = index;
    }

    /** 호환 모듈 전용: 청크의 index 번째(0 = 맨 아래) 섹션 보기. */
    public static LevelChunkSection furnaceView(ChunkAccess chunk, int index) { return new LevelChunkSection(chunk, index); }

    /** Furnace: 이 섹션의 번호(0 = 맨 아래). */
    public int furnaceIndex() { return index; }

    private int baseY() { return chunk.getMinBuildHeight() + (index << 4); }

    public BlockState getBlockState(int x, int y, int z) {
        var pos = chunk.getPos();
        return NmsChunks.state(chunk.furnaceStateIdAt((pos.x << 4) + (x & 15), baseY() + (y & 15), (pos.z << 4) + (z & 15)));
    }

    public FluidState getFluidState(int x, int y, int z) { return getBlockState(x, y, z).getFluidState(); }

    /** 바닐라 디버그 잠금 — 아무것도 하지 않는다(PalettedContainer#acquire 참고). */
    public void acquire() {}

    public void release() {}

    /** 공기(air·cave_air·void_air) 아닌 칸이 없는가. */
    public boolean hasOnlyAir() { return chunk.furnaceSectionNonAir(index) == 0; }

    /** 무작위 틱 받는 칸(블록 또는 그 유체)이 있는가. */
    public boolean isRandomlyTicking() { return chunk.furnaceSectionRandomlyTicking(index); }

    /** 바닐라는 칸 수를 다시 센다. Furnace 서버는 늘 정확한 수를 갖고 있어 할 일이 없다. */
    public void recalcBlockCounts() {}

    /** 블록 상태 컨테이너 보기(읽기 전용, copy() 는 고칠 수 있는 사본). */
    public PalettedContainer<BlockState> getStates() {
        var pos = chunk.getPos();
        int bx = pos.x << 4, bz = pos.z << 4;
        return PalettedContainer.furnaceView(4, NmsChunks::state, net.minecraft.world.level.block.Block::getId,
                i -> chunk.furnaceStateIdAt(bx + (i & 15), baseY() + (i >> 8), bz + (i >> 4 & 15)),
                () -> chunk.furnaceSectionStates(index));
    }

    /** 바이옴 컨테이너 보기(4×4×4). */
    public PalettedContainerRO<Holder<Biome>> getBiomes() {
        return PalettedContainer.<Holder<Biome>>furnaceView(2, NmsChunks::biome, NmsChunks::biomeId,
                i -> chunk.furnaceSectionBiomes(index)[i],
                () -> chunk.furnaceSectionBiomes(index));
    }

    /** 섹션 안 바이옴 칸(0~3) 의 바이옴. */
    public Holder<Biome> getNoiseBiome(int x, int y, int z) { return getBiomes().get(x & 3, y & 3, z & 3); }

    public boolean maybeHas(Predicate<BlockState> predicate) { return getStates().maybeHas(predicate); }

    @Override
    public boolean equals(Object o) { return o instanceof LevelChunkSection s && s.index == index && s.chunk.equals(chunk); }

    @Override
    public int hashCode() { return chunk.hashCode() * 31 + index; }

    @Override
    public String toString() { return "LevelChunkSection[" + chunk.getPos() + ", " + index + "]"; }
}
