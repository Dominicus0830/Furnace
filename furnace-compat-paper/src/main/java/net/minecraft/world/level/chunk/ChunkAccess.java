package net.minecraft.world.level.chunk;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import org.domi.furnace.api.world.World;
import org.domi.furnace.compat.nms.NmsChunks;

/**
 * 청크 shim 의 바탕(Mojang 이름, N2a): 로드된 서버 청크의 <b>보기</b>. 값을 들고 있지 않고 읽을 때마다 서버 청크를 본다 —
 * 그 청크를 가진 region 스레드(이벤트 처리기·entityScheduler·전역 빌림 구간)에서만 읽히고, 아니면 ThreadOwnershipException.
 * 같은 청크의 보기는 equals 가 같다(바닐라는 로드돼 있는 동안 같은 객체 — 여기는 부를 때마다 새 객체일 수 있다).
 * <p>필드(sections·heightmaps 등)·섹션 배열에 대입하기·생성 중 청크(ProtoChunk) 표면은 N2b 라 없다.
 */
public abstract class ChunkAccess implements BlockGetter {

    protected final World furnaceWorld;
    private final ChunkPos pos;

    protected ChunkAccess(World furnaceWorld, int chunkX, int chunkZ) {
        this.furnaceWorld = java.util.Objects.requireNonNull(furnaceWorld, "world");
        this.pos = new ChunkPos(chunkX, chunkZ);
    }

    public ChunkPos getPos() { return pos; }

    @Override
    public int getMinBuildHeight() { return NmsChunks.bridge().minY(furnaceWorld); }

    @Override
    public int getHeight() { return NmsChunks.bridge().height(furnaceWorld); }

    /** 섹션 보기 배열(새 배열 — 원소를 바꿔 넣어도 청크에 닿지 않는다). 로드된 청크의 섹션은 null 이 아니다. */
    public LevelChunkSection[] getSections() {
        LevelChunkSection[] out = new LevelChunkSection[getSectionsCount()];
        for (int i = 0; i < out.length; i++) out[i] = LevelChunkSection.furnaceView(this, i);
        return out;
    }

    public LevelChunkSection getSection(int index) {
        if (index < 0 || index >= getSectionsCount()) throw new ArrayIndexOutOfBoundsException(index);
        return LevelChunkSection.furnaceView(this, index);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }

    @Override
    public BlockState getBlockStateIfLoaded(BlockPos pos) { return getBlockState(pos); }

    /** 하이트맵 보기(만들지 않는다 — 로드된 청크는 살아 있는 네 종류를 늘 가진다. 월드젠 종류는 비어 있다). */
    public Heightmap getOrCreateHeightmapUnprimed(Heightmap.Types type) { return Heightmap.furnaceView(this, type); }

    /** 바닐라 LevelChunk: 살아 있는 네 종류만 있다. */
    public boolean hasPrimedHeightmap(Heightmap.Types type) { return type.keepAfterWorldgen(); }

    /** 그 칸(청크 안 x·z) 의 가장 높은 해당 블록 y(빈 기둥이면 최저 높이 - 1). */
    public int getHeight(Heightmap.Types type, int x, int z) { return getOrCreateHeightmapUnprimed(type).getHighestTaken(x & 15, z & 15); }

    /** 로드된 청크는 늘 다 만들어진 상태다. */
    public ChunkStatus getPersistedStatus() { return ChunkStatus.FULL; }

    /**
     * 저장 대상 표시. true 면 서버가 이 청크를 저장 대상으로 표시한다. false(이미 저장됐다고 알림)는 Furnace 가 따로 저장 시점을 관리해 무시한다.
     */
    public void setUnsaved(boolean unsaved) {
        if (unsaved && !NmsChunks.bridge().markUnsavedNow(furnaceWorld, pos.x, pos.z)) throw notLoaded("ChunkAccess#setUnsaved");
    }

    /** 바이옴 칸 좌표(블록 좌표 >> 2)의 바이옴 — 높이는 끝 칸으로 죈다(바닐라 ChunkAccess.getNoiseBiome). */
    public Holder<Biome> getNoiseBiome(int qx, int qy, int qz) {
        int minQ = getMinBuildHeight() >> 2, maxQ = minQ + (getHeight() >> 2) - 1;
        int y = Math.max(minQ, Math.min(maxQ, qy));
        int sec = getSectionIndex(y << 2);
        return furnaceSectionBiomeAt(sec, qx & 3, y & 3, qz & 3);
    }

    // ------------------------------------------------------------------ 서버 다리(호환 모듈 전용)

    /** Furnace: 이 청크의 서버 월드 핸들. */
    public World furnaceWorld() { return furnaceWorld; }

    protected IllegalStateException notLoaded(String what) { return NmsChunks.notLoaded(furnaceWorld, pos.x, pos.z, what); }

    /** 월드 좌표의 상태 번호(높이 안). 청크가 로드돼 있지 않으면 IllegalStateException. */
    public int furnaceStateIdAt(int x, int y, int z) {
        int id = NmsChunks.bridge().stateIdNow(furnaceWorld, x, y, z);
        if (id < 0) throw notLoaded("getBlockState");
        return id;
    }

    public int[] furnaceSectionStates(int index) {
        int[] v = NmsChunks.bridge().sectionStatesNow(furnaceWorld, pos.x, index, pos.z);
        if (v == null) throw notLoaded("LevelChunkSection#getStates");
        return v;
    }

    public int furnaceSectionNonAir(int index) {
        int n = NmsChunks.bridge().sectionNonAirNow(furnaceWorld, pos.x, index, pos.z);
        if (n < 0) throw notLoaded("LevelChunkSection#hasOnlyAir");
        return n;
    }

    public boolean furnaceSectionRandomlyTicking(int index) {
        if (!NmsChunks.bridge().isLoaded(furnaceWorld, pos.x, pos.z)) throw notLoaded("LevelChunkSection#isRandomlyTicking");
        return NmsChunks.bridge().sectionRandomlyTickingNow(furnaceWorld, pos.x, index, pos.z);
    }

    public int[] furnaceSectionBiomes(int index) {
        int[] v = NmsChunks.bridge().sectionBiomesNow(furnaceWorld, pos.x, index, pos.z);
        if (v == null) throw notLoaded("LevelChunkSection#getBiomes");
        return v;
    }

    private Holder<Biome> furnaceSectionBiomeAt(int index, int x, int y, int z) {
        return NmsChunks.biome(furnaceSectionBiomes(index)[(y << 2 | z) << 2 | x]);
    }

    /** 하이트맵 첫 빈 y(청크 안 x·z). */
    public int furnaceFirstAvailable(Heightmap.Types type, int x, int z) {
        int v = NmsChunks.bridge().firstAvailableNow(furnaceWorld, type.getSerializationKey(), (pos.x << 4) + x, (pos.z << 4) + z);
        if (v == Integer.MIN_VALUE) throw notLoaded("Heightmap#getFirstAvailable");
        return v;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ChunkAccess c && c.getClass() == getClass() && c.pos.equals(pos) && c.furnaceWorld.equals(furnaceWorld);
    }

    @Override
    public int hashCode() { return furnaceWorld.hashCode() * 31 + pos.hashCode(); }

    @Override
    public String toString() { return getClass().getSimpleName() + "[" + pos.x + ", " + pos.z + " in " + furnaceWorld.dimension() + "]"; }
}
