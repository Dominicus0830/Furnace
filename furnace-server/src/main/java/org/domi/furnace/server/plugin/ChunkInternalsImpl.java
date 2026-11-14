// 청크 내부 읽기 다리(N2a) 구현 — 호환 계층의 NMS 청크 shim 이 서버 청크를 번호 단위로 읽는다(N2b-2 부터 주인 스레드 원시 쓰기도). 소유 검사는 WorldHandle.ownedRegion
// (전역 빌림 구간이면 그 region 을 빌린다)과 같다. 공유 구역을 복사하지 않는 읽기 길(sectionForRead)만 쓴다.
package org.domi.furnace.server.plugin;

import java.util.List;
import org.domi.furnace.api.internal.ChunkInternals;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.ChunkSection;
import org.domi.furnace.server.world.HeightmapType;
import org.domi.furnace.server.world.Region;
import org.domi.furnace.server.world.RegionLevel;
import org.domi.furnace.server.block.Pos;

final class ChunkInternalsImpl implements ChunkInternals {

    private final PluginHost host;

    ChunkInternalsImpl(PluginHost host) { this.host = host; }

    private static WorldHandle handle(org.domi.furnace.api.world.World w) {
        if (w instanceof WorldHandle wh) return wh;
        throw new IllegalArgumentException("Furnace 서버가 만든 월드 핸들이 아니다: " + w);
    }

    @Override
    public int minY(org.domi.furnace.api.world.World world) { return handle(world).handle().dimensionType().minY(); }

    @Override
    public int height(org.domi.furnace.api.world.World world) {
        var t = handle(world).handle().dimensionType();
        return t.maxY() - t.minY();
    }

    @Override
    public boolean isLoaded(org.domi.furnace.api.world.World world, int chunkX, int chunkZ) {
        return handle(world).handle().isChunkLoaded(chunkX, chunkZ);
    }

    @Override
    public boolean ownsChunk(org.domi.furnace.api.world.World world, int chunkX, int chunkZ) {
        return org.domi.furnace.server.world.RegionBorrow.claimChunk(handle(world).handle(), chunkX, chunkZ);
    }

    /**
     * 그 청크를 가진 region(로드돼 있지 않으면 null — 소유 검사 전에 본다: region 이 없는 청크에서 "다른 스레드"로 오해하지 않게).
     * 로드돼 있는데 소유 스레드가 아니면 ThreadOwnershipException.
     */
    private static Region ownedLoaded(org.domi.furnace.api.world.World world, int cx, int cz, String what) {
        WorldHandle h = handle(world);
        if (!h.handle().isChunkLoaded(cx, cz)) return null;
        return h.ownedRegion(new BlockPos(cx << 4, 0, cz << 4), what);
    }

    /** 소유 스레드 확인(아니면 ThreadOwnershipException) 뒤 그 청크(로드돼 있지 않으면 null). */
    private static ChunkColumn column(org.domi.furnace.api.world.World world, int cx, int cz, String what) {
        Region r = ownedLoaded(world, cx, cz, what);
        return r == null ? null : r.loadedOwnedChunk(cx, cz);
    }

    private static ChunkSection section(org.domi.furnace.api.world.World world, int cx, int index, int cz, String what) {
        ChunkColumn col = column(world, cx, cz, what);
        if (col == null) return null;
        int count = col.sectionCount();
        if (index < 0 || index >= count) throw new IndexOutOfBoundsException("섹션 번호 " + index + " (0.." + (count - 1) + ")");
        return col.sectionForRead(index);
    }

    @Override
    public boolean stateRandomlyTicking(int stateId) {
        if (stateId < 0 || stateId >= org.domi.furnace.server.block.Blocks.get().stateCount()) return false;
        return org.domi.furnace.server.block.BlockBehaviors.of(stateId).isRandomlyTicking(stateId);
    }

    @Override
    public Object loadedChunkToken(org.domi.furnace.api.world.World world, int chunkX, int chunkZ) {
        return handle(world).handle().loadedChunkToken(chunkX, chunkZ);
    }

    @Override
    public int stateIdNow(org.domi.furnace.api.world.World world, int x, int y, int z) {
        ChunkColumn col = column(world, x >> 4, z >> 4, "LevelChunk#getBlockState");
        return col == null ? -1 : col.getBlock(x & 15, y, z & 15);
    }

