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
import org.domi.furnace.api.internal.ChunkInternals;
import org.domi.furnace.api.world.World;
import org.domi.furnace.compat.nms.NmsChunks;

/**
 * 청크 shim 의 바탕(Mojang 이름, N2a): 로드된 서버 청크의 <b>보기</b>. 블록 값을 들고 있지 않고 읽을 때마다 서버 청크를 본다 —
 * 그 청크를 가진 region 스레드(이벤트 처리기·entityScheduler·전역 빌림 구간)에서는 살아 있는 값을, 다른 스레드(비동기 작업·플러그인 일꾼·
 * 다른 region — 결정 D4)에서는 주인이 틱 끝에 낸 <b>공개 스냅샷</b>을 기다리지 않고 읽는다(N2b-3, 설계 3.6 — 최대 한 틱 옛 값, Paper 의 경쟁 읽기와
 * 같은 성질). 한 번의 읽기 메서드 안의 값은 한 판에서 나온다. 다른 스레드의 쓰기는 거울 칸 일지에 쌓여 주인이 1틱 안에 재생하고(결정 D1), 쓴 스레드는
 * 자기 쓰기를 곧바로 읽는다(N2b-4).
 * <p>정체성(N2b-1): 바닐라처럼 로드 한 번에 shim 하나({@code NmsChunks.loadedChunk}), 그 안에 섹션 배열 {@code sections} 하나와 칸마다 섹션 객체 하나,
 * 하이트맵 맵 {@code heightmaps} 하나. {@link #getSections()} 는 늘 같은 배열이다. 배열 칸에 플러그인이 새 섹션을 넣으면(FAWE 의 VarHandle CAS 포함)
 * 주인 region 이 그 내용을 서버 섹션으로 들여오고 그 섹션을 그 자리의 보기로 붙인다(N2b-4 거울 — {@code FurnaceChunkMirror}). 생성 중 청크(ProtoChunk) 표면은 없다.
 */
public abstract class ChunkAccess implements BlockGetter {

    protected final World furnaceWorld;
    private final ChunkPos pos;
    /** 바닐라 필드: 섹션 배열(0 = 맨 아래). 처음엔 서버 섹션의 보기들. */
    protected final LevelChunkSection[] sections;
    /**
     * 바닐라 필드(Paper 처럼 공개): 하이트맵. 로드된 청크는 살아 있는 네 종류의 보기를 가진다 — 서버가 고정 하이트맵 모드(원시 쓰기·setRawData·낡은
     * 저장값, N2b-2)면 그 고정 값을 그대로 비춘다.
     */
    public final java.util.Map<Heightmap.Types, Heightmap> heightmaps = new java.util.EnumMap<>(Heightmap.Types.class);

    /** Furnace: 이 shim 의 거울(N2b-4 — 플러그인 쓰기를 주인이 서버로 들여온다). */
    final FurnaceChunkMirror furnaceMirror;

    protected ChunkAccess(World furnaceWorld, int chunkX, int chunkZ) {
        this.furnaceWorld = java.util.Objects.requireNonNull(furnaceWorld, "world");
        this.pos = new ChunkPos(chunkX, chunkZ);
        this.sections = new LevelChunkSection[NmsChunks.bridge().height(furnaceWorld) >> 4];
        int[][] counts = initialCounts(sections.length);
        for (int i = 0; i < sections.length; i++) sections[i] = LevelChunkSection.furnaceView(this, i, counts == null ? null : counts[i]);
        for (Heightmap.Types t : Heightmap.Types.values()) if (t.keepAfterWorldgen()) heightmaps.put(t, Heightmap.furnaceView(this, t));
        this.furnaceMirror = new FurnaceChunkMirror(this, sections);
    }

