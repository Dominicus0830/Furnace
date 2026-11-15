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

    // ------------------------------------------------------------------ 다른 스레드 읽기(N2b-3)

    @Override
    public int vanillaStateIdNow(org.domi.furnace.api.world.World world, int x, int y, int z) {
        ChunkColumn col = column(world, x >> 4, z >> 4, "LevelChunk#getBlockState");
        if (col == null) return -1;
        return col.sectionVanillaHasOnlyAir(y) ? 0 : col.getBlock(x & 15, y, z & 15);
    }

    @Override
    public long[] blockEntityPositionsNow(org.domi.furnace.api.world.World world, int chunkX, int chunkZ) {
        ChunkColumn col = column(world, chunkX, chunkZ, "ChunkAccess#getBlockEntitiesPos");
        return col == null ? null : col.blockEntityPositions(chunkX, chunkZ);
    }

    /** 로드된 컬럼(아무 스레드, 최근 값 — 공개 스냅샷·shim 칸만 만진다). */
    private static ChunkColumn loadedAny(org.domi.furnace.api.world.World world, int cx, int cz) {
        return handle(world).handle().loadedChunkColumn(cx, cz);
    }

    @Override
    public ChunkView chunkView(org.domi.furnace.api.world.World world, int chunkX, int chunkZ) {
        ChunkColumn col = loadedAny(world, chunkX, chunkZ);
        if (col == null) return null;
        ChunkView v = col.readViewTouch();
        if (v == null) requestView(handle(world).handle(), col, chunkX, chunkZ);
        return v;
    }

    /** 주인에게 공개를 부탁한다(한 컬럼에 동시에 하나 — 작업이 돌면 그 자리에서 공개하고 틱 끝마다 이어 낸다). */
    private static void requestView(org.domi.furnace.server.world.World w, ChunkColumn col, int cx, int cz) {
        if (!col.claimViewRequest()) return;
        try {
            if (w.submitAtOwnedChunk(new ChunkPos(cx, cz), r -> r.publishReadViewNow(cx, cz)) == null) col.publishRequestFailed();   // 그 사이 내려감 — region 을 만들지 않는다(R4)
        } catch (java.util.concurrent.RejectedExecutionException e) {
            col.publishRequestFailed();   // 내린 월드 — 다음 부탁이 다시 보낼 수 있게
        }
    }

    @Override
    public ChunkView awaitChunkView(org.domi.furnace.api.world.World world, int chunkX, int chunkZ) {
        var w = handle(world).handle();
        if (org.domi.furnace.server.world.RegionBorrow.claimChunk(w, chunkX, chunkZ)) {
            Region r = w.regionizer().ownerOf(chunkX, chunkZ);
            return r == null ? null : r.publishReadViewNow(chunkX, chunkZ);
        }
        ChunkColumn col = loadedAny(world, chunkX, chunkZ);
        if (col == null) return null;
        ChunkView v = col.readViewTouch();
        if (v != null) return v;
        // region 스레드와 전역 스레드는 기다리지 않는다: region 이 전역을 기다리는 길(GlobalWait·전역이 쥔 잠금)이 있어 서로 기다리게 된다(PR #382 검토 R1).
        // 전역의 빌림 구간은 위 claimChunk 에서 이미 주인이 된다 — 여기 온 것은 빌림이 바로 그 교착을 알아채고 포기했거나 구간 밖인 경우다.
        if (org.domi.furnace.server.world.Regionizer.isRegionWorkerThread() || org.domi.furnace.server.sched.GlobalThread.isGlobalThread()) {
            requestView(w, col, chunkX, chunkZ);
            throw new IllegalStateException("청크 " + chunkX + ", " + chunkZ + " (" + w.dimension() + ") 는 다른 region 의 것이고 아직 공개 스냅샷이 없다 — "
                    + "region·전역 스레드는 다른 region 을 기다릴 수 없다(교착). 공개를 부탁해 두었으니 다음 틱부터 읽힌다(getChunkNow 로 확인)");
        }
        // region 이 아닌 스레드: 바닐라 getChunk 가 다른 스레드에서 주 스레드를 기다리는 것과 같은 간선. 주인이 없으면 region 을 만들지 않고(R4),
        // 종료 중 돌지 않을 작업을 영원히 기다리지 않게 상한을 둔다.
        var f = w.submitAtOwnedChunk(new ChunkPos(chunkX, chunkZ), r -> r.publishReadViewNow(chunkX, chunkZ));
        if (f == null) return null;
        try {
            return f.get(AWAIT_VIEW_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException("청크 " + chunkX + ", " + chunkZ + " (" + w.dimension() + ") 의 주인 region 이 " + AWAIT_VIEW_SECONDS
                    + "초 안에 공개하지 않았다(서버 종료 중이거나 region 이 멈춤)", e);
        } catch (java.util.concurrent.ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw new IllegalStateException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("공개 기다림이 끊겼다", e);
        }
    }

    // ------------------------------------------------------------------ 거울·들여오기(N2b-4)

    @Override
    public boolean requestMirrorSync(org.domi.furnace.api.world.World world, int chunkX, int chunkZ, ChunkMirror mirror) {
        java.util.Objects.requireNonNull(mirror, "mirror");
        var w = handle(world).handle();
        ChunkColumn col = loadedAny(world, chunkX, chunkZ);
        if (col == null) return false;
        boolean[] woke = {false};
        if (!col.markMirrorPending(mirror, woke)) return false;
        if (!woke[0]) return true;   // 이미 부탁이 가 있다
        w.mirrorPendingChunks.incrementAndGet();
        // 작업이 거울을 강하게 쥔다(돌 때까지 사라지지 않게). 주인이 없으면(그 사이 내려감) region 을 만들지 않고 표시를 거둔다(R4)
        boolean sent;
        try {
            sent = w.submitAtOwnedChunk(new ChunkPos(chunkX, chunkZ), r -> {
                r.syncMirrorTask(col, chunkX, chunkZ, mirror);
                return null;
            }) != null;
        } catch (java.util.concurrent.RejectedExecutionException e) {
            sent = false;   // 내린 월드
        }
        if (!sent && col.dropMirrorPending()) w.mirrorPendingChunks.decrementAndGet();
        return true;
    }

    @Override
    public boolean importSectionNow(org.domi.furnace.api.world.World world, int chunkX, int sectionIndex, int chunkZ, int[] states, int[] biomes, int[] counts) {
        if (states != null) {
            if (states.length != 4096) throw new IllegalArgumentException("블록 칸은 4096개여야 한다: " + states.length);
            for (int id : states) checkState(id);
        }
        if (biomes != null) {
            if (biomes.length != 64) throw new IllegalArgumentException("바이옴 칸은 64개여야 한다: " + biomes.length);
            int n = biomeKeys().size();
            for (int id : biomes) if (id < 0 || (n > 0 && id >= n)) throw new IllegalArgumentException("바이옴 번호 범위 밖: " + id);
        }
        if (counts != null && counts.length != 3) throw new IllegalArgumentException("개수는 셋이어야 한다: " + counts.length);
        ChunkColumn col = column(world, chunkX, chunkZ, "청크 섹션 들여오기");
        if (col == null) return false;
        col.importSection(checkSection(col, sectionIndex), states, biomes, counts);
        return true;
    }

    @Override
    public ChunkView publishChunkViewNow(org.domi.furnace.api.world.World world, int chunkX, int chunkZ) {
        Region r = ownedLoaded(world, chunkX, chunkZ, "청크 공개");
        return r == null ? null : r.publishReadViewNow(chunkX, chunkZ);
    }

    @Override
    public long contentStampNow(org.domi.furnace.api.world.World world, int chunkX, int chunkZ) {
        ChunkColumn col = column(world, chunkX, chunkZ, "청크 내용 판 번호");
        return col == null ? -1L : col.modCount();
    }

    /** 비 region 스레드가 주인의 공개를 기다리는 상한(초). 바닐라 getChunk 의 join 은 상한이 없지만 Furnace 는 종료 중 region 작업이 돌지 않는다. */
    private static final int AWAIT_VIEW_SECONDS = 30;

    @Override
    public Object chunkShim(org.domi.furnace.api.world.World world, int chunkX, int chunkZ) {
        ChunkColumn col = loadedAny(world, chunkX, chunkZ);
        return col == null ? null : col.viewHolder();
    }

    @Override
    public Object attachChunkShim(org.domi.furnace.api.world.World world, int chunkX, int chunkZ, Object shim) {
        return attachChunkShim(world, chunkX, chunkZ, shim, null);
    }

    @Override
    public Object attachChunkShim(org.domi.furnace.api.world.World world, int chunkX, int chunkZ, Object shim, ChunkMirror mirror) {
        var w = handle(world).handle();
        ChunkColumn col = loadedAny(world, chunkX, chunkZ);
        if (col == null) return null;
        Object won = col.attachViewHolder(shim, mirror);
        Region r = w.regionizer().ownerOf(chunkX, chunkZ);
        if (r != null && r.isOwnerThread()) {
            r.publishReadViewNow(chunkX, chunkZ);   // 주인 스레드면 그 자리에서 — 이 shim 이 곧바로 다른 스레드로 넘어가도 읽히게(R16)
        } else if (col.readView() == null) {
            requestView(w, col, chunkX, chunkZ);   // 공개본이 있으면 붙은 shim 을 주인이 틱 끝에 보고 이어 낸다
        }
        return won;
    }

}