    @Override
    public int[] sectionStatesNow(org.domi.furnace.api.world.World world, int chunkX, int sectionIndex, int chunkZ) {
        ChunkSection s = section(world, chunkX, sectionIndex, chunkZ, "LevelChunkSection#getStates");
        return s == null ? null : s.blocks();   // 새 배열(scratch 면 그 사본)
    }

    @Override
    public int sectionNonAirNow(org.domi.furnace.api.world.World world, int chunkX, int sectionIndex, int chunkZ) {
        ChunkSection s = section(world, chunkX, sectionIndex, chunkZ, "LevelChunkSection#hasOnlyAir");
        if (s == null) return -1;
        if (!s.scratchOpen()) return s.nonAirCount();
        int n = 0;   // scratch 가 열린 동안은 센 값이 낡을 수 있다 — 칸을 센다
        for (int v : s.blocks()) if (!ChunkSection.isAir(v)) n++;
        return n;
    }

    @Override
    public boolean sectionRandomlyTickingNow(org.domi.furnace.api.world.World world, int chunkX, int sectionIndex, int chunkZ) {
        ChunkSection s = section(world, chunkX, sectionIndex, chunkZ, "LevelChunkSection#isRandomlyTicking");
        return s != null && s.isRandomlyTicking();
    }

    @Override
    public int sectionRandomTickingPartsNow(org.domi.furnace.api.world.World world, int chunkX, int sectionIndex, int chunkZ) {
        ChunkSection s = section(world, chunkX, sectionIndex, chunkZ, "LevelChunkSection#isRandomlyTickingBlocks/Fluids");
        return s == null ? 0 : s.randomTickingParts();
    }

    @Override
    public int[] sectionBiomesNow(org.domi.furnace.api.world.World world, int chunkX, int sectionIndex, int chunkZ) {
        ChunkSection s = section(world, chunkX, sectionIndex, chunkZ, "LevelChunkSection#getBiomes");
        return s == null ? null : s.biomes();
    }

    @Override
    public List<String> biomeKeys() {
        var w = host.core().worlds().isEmpty() ? null : host.core().worlds().get(0);
        if (w == null) return List.of();
        WorldHandle h = host.worldHandle(w.dimension());
        return h == null ? List.of() : h.biomeKeyList();
    }

    @Override
    public int firstAvailableNow(org.domi.furnace.api.world.World world, String heightmap, int x, int z) {
        HeightmapType type = HeightmapType.byKey(heightmap.toLowerCase(java.util.Locale.ROOT));
        if (type == null) throw new IllegalArgumentException("모르는 하이트맵: " + heightmap);
        Region r = ownedLoaded(world, x >> 4, z >> 4, "Heightmap#getFirstAvailable");
        return r == null ? Integer.MIN_VALUE : r.heightAt(type, x, z);
    }

    @Override
    public boolean markUnsavedNow(org.domi.furnace.api.world.World world, int chunkX, int chunkZ) {
        Region r = ownedLoaded(world, chunkX, chunkZ, "ChunkAccess#setUnsaved");
        ChunkPos cp = new ChunkPos(chunkX, chunkZ);
        if (r == null || !r.chunkLoaded(cp)) return false;
        r.markChunkChanged(cp);
        return true;
    }

    @Override
    public boolean setBlockNow(org.domi.furnace.api.world.World world, int x, int y, int z, int stateId, int flags, int maxUpdateDepth) {
        Region r = ownedLoaded(world, x >> 4, z >> 4, "Level#setBlock");
        RegionLevel lv = r == null || !r.chunkLoaded(new ChunkPos(x >> 4, z >> 4)) ? null : r.level();
        return lv != null && lv.setBlock(new Pos(x, y, z), stateId, flags, maxUpdateDepth);
    }

    private static HeightmapType heightmapType(String key) {
        HeightmapType type = HeightmapType.byKey(key.toLowerCase(java.util.Locale.ROOT));
        if (type == null) throw new IllegalArgumentException("모르는 하이트맵: " + key);
        return type;
    }

    private static void checkState(int stateId) {
        if (stateId < 0 || stateId >= org.domi.furnace.server.block.Blocks.get().stateCount()) {
            throw new IllegalArgumentException("블록 상태 번호 범위 밖: " + stateId);
        }
    }