    /** 섹션 개수 필드의 처음 값(서버 바닐라 개수 — 주인은 살아 있는 값, 그 밖은 공개 스냅샷). 읽을 수 없으면 null(0 으로 둔다). */
    private int[][] initialCounts(int n) {
        try {
            var b = NmsChunks.bridge();
            int[][] out = new int[n][];
            if (b.ownsChunk(furnaceWorld, pos.x, pos.z)) {
                for (int i = 0; i < n; i++) out[i] = b.sectionCountsNow(furnaceWorld, pos.x, i, pos.z);
            } else {
                var v = b.chunkView(furnaceWorld, pos.x, pos.z);
                if (v == null) return null;
                for (int i = 0; i < n && i < v.sectionCount(); i++) out[i] = v.sectionCounts(i);
            }
            return out;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Furnace: 이 shim 의 거울(서버 다리에 붙인다 — {@code NmsChunks.loadedChunk}). 거울의 맞추기는 그 청크의 주인 region 스레드에서만 일한다(다른 스레드가
     * 불러도 아무것도 하지 않는다).
     */
    public org.domi.furnace.api.internal.ChunkInternals.ChunkMirror furnaceMirror() { return furnaceMirror; }

    public ChunkPos getPos() { return pos; }

    @Override
    public int getMinBuildHeight() { return NmsChunks.bridge().minY(furnaceWorld); }

    @Override
    public int getHeight() { return NmsChunks.bridge().height(furnaceWorld); }

    /** 바닐라: 섹션 배열 그 자체(복사하지 않는다 — 이 shim 이 사는 동안 같은 배열). 로드된 청크의 섹션은 null 이 아니다. */
    public LevelChunkSection[] getSections() { return sections; }

    /** 바닐라: getSections()[index](범위 밖이면 ArrayIndexOutOfBoundsException). */
    public LevelChunkSection getSection(int index) { return getSections()[index]; }

    @Override
    public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }

    @Override
    public BlockState getBlockStateIfLoaded(BlockPos pos) { return getBlockState(pos); }

    /** 바닐라: heightmaps 의 그 종류, 없으면 만들어 넣는다(로드된 청크는 살아 있는 네 종류를 늘 가진다 — 월드젠 종류는 비어 있는 하이트맵). */
    public Heightmap getOrCreateHeightmapUnprimed(Heightmap.Types type) { return heightmaps.computeIfAbsent(type, t -> Heightmap.furnaceView(this, t)); }

    /** 바닐라: heightmaps 에 그 종류가 있는가(로드된 청크는 살아 있는 네 종류). */
    public boolean hasPrimedHeightmap(Heightmap.Types type) { return heightmaps.get(type) != null; }

    /**
     * 그 칸(청크 안 x·z) 의 가장 높은 해당 블록 y(빈 기둥이면 최저 높이 - 1). 바닐라는 없는 하이트맵(로드된 청크의 *_WG)을 primeHeightmaps 로
     * 지금 블록에서 계산한다 — WORLD_SURFACE_WG·OCEAN_FLOOR_WG 는 WORLD_SURFACE·OCEAN_FLOOR 와 술어가 같아 그 값과 같다.
     * 가정: 바닐라는 처음 계산한 WG 맵을 그 뒤 갱신하지 않아 블록이 바뀌면 낡는다. 여기서는 늘 지금 값이다.
     */
    public int getHeight(Heightmap.Types type, int x, int z) {
        Heightmap.Types live = switch (type) {
            case WORLD_SURFACE_WG -> Heightmap.Types.WORLD_SURFACE;
            case OCEAN_FLOOR_WG -> Heightmap.Types.OCEAN_FLOOR;
            default -> type;
        };
        return getOrCreateHeightmapUnprimed(live).getHighestTaken(x & 15, z & 15);
    }

    /** 로드된 청크는 늘 다 만들어진 상태다. */
    public ChunkStatus getPersistedStatus() { return ChunkStatus.FULL; }

    /**
     * 저장 대상 표시. true 면 서버가 이 청크를 저장 대상으로 표시한다. false(이미 저장됐다고 알림)는 Furnace 가 따로 저장 시점을 관리해 무시한다.
     */
    public void setUnsaved(boolean unsaved) {
        if (!unsaved || furnaceMirror.dead()) return;   // 내린 청크의 shim — 바닐라: 죽은 LevelChunk 의 표시는 월드에 닿지 않는다
        if (!furnaceOwner()) {   // 다른 스레드(FAWE 작업 스레드 등): 거울 일지 — 주인이 앞선 쓰기를 들여온 뒤 그 순서대로 저장 표시(N2b-4)
            furnaceMirror.unsaved();
            return;
        }
        furnaceMirror.ownerSyncAll();   // 맞춤 지점: 거울을 먼저 들여온 뒤 저장 표시(D3 — 들여온 섹션이 저장된다)
        if (!NmsChunks.bridge().markUnsavedNow(furnaceWorld, pos.x, pos.z)) throw notLoaded("ChunkAccess#setUnsaved");
    }

    /** 바이옴 칸 좌표(블록 좌표 >> 2)의 바이옴 — 높이는 끝 칸으로 죈다(바닐라 ChunkAccess.getNoiseBiome). */
    public Holder<Biome> getNoiseBiome(int qx, int qy, int qz) {
        int minQ = getMinBuildHeight() >> 2, maxQ = minQ + (getHeight() >> 2) - 1;
        int y = Math.max(minQ, Math.min(maxQ, qy));
        int sec = getSectionIndex(y << 2);
        return furnaceSectionBiomeAt(sec, qx & 3, y & 3, qz & 3);
    }

    /**
     * 바닐라 ChunkAccess.getBlockEntitiesPos: 블록 엔티티가 있는 칸(아직 만들지 않은 생성 블록 엔티티 포함). 새 집합 — 고쳐도 청크에 닿지 않는다
     * (바닐라는 두 맵의 키를 합친 새 집합).
     */
    public java.util.Set<BlockPos> getBlockEntitiesPos() {
        java.util.Set<BlockPos> out = new java.util.HashSet<>();
        for (long p : furnaceBlockEntityPositions("ChunkAccess#getBlockEntitiesPos")) out.add(BlockPos.of(p));
        return out;
    }

    // ------------------------------------------------------------------ 블록 엔티티(T-198)

    /**
     * 블록 엔티티 보기의 정체성 표: 칸 → 보기. 같은 칸·같은 서버 블록 엔티티면 같은 보기를 준다(바닐라: 청크 블록 엔티티 표의 그 객체). 지워졌거나 다른 서버
     * 객체로 갈린 보기는 다음 찾기 때 새 보기로 바뀐다.
     */
    private final java.util.concurrent.ConcurrentHashMap<Long, net.minecraft.world.level.block.entity.BlockEntity> furnaceBlockEntityViews =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** 다른 스레드: 공개 판 하나의 블록 엔티티 칸 집합({판, 집합}). */
    private volatile Object[] furnaceBlockEntityPositionCache;

    /** 블록 엔티티가 있는 칸(BlockPos.asLong). 주인은 살아 있는 값, 다른 스레드는 공개 판 + 자기 블록 엔티티 쓰기 덧칠. */
    private java.util.Set<Long> furnaceBlockEntityPositions(String what) {
        var v = furnaceForeignView(what);
        if (v == null) {
            furnaceMirror.ownerSyncIfDirty();
            long[] packed = NmsChunks.bridge().blockEntityPositionsNow(furnaceWorld, pos.x, pos.z);
            if (packed == null) throw notLoaded(what);
            java.util.Set<Long> out = new java.util.HashSet<>(packed.length * 2);
            for (long p : packed) out.add(p);
            return out;
        }
        java.util.Set<Long> base = furnacePublishedPositions(v);
        var over = furnaceMirror.blockEntityOverlay();
        if (over.isEmpty()) return new java.util.HashSet<>(base);
        java.util.Set<Long> out = new java.util.HashSet<>(base);
        for (var e : over.entrySet()) {
            if (e.getValue() == FurnaceChunkMirror.BE_REMOVED) out.remove(e.getKey());
            else out.add(e.getKey());
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private java.util.Set<Long> furnacePublishedPositions(ChunkInternals.ChunkView v) {
        Object[] c = furnaceBlockEntityPositionCache;
        if (c != null && c[0] == v) return (java.util.Set<Long>) c[1];
        long[] packed = v.blockEntityPositions();
        java.util.Set<Long> s = new java.util.HashSet<>(packed.length * 2);
        for (long p : packed) s.add(p);
        furnaceBlockEntityPositionCache = new Object[] {v, s};
        return s;
    }

    /** 바닐라 BlockGetter/ChunkAccess getBlockEntity(pos): 그 칸의 블록 엔티티(만들지 않는다 — LevelChunk 의 CHECK 와 같다), 없으면 null. */
    public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos) { return furnaceBlockEntity(pos, false); }

    /**
     * 호환 모듈 전용 — 바닐라 LevelChunk.getBlockEntity(pos, 종류): 그 칸의 블록 엔티티 보기. create(IMMEDIATE)면 없고 그 칸 블록이 블록 엔티티를 가지는
     * 종류일 때 만든다(주인은 바로, 다른 스레드는 보기를 먼저 주고 주인이 만들어 묶는다 — 거울 일지). 주인은 살아 있는 값, 다른 스레드는 공개 판 + 자기 쓰기.
     */
    public net.minecraft.world.level.block.entity.BlockEntity furnaceBlockEntity(BlockPos at, boolean create) {
        int x = at.getX(), y = at.getY(), z = at.getZ();
        if (isOutsideBuildHeight(y) || (x >> 4) != pos.x || (z >> 4) != pos.z) return null;   // 바닐라: 이 청크의 표에 없는 칸
        long k = BlockPos.asLong(x, y, z);
        var v = furnaceForeignView("LevelChunk#getBlockEntity");
        if (v == null) {
            furnaceMirror.ownerSync(slotOf(y));
            Object h = NmsChunks.bridge().blockEntityNow(furnaceWorld, x, y, z, create);
            if (h == null) return null;
            var cur = furnaceBlockEntityViews.get(k);
            if (cur != null && java.util.Objects.equals(cur.furnaceHandle(), h)) return cur;
            if (cur != null && cur.furnaceHandle() == null && !cur.isRemoved()) {   // 다른 스레드가 만든 보기 — 처음 보는 주인이 묶는다
                cur.furnaceBind(h);
                if (java.util.Objects.equals(cur.furnaceHandle(), h)) return cur;
            }
            var nv = net.minecraft.world.level.block.entity.BlockEntity.furnaceView(this, at.immutable(), getBlockState(at), h);
            if (nv != null) furnaceBlockEntityViews.put(k, nv);
            return nv;
        }
        Object o = furnaceMirror.blockEntityOverlayAt(k);
        if (o instanceof net.minecraft.world.level.block.entity.BlockEntity be) return be;
        if (o == null && furnacePublishedPositions(v).contains(k)) {
            var cur = furnaceBlockEntityViews.get(k);
            if (cur != null && !cur.isRemoved()) return cur;
            var nv = net.minecraft.world.level.block.entity.BlockEntity.furnaceView(this, at.immutable(), getBlockState(at), null);
            if (nv == null) return null;
            return furnaceBlockEntityViews.compute(k, (kk, old) -> old != null && !old.isRemoved() ? old : nv);
        }
        if (!create) return null;
        // 바닐라 IMMEDIATE: 이 스레드가 보는 블록(플러그인이 넣은 섹션 포함)이 블록 엔티티를 가지는 종류면 만든다
        var nv = net.minecraft.world.level.block.entity.BlockEntity.furnaceView(this, at.immutable(), getBlockState(at), null);
        if (nv == null || !furnaceMirror.blockEntityCreate(x, y, z, nv)) return null;
        furnaceBlockEntityViews.put(k, nv);
        return nv;
    }

    /**
     * 호환 모듈 전용 — 바닐라 LevelChunk.removeBlockEntity: 그 칸의 블록 엔티티를 청크에서 떼고 지움 표시(블록은 그대로). 뗀 보기(없으면 null — 다른 스레드는
     * 그 스레드가 보는 판 기준). 다른 스레드는 거울 일지로 주인이 지우고, 그 사이 이 스레드의 찾기는 없는 것으로 본다. 내린 청크는 아무것도 하지 않는다.
     */
    public net.minecraft.world.level.block.entity.BlockEntity furnaceRemoveBlockEntity(BlockPos at) {
        int x = at.getX(), y = at.getY(), z = at.getZ();
        if (isOutsideBuildHeight(y) || (x >> 4) != pos.x || (z >> 4) != pos.z || furnaceMirror.dead()) return null;
        long k = BlockPos.asLong(x, y, z);
        var cur = furnaceBlockEntity(at, false);   // 주인은 맞춘 뒤 묶인 보기, 다른 스레드는 그 스레드의 판
        if (furnaceOwner()) NmsChunks.bridge().removeBlockEntityNow(furnaceWorld, x, y, z);
        else furnaceMirror.blockEntityRemove(x, y, z, cur);
        if (cur != null) {
            cur.setRemoved();
            furnaceBlockEntityViews.remove(k, cur);
        }
        return cur;
    }

    /** 호환 모듈 전용 — 이 청크의 블록 엔티티 표(칸 → 보기, 새 표 — 고쳐도 청크에 닿지 않는다). */
    public java.util.Map<BlockPos, net.minecraft.world.level.block.entity.BlockEntity> furnaceBlockEntities() {
        java.util.Map<BlockPos, net.minecraft.world.level.block.entity.BlockEntity> out = new java.util.HashMap<>();
        for (long p : furnaceBlockEntityPositions("LevelChunk#getBlockEntities")) {
            BlockPos bp = BlockPos.of(p);
            var be = furnaceBlockEntity(bp, false);
            if (be != null) out.put(bp, be);
        }
        return out;
    }

    /**
     * 호환 모듈 전용(거울·주인 스레드): 보기가 아직 묶이지 않았으면 그 칸의 지금 서버 블록 엔티티에 묶는다. 묶인 손잡이(없으면 null).
     */
    public static Object furnaceBindNow(World world, net.minecraft.world.level.block.entity.BlockEntity view) {
        Object h = view.furnaceHandle();
        if (h != null) return h;
        var p = view.getBlockPos();
        h = NmsChunks.bridge().blockEntityNow(world, p.getX(), p.getY(), p.getZ(), false);
        if (h != null) view.furnaceBind(h);
        return view.furnaceHandle();
    }

    /** 보기를 기다려 읽을 때의 상한(초) — 다른 region 의 주인이 그 블록 엔티티를 저장해 줄 때까지. */
    private static final long BLOCK_ENTITY_WAIT_SECONDS = 10;

    /**
     * 호환 모듈 전용 — 보기의 저장 꼴(saveWithFullMetadata, 새 트리). 주인 스레드면 바로(맞춘 뒤), 틱 스레드가 아닌 다른 스레드는 주인 region 에 부탁해 기다린다
     * (최대 10초). 다른 region·전역 스레드는 서로 기다리는 교착을 피하려고 ThreadOwnershipException(엔티티 찾기와 같은 규칙). 묶인 블록 엔티티가 지워졌으면
     * 그 마지막 내용, 그 칸에 블록 엔티티가 없으면 IllegalStateException.
     */
    public org.domi.furnace.api.nbt.NbtCompound furnaceBlockEntitySave(net.minecraft.world.level.block.entity.BlockEntity view) {
        var b = NmsChunks.bridge();
        var p = view.getBlockPos();
        if (furnaceMirror.dead()) {   // 내린 청크: 묶인 객체는 월드에서 떨어졌다 — 그 내용 그대로
            Object h = view.furnaceHandle();
            if (h == null) throw new IllegalStateException("the chunk of this block entity was unloaded: " + p);
            return b.blockEntitySaveNow(h);
        }
        if (furnaceOwner()) return furnaceSaveOwned(view);
        if (org.domi.furnace.compat.thread.PrimaryThread.isPrimary() || org.domi.furnace.api.Furnace.server().scheduler().isTickThread()) {
            throw new org.domi.furnace.api.concurrent.ThreadOwnershipException("BlockEntity.save*: the block entity at " + p + " belongs to another region - a region or global"
                    + " thread cannot wait for another region (deadlock); read it on its owning region (current thread: " + Thread.currentThread().getName() + ")");
        }
        var f = furnaceWorld.scheduler(new org.domi.furnace.api.world.BlockPos(p.getX(), p.getY(), p.getZ())).submit(() -> {
            if (furnaceMirror.dead() || !furnaceOwner()) {
                Object h = view.furnaceHandle();
                if (h == null) throw new IllegalStateException("the chunk of this block entity was unloaded: " + p);
                return b.blockEntitySaveNow(h);
            }
            return furnaceSaveOwned(view);
        });
        try {
            return f.get(BLOCK_ENTITY_WAIT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("BlockEntity.save*", "interrupted while waiting for a block entity in another region: " + p);
        } catch (java.util.concurrent.ExecutionException ex) {
            if (ex.getCause() instanceof RuntimeException re) throw re;
            throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("BlockEntity.save*", "could not read the block entity on the owning region thread (" + p + "): " + ex.getCause());
        } catch (java.util.concurrent.TimeoutException ex) {
            throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("BlockEntity.save*", "the owning region did not read the block entity within " + BLOCK_ENTITY_WAIT_SECONDS + " seconds (" + p + ")");
        }
    }

    private org.domi.furnace.api.nbt.NbtCompound furnaceSaveOwned(net.minecraft.world.level.block.entity.BlockEntity view) {
        furnaceMirror.ownerSyncIfDirty();   // 이 스레드·다른 스레드의 앞선 블록 엔티티 일지(지우기·싣기)를 먼저
        Object h = furnaceBindNow(furnaceWorld, view);
        if (h == null) throw new IllegalStateException("no block entity at that position (removed, or its chunk was unloaded): " + view.getBlockPos());
        return NmsChunks.bridge().blockEntitySaveNow(h);
    }

    /** 호환 모듈 전용 — 보기에 tag 를 싣는다(바닐라 loadWithComponents). 주인은 바로, 다른 스레드는 거울 일지. 내린 청크는 월드에 닿지 않는다. */
    public void furnaceBlockEntityLoad(net.minecraft.world.level.block.entity.BlockEntity view, org.domi.furnace.api.nbt.NbtCompound tag) {
        if (furnaceMirror.dead()) return;
        if (!furnaceOwner()) {
            furnaceMirror.blockEntityLoad(view, tag.copy());
            return;
        }
        furnaceMirror.ownerSyncIfDirty();
        Object h = furnaceBindNow(furnaceWorld, view);
        if (h != null) NmsChunks.bridge().blockEntityLoadNow(furnaceWorld, h, tag);
    }

    /** 호환 모듈 전용 — 바닐라 정적 BlockEntity.setChanged(level, pos, state) 의 이 청크 부분. 주인은 바로, 다른 스레드는 거울 일지. */
    public void furnaceBlockEntityChanged(BlockPos at) {
        if (furnaceMirror.dead()) return;
        if (!furnaceOwner()) {
            furnaceMirror.blockEntityChanged(at.getX(), at.getY(), at.getZ());
            return;
        }
        furnaceMirror.ownerSyncIfDirty();
        NmsChunks.bridge().blockEntityChangedNow(furnaceWorld, at.getX(), at.getY(), at.getZ());
    }

    // ------------------------------------------------------------------ 서버 다리(호환 모듈 전용)

    /** Furnace: 이 청크의 서버 월드 핸들. */
    public World furnaceWorld() { return furnaceWorld; }

    protected IllegalStateException notLoaded(String what) { return NmsChunks.notLoaded(furnaceWorld, pos.x, pos.z, what); }

    /**
     * 읽기 출처(N2b-3): 지금 스레드가 이 청크의 주인(전역 빌림 구간 포함)이면 null(살아 있는 값을 다리로), 아니면 주인이 낸 공개 스냅샷(기다리지 않음).
     * 로드돼 있지 않으면 IllegalStateException. 로드돼 있는데 아직 공개본이 없으면(이 shim 이 공개 전에 다른 스레드로 넘어간 드문 경우) 공개를 부탁하고
     * ThreadOwnershipException — 다음 틱부터 읽힌다.
     */
    public org.domi.furnace.api.internal.ChunkInternals.ChunkView furnaceForeignView(String what) {
        var b = NmsChunks.bridge();
        if (b.ownsChunk(furnaceWorld, pos.x, pos.z)) return null;
        var v = b.chunkView(furnaceWorld, pos.x, pos.z);
        if (v != null) return v;
        if (!b.isLoaded(furnaceWorld, pos.x, pos.z)) throw notLoaded(what);
        // 붙은 shim 인데 판이 없다 = 주인이 오래 안 읽힌 판을 거두는 바로 그 순간(PR #382 검토 R3). 주인은 거둔 뒤 붙은 shim 을 보고 곧바로(수 µs)
        // 다시 내므로 잠깐 다시 본다(최대 약 2ms).
        if (b.chunkShim(furnaceWorld, pos.x, pos.z) == this) {
            for (int i = 0; i < 100 && v == null; i++) {
                java.util.concurrent.locks.LockSupport.parkNanos(20_000L);
                v = b.chunkView(furnaceWorld, pos.x, pos.z);
            }
            if (v != null) return v;
        }
        throw new org.domi.furnace.api.concurrent.ThreadOwnershipException(what + ": chunk " + pos.x + ", " + pos.z + " (" + furnaceWorld.dimension()
                + ") has no published snapshot yet - a thread that is not the owning region reads the copy the owner publishes at the end of its tick. Publishing was requested, so it can be read from the next tick"
                + " (current thread: " + Thread.currentThread().getName() + ")");
    }

    /** 지금 스레드가 이 청크의 주인 region 스레드(전역 빌림 구간 포함)인가. */
    public boolean furnaceOwner() { return NmsChunks.bridge().ownsChunk(furnaceWorld, pos.x, pos.z); }

    private int slotOf(int y) { return (y - getMinBuildHeight()) >> 4; }

    /** 월드 좌표의 상태 번호(높이 안). 다른 스레드는 자기 쓰기 덧칠이 먼저. 청크가 로드돼 있지 않으면 IllegalStateException. */
    public int furnaceStateIdAt(int x, int y, int z) {
        var v = furnaceForeignView("getBlockState");
        int slot = slotOf(y);
        if (v != null) {
            int o = furnaceMirror.overlayCellAt(slot, (y & 15) << 8 | (z & 15) << 4 | (x & 15));
            return o >= 0 ? o : v.stateId(x, y, z);
        }
        furnaceMirror.ownerSync(slot);
        int id = NmsChunks.bridge().stateIdNow(furnaceWorld, x, y, z);
        if (id < 0) throw notLoaded("getBlockState");
        return id;
    }

    /** 섹션 칸 하나(칸 = y*256+z*16+x)의 상태 번호. */
    int furnaceSectionCell(int index, int cell) {
        return furnaceStateIdAt((pos.x << 4) + (cell & 15), getMinBuildHeight() + (index << 4) + (cell >> 8), (pos.z << 4) + (cell >> 4 & 15));
    }

    /** 바닐라 LevelChunk.getBlockState 의 번호(높이 안, 주인 스레드): 섹션 바닐라 nonEmpty == 0 이면 공기 — 배열 없이 한 번에(PR #373 검토 N3). */
    public int furnaceVanillaStateIdAt(int x, int y, int z) {
        var v = furnaceForeignView("LevelChunk#getBlockState");
        if (v != null) return v.vanillaStateId(x, y, z);
        furnaceMirror.ownerSync(slotOf(y));
        int id = NmsChunks.bridge().vanillaStateIdNow(furnaceWorld, x, y, z);
        if (id < 0) throw notLoaded("LevelChunk#getBlockState");
        return id;
    }

    public int[] furnaceSectionStates(int index) {
        var src = furnaceForeignView("LevelChunkSection#getStates");
        if (src != null) return furnaceMirror.overlayCells(index, src.sectionStates(index));
        furnaceMirror.ownerSync(index);
        int[] v = NmsChunks.bridge().sectionStatesNow(furnaceWorld, pos.x, index, pos.z);
        if (v == null) throw notLoaded("LevelChunkSection#getStates");
        return v;
    }

    /** 실체화용(N2b-4): 섹션 칸 번호 전부와 그 판 번호(stampOut[0]) — 한 출처에서. 다른 스레드의 덧칠이 있으면 판 번호는 MIN(늘 다시 실체화). */
    int[] furnaceSectionIdsStamped(int index, boolean biome, long[] stampOut) {
        var src = furnaceForeignView(biome ? "PalettedContainer.data (biomes)" : "PalettedContainer.data");
        if (src != null) {
            int[] ids = biome ? src.sectionBiomes(index) : src.sectionStates(index);
            int[] over = biome ? furnaceMirror.overlayBiomes(index, ids.clone()) : furnaceMirror.overlayCells(index, ids.clone());
            stampOut[0] = java.util.Arrays.equals(over, ids) ? src.contentStamp() : Long.MIN_VALUE;
            return over;
        }
        furnaceMirror.ownerSync(index);
        var b = NmsChunks.bridge();
        stampOut[0] = b.contentStampNow(furnaceWorld, pos.x, pos.z);
        int[] v = biome ? b.sectionBiomesNow(furnaceWorld, pos.x, index, pos.z) : b.sectionStatesNow(furnaceWorld, pos.x, index, pos.z);
        if (v == null) throw notLoaded("PalettedContainer.data");
        return v;
    }

    public int furnaceSectionNonAir(int index) {
        var src = furnaceForeignView("LevelChunkSection#hasOnlyAir");
        if (src == null) furnaceMirror.ownerSync(index);
        int n = src != null ? src.sectionNonAir(index) : NmsChunks.bridge().sectionNonAirNow(furnaceWorld, pos.x, index, pos.z);
        if (n < 0) throw notLoaded("LevelChunkSection#hasOnlyAir");
        return n;
    }

    public boolean furnaceSectionRandomlyTicking(int index) {
        return furnaceSectionRandomTickingParts(index) != 0;
    }

    /** 서버 섹션의 바닐라 판정 비트(1 = 블록, 2 = 유체). */
    public int furnaceSectionRandomTickingParts(int index) {
        var src = furnaceForeignView("LevelChunkSection#isRandomlyTicking");
        if (src != null) return src.sectionRandomTickingParts(index);
        if (!NmsChunks.bridge().isLoaded(furnaceWorld, pos.x, pos.z)) throw notLoaded("LevelChunkSection#isRandomlyTicking");
        furnaceMirror.ownerSync(index);
        return NmsChunks.bridge().sectionRandomTickingPartsNow(furnaceWorld, pos.x, index, pos.z);
    }

    public int[] furnaceSectionBiomes(int index) {
        var src = furnaceForeignView("LevelChunkSection#getBiomes");
        if (src != null) return furnaceMirror.overlayBiomes(index, src.sectionBiomes(index));
        furnaceMirror.ownerSync(index);
        int[] v = NmsChunks.bridge().sectionBiomesNow(furnaceWorld, pos.x, index, pos.z);
        if (v == null) throw notLoaded("LevelChunkSection#getBiomes");
        return v;
    }

    /** 섹션 직렬화용 {개수 셋, 블록 번호 4096, 바이옴 번호 64} — 한 출처(한 판)에서 함께 읽는다(다른 스레드는 자기 쓰기 덧칠을 입혀). */
    public int[][] furnaceSectionData(int index) {
        var src = furnaceForeignView("LevelChunkSection#write");
        if (src != null) {
            return new int[][] {src.sectionCounts(index), furnaceMirror.overlayCells(index, src.sectionStates(index)),
                furnaceMirror.overlayBiomes(index, src.sectionBiomes(index))};
        }
        return new int[][] {furnaceSectionCounts(index), furnaceSectionStates(index), furnaceSectionBiomes(index)};
    }

    private Holder<Biome> furnaceSectionBiomeAt(int index, int x, int y, int z) {
        return NmsChunks.biome(furnaceSectionBiomes(index)[(y << 2 | z) << 2 | x]);
    }

    // ---- 칸 쓰기: 주인 region 스레드는 서버에 바로(N2b-2 원시 쓰기), 그 밖은 거울 칸 일지(N2b-4, 결정 D1 — 주인이 1틱 안에 그 칸만 재생)

    /** 섹션 칸 쓰기의 옛 번호(다른 스레드: 자기 덧칠 → 공개 스냅샷). */
    private int foreignCellId(ChunkInternals.ChunkView v, int index, int cell) {
        int o = furnaceMirror.overlayCellAt(index, cell);
        if (o >= 0) return o;
        return v.stateId((pos.x << 4) + (cell & 15), getMinBuildHeight() + (index << 4) + (cell >> 8), (pos.z << 4) + (cell >> 4 & 15));
    }

    /**
     * LevelChunkSection#setBlockState(붙은 보기, 칸 = y*256+z*16+x): 개수 ±1 — 주인은 서버가, 그 밖은 섹션 필드에 곧바로. 옛 상태 번호. 그 사이 섹션이
     * 배열에서 빠져 떼어 내졌으면 {@link PalettedContainer#FURNACE_DETACHED}(부른 쪽이 그 섹션에 바닐라로 쓴다 — 월드에 닿지 않는다, PR #396 검토 R2).
     */
    int furnaceSectionSet(LevelChunkSection section, int index, int cell, int stateId) {
        var v = furnaceForeignView("LevelChunkSection#setBlockState");
        if (v != null) return furnaceMirror.sectionSet(section, index, cell, stateId, foreignCellId(v, index, cell));
        furnaceMirror.ownerSync(index);
        if (section.furnaceChunk() != this || section.furnaceIndex() != index) return PalettedContainer.FURNACE_DETACHED;   // 맞추는 사이 배열에서 빠졌다
        int old = NmsChunks.bridge().setSectionStateRawNow(furnaceWorld, pos.x, index, pos.z, cell, stateId, true);
        if (old < 0) throw notLoaded("LevelChunkSection#setBlockState");
        return old;
    }

    /** 블록 컨테이너 칸 쓰기(개수 그대로, 붙은 보기 self — 공급원 src). 옛 상태 번호. 그 사이 떼어 내졌으면 FURNACE_DETACHED. */
    int furnaceContainerSet(PalettedContainer<?> self, Object src, int index, int cell, int stateId) {
        var v = furnaceForeignView("PalettedContainer#set");
        if (v != null) {
            int old = foreignCellId(v, index, cell);
            return furnaceMirror.containerSet(self, src, index, cell, stateId) ? old : PalettedContainer.FURNACE_DETACHED;
        }
        furnaceMirror.ownerSync(index);
        if (self.furnaceSourceObject() != src) return PalettedContainer.FURNACE_DETACHED;   // 맞추는 사이 떼어 내졌다
        int old = NmsChunks.bridge().setSectionStateRawNow(furnaceWorld, pos.x, index, pos.z, cell, stateId, false);
        if (old < 0) throw notLoaded("PalettedContainer#set");
        return old;
    }

    /**
     * 섹션 칸 원시 쓰기(칸 = y*256+z*16+x) — 지금 그 자리 섹션으로. vanillaCounts 면 LevelChunkSection.setBlockState(개수 ±1), 아니면 컨테이너 set.
     * 옛 상태 번호.
     */
    public int furnaceSetSectionState(int index, int cell, int stateId, boolean vanillaCounts) {
        LevelChunkSection s = sections[index];
        BlockState state = NmsChunks.state(stateId);
        BlockState old = vanillaCounts ? s.setBlockState(cell & 15, cell >> 8, cell >> 4 & 15, state)
                : s.getStates().getAndSet(cell & 15, cell >> 8, cell >> 4 & 15, state);
        return old.furnaceStateId();
    }

    /** 섹션 바이옴 칸 쓰기(칸 = y*16+z*4+x, 붙은 보기 self — 공급원 src). 옛 바이옴 번호. 그 사이 떼어 내졌으면 FURNACE_DETACHED. */
    int furnaceSetSectionBiome(PalettedContainer<?> self, Object src, int index, int cell, int biomeId) {
        var v = furnaceForeignView("PalettedContainer#set (biomes)");
        if (v != null) {
            int old = furnaceMirror.overlayBiomes(index, v.sectionBiomes(index))[cell];
            return furnaceMirror.biomeSet(self, src, index, cell, biomeId) ? old : PalettedContainer.FURNACE_DETACHED;
        }
        furnaceMirror.ownerSync(index);
        if (self.furnaceSourceObject() != src) return PalettedContainer.FURNACE_DETACHED;
        int old = NmsChunks.bridge().setSectionBiomeRawNow(furnaceWorld, pos.x, index, pos.z, cell, biomeId);
        if (old < 0) throw notLoaded("PalettedContainer#set (biomes)");
        return old;
    }

    /** 섹션의 바닐라 개수 {nonEmpty, tickingBlock, tickingFluid}(주인은 살아 있는 값, 그 밖은 공개 스냅샷). */
    public int[] furnaceSectionCounts(int index) {
        var src = furnaceForeignView("LevelChunkSection count");
        if (src == null) furnaceMirror.ownerSync(index);
        int[] c = src != null ? src.sectionCounts(index) : NmsChunks.bridge().sectionCountsNow(furnaceWorld, pos.x, index, pos.z);
        if (c == null) throw notLoaded("LevelChunkSection count");
        return c;
    }

    /** 섹션의 바닐라 개수 대입(주인 스레드). */
    public void furnaceSetSectionCounts(int index, int nonEmpty, int tickingBlock, int tickingFluid) {
        furnaceMirror.ownerSync(index);
        if (!NmsChunks.bridge().setSectionCountsNow(furnaceWorld, pos.x, index, pos.z, nonEmpty, tickingBlock, tickingFluid)) {
            throw notLoaded("LevelChunkSection count");
        }
    }

    /** 하이트맵 setRawData(살아 있는 종류). 길이가 맞아 그대로 들어갔으면(다른 스레드는 들어갈 것이면) true. */
    public boolean furnaceSetHeightmapRaw(Heightmap.Types type, long[] raw) {
        boolean dead = furnaceMirror.dead();   // 내린 청크의 shim: 쓰기는 월드(같은 좌표의 새 로드)에 닿지 않는다
        if (dead || !furnaceOwner()) {
            if (!dead) furnaceMirror.heightmapRaw(type, raw);   // 주인이 순서대로 재생(길이가 틀리면 그때 경고 + prime)
            int bits = 32 - Integer.numberOfLeadingZeros(getHeight());
            int perLong = 64 / bits;
            return raw.length == (256 + perLong - 1) / perLong;
        }
        furnaceMirror.ownerSyncIfDirty();
        int r = NmsChunks.bridge().setHeightmapRawNow(furnaceWorld, pos.x, pos.z, type.getSerializationKey(), raw);
        if (r < 0) throw notLoaded("Heightmap#setRawData");
        return r == 1;
    }

    /** 하이트맵 update(청크 안 x·z, 월드 y). 바뀌면 true — 다른 스레드는 일지에 쌓고 false(결과는 주인이 재생할 때 정해진다). */
    public boolean furnaceHeightmapUpdate(Heightmap.Types type, int x, int y, int z, int stateId) {
        if (furnaceMirror.dead()) return false;   // 내린 청크의 shim
        if (!furnaceOwner()) {
            furnaceMirror.heightmapUpdate(type, x, y, z, stateId);
            return false;
        }
        furnaceMirror.ownerSyncIfDirty();
        int r = NmsChunks.bridge().updateHeightmapNow(furnaceWorld, type.getSerializationKey(), (pos.x << 4) + x, y, (pos.z << 4) + z, stateId);
        if (r < 0) throw notLoaded("Heightmap#update");
        return r == 1;
    }

    /** 하이트맵 첫 빈 y(청크 안 x·z). */
    public int furnaceFirstAvailable(Heightmap.Types type, int x, int z) {
        var src = furnaceForeignView("Heightmap#getFirstAvailable");
        if (src != null) return src.firstAvailable(type.getSerializationKey(), x, z);
        furnaceMirror.ownerSyncIfDirty();
        int v = NmsChunks.bridge().firstAvailableNow(furnaceWorld, type.getSerializationKey(), (pos.x << 4) + x, (pos.z << 4) + z);
        if (v == Integer.MIN_VALUE) throw notLoaded("Heightmap#getFirstAvailable");
        return v;
    }

    /** 하이트맵 256칸(i = z*16+x)의 첫 빈 y — 한 출처(한 판)에서. */
    public int[] furnaceFirstAvailableAll(Heightmap.Types type) {
        var src = furnaceForeignView("Heightmap#getRawData");
        if (src == null) furnaceMirror.ownerSyncIfDirty();
        int[] out = new int[256];
        String key = type.getSerializationKey();
        for (int i = 0; i < 256; i++) {
            if (src != null) {
                out[i] = src.firstAvailable(key, i & 15, i >> 4);
            } else {
                int v = NmsChunks.bridge().firstAvailableNow(furnaceWorld, key, (pos.x << 4) + (i & 15), (pos.z << 4) + (i >> 4));
                if (v == Integer.MIN_VALUE) throw notLoaded("Heightmap#getRawData");
                out[i] = v;
            }
        }
        return out;
    }

    // ---- 쓰기 장벽 입구(N2b-4, 호환 모듈 전용 — 변환된 플러그인 바이트코드가 NmsReflect·NmsArrays 를 거쳐 부른다)

    /** Field.get 앞: 붙은 보기 컨테이너의 data 를 읽으면 실체화. */
    public static void furnaceBarrierFieldGet(java.lang.reflect.Field field, Object target) {
        if (target instanceof PalettedContainer<?> pc && pc.furnaceAttached() && field.getDeclaringClass() == PalettedContainer.class
                && field.getName().equals("data")) {
            pc.furnaceMaterializeData();
        }
    }

    /** Field.set 뒤: 붙은 섹션·컨테이너면 거울에 알린다. */
    public static void furnaceBarrierFieldSet(Object target) {
        if (target instanceof LevelChunkSection s) {
            ChunkAccess c = s.furnaceChunk();
            if (c != null) c.furnaceMirror.noteWrite();
        } else if (target instanceof PalettedContainer<?> pc) {
            pc.furnaceFieldWritten();
        }
    }

    /** VarHandle 배열 칸 쓰기 뒤: 거울 섹션 배열이면 알린다. */
    public static void furnaceBarrierArrayWritten(Object[] array) { FurnaceChunkMirror.arrayWritten(array); }

    @Override
    public boolean equals(Object o) {
        return o instanceof ChunkAccess c && c.getClass() == getClass() && c.pos.equals(pos) && c.furnaceWorld.equals(furnaceWorld);
    }

    @Override
    public int hashCode() { return furnaceWorld.hashCode() * 31 + pos.hashCode(); }

    @Override
    public String toString() { return getClass().getSimpleName() + "[" + pos.x + ", " + pos.z + " in " + furnaceWorld.dimension() + "]"; }
}
