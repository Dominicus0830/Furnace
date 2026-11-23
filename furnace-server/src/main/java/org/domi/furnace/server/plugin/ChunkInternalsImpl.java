// 청크 내부 읽기 다리(N2a) 구현 — 호환 계층의 NMS 청크 shim 이 서버 청크를 번호 단위로 읽는다(N2b-2 부터 주인 스레드 원시 쓰기도). 소유 검사는 WorldHandle.ownedRegion
// (전역 빌림 구간이면 그 region 을 빌린다)과 같다. 공유 구역을 복사하지 않는 읽기 길(sectionForRead)만 쓴다.
package org.domi.furnace.server.plugin;

import java.util.List;
import org.domi.furnace.api.internal.ChunkInternals;
import org.domi.furnace.api.internal.ChunkInternals.EntityOp;
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
        throw new IllegalArgumentException("not a world handle created by the Furnace server: " + w);
    }

    @Override
    public int noiseBiome(org.domi.furnace.api.world.World world, int quartX, int quartY, int quartZ) {
        return handle(world).handle().worldSource().noiseBiome(quartX, quartY, quartZ);
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
    public java.util.concurrent.CompletableFuture<org.domi.furnace.api.nbt.NbtCompound> storedNbt(org.domi.furnace.api.world.World world, int kind, int chunkX, int chunkZ) {
        int k = switch (kind) {
            case STORED_CHUNK -> org.domi.furnace.server.world.World.STORED_CHUNK;
            case STORED_POI -> org.domi.furnace.server.world.World.STORED_POI;
            case STORED_ENTITIES -> org.domi.furnace.server.world.World.STORED_ENTITIES;
            default -> throw new IllegalArgumentException("stored kind " + kind);
        };
        return handle(world).handle().readStoredAsync(k, chunkX, chunkZ).thenApply(t -> t == null ? null : ApiNbt.toApi(t));
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
        if (index < 0 || index >= count) throw new IndexOutOfBoundsException("section index " + index + " (0.." + (count - 1) + ")");
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
    public ChunkInternals.PackedStates sectionStatesPackedNow(org.domi.furnace.api.world.World world, int chunkX, int sectionIndex, int chunkZ) {
        ChunkSection s = section(world, chunkX, sectionIndex, chunkZ, "PalettedContainer.data");
        return s == null ? null : s.blocksPacked();   // 사본(scratch 가 열려 있으면 null)
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
        if (type == null) throw new IllegalArgumentException("unknown heightmap: " + heightmap);
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
    public int fullChunkStatusNow(org.domi.furnace.api.world.World world, int chunkX, int chunkZ) {
        Region r = ownedLoaded(world, chunkX, chunkZ, "LevelChunk#getFullStatus");
        return r == null || !r.chunkLoaded(new ChunkPos(chunkX, chunkZ)) ? -1 : r.fullChunkStatus(chunkX, chunkZ);
    }

    @Override
    public boolean setBlockNow(org.domi.furnace.api.world.World world, int x, int y, int z, int stateId, int flags, int maxUpdateDepth) {
        Region r = ownedLoaded(world, x >> 4, z >> 4, "Level#setBlock");
        RegionLevel lv = r == null || !r.chunkLoaded(new ChunkPos(x >> 4, z >> 4)) ? null : r.level();
        return lv != null && lv.setBlock(new Pos(x, y, z), stateId, flags, maxUpdateDepth);
    }

    @Override
    public boolean fluidTickNow(org.domi.furnace.api.world.World world, int x, int y, int z, org.domi.furnace.api.world.FluidData fluid) {
        int f = packedFluid(fluid);
        Region r = ownedLoaded(world, x >> 4, z >> 4, "FluidState#tick");
        RegionLevel lv = r == null || !r.chunkLoaded(new ChunkPos(x >> 4, z >> 4)) ? null : r.level();
        if (lv == null) return false;
        org.domi.furnace.server.block.FluidTicker.tick(lv, new Pos(x, y, z), f);
        return true;
    }

    /** API 유체 값 → 서버 유체 값(종류 | 양 << 3 | 떨어짐 << 7, Fluids 와 같은 꼴). */
    static int packedFluid(org.domi.furnace.api.world.FluidData fluid) {
        int t = switch (fluid.type()) {
            case "minecraft:empty" -> org.domi.furnace.server.block.Fluids.EMPTY;
            case "minecraft:flowing_water" -> org.domi.furnace.server.block.Fluids.T_FLOWING_WATER;
            case "minecraft:water" -> org.domi.furnace.server.block.Fluids.T_WATER;
            case "minecraft:flowing_lava" -> org.domi.furnace.server.block.Fluids.T_FLOWING_LAVA;
            case "minecraft:lava" -> org.domi.furnace.server.block.Fluids.T_LAVA;
            default -> throw new IllegalArgumentException("unknown fluid: " + fluid.type());
        };
        if (t == org.domi.furnace.server.block.Fluids.EMPTY) return org.domi.furnace.server.block.Fluids.EMPTY;
        if (fluid.amount() < 1 || fluid.amount() > 8) throw new IllegalArgumentException("fluid amount out of range: " + fluid.amount());
        return t | fluid.amount() << 3 | (fluid.falling() ? 1 << 7 : 0);
    }

    @Override
    public boolean blockTickNow(org.domi.furnace.api.world.World world, int x, int y, int z, int stateId, java.util.random.RandomGenerator random) {
        checkState(stateId);
        java.util.Objects.requireNonNull(random, "random");
        Region r = ownedLoaded(world, x >> 4, z >> 4, "BlockState#tick");
        RegionLevel lv = r == null || !r.chunkLoaded(new ChunkPos(x >> 4, z >> 4)) ? null : r.level();
        if (lv == null) return false;
        org.domi.furnace.server.block.BlockBehaviors.of(stateId).tick(stateId, lv, new Pos(x, y, z), new CallerRandom(random));
        return true;
    }

    /** 부른 쪽 난수를 서버 블록 동작의 난수로(값은 부른 쪽 난수열 그대로). */
    private record CallerRandom(java.util.random.RandomGenerator r) implements org.domi.furnace.server.block.RandomSource {
        public int nextInt() { return r.nextInt(); }

        public int nextInt(int bound) { return r.nextInt(bound); }

        public long nextLong() { return r.nextLong(); }

        public float nextFloat() { return r.nextFloat(); }

        public double nextDouble() { return r.nextDouble(); }

        public boolean nextBoolean() { return r.nextBoolean(); }

        public double nextGaussian() { return r.nextGaussian(); }
    }

    @Override
    public byte[] itemStackWireBytes(org.domi.furnace.api.item.ItemStack stack) {
        io.netty.buffer.ByteBuf b = io.netty.buffer.Unpooled.buffer();
        try {
            org.domi.furnace.server.item.ItemStackCodecs.writeStack(b, ApiConvert.item(stack));
            byte[] out = new byte[b.readableBytes()];
            b.readBytes(out);
            return out;
        } finally {
            b.release();
        }
    }

    @Override
    public ItemWire readItemStackWire(java.nio.ByteBuffer src) {
        io.netty.buffer.ByteBuf b = io.netty.buffer.Unpooled.wrappedBuffer(src.duplicate());
        org.domi.furnace.server.entity.ItemStack s = org.domi.furnace.server.item.ItemStackCodecs.readStack(b);
        return new ItemWire(apiItem(s), b.readerIndex());
    }

    private static HeightmapType heightmapType(String key) {
        HeightmapType type = HeightmapType.byKey(key.toLowerCase(java.util.Locale.ROOT));
        if (type == null) throw new IllegalArgumentException("unknown heightmap: " + key);
        return type;
    }

    private static void checkState(int stateId) {
        if (stateId < 0 || stateId >= org.domi.furnace.server.block.Blocks.get().stateCount()) {
            throw new IllegalArgumentException("block state id out of range: " + stateId);
        }
    }

    private static int checkSection(ChunkColumn col, int index) {
        int count = col.sectionCount();
        if (index < 0 || index >= count) throw new IndexOutOfBoundsException("section index " + index + " (0.." + (count - 1) + ")");
        return index;
    }

    @Override
    public int setSectionStateRawNow(org.domi.furnace.api.world.World world, int chunkX, int sectionIndex, int chunkZ, int cellIndex, int stateId,
                                     boolean vanillaCounts) {
        if (cellIndex < 0 || cellIndex >= 4096) throw new IllegalArgumentException("section cell index out of range: " + cellIndex);
        checkState(stateId);
        ChunkColumn col = column(world, chunkX, chunkZ, "LevelChunkSection#setBlockState");
        if (col == null) return -1;
        return col.setSectionStateRaw(checkSection(col, sectionIndex), cellIndex, stateId, vanillaCounts);
    }

    @Override
    public int setSectionBiomeRawNow(org.domi.furnace.api.world.World world, int chunkX, int sectionIndex, int chunkZ, int cellIndex, int biomeId) {
        if (cellIndex < 0 || cellIndex >= 64) throw new IllegalArgumentException("biome cell index out of range: " + cellIndex);
        int biomes = biomeKeys().size();
        if (biomeId < 0 || (biomes > 0 && biomeId >= biomes)) throw new IllegalArgumentException("biome id out of range: " + biomeId);
        ChunkColumn col = column(world, chunkX, chunkZ, "PalettedContainer#set (biomes)");
        if (col == null) return -1;
        return col.setSectionBiomeRaw(checkSection(col, sectionIndex), cellIndex, biomeId);
    }

    @Override
    public int[] sectionCountsNow(org.domi.furnace.api.world.World world, int chunkX, int sectionIndex, int chunkZ) {
        ChunkColumn col = column(world, chunkX, chunkZ, "LevelChunkSection count");
        return col == null ? null : col.sectionVanillaCounts(checkSection(col, sectionIndex));
    }

    @Override
    public boolean setSectionCountsNow(org.domi.furnace.api.world.World world, int chunkX, int sectionIndex, int chunkZ,
                                       int nonEmptyBlockCount, int tickingBlockCount, int tickingFluidCount) {
        ChunkColumn col = column(world, chunkX, chunkZ, "LevelChunkSection count assignment");
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
        return setBlockStateNow(world, x, y, z, stateId, moved, true);
    }

    @Override
    public int setBlockStateNow(org.domi.furnace.api.world.World world, int x, int y, int z, int stateId, boolean moved, boolean doPlace) {
        Region r = ownedLoaded(world, x >> 4, z >> 4, "LevelChunk#setBlockState");
        RegionLevel lv = r == null || !r.chunkLoaded(new ChunkPos(x >> 4, z >> 4)) ? null : r.level();
        return lv == null ? -1 : lv.setBlockStateInChunkOnly(new Pos(x, y, z), stateId, moved, doPlace);
    }

    private static final String[] EFFECT_NAMES = {"Level#sendBlockUpdated", "ServerChunkCache#blockChanged", "Level#updateNeighborsAt",
            "Level#updateNeighbourForOutputSignal", "Level#onBlockStateChange", "BlockState#onPlace", "BlockState#handleNeighborChanged",
            "BlockState#updateNeighbourShapes", "BlockState#updateIndirectNeighbourShapes", "BlockState#triggerEvent", "Block#updateFromNeighbourShapes",
            "Level#scheduleTick(Block)", "Level#scheduleTick(Fluid)",
            "PoiManager#add", "PoiManager#remove"};

    /** T-204: 쪼갠 Level.setBlock 조각 — 서버 setBlock 과 같은 코드(ChunkLevel·BlockBehavior·B). */
    @Override
    public int blockEffectNow(org.domi.furnace.api.world.World world, int kind, int x, int y, int z, int a, int b, int c, long other) {
        if (kind < 0 || kind >= EFFECT_NAMES.length) throw new IllegalArgumentException("unknown block effect " + kind);
        RegionLevel lv = ownedLevel(world, x, z, EFFECT_NAMES[kind]);
        if (lv == null) return -1;
        if (kind == EFFECT_SCHEDULE_FLUID_TICK || kind == EFFECT_SCHEDULE_BLOCK_TICK) {
            if (c < -3 || c > 3) throw new IllegalArgumentException("tick priority out of range: " + c);
            Pos p = new Pos(x, y, z);
            if (kind == EFFECT_SCHEDULE_BLOCK_TICK) {
                checkState(a);
                lv.scheduleTick(p, org.domi.furnace.server.block.B.block(a), b, c);
            } else {
                if (a < 0 || a > 4) throw new IllegalArgumentException("unknown fluid type " + a);
                lv.scheduleFluidTick(p, a, b, c);
            }
            return 0;
        }
        if (kind == EFFECT_POI_ADD || kind == EFFECT_POI_REMOVE) {
            Pos p = new Pos(x, y, z);
            if (kind == EFFECT_POI_REMOVE) lv.poiManager().remove(p);
            else {
                var types = org.domi.furnace.server.world.poi.PoiTypes.all();
                if (a < 0 || a >= types.size()) throw new IllegalArgumentException("unknown poi type " + a);
                lv.poiManager().add(p, types.get(a));
            }
            return 0;
        }
        if (kind != EFFECT_BLOCK_CHANGED) checkState(a);
        if (kind == EFFECT_SEND_BLOCK_UPDATED || kind == EFFECT_POI || kind == EFFECT_ON_PLACE || kind == EFFECT_NEIGHBOR_CHANGED) checkState(b);
        Pos p = new Pos(x, y, z);
        var beh = org.domi.furnace.server.block.BlockBehaviors.of(kind == EFFECT_BLOCK_CHANGED ? 0 : a);
        switch (kind) {
            case EFFECT_SEND_BLOCK_UPDATED -> lv.sendBlockUpdated(p, a, b);
            case EFFECT_BLOCK_CHANGED -> lv.sendBlockUpdated(p);
            case EFFECT_UPDATE_NEIGHBORS -> lv.updateNeighborsAt(p, org.domi.furnace.server.block.B.block(a));
            case EFFECT_OUTPUT_SIGNAL -> lv.updateNeighbourForOutputSignal(p, org.domi.furnace.server.block.B.block(a));
            case EFFECT_POI -> lv.poiManager().onBlockStateChange(p, a, b);
            case EFFECT_ON_PLACE -> beh.onPlace(a, lv, p, b, c != 0);
            case EFFECT_NEIGHBOR_CHANGED -> beh.neighborChanged(a, lv, p, org.domi.furnace.server.block.B.block(b), Pos.of(other), c != 0);
            case EFFECT_NEIGHBOUR_SHAPES -> org.domi.furnace.server.block.B.updateNeighbourShapes(a, lv, p, b, c);
            case EFFECT_INDIRECT_SHAPES -> beh.updateIndirectNeighbourShapes(a, lv, p, b, c);
            case EFFECT_TRIGGER_EVENT -> { return beh.triggerEvent(a, lv, p, b, c) ? 1 : 0; }
            case EFFECT_FROM_NEIGHBOUR_SHAPES -> { return org.domi.furnace.server.block.B.updateFromNeighbourShapes(a, lv, p); }
            default -> throw new IllegalStateException();
        }
        return 0;
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
            throw new IllegalStateException("chunk " + chunkX + ", " + chunkZ + " (" + w.dimension() + ") belongs to another region and has no published snapshot yet - "
                    + "region and global threads cannot wait for another region (deadlock). Publishing was requested, so it can be read from the next tick (check with getChunkNow)");
        }
        // region 이 아닌 스레드: 바닐라 getChunk 가 다른 스레드에서 주 스레드를 기다리는 것과 같은 간선. 주인이 없으면 region 을 만들지 않고(R4),
        // 종료 중 돌지 않을 작업을 영원히 기다리지 않게 상한을 둔다.
        var f = w.submitAtOwnedChunk(new ChunkPos(chunkX, chunkZ), r -> r.publishReadViewNow(chunkX, chunkZ));
        if (f == null) return null;
        try {
            return f.get(AWAIT_VIEW_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException("the owning region of chunk " + chunkX + ", " + chunkZ + " (" + w.dimension() + ") did not publish it within " + AWAIT_VIEW_SECONDS
                    + " seconds (the server is stopping or the region is stuck)", e);
        } catch (java.util.concurrent.ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw new IllegalStateException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("waiting for the publication was interrupted", e);
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
            if (states.length != 4096) throw new IllegalArgumentException("there must be 4096 block cells: " + states.length);
            for (int id : states) checkState(id);
        }
        if (biomes != null) {
            if (biomes.length != 64) throw new IllegalArgumentException("there must be 64 biome cells: " + biomes.length);
            int n = biomeKeys().size();
            for (int id : biomes) if (id < 0 || (n > 0 && id >= n)) throw new IllegalArgumentException("biome id out of range: " + id);
        }
        if (counts != null && counts.length != 3) throw new IllegalArgumentException("there must be three counts: " + counts.length);
        ChunkColumn col = column(world, chunkX, chunkZ, "importing chunk sections");
        if (col == null) return false;
        col.importSection(checkSection(col, sectionIndex), states, biomes, counts);
        return true;
    }

    @Override
    public ChunkView publishChunkViewNow(org.domi.furnace.api.world.World world, int chunkX, int chunkZ) {
        Region r = ownedLoaded(world, chunkX, chunkZ, "publishing a chunk");
        return r == null ? null : r.publishReadViewNow(chunkX, chunkZ);
    }

    @Override
    public int relightChunks(org.domi.furnace.api.world.World world, int[] chunkX, int[] chunkZ, ChunkCallback chunkDone,
                             java.util.function.IntConsumer allDone) {
        if (chunkX.length != chunkZ.length) throw new IllegalArgumentException("chunkX/chunkZ length mismatch");
        long[] keys = new long[chunkX.length];
        for (int i = 0; i < keys.length; i++) keys[i] = org.domi.furnace.server.world.Regionizer.chunkKey(chunkX[i], chunkZ[i]);
        java.util.function.LongConsumer each = chunkDone == null ? null
                : k -> chunkDone.accept(org.domi.furnace.server.world.Regionizer.chunkX(k), org.domi.furnace.server.world.Regionizer.chunkZ(k));
        return handle(world).handle().relight().relight(keys, each, allDone);
    }

    // ------------------------------------------------------------------ 빛 읽기·검사(T-205)

    @Override
    public boolean hasSkyLight(org.domi.furnace.api.world.World world) { return handle(world).handle().dimensionType().hasSkyLight(); }

    @Override
    public boolean lightReadyNow(org.domi.furnace.api.world.World world, int chunkX, int chunkZ) {
        ChunkColumn col = column(world, chunkX, chunkZ, "chunk light");
        return col != null && col.light() != null && col.light().lightEnabled;
    }

    @Override
    public int lightNow(org.domi.furnace.api.world.World world, int x, int y, int z, boolean sky) {
        ChunkColumn col = column(world, x >> 4, z >> 4, "LayerLightEventListener#getLightValue");
        var cl = col == null ? null : col.light();
        if (cl == null) return -1;
        int i = org.domi.furnace.server.light.ChunkLight.sectionIndex(y);
        if (i < 0 || i >= org.domi.furnace.server.light.ChunkLight.SECTIONS) return -1;   // 서버 빛 배열 밖(오버월드보다 넓은 차원) — 자료 없음
        return sky ? cl.skyAt(x, y, z) : cl.blockAt(x, y, z);
    }

    @Override
    public byte[] lightLayerNow(org.domi.furnace.api.world.World world, int chunkX, int sectionY, int chunkZ, boolean sky) {
        ChunkColumn col = column(world, chunkX, chunkZ, "LayerLightEventListener#getDataLayerData");
        var cl = col == null ? null : col.light();
        int i = sectionY - org.domi.furnace.server.light.ChunkLight.MIN_SECTION;
        if (cl == null || i < 0 || i >= org.domi.furnace.server.light.ChunkLight.SECTIONS) return null;
        return (sky ? cl.sky(i) : cl.block(i)).clone();
    }

    @Override
    public boolean checkLight(org.domi.furnace.api.world.World world, int x, int y, int z) {
        WorldHandle h = handle(world);
        var w = h.handle();
        var t = w.dimensionType();
        if (y < t.minY() || y >= t.maxY()) return false;
        int cx = x >> 4, cz = z >> 4;
        if (!w.isChunkLoaded(cx, cz)) return false;
        if (org.domi.furnace.server.world.RegionBorrow.claimChunk(w, cx, cz)) {
            Region r = h.ownedRegion(new BlockPos(x, y, z), "LevelLightEngine#checkBlock");   // 거울 대기가 있으면 먼저 들여온다(맞춤 지점)
            return r != null && r.chunkLoaded(new ChunkPos(cx, cz)) && r.level().checkLightAt(x, y, z);
        }
        // 다른 스레드: 주인에게 보낸다(기다리지 않음). 주인은 그 청크의 거울 쓰기를 먼저 들여온 뒤 검사를 건다 — 검사가 플러그인이 방금 쓴 블록을 보게
        ChunkPos cp = new ChunkPos(cx, cz);
        java.util.concurrent.CompletableFuture<Boolean> f;
        try {
            f = w.submitAtOwnedChunk(cp, r -> {
                if (!r.chunkLoaded(cp)) return false;
                w.syncMirrorIfPending(r, cx, cz);
                return r.level().checkLightAt(x, y, z);
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            return false;   // 내린 월드
        }
        return f != null && !f.isCompletedExceptionally();
    }

    @Override
    public long contentStampNow(org.domi.furnace.api.world.World world, int chunkX, int chunkZ) {
        ChunkColumn col = column(world, chunkX, chunkZ, "chunk content revision number");
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


    // ------------------------------------------------------------------ 블록 엔티티(T-198)

    /**
     * 아직 해석하지 않은 생성 블록 엔티티 NBT(서버에 틀이 없는 종류 — 바닐라의 pendingBlockEntities 자리)의 손잡이. 같은 열·같은 칸이면 같은 손잡이다
     * (호출마다 새 객체라도 equals) — 호환 계층이 같은 칸의 보기를 다시 쓰게. 그 칸의 생성 NBT 가 지워지면 지워진 것이고, 그 뒤에도 마지막 내용을 든다.
     */
    static final class GeneratedBlockEntityRef {
        private final ChunkColumn col;
        private final int x, y, z;
        private volatile org.domi.furnace.server.net.io.nbt.NbtCompound last;

        GeneratedBlockEntityRef(ChunkColumn col, int x, int y, int z, org.domi.furnace.server.net.io.nbt.NbtCompound nbt) {
            this.col = col;
            this.x = x;
            this.y = y;
            this.z = z;
            this.last = nbt;
        }

        int x() { return x; }
        int y() { return y; }
        int z() { return z; }

        /** 지금 내용(지워졌으면 마지막으로 본 내용). */
        org.domi.furnace.server.net.io.nbt.NbtCompound nbt() {
            var cur = col.generatedBlockEntity(x & 15, y, z & 15);
            if (cur != null) last = cur;
            return last;
        }

        boolean removed() { return col.generatedBlockEntity(x & 15, y, z & 15) == null; }

        @Override
        public boolean equals(Object o) {
            return o instanceof GeneratedBlockEntityRef g && g.col == col && g.x == x && g.y == y && g.z == z;
        }

        @Override
        public int hashCode() { return System.identityHashCode(col) * 31 + java.util.Objects.hash(x, y, z); }
    }

    /** 주인 스레드의 로드된 그 칸 레벨(아니면 null — 소유 스레드가 아니면 ThreadOwnershipException). */
    private static RegionLevel ownedLevel(org.domi.furnace.api.world.World world, int x, int z, String what) {
        Region r = ownedLoaded(world, x >> 4, z >> 4, what);
        return r == null || !r.chunkLoaded(new ChunkPos(x >> 4, z >> 4)) ? null : r.level();
    }

    @Override
    public Object blockEntityNow(org.domi.furnace.api.world.World world, int x, int y, int z, boolean create) {
        RegionLevel lv = ownedLevel(world, x, z, "LevelChunk#getBlockEntity");
        if (lv == null || lv.isOutsideBuildHeight(y)) return null;
        Pos p = new Pos(x, y, z);
        Object be = create ? lv.getBlockEntity(p) : lv.getBlockEntityIfPresent(p);
        if (be != null) return be;
        ChunkColumn col = column(world, x >> 4, z >> 4, "LevelChunk#getBlockEntity");
        var nbt = col == null ? null : col.generatedBlockEntity(x & 15, y, z & 15);
        return nbt == null ? null : new GeneratedBlockEntityRef(col, x, y, z, nbt);
    }

    @Override
    public Object removeBlockEntityNow(org.domi.furnace.api.world.World world, int x, int y, int z) {
        RegionLevel lv = ownedLevel(world, x, z, "LevelChunk#removeBlockEntity");
        if (lv == null || lv.isOutsideBuildHeight(y)) return null;
        Pos p = new Pos(x, y, z);
        ChunkColumn col = column(world, x >> 4, z >> 4, "LevelChunk#removeBlockEntity");
        Object be = col == null ? null : col.blockEntityAt(x, y, z);
        if (be == null && col != null) {
            var nbt = col.generatedBlockEntity(x & 15, y, z & 15);
            if (nbt != null) be = new GeneratedBlockEntityRef(col, x, y, z, nbt);
        }
        lv.removeBlockEntity(p);   // 서버의 바닐라 길: 게임 이벤트 리스너 빼기, 지움 표시(틱 목록은 지움 표시로 빠진다), 생성 NBT 도 지운다
        return be;
    }

    @Override
    public boolean blockEntityRemoved(Object blockEntity) {
        if (blockEntity instanceof org.domi.furnace.server.block.entity.BlockEntity be) return be.isRemoved();
        if (blockEntity instanceof GeneratedBlockEntityRef g) return g.removed();
        throw new IllegalArgumentException("not a block entity handle: " + blockEntity);
    }

    @Override
    public org.domi.furnace.api.nbt.NbtCompound blockEntitySaveNow(Object blockEntity) {
        org.domi.furnace.server.net.io.nbt.NbtCompound tag;
        if (blockEntity instanceof org.domi.furnace.server.block.entity.BlockEntity be) {
            tag = be.saveWithFullMetadata();
        } else if (blockEntity instanceof GeneratedBlockEntityRef g) {
            tag = (org.domi.furnace.server.net.io.nbt.NbtCompound) org.domi.furnace.server.net.io.nbt.NbtTags.copy(g.nbt());
            tag.putInt("x", g.x());
            tag.putInt("y", g.y());
            tag.putInt("z", g.z());
        } else {
            throw new IllegalArgumentException("not a block entity handle: " + blockEntity);
        }
        return ApiNbt.toApi((org.domi.furnace.server.net.io.nbt.NbtCompound) org.domi.furnace.server.net.io.nbt.NbtTags.thaw(org.domi.furnace.server.net.io.nbt.NbtTags.copy(tag)));
    }

    @Override
    public org.domi.furnace.api.nbt.NbtCompound blockEntityUpdateTagNow(Object blockEntity) {
        if (blockEntity instanceof org.domi.furnace.server.block.entity.BlockEntity be) return updateTagOf(be);
        if (blockEntity instanceof GeneratedBlockEntityRef) return null;   // 서버에 틀이 없는 종류 — 청크 패킷에도 싣지 않는다
        throw new IllegalArgumentException("not a block entity handle: " + blockEntity);
    }

    @Override
    public org.domi.furnace.api.nbt.NbtCompound detachedBlockEntityUpdateTag(int stateId, org.domi.furnace.api.nbt.NbtCompound tag) {
        checkState(stateId);
        var be = detachedBlockEntity(stateId, new Pos(0, 0, 0), tag);
        return be == null ? null : updateTagOf(be);
    }

    /** 갱신 패킷을 보내는 종류면 보내는 그대로의 태그(ChunkLevel 의 block_entity_data 와 같은 sentUpdateTag), 아니면 null. */
    private static org.domi.furnace.api.nbt.NbtCompound updateTagOf(org.domi.furnace.server.block.entity.BlockEntity be) {
        if (!be.hasUpdatePacket()) return null;
        var t = be.sentUpdateTag();
        return ApiNbt.toApi((org.domi.furnace.server.net.io.nbt.NbtCompound) org.domi.furnace.server.net.io.nbt.NbtTags.thaw(org.domi.furnace.server.net.io.nbt.NbtTags.copy(t)));
    }

    @Override
    public boolean blockEntitySetStateNow(org.domi.furnace.api.world.World world, Object blockEntity, int stateId) {
        if (!(blockEntity instanceof org.domi.furnace.server.block.entity.BlockEntity be)) return false;
        if (ownedLevel(world, be.pos().x(), be.pos().z(), "BlockEntity#setBlockState") == null || be.isRemoved()) return false;
        be.setBlockState(stateId);
        return true;
    }

    @Override
    public boolean blockEntityLoadNow(org.domi.furnace.api.world.World world, Object blockEntity, org.domi.furnace.api.nbt.NbtCompound tag) {
        java.util.Objects.requireNonNull(tag, "tag");
        if (blockEntity instanceof org.domi.furnace.server.block.entity.BlockEntity be) {
            RegionLevel lv = ownedLevel(world, be.pos().x(), be.pos().z(), "BlockEntity#loadWithComponents");
            if (lv == null) return false;
            be.loadWithComponentsInLevel(lv, (org.domi.furnace.server.net.io.nbt.NbtCompound) org.domi.furnace.server.net.io.nbt.NbtTags.freeze(ApiNbt.toServer(tag)));
            return true;
        }
        if (blockEntity instanceof GeneratedBlockEntityRef g) {
            // 서버가 해석하지 못하는 종류: 바닐라 loadAdditional 이 아는 키만 읽듯 고를 수 없어 실은 NBT 를 그대로 든다(id 는 그 종류 그대로)
            column(world, g.x() >> 4, g.z() >> 4, "BlockEntity#loadWithComponents");   // 소유 검사
            if (g.removed()) return false;
            var n = ApiNbt.toServer(tag);
            n.putString("id", (String) g.nbt().tags().get("id"));
            n.tags().remove("x");
            n.tags().remove("y");
            n.tags().remove("z");
            g.col.putGeneratedBlockEntity(g.x() & 15, g.y(), g.z() & 15, n);
            g.last = n;
            return true;
        }
        throw new IllegalArgumentException("not a block entity handle: " + blockEntity);
    }

    @Override
    public boolean blockEntityChangedNow(org.domi.furnace.api.world.World world, int x, int y, int z) {
        RegionLevel lv = ownedLevel(world, x, z, "BlockEntity#setChanged");
        if (lv == null) return false;
        Pos p = new Pos(x, y, z);
        lv.blockEntityChanged(p);
        int state = lv.getBlockState(p);
        if (!org.domi.furnace.server.block.B.isAir(state)) lv.updateNeighbourForOutputSignal(p, org.domi.furnace.server.block.B.block(state));
        return true;
    }

    // ------------------------------------------------------------------ 레벨 공통(T-211 3단계)

    @Override
    public List<String> enabledFeatureFlags() {
        List<String> out = new java.util.ArrayList<>();
        for (String f : org.domi.furnace.server.data.FeatureFlags.ALL) if (org.domi.furnace.server.data.FeatureFlags.isEnabled(f)) out.add(f);
        return out;
    }

    @Override
    public float[] currentDifficultyNow(org.domi.furnace.api.world.World world, int x, int y, int z) {
        RegionLevel lv = ownedLevel(world, x, z, "Level#getCurrentDifficultyAt");
        org.domi.furnace.server.entity.DifficultyInstance d;
        if (lv != null) {
            d = lv.getCurrentDifficultyAt(new Pos(x, y, z));
        } else {   // 바닐라 hasChunkAt 거짓: 머문 시간·달 밝기 0
            var services = handle(world).handle().levelServices();
            d = new org.domi.furnace.server.entity.DifficultyInstance(services.difficulty(), services.dayTime(), 0L, 0.0F,
                    org.domi.furnace.server.world.DifficultyModifiers.local());
        }
        return new float[] {d.difficulty(), d.effectiveDifficulty()};
    }

    @Override
    public boolean destroyBlockNow(org.domi.furnace.api.world.World world, int x, int y, int z, boolean drop, org.domi.furnace.api.entity.Entity breaker,
                                   int maxUpdateDepth) {
        RegionLevel lv = ownedLevel(world, x, z, "Level#destroyBlock");
        if (lv == null) return false;
        Pos p = new Pos(x, y, z);
        if (breaker == null) return lv.destroyBlock(p, drop, null, maxUpdateDepth);
        Object internal;
        try {
            internal = org.domi.furnace.api.Furnace.server().internals().entityNow(breaker, Object.class);
        } catch (org.domi.furnace.api.concurrent.ThreadOwnershipException e) {
            throw new IllegalArgumentException("the breaking entity is not owned by this thread: " + e.getMessage(), e);
        } catch (IllegalStateException e) {
            throw new IllegalArgumentException(e.getMessage(), e);   // 사라진 엔티티
        }
        if (internal instanceof org.domi.furnace.server.entity.Player pl) {
            return lv.destroyBlock(p, drop, new org.domi.furnace.server.block.PlayerActor(pl), maxUpdateDepth);
        }
        return lv.destroyBlockByEntity(p, drop, internal, maxUpdateDepth);
    }

    @Override
    public boolean clearBlockEntityContentNow(org.domi.furnace.api.world.World world, Object blockEntity) {
        if (blockEntity instanceof GeneratedBlockEntityRef g) {   // 아직 만들지 않은 생성 블록 엔티티: 바닐라 getBlockEntity 처럼 만든 뒤
            RegionLevel lv = ownedLevel(world, g.x(), g.z(), "Clearable#clearContent");
            if (lv == null || g.removed()) return false;
            blockEntity = lv.getBlockEntity(new Pos(g.x(), g.y(), g.z()));
        }
        if (!(blockEntity instanceof org.domi.furnace.server.block.entity.BlockEntity be)) return false;
        RegionLevel lv = ownedLevel(world, be.pos().x(), be.pos().z(), "Clearable#clearContent");
        if (lv == null) return false;
        return org.domi.furnace.server.block.entity.Clearables.tryClear(be, lv);
    }

    @Override
    public org.domi.furnace.api.nbt.NbtCompound entitySaveNow(org.domi.furnace.api.entity.Entity entity) {
        Object internal;
        try {
            internal = org.domi.furnace.api.Furnace.server().internals().entityNow(entity, Object.class);
        } catch (IllegalStateException gone) {
            return null;   // 사라진 엔티티: 바닐라도 지움 이유가 저장하지 않는 것(DISCARDED·KILLED)이면 거짓
        }
        // 플레이어는 저장하지 않는 종류(EntityType.PLAYER noSave → getEncodeId null)
        if (!(internal instanceof org.domi.furnace.server.entity.Entity e)) return null;
        var tag = e.save();
        if (tag == null) return null;
        return ApiNbt.toApi((org.domi.furnace.server.net.io.nbt.NbtCompound) org.domi.furnace.server.net.io.nbt.NbtTags.thaw(org.domi.furnace.server.net.io.nbt.NbtTags.copy(tag)));
    }

    @Override
    public Object entityEditNow(org.domi.furnace.api.entity.Entity entity, EntityEdit edit) {
        if (entity instanceof PlayerHandle ph) {
            if (!(edit instanceof EntityEdit.SaveWithoutId)) throw new IllegalArgumentException("not supported for a player: " + edit);
            var tag = org.domi.furnace.server.command.builtin.DataCommands.entityData(ph.handle());
            tag.tags().remove("SelectedItem");   // /data 비교용으로 붙이는 키 — 바닐라 Player#saveWithoutId 에는 없다
            return ApiNbt.toApi(tag);
        }
        var e = org.domi.furnace.api.Furnace.server().internals().entityNow(entity, org.domi.furnace.server.entity.Entity.class);
        switch (edit) {
            case EntityEdit.SaveWithoutId s -> {
                return ApiNbt.toApi(org.domi.furnace.server.command.builtin.DataCommands.entityData(e));
            }
            case EntityEdit.Load l -> {
                var tag = ApiNbt.toServer(l.tag());
                if (tag.tags().get("UUID") instanceof int[] u && u.length == 4
                        && !new java.util.UUID((long) u[0] << 32 | u[1] & 0xFFFFFFFFL, (long) u[2] << 32 | u[3] & 0xFFFFFFFFL).equals(e.uuid())) {
                    throw new IllegalArgumentException("the tag has another UUID - the UUID of an entity in a world is not changed");
                }
                org.domi.furnace.server.command.builtin.DataCommands.setEntityData(e, tag);   // 바닐라 load(같은 UUID 라 그대로)
            }
            case EntityEdit.SetPosRaw m -> e.setPosRaw(m.x(), m.y(), m.z());
            case EntityEdit.TeleportSameLevel t -> {
                e.teleportSameLevel(t.x(), t.y(), t.z(), t.yRot(), t.xRot());
                if (e instanceof org.domi.furnace.server.entity.LivingEntity le) le.setYHeadRot(t.yRot());
            }
            case EntityEdit.SetYHeadRot h -> {
                if (e instanceof org.domi.furnace.server.entity.LivingEntity le) le.setYHeadRot(h.yHeadRot());
            }
            case EntityEdit.StartRiding r -> {
                if (r.vehicle() instanceof PlayerHandle) throw new IllegalArgumentException("riding a player is not supported");
                var v = org.domi.furnace.api.Furnace.server().internals().entityNow(r.vehicle(), org.domi.furnace.server.entity.Entity.class);
                return e.startRiding(v, r.force());
            }
            case EntityEdit.StopRiding s -> e.stopRiding();
            case EntityEdit.EjectPassengers s -> e.ejectPassengers();
            case EntityEdit.ClearContent s -> {
                // 바닐라 ContainerEntity#clearChestVehicleContent: unpackChestVehicleLootTable(null) 뒤 칸 비우기
                if (!(e instanceof org.domi.furnace.server.entity.ContainerEntity ce)) throw new IllegalArgumentException("not a container entity: " + e);
                ce.unpackLootTable();
                java.util.Arrays.fill(ce.chestVehicleItems(), org.domi.furnace.server.entity.ItemStack.EMPTY);
            }
            case EntityEdit.HangingDirection s -> {
                return hanging(e).direction();
            }
            case EntityEdit.HangingRotate r -> {
                return hanging(e).rotate(r.rotation());
            }
            case EntityEdit.ItemFrameRotation s -> {
                return itemFrame(e).rotation();
            }
            case EntityEdit.SetItemFrameRotation r -> itemFrame(e).setRotation(r.rotation());
        }
        return null;
    }

    private static org.domi.furnace.server.entity.HangingEntity hanging(org.domi.furnace.server.entity.Entity e) {
        if (!(e instanceof org.domi.furnace.server.entity.HangingEntity h)) throw new IllegalArgumentException("not a hanging entity: " + e);
        return h;
    }

    private static org.domi.furnace.server.entity.ItemFrame itemFrame(org.domi.furnace.server.entity.Entity e) {
        if (!(e instanceof org.domi.furnace.server.entity.ItemFrame f)) throw new IllegalArgumentException("not an item frame: " + e);
        return f;
    }

    @Override
    public PlayerAbilities playerAbilities(org.domi.furnace.api.entity.Player player) {
        if (!(player instanceof PlayerHandle ph)) throw new IllegalArgumentException("not a server player: " + player);
        var a = ph.handle().vitals().abilitiesCopy();
        return new PlayerAbilities(a.invulnerable, a.flying, a.mayfly, a.instabuild, a.mayBuild, a.flyingSpeed, a.walkingSpeed);
    }

    @Override
    public void ackBlockChanges(org.domi.furnace.api.entity.Player player, int sequence) {
        if (sequence < 0) throw new IllegalArgumentException("Expected packet sequence nr >= 0");
        var p = serverPlayer(player, "ServerGamePacketListenerImpl#ackBlockChangesUpTo");
        var w = host.core().world(p.dimension());
        if (w == null) return;   // 월드를 옮기는 사이 — 그 플레이어의 블록 요청도 없다
        w.runAt(p.x(), p.z(), r -> r.level().ackBlockChangesUpTo(p, sequence));
    }

    @Override
    public int placeStepNow(org.domi.furnace.api.world.World world, int kind, int x, int y, int z, int state, org.domi.furnace.api.entity.Player placer,
                            org.domi.furnace.api.item.ItemStack item, boolean opData) {
        String what = switch (kind) {
            case PLACE_CUSTOM_DATA -> "BlockItem#updateCustomBlockEntityTag";
            case PLACE_APPLY_COMPONENTS -> "BlockEntity#applyComponentsFromItemStack";
            case PLACE_SET_PLACED_BY -> "Block#setPlacedBy";
            default -> throw new IllegalArgumentException("unknown place step " + kind);
        };
        RegionLevel lv = ownedLevel(world, x, z, what);
        if (lv == null) return -1;
        var w = handle(world).handle();
        var stack = ApiConvert.item(item, w.itemRegistry());
        Pos p = new Pos(x, y, z);
        switch (kind) {
            case PLACE_CUSTOM_DATA -> {
                // 바닐라 updateCustomBlockEntityTag(서버 BlockPlacement 와 같은 판정 — 관리자 판정만 부르는 쪽이 Paper 규칙으로 넘긴다)
                if (!(org.domi.furnace.server.item.ItemTools.component(stack, "minecraft:block_entity_data")
                        instanceof org.domi.furnace.server.net.io.nbt.NbtCompound data) || data.tags().isEmpty()) return 0;
                if (!(lv.getBlockEntity(p) instanceof org.domi.furnace.server.block.entity.BlockEntity be)) return 0;
                if (be.onlyOpCanSetNbt() && !opData) return 0;
                return be.loadCustomDataInto(lv, data) ? 1 : 0;
            }
            case PLACE_APPLY_COMPONENTS -> {
                if (lv.getBlockEntity(p) instanceof org.domi.furnace.server.block.entity.BlockEntity be) {
                    be.applyComponentsFromItemStack(stack);
                    // 바닐라는 applyImplicitComponents 안(머리 SkullBlockEntity.setOwner)에서 이름만 있는 프로필 확인을 시작한다 — 서버 이식은 그 일을
                    // onAddedToLevel 에 두었으므로 놓기 길(BlockPlacement.updateBlockEntityComponents)처럼 이어 부른다. 저장 표시는 바닐라처럼 하지 않는다
                    be.onAddedToLevel(lv);
                }
                return 0;
            }
            default -> {
                checkState(state);
                org.domi.furnace.server.block.BlockActor actor = null;
                if (placer != null) {
                    if (!(placer instanceof PlayerHandle ph)) throw new IllegalArgumentException(what + ": not a server player: " + placer);
                    var pl = ph.handle();
                    // setPlacedBy 는 넘긴 아이템을 쓴다 — 손은 비운 행위자(위치·시선·권한은 그 플레이어)
                    actor = new org.domi.furnace.server.block.PlayerActor(pl, org.domi.furnace.server.entity.ItemStack.EMPTY,
                            org.domi.furnace.server.entity.ItemStack.EMPTY, host.core().profilePermissions(org.domi.furnace.server.ServerCore.profileOf(pl)));
                }
                org.domi.furnace.server.block.BlockBehaviors.of(state).setPlacedBy(lv, p, state, actor, stack);
                return 0;
            }
        }
    }

    @Override
    public boolean underSpawnProtection(org.domi.furnace.api.world.World world, org.domi.furnace.api.entity.Player player, int x, int z) {
        if (!(player instanceof PlayerHandle ph)) throw new IllegalArgumentException("not a server player: " + player);
        return host.core().isUnderSpawnProtection(world.dimension(), x, z, ph.handle());
    }

    @Override
    public void setPlayerAbilities(org.domi.furnace.api.entity.Player player, PlayerAbilities v, boolean send) {
        if (!(player instanceof PlayerHandle ph)) throw new IllegalArgumentException("not a server player: " + player);
        var a = new org.domi.furnace.server.entity.Abilities();
        a.invulnerable = v.invulnerable();
        a.flying = v.flying();
        a.mayfly = v.mayfly();
        a.instabuild = v.instabuild();
        a.mayBuild = v.mayBuild();
        a.flyingSpeed = v.flyingSpeed();
        a.walkingSpeed = v.walkingSpeed();
        ph.handle().vitals().setAbilities(a, send);
    }

    @Override
    public org.domi.furnace.api.nbt.NbtCompound entitySaveAsPassengerNow(org.domi.furnace.api.entity.Entity entity) {
        Object internal;
        try {
            internal = org.domi.furnace.api.Furnace.server().internals().entityNow(entity, Object.class);
        } catch (IllegalStateException gone) {
            return null;
        }
        if (!(internal instanceof org.domi.furnace.server.entity.Entity e)) return null;   // 플레이어는 저장하지 않는 종류
        var tag = e.saveAsPassenger();
        if (tag == null) return null;
        return ApiNbt.toApi((org.domi.furnace.server.net.io.nbt.NbtCompound) org.domi.furnace.server.net.io.nbt.NbtTags.thaw(org.domi.furnace.server.net.io.nbt.NbtTags.copy(tag)));
    }

    @Override
    public void entityRemoveNow(org.domi.furnace.api.entity.Entity entity, String reason) {
        if (entity instanceof org.domi.furnace.api.entity.Player) throw new IllegalArgumentException("a player cannot be removed this way: " + entity.uniqueId());
        var why = org.domi.furnace.server.entity.Entity.RemovalReason.valueOf(reason);
        Object internal;
        try {
            internal = org.domi.furnace.api.Furnace.server().internals().entityNow(entity, Object.class);
        } catch (IllegalStateException gone) {
            return;
        }
        if (internal instanceof org.domi.furnace.server.entity.Entity e) e.remove(why);
    }

    @Override
    public void entityDiscardNow(org.domi.furnace.api.entity.Entity entity) {
        if (entity instanceof org.domi.furnace.api.entity.Player) throw new IllegalArgumentException("a player cannot be discarded: " + entity.uniqueId());
        Object internal;
        try {
            internal = org.domi.furnace.api.Furnace.server().internals().entityNow(entity, Object.class);
        } catch (IllegalStateException gone) {
            return;   // 이미 지워졌다 — 바닐라도 다시 지우면 아무 일 없다
        }
        if (internal instanceof org.domi.furnace.server.entity.Entity e) e.discard();
    }

    @Override
    public NmsEntityAdd addNmsEntityNow(org.domi.furnace.api.world.World world, int x, int z, String id, List<EntityOp> ops,
                                        String spawnReason, boolean fireEvents) {
        RegionLevel lv = ownedLevel(world, x, z, "ServerLevel#addFreshEntity");
        if (lv == null) return NmsEntityAdd.REFUSED;   // 청크가 로드되지 않았다
        var e = createNmsEntity(lv, id, ops);
        if (ownedLevel(world, (int) Math.floor(e.x()), (int) Math.floor(e.z()), "ServerLevel#addFreshEntity") != lv) {
            throw new org.domi.furnace.api.concurrent.ThreadOwnershipException("ServerLevel#addFreshEntity: the entity ended at " + e.x() + ", " + e.z()
                    + " but was routed to the region of " + x + ", " + z);
        }
        if (spawnReason != null) e.spawnReason(spawnReason);
        // Paper ServerLevel#addEntity: 빈 아이템 엔티티는 넣지 않는다(이벤트 전에 거짓, 지우지 않음)
        if (fireEvents && e instanceof org.domi.furnace.server.entity.ItemEntity ie && ie.getItem().isEmpty()) return NmsEntityAdd.REFUSED;
        int r = lv.addFreshEntityResult(e, fireEvents);
        if (r == org.domi.furnace.server.block.ChunkLevel.ADDED) return new NmsEntityAdd(EntityHandle.of(host, e), false, false);
        // Paper doEntityAddEventCalling: 이벤트를 취소했거나 처리기가 지웠으면 그 엔티티를 지운다. UUID 겹침은 지우지 않는다
        return new NmsEntityAdd(null, r == org.domi.furnace.server.block.ChunkLevel.ADD_CANCELLED, r == org.domi.furnace.server.block.ChunkLevel.ADD_DUPLICATE);
    }

    /** NMS 로 만든 엔티티 하나: 서버 엔티티를 만들고 기록한 조작을 차례로 재생한다. */
    private static org.domi.furnace.server.entity.Entity createNmsEntity(RegionLevel lv, String id, List<EntityOp> ops) {
        var e = org.domi.furnace.server.entity.Entities.create(lv, id);
        if (e == null) throw new IllegalArgumentException("entity type that cannot be created: " + id);
        for (EntityOp op : ops) {
            switch (op) {
                case EntityOp.Load l -> e.load((org.domi.furnace.server.net.io.nbt.NbtCompound) org.domi.furnace.server.net.io.nbt.NbtTags.freeze(ApiNbt.toServer(l.tag())));
                case EntityOp.AbsMoveTo m -> e.absMoveTo(m.x(), m.y(), m.z(), m.yRot(), m.xRot());
                case EntityOp.SetUuid u -> e.setUuid(u.uuid());
                case EntityOp.SetYHeadRot h -> {
                    if (e instanceof org.domi.furnace.server.entity.LivingEntity le) le.setYHeadRot(h.yHeadRot());
                }
                case EntityOp.HangingRotate r -> hanging(e).rotate(r.rotation());
                case EntityOp.SetItemFrameRotation r -> itemFrame(e).setRotation(r.rotation());
            }
        }
        return e;
    }

    @Override
    public NmsGroupAdd addNmsEntityWithPassengersNow(org.domi.furnace.api.world.World world, int x, int z, NmsEntitySpec root,
                                                                                  String spawnReason) {
        int count = countSpecs(root);
        List<org.domi.furnace.api.entity.Entity> out = new java.util.ArrayList<>(java.util.Collections.nCopies(count, null));
        RegionLevel lv = ownedLevel(world, x, z, "ServerLevel#tryAddFreshEntityWithPassengers");
        if (lv == null) return new NmsGroupAdd(out, GroupResult.NOT_LOADED);   // 청크가 로드되지 않았다
        List<org.domi.furnace.server.entity.Entity> made = new java.util.ArrayList<>(java.util.Collections.nCopies(count, null));
        var e = buildNms(lv, root, made, new int[1]);
        e.reapplyPosition();   // 바닐라는 첫 틱에 탈것이 승객을 제자리에 놓는다. 먼 Pos 로 저장된 승객이 다른 region 에 걸리지 않게 먼저 놓는다
        if (ownedLevel(world, (int) Math.floor(e.x()), (int) Math.floor(e.z()), "ServerLevel#tryAddFreshEntityWithPassengers") != lv) {
            throw new org.domi.furnace.api.concurrent.ThreadOwnershipException("ServerLevel#tryAddFreshEntityWithPassengers: the entity ended at " + e.x() + ", "
                    + e.z() + " but was routed to the region of " + x + ", " + z);
        }
        if (spawnReason != null) for (var m : made) if (m != null) m.spawnReason(spawnReason);
        int added = lv.addFreshEntityGroupResult(e);
        if (added != org.domi.furnace.server.block.ChunkLevel.ADDED) {
            return new NmsGroupAdd(out, added == org.domi.furnace.server.block.ChunkLevel.ADD_DUPLICATE ? GroupResult.UUID_TAKEN : GroupResult.CANCELLED);
        }
        for (int i = 0; i < count; i++) {
            var m = made.get(i);
            // 태우지 못한 승객은 넣지 않았다(바닐라도 그 승객은 월드에 들지 않는다)
            if (lv.entities().contains(m)) out.set(i, EntityHandle.of(host, m));
        }
        return new NmsGroupAdd(out, GroupResult.ADDED);
    }

    private static int countSpecs(NmsEntitySpec s) {
        int n = 1;
        for (var p : s.passengers()) n += countSpecs(p);
        return n;
    }

    /** 바닐라 loadEntityRecursive 의 뒷부분: 승객을 깊이 우선으로 만들어 강제로 태운다. made 의 자리는 spec 의 깊이 우선 차례. */
    private static org.domi.furnace.server.entity.Entity buildNms(RegionLevel lv, NmsEntitySpec spec, List<org.domi.furnace.server.entity.Entity> made, int[] next) {
        int at = next[0]++;
        var e = createNmsEntity(lv, spec.id(), spec.ops());
        made.set(at, e);
        // 못 타면 그 승객은 함께 넣기에 들지 않는다. 아직 월드 밖이라 탑승 이벤트는 없다(Paper 와 같다 — 넣을 때 스폰 이벤트만)
        for (var ps : spec.passengers()) buildNms(lv, ps, made, next).startRidingBeforeAdd(e);
        return e;
    }

    @Override
    public org.domi.furnace.api.entity.Player detachedPlayer(org.domi.furnace.api.world.World world, java.util.UUID uuid, String name,
                                                             List<org.domi.furnace.api.entity.ProfileProperty> properties) {
        var w = handle(world).handle();
        var props = new java.util.ArrayList<org.domi.furnace.server.players.GameProfile.Property>(properties.size());
        for (var q : properties) props.add(new org.domi.furnace.server.players.GameProfile.Property(q.name(), q.value(), q.signature()));
        var p = org.domi.furnace.server.entity.Player.detached(w.entityIds().next(), java.util.Objects.requireNonNull(uuid, "uuid"),
                java.util.Objects.requireNonNull(name, "name"), props, w.dimension());
        var spawn = w.defaultSpawn();   // 바닐라 Player(level, sharedSpawnPos, sharedSpawnAngle, profile) → moveTo(x+0.5, y+1, z+0.5, angle, 0)
        p.moveDetached(spawn.x() + 0.5, spawn.y() + 1, spawn.z() + 0.5, spawn.yaw(), 0.0F);
        return host.player(p);
    }

    @Override
    public void moveDetachedPlayer(org.domi.furnace.api.entity.Player player, double x, double y, double z, float yRot, float xRot) {
        if (!(player instanceof PlayerHandle ph) || !ph.handle().isDetached()) {
            throw new IllegalArgumentException("not a detached player made by detachedPlayer: " + player);
        }
        ph.handle().moveDetached(x, y, z, yRot, xRot);
    }

    @Override
    public void sendSystemMessage(org.domi.furnace.api.entity.Player player, org.domi.furnace.api.text.Text message, boolean overlay) {
        if (!(player instanceof PlayerHandle ph)) throw new IllegalArgumentException("not a server player: " + player);
        ph.handle().sendSystemMessage(message, overlay);
    }

    @Override
    public BlockDestroy destroyBlockAsPlayer(org.domi.furnace.api.world.World world, org.domi.furnace.api.entity.Player player, int x, int y, int z,
                                             org.domi.furnace.api.item.ItemStack main, org.domi.furnace.api.item.ItemStack off) {
        String what = "ServerPlayerGameMode#destroyBlock";
        if (!(player instanceof PlayerHandle ph)) throw new IllegalArgumentException("not a server player: " + player);
        Region r = ownedLoaded(world, x >> 4, z >> 4, what);
        if (r == null || !r.chunkLoaded(new ChunkPos(x >> 4, z >> 4))) {
            throw new IllegalStateException(what + ": the chunk at " + x + ", " + z + " is not loaded");
        }
        var p = ph.handle();
        Pos pos = new Pos(x, y, z);
        if (!p.isDetached()) return new BlockDestroy(r.destroyBlockAsPlayer(p, pos), null, null);
        // 월드에 없는 플레이어(T-212): 넘겨 받은 두 손으로, 접속한 플레이어와 같은 블록 부수기 이벤트(Paper 처럼 — 결정 S2)
        RegionLevel lv = r.level();
        var w = handle(world).handle();
        var core = host.core();
        var items = w.itemRegistry();
        var actor = new org.domi.furnace.server.block.BreakerActor(p, ApiConvert.item(main, items), ApiConvert.item(off, items), null,
                core.profilePermissions(org.domi.furnace.server.ServerCore.profileOf(p)), false, -1);
        var breakHost = new org.domi.furnace.server.block.BlockBreaking.Host() {
            public void sendBlockUpdate(org.domi.furnace.server.block.BlockLevel level, Pos at) {}   // 연결이 없다

            public boolean allowBreak(org.domi.furnace.server.block.BlockLevel level, Pos at, int state) {
                var bus = core.events();
                if (!bus.hasSubscribers(org.domi.furnace.api.event.block.BlockBreakEvent.class)) return true;
                return !bus.call(new org.domi.furnace.api.event.block.BlockBreakEvent(p.dimension(), new BlockPos(at.x(), at.y(), at.z()),
                        w.blockRegistry().nameOf(state), host.player(p))).isCancelled();
            }

            public boolean owns(Pos at) { return ownsChunk(world, at.x() >> 4, at.z() >> 4); }
        };
        if (!p.beginDetachedUse()) {   // useItemNow 와 같다 — 같은 플레이어를 두 region 이 동시에 쓰지 않는다
            throw new IllegalStateException(what + ": the player " + p.name() + " is already used on another thread");
        }
        boolean destroyed;
        p.currentLevel = lv;
        try {
            destroyed = p.breaking().destroyBlock(lv, actor, breakHost, pos);
        } finally {
            p.currentLevel = null;
            p.endDetachedUse();
        }
        return new BlockDestroy(destroyed, ApiConvert.item(actor.itemInHand(0), items), ApiConvert.item(actor.itemInHand(1), items));
    }

    private static org.domi.furnace.server.entity.Player serverPlayer(org.domi.furnace.api.entity.Player player, String what) {
        if (!(player instanceof PlayerHandle ph)) throw new IllegalArgumentException(what + ": not a server player: " + player);
        var p = ph.handle();
        if (p.isDetached() || p.inventoryHost() == null) throw new IllegalArgumentException(what + ": the player is not connected: " + p.name());
        return p;
    }

    /** 서버 스택 → API 값(세계·레지스트리 없이, 깔린 아이템 문맥의 이름으로). */
    private static org.domi.furnace.api.item.ItemStack apiItem(org.domi.furnace.server.entity.ItemStack s) {
        if (s == null || s.isEmpty()) return org.domi.furnace.api.item.ItemStack.EMPTY;
        var form = s.patch().apiForm();
        return new org.domi.furnace.api.item.ItemStack(org.domi.furnace.server.item.ItemTools.itemName(s), s.count(), form.components(), form.removed());
    }

    @Override
    public HandSlot handSlotNow(org.domi.furnace.api.entity.Player player, boolean mainHand) {
        var p = serverPlayer(player, "getMainHandItem");
        var inv = p.inventoryHost();
        int ws = mainHand ? 36 + inv.selected() : 45;
        return new HandSlot(ws, apiItem(inv.slot(ws)));
    }

    @Override
    public org.domi.furnace.api.item.ItemStack windowSlotNow(org.domi.furnace.api.entity.Player player, int windowSlot) {
        return apiItem(serverPlayer(player, "getItemBySlot").inventoryHost().slot(windowSlot));
    }

    @Override
    public boolean replaceSlotNow(org.domi.furnace.api.entity.Player player, int windowSlot, org.domi.furnace.api.item.ItemStack expect,
                                  org.domi.furnace.api.item.ItemStack now) {
        var p = serverPlayer(player, "ItemStack#set");
        var inv = p.inventory();
        var next = ApiConvert.item(now);
        synchronized (inv) {   // PlayerInventory 의 get·replace 와 같은 잠금 — 읽기와 교체 사이에 다른 쓰기가 끼지 않는다
            if (!apiItem(inv.get(windowSlot)).equals(expect)) return false;
            inv.replace(windowSlot, inv.get(windowSlot), next);
        }
        p.inventoryHost().inventoryChanged(windowSlot);
        return true;
    }

    @Override
    public FoodState foodNow(org.domi.furnace.api.entity.Player player) {
        var v = serverPlayer0(player).vitals();
        return new FoodState(v.foodLevel(), v.saturation(), v.exhaustion());
    }

    @Override
    public void setFoodFieldNow(org.domi.furnace.api.entity.Player player, int field, float value) {
        serverPlayer0(player).vitals().setFoodField(field, value);
    }

    @Override
    public void addExhaustionNow(org.domi.furnace.api.entity.Player player, float amount) {
        serverPlayer0(player).vitals().addExhaustion(amount);
    }

    @Override
    public void causeFoodExhaustionNow(org.domi.furnace.api.entity.Player player, float amount) {
        serverPlayer0(player).vitals().causeFoodExhaustion(amount);
    }

    private static org.domi.furnace.server.entity.Player serverPlayer0(org.domi.furnace.api.entity.Player player) {
        if (!(player instanceof PlayerHandle ph)) throw new IllegalArgumentException("not a server player: " + player);
        return ph.handle();
    }

    @Override
    public boolean correctToolForDrops(org.domi.furnace.api.item.ItemStack tool, int stateId) {
        if (tool == null || tool.isEmpty() || stateId < 0 || stateId >= org.domi.furnace.server.block.Blocks.get().stateCount()) return false;
        return org.domi.furnace.server.item.ItemTools.isCorrectToolForDrops(ApiConvert.item(tool), stateId);
    }

    @Override
    public boolean stateRequiresCorrectTool(int stateId) {
        if (stateId < 0 || stateId >= org.domi.furnace.server.block.Blocks.get().stateCount()) return false;
        var blocks = org.domi.furnace.server.block.Blocks.get();
        return org.domi.furnace.server.block.BlockInfo.get().requiresCorrectToolForDrops(blocks.blockOf(stateId));
    }

    @Override
    public int expDropNow(org.domi.furnace.api.world.World world, int state, int x, int y, int z, org.domi.furnace.api.item.ItemStack tool) {
        RegionLevel lv = ownedLevel(world, x, z, "Block#getExpDrop");
        if (lv == null) throw new IllegalStateException("Block#getExpDrop: the chunk at " + x + ", " + z + " is not loaded");
        var items = handle(world).handle().itemRegistry();
        return org.domi.furnace.server.block.BlockBehaviors.of(state).expDrop(state, lv, new Pos(x, y, z), ApiConvert.item(tool, items));
    }

    @Override
    public ItemUse useItemNow(org.domi.furnace.api.world.World world, org.domi.furnace.api.entity.Player player, ItemUseKind kind, boolean mainHand,
                              org.domi.furnace.api.item.ItemStack main, org.domi.furnace.api.item.ItemStack off, org.domi.furnace.api.item.ItemStack stack,
                              int state, int x, int y, int z, int face, double hitX, double hitY, double hitZ, boolean inside) {
        if (!(player instanceof PlayerHandle ph) || !ph.handle().isDetached()) {
            throw new IllegalArgumentException("not a detached player made by detachedPlayer: " + player);
        }
        String what = switch (kind) {
            case USE_ON -> "ItemStack#useOn";
            case USE_ITEM_ON -> "BlockState#useItemOn";
            case USE -> "Item#use";
        };
        RegionLevel lv = ownedLevel(world, x, z, what);
        if (lv == null) throw new IllegalStateException(what + ": the chunk at " + x + ", " + z + " is not loaded");
        var p = ph.handle();
        var items = handle(world).handle().itemRegistry();
        var core = host.core();
        var events = core.gameEvents();
        int hand = mainHand ? org.domi.furnace.server.block.BlockActor.MAIN_HAND : org.domi.furnace.server.block.BlockActor.OFF_HAND;
        final var before = ApiConvert.item(mainHand ? main : off);
        // 접속한 플레이어의 블록 놓기와 같은 이벤트(ConnectionHandler#handleUseItemOn 의 allowPlace) — Paper ItemStack#useOn 의 BlockPlaceEvent
        var actor = new org.domi.furnace.server.block.PlayerActor(p, ApiConvert.item(main), ApiConvert.item(off),
                core.profilePermissions(org.domi.furnace.server.ServerCore.profileOf(p))) {
            @Override
            public boolean allowPlace(Pos at, int state, Pos against) {
                if (!events.wantsBlockPlace()) return true;
                return events.blockPlace(p, at, state, lv.getBlockState(at), against, before, mainHand);
            }
        };
        var pos = new Pos(x, y, z);
        var hit = new org.domi.furnace.server.block.Hit(pos, face, hitX, hitY, hitZ, inside);
        String result;
        org.domi.furnace.api.item.ItemStack used = null;
        if (!p.beginDetachedUse()) {   // 같은 플레이어를 두 region 이 동시에 쓰면 그 레벨(currentLevel)이 남의 스레드로 샌다
            throw new IllegalStateException(what + ": the player " + p.name() + " is already using an item on another thread");
        }
        p.currentLevel = lv;   // 쓰는 동안만 이 레벨에 있다(게임 이벤트·소리가 그 자리에서 난다 — 바닐라는 가짜 플레이어도 레벨을 가진다)
        try {
            switch (kind) {
                case USE_ON -> result = org.domi.furnace.server.block.BlockPlacement.useOn(lv, actor, hand, hit).name();
                case USE_ITEM_ON -> {   // 바닐라 BlockStateBase#useItemOn: 그 상태 객체의 블록으로(칸의 지금 상태가 아니라)
                    result = org.domi.furnace.server.block.BlockBehaviors.of(state).useItemOn(ApiConvert.item(stack), state, lv, pos, actor, hand, hit).name();
                }
                default -> {
                    var s = actor.itemInHand(hand);
                    if (org.domi.furnace.server.item.ItemTools.itemName(s).equals("minecraft:fishing_rod")) {   // 찌가 월드에 없는 플레이어에 묶여 남는다(설명서 6.6)
                        throw new UnsupportedOperationException("Item#use of " + org.domi.furnace.server.item.ItemTools.itemName(s)
                                + " is not available for a player that is not in a world");
                    }
                    String ranged = org.domi.furnace.server.entity.RangedWeapons.useResult(lv, actor, p, hand);   // 활·쇠뇌·삼지창
                    if (ranged != null) {
                        result = ranged;
                        used = ApiConvert.item(actor.itemInHand(hand), items);   // 바닐라: 손의 그 스택(쇠뇌는 쏘며 고친 것)
                    } else if (org.domi.furnace.server.entity.Consuming.tryStart(p, hand, s)) {   // 먹기·마시기·방패·망원경: startUsingItem → consume
                        result = "CONSUME";
                        used = ApiConvert.item(s, items);
                    } else if (org.domi.furnace.server.item.ItemTools.component(s, "minecraft:food") != null) {   // Item#use: 못 먹으면 fail
                        result = "FAIL";
                        used = ApiConvert.item(s, items);
                    } else {
                        var h = org.domi.furnace.server.block.ItemUse.use(lv, actor, hand, s);
                        result = h.result().name();
                        used = ApiConvert.item(h.stack(), items);
                    }
                }
            }
        } finally {
            p.currentLevel = null;
            p.endDetachedUse();
        }
        return new ItemUse(result, used, ApiConvert.item(actor.itemInHand(0), items), ApiConvert.item(actor.itemInHand(1), items));
    }

    /** 명령 범위 안에서 넣는다 — 주인이 쉬고 있으면 drain 차례로 곧바로 돈다(CommandDrainGate: 같은 예산·같은 끄기 옵션). */
    @Override
    public <T> java.util.concurrent.CompletableFuture<T> submitAwaited(org.domi.furnace.api.world.World world, int x, int y, int z, java.util.function.Supplier<T> task) {
        org.domi.furnace.server.world.CommandDrainGate.enterScope();
        try {
            return world.scheduler(new BlockPos(x, y, z)).submit(task);
        } finally {
            org.domi.furnace.server.world.CommandDrainGate.exitScope();
        }
    }

    /** 월드에 붙지 않은 서버 블록 엔티티(그 상태의 newBlockEntity + 저장 꼴 싣기). 블록 엔티티가 없는 상태면 null. */
    static org.domi.furnace.server.block.entity.BlockEntity detachedBlockEntity(int stateId, Pos pos, org.domi.furnace.api.nbt.NbtCompound tag) {
        String name = org.domi.furnace.server.block.Blocks.get().nameOfState(stateId);
        org.domi.furnace.server.block.entity.BlockEntity be = switch (name) {
            case "minecraft:chest", "minecraft:trapped_chest" -> org.domi.furnace.server.world.ChestBlockEntity.empty(name, pos.x(), pos.y(), pos.z());
            default -> org.domi.furnace.server.block.BlockBehaviors.of(stateId).newBlockEntity(pos, stateId);
        };
        if (be == null) return null;
        var t = (org.domi.furnace.server.net.io.nbt.NbtCompound) org.domi.furnace.server.net.io.nbt.NbtTags.freeze(ApiNbt.toServer(tag));
        be.loadWithBukkitValues(t);
        be.loadComponents(t);
        return be;
    }

    @Override
    public boolean ultraWarm(org.domi.furnace.api.world.World world) { return handle(world).handle().dimensionType().ultraWarm(); }

    @Override
    public boolean hasCeiling(org.domi.furnace.api.world.World world) { return handle(world).handle().dimensionType().hasCeiling(); }

    @Override
    public double[] borderCenterAndSize(org.domi.furnace.api.world.World world) {
        var b = handle(world).handle().border();
        synchronized (b) { return new double[] {b.getCenterX(), b.getCenterZ(), b.getSize()}; }
    }

    @Override
    public MapInfo mapInfo(int mapId) {
        var d = org.domi.furnace.server.map.MapStore.get(mapId);
        return d == null ? null : new MapInfo(d.centerX(), d.centerZ(), d.dimension(), d.scale());
    }

    @Override
    public BiomeInfo biomeInfo(String biomeKey) {
        if (biomeKey == null) return null;
        var store = host.pluginsImpl().gameRegistries();
        var custom = store == null ? null : store.biome(biomeKey);
        if (custom != null) {
            var d = custom.definition();
            return new BiomeInfo(d.hasPrecipitation(), d.temperature(), d.temperatureModifier() == org.domi.furnace.api.worldgen.BiomeDefinition.TemperatureModifier.FROZEN,
                    d.downfall(), d.fogColor(), d.waterColor(), d.waterFogColor(), d.skyColor(), d.foliageColor().orElse(-1), d.grassColor().orElse(-1),
                    d.grassColorModifier().key());
        }
        try {
            var b = org.domi.furnace.server.world.gen.WorldgenData.load("worldgen/" + org.domi.furnace.api.GameVersion.RESOURCE_TAG).biome(biomeKey);
            var fx = b.has("effects") ? b.getAsJsonObject("effects") : new com.google.gson.JsonObject();
            return new BiomeInfo(!b.has("has_precipitation") || b.get("has_precipitation").getAsBoolean(), b.has("temperature") ? b.get("temperature").getAsFloat() : 0.5F,
                    b.has("temperature_modifier") && "frozen".equals(b.get("temperature_modifier").getAsString()), b.has("downfall") ? b.get("downfall").getAsFloat() : 0.5F,
                    intOr(fx, "fog_color"), intOr(fx, "water_color"), intOr(fx, "water_fog_color"), intOr(fx, "sky_color"), intOr(fx, "foliage_color"), intOr(fx, "grass_color"),
                    fx.has("grass_color_modifier") ? fx.get("grass_color_modifier").getAsString() : "none");
        } catch (RuntimeException unknown) {
            return null;
        }
    }

    private static int intOr(com.google.gson.JsonObject o, String key) { return o.has(key) ? o.get(key).getAsInt() : -1; }

    @Override
    public double biomeInfoNoise(double x, double z) { return org.domi.furnace.server.world.gen.BiomeClimate.biomeInfoNoise(x, z); }

    @Override
    public boolean withinBorder(org.domi.furnace.api.world.World world, double x, double z) { return handle(world).handle().border().isWithinBounds(x, z); }

    @Override
    public int stateForPlacement(int block, PlacementContext ctx) {
        checkState(block);
        return PlacementBridge.stateForPlacement(block, ctx);
    }

    @Override
    public boolean canBeReplaced(int state, PlacementContext ctx) {
        checkState(state);
        return PlacementBridge.canBeReplaced(state, ctx);
    }

    @Override
    public boolean canSurvive(int state, PlacementLevel level, int x, int y, int z) {
        checkState(state);
        return PlacementBridge.canSurvive(state, level, x, y, z);
    }

    @Override
    public org.domi.furnace.api.nbt.NbtCompound normalizeBlockEntityNbt(int stateId, org.domi.furnace.api.nbt.NbtCompound tag) {
        checkState(stateId);
        var be = detachedBlockEntity(stateId, new Pos(0, 0, 0), tag);
        if (be == null) return null;
        var out = be.saveWithoutMetadata();
        out.putString("id", be.type());
        return ApiNbt.toApi((org.domi.furnace.server.net.io.nbt.NbtCompound) org.domi.furnace.server.net.io.nbt.NbtTags.thaw(org.domi.furnace.server.net.io.nbt.NbtTags.copy(out)));
    }

    // ------------------------------------------------------------------ 지역 파일(T-247)

    @Override
    public RegionFileAccess openRegionFile(java.nio.file.Path path, boolean sync) throws java.io.IOException {
        var file = new org.domi.furnace.server.world.anvil.RegionFile(path, sync);
        return new RegionFileAccess() {
            @Override
            public byte[] read(int chunkX, int chunkZ) throws java.io.IOException { return file.readChunk(chunkX, chunkZ); }

            @Override
            public void write(int chunkX, int chunkZ, byte[] data, int compression) throws java.io.IOException {
                file.writeChunk(chunkX, chunkZ, data, compression);
            }

            @Override
            public boolean hasChunk(int chunkX, int chunkZ) { return file.hasChunk(chunkX, chunkZ); }

            @Override
            public void flush() throws java.io.IOException { file.flush(); }

            @Override
            public void close() throws java.io.IOException { file.close(); }
        };
    }

    @Override
    public int regionFileCompression() { return org.domi.furnace.server.world.anvil.Compression.writeType(); }

    @Override
    public boolean syncChunkWrites() {
        var s = host.core().settings();
        return s != null && s.get().syncChunkWrites;   // server.properties 가 없는 시험 서버는 기본값(거짓)
    }

    // ------------------------------------------------------------------ 엔티티 모습 읽기(NMS shim 5번 묶음)

    @Override
    public EntityLook entityLook(org.domi.furnace.api.entity.Entity entity) {
        if (entity instanceof PlayerHandle ph) {
            var p = ph.handle();
            double[] k = knownMovement(p);
            var v = p.vehicle();
            return new EntityLook(p.sharedFlags(), p.pose(), k[0], k[1], k[2], p.vitals().isFlying(),
                    p.isSpectator(), host.core().profilePermissions(org.domi.furnace.server.ServerCore.profileOf(p)), v == null ? null : v.uuid(), List.of());
        }
        var e = org.domi.furnace.api.Furnace.server().internals().entityNow(entity, org.domi.furnace.server.entity.Entity.class);
        double[] k = knownMovement(e);
        var v = e.vehicle();
        List<java.util.UUID> passengers = new java.util.ArrayList<>(e.passengers().size());
        for (var r : e.passengers()) passengers.add(uuidOf(r));
        return new EntityLook(e.sharedFlagsByte(), e.getPose(), k[0], k[1], k[2], false, false, 0, v == null ? null : v.uuid(), List.copyOf(passengers));
    }

    private static java.util.UUID uuidOf(org.domi.furnace.server.entity.Rider r) {
        return r instanceof org.domi.furnace.server.entity.Player p ? p.uuid() : ((org.domi.furnace.server.entity.Entity) r).uuid();
    }

    /**
     * 바닐라 getKnownMovement. 플레이어(ServerPlayer): 남이 조종하는 탈것에 탔으면 그 탈것의 것, 아니면 클라이언트가 마지막으로 알린 움직임.
     * 다른 엔티티(Entity): 살아 있고 조종하는 승객이 플레이어면 그 플레이어의 것, 아니면 지금 속도.
     */
    private static double[] knownMovement(org.domi.furnace.server.entity.Rider r) {
        if (r instanceof org.domi.furnace.server.entity.Player p) {
            var v = p.vehicle();
            return v != null && v.getControllingPassenger() != p ? knownMovement(v) : p.knownMovement();
        }
        var e = (org.domi.furnace.server.entity.Entity) r;
        if (e.getControllingPassenger() instanceof org.domi.furnace.server.entity.Player cp && e.isAlive()) return knownMovement(cp);
        return new double[] {e.vx(), e.vy(), e.vz()};
    }
}