    private static int checkSection(ChunkColumn col, int index) {
        int count = col.sectionCount();
        if (index < 0 || index >= count) throw new IndexOutOfBoundsException("섹션 번호 " + index + " (0.." + (count - 1) + ")");
        return index;
    }

    @Override
    public int setSectionStateRawNow(org.domi.furnace.api.world.World world, int chunkX, int sectionIndex, int chunkZ, int cellIndex, int stateId,
                                     boolean vanillaCounts) {
        if (cellIndex < 0 || cellIndex >= 4096) throw new IllegalArgumentException("섹션 칸 번호 범위 밖: " + cellIndex);
        checkState(stateId);
        ChunkColumn col = column(world, chunkX, chunkZ, "LevelChunkSection#setBlockState");
        if (col == null) return -1;
        return col.setSectionStateRaw(checkSection(col, sectionIndex), cellIndex, stateId, vanillaCounts);
    }

    @Override
    public int setSectionBiomeRawNow(org.domi.furnace.api.world.World world, int chunkX, int sectionIndex, int chunkZ, int cellIndex, int biomeId) {
        if (cellIndex < 0 || cellIndex >= 64) throw new IllegalArgumentException("바이옴 칸 번호 범위 밖: " + cellIndex);
        int biomes = biomeKeys().size();
        if (biomeId < 0 || (biomes > 0 && biomeId >= biomes)) throw new IllegalArgumentException("바이옴 번호 범위 밖: " + biomeId);
        ChunkColumn col = column(world, chunkX, chunkZ, "PalettedContainer#set (바이옴)");
        if (col == null) return -1;
        return col.setSectionBiomeRaw(checkSection(col, sectionIndex), cellIndex, biomeId);
    }

    @Override
    public int[] sectionCountsNow(org.domi.furnace.api.world.World world, int chunkX, int sectionIndex, int chunkZ) {
        ChunkColumn col = column(world, chunkX, chunkZ, "LevelChunkSection 개수");
        return col == null ? null : col.sectionVanillaCounts(checkSection(col, sectionIndex));
    }

    @Override
    public boolean setSectionCountsNow(org.domi.furnace.api.world.World world, int chunkX, int sectionIndex, int chunkZ,
                                       int nonEmptyBlockCount, int tickingBlockCount, int tickingFluidCount) {
        ChunkColumn col = column(world, chunkX, chunkZ, "LevelChunkSection 개수 대입");
        if (col == null) return false;
        col.setSectionVanillaCounts(checkSection(col, sectionIndex), nonEmptyBlockCount, tickingBlockCount, tickingFluidCount);
        return true;
    }

    @Override
    public int setHeightmapRawNow(org.domi.furnace.api.world.World world, int chunkX, int chunkZ, String heightmap, long[] raw) {
        HeightmapType type = heightmapType(heightmap);
        java.util.Objects.requireNonNull(raw, "raw");
        ChunkColumn col = column(world, chunkX, chunkZ, "Heightmap#setRawData");
        if (col == null) return -1;
        boolean ok = col.setHeightmapRaw(type, raw.clone());
        if (!ok) {
            org.slf4j.LoggerFactory.getLogger(ChunkInternalsImpl.class).warn("Ignoring heightmap data for chunk [{}, {}], size does not match; expected: {}, got: {}",
                    chunkX, chunkZ, col.heightmapRawLength(), raw.length);
        }
        return ok ? 1 : 0;
    }

    @Override
    public int updateHeightmapNow(org.domi.furnace.api.world.World world, String heightmap, int x, int y, int z, int stateId) {
        HeightmapType type = heightmapType(heightmap);
        checkState(stateId);
        ChunkColumn col = column(world, x >> 4, z >> 4, "Heightmap#update");
        if (col == null) return -1;
        return col.heightmapUpdate(type, x & 15, y, z & 15, stateId) ? 1 : 0;
    }

    @Override
    public int setBlockStateNow(org.domi.furnace.api.world.World world, int x, int y, int z, int stateId, boolean moved) {
        Region r = ownedLoaded(world, x >> 4, z >> 4, "LevelChunk#setBlockState");
        RegionLevel lv = r == null || !r.chunkLoaded(new ChunkPos(x >> 4, z >> 4)) ? null : r.level();
        return lv == null ? -1 : lv.setBlockStateInChunkOnly(new Pos(x, y, z), stateId, moved);
    }
}
