// 플러그인이 보는 월드 — 서버 World 를 감싼다. 블록 접근은 모두 그 칸을 가진 region 스레드에서 실행된다.
package org.domi.furnace.server.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.inventory.Inventory;
import org.domi.furnace.api.world.Location;
import org.domi.furnace.api.concurrent.ThreadOwner;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.sched.TaskScheduler;
import org.domi.furnace.api.world.Block;
import org.domi.furnace.api.world.BlockSnapshot;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.GameRules;
import org.domi.furnace.server.world.Region;
import org.domi.furnace.server.world.World;

public final class WorldHandle implements org.domi.furnace.api.world.World {

    private final PluginHost host;
    private final World world;

    private final SpawnPolicyImpl spawnPolicy;

    WorldHandle(PluginHost host, World world) {
        this.host = host;
        this.world = world;
        this.spawnPolicy = new SpawnPolicyImpl(world.spawnPolicy());
    }

    public World handle() { return world; }

    private ServerCore core() { return host.core(); }

    @Override
    public String name() {
        if (world.levelName() != null) return world.levelName();   // 추가 월드: 폴더 이름
        String base = core().levelData().levelName;
        return switch (world.dimension()) {
            case "minecraft:overworld" -> base;
            case "minecraft:the_nether" -> base + "_nether";
            case "minecraft:the_end" -> base + "_the_end";
            default -> world.dimension();
        };
    }

    @Override
    public String dimension() { return world.dimension(); }

    @Override
    public long seed() { return core().seedOf(world); }

    @Override
    public String dimensionType() { return world.dimensionType().typeKey(); }

    @Override
    public org.domi.furnace.api.world.WorldEnvironment environment() {
        return switch (world.dimensionType().gen()) {
            case NETHER -> org.domi.furnace.api.world.WorldEnvironment.NETHER;
            case END -> org.domi.furnace.api.world.WorldEnvironment.THE_END;
            default -> org.domi.furnace.api.world.WorldEnvironment.NORMAL;
        };
    }

    @Override
    public boolean isLoaded() { return !world.isUnloaded(); }

    @Override
    public String gameRule(String name) {
        GameRules.Rule r = GameRules.RULES.get(name);
        return r == null ? null : GameRules.serialize(r, core().gameRules().raw(name));
    }

    @Override
    public int loadedChunkCount() { return world.loadedChunkCount(); }

    @Override
    public CompletableFuture<Boolean> regenerateChunk(int chunkX, int chunkZ, org.domi.furnace.api.world.ChunkRegeneration options) {
        java.util.Objects.requireNonNull(options, "options");
        return world.regenerateChunk(chunkX, chunkZ, options.removeEntities(), options.generationEntities());
    }

    private static final long REGION_TPS_WINDOW = 5_000_000_000L, REGION_TPS_MIN_HISTORY = 1_000_000_000L;

    private org.domi.furnace.api.world.RegionInfo toApi(org.domi.furnace.server.world.Regionizer.RegionView v) {
        var i = v.info();
        return new org.domi.furnace.api.world.RegionInfo(i.id(), world.dimension(), i.chunks(), i.minChunkX(), i.minChunkZ(),
                i.maxChunkX(), i.maxChunkZ(), i.thread(), v.tps(), i.lastTickNanos(), i.maxTickNanos(), i.players());
    }

    @Override
    public Optional<org.domi.furnace.api.world.RegionInfo> regionAt(int chunkX, int chunkZ) {
        var v = world.regionizer().regionViewAt(chunkX, chunkZ, REGION_TPS_WINDOW, REGION_TPS_MIN_HISTORY);
        return Optional.ofNullable(v == null ? null : toApi(v));
    }

    @Override
    public List<org.domi.furnace.api.world.RegionInfo> regions() {
        List<org.domi.furnace.api.world.RegionInfo> out = new ArrayList<>();
        for (var v : world.regionizer().regionViews(REGION_TPS_WINDOW, REGION_TPS_MIN_HISTORY)) out.add(toApi(v));
        return out;
    }

    private boolean inHeight(int y) { return y >= world.dimensionType().minY() && y < world.dimensionType().maxY(); }

    private Optional<BlockSnapshot> read(Region r, BlockPos pos) {
        ChunkPos cp = new ChunkPos(pos.chunkX(), pos.chunkZ());
        if (!inHeight(pos.y()) || !r.chunkLoaded(cp)) return Optional.empty();
        int state = r.getBlock(pos.x(), pos.y(), pos.z());
        return Optional.of(new BlockSnapshot(world.dimension(), pos, ApiConvert.block(state, world.blockRegistry())));
    }

    @Override
    public Block block(BlockPos pos) { return new BlockHandle(this, pos); }

    @Override
    public CompletableFuture<Optional<BlockSnapshot>> blockAt(BlockPos pos) {
        CompletableFuture<Optional<BlockSnapshot>> f = world.submitAtOwnedChunk(new ChunkPos(pos.chunkX(), pos.chunkZ()), r -> read(r, pos));
        return RegionFuture.wrap(f != null ? f : CompletableFuture.completedFuture(Optional.empty()));
    }

    @Override
    public CompletableFuture<Boolean> setBlock(BlockPos pos, BlockData data) {
        int state;
        try {
            state = ApiConvert.stateId(data, world.blockRegistry());
        } catch (IllegalArgumentException e) {
            return RegionFuture.wrap(CompletableFuture.failedFuture(e));
        }
        CompletableFuture<Boolean> f = world.submitAtOwnedChunk(new ChunkPos(pos.chunkX(), pos.chunkZ()), r -> placeOwned(r, pos, state));
        return RegionFuture.wrap(f != null ? f : CompletableFuture.completedFuture(false));
    }

    /** 소유 스레드에서: 그 칸을 state 로 바꾸고 방송한다(청크가 없거나 높이 밖이면 false). */
    private boolean placeOwned(Region r, BlockPos pos, int state) {
        if (!inHeight(pos.y()) || !r.chunkLoaded(new ChunkPos(pos.chunkX(), pos.chunkZ()))) return false;
        var level = r.level();
        boolean ok = level.setBlock(new Pos(pos.x(), pos.y(), pos.z()), state, 11);
        level.broadcastChanges();
        return ok;
    }

    // ------------------------------------------------------------------ 빠른 블록 쓰기(E7)

    @Override
    public CompletableFuture<org.domi.furnace.api.world.BlockEditResult> editBlocks(org.domi.furnace.api.world.BlockEditOptions options,
                                                                                  java.util.function.Consumer<org.domi.furnace.api.world.BlockEditor> edit) {
        org.domi.furnace.server.world.BlockEditBuffer buf;
        try {
            buf = fillEdit(edit);
        } catch (IllegalArgumentException e) {
            return RegionFuture.wrap(CompletableFuture.failedFuture(e));
        }
        return RegionFuture.wrap(org.domi.furnace.server.world.BlockEdits.apply(world, buf, mode(options)).thenApply(WorldHandle::toApi));
    }

    @Override
    public org.domi.furnace.api.world.BlockEditResult editBlocksNow(org.domi.furnace.api.world.BlockEditOptions options,
                                                                    java.util.function.Consumer<org.domi.furnace.api.world.BlockEditor> edit) {
        var mode = mode(options);
        var buf = fillEdit(edit);
        // 모든 청크가 이 스레드 것인지 먼저 다 확인한다 — 하나라도 아니면 아무것도 바꾸지 않는다
        java.util.Map<Region, java.util.List<org.domi.furnace.server.world.BlockEditBuffer.ChunkEdit>> byRegion = new java.util.LinkedHashMap<>();
        for (var e : buf.chunks()) {
            Region r = ownedRegion(new BlockPos(e.cx() << 4, 0, e.cz() << 4), "editBlocksNow(chunk " + e.cx() + ", " + e.cz() + ")");
            if (r == null) throw new ThreadOwnershipException("editBlocksNow: no region for chunk " + e.cx() + ", " + e.cz());
            byRegion.computeIfAbsent(r, k -> new ArrayList<>()).add(e);
        }
        return toApi(org.domi.furnace.server.world.BlockEdits.applyNow(byRegion, buf, mode));
    }

    private static org.domi.furnace.api.world.BlockEditResult toApi(org.domi.furnace.server.world.BlockEdits.Result r) {
        return new org.domi.furnace.api.world.BlockEditResult(r.changed(), r.unchanged(), r.skipped());
    }

    /** API 옵션 → 바닐라 setBlock 플래그와 묶음 방식. */
    static org.domi.furnace.server.world.BlockEdits.Mode mode(org.domi.furnace.api.world.BlockEditOptions o) {
        java.util.Objects.requireNonNull(o, "options");
        int flags = 0;
        if (o.neighborUpdates() == org.domi.furnace.api.world.BlockEditOptions.NeighborUpdates.IMMEDIATE) flags |= 1 | 8;   // World.setBlock 의 11 과 같게
        if (o.clientUpdates() != org.domi.furnace.api.world.BlockEditOptions.ClientUpdates.NONE) flags |= 2;
        if (!o.shapeUpdates()) flags |= 16;
        return new org.domi.furnace.server.world.BlockEdits.Mode(flags,
                o.neighborUpdates() == org.domi.furnace.api.world.BlockEditOptions.NeighborUpdates.AFTER_EDIT,
                o.clientUpdates() == org.domi.furnace.api.world.BlockEditOptions.ClientUpdates.CHUNKS, o.clearContainers(), o.chunksPerTick());
    }

    /** edit 을 지금 이 스레드에서 불러 쓰기 목록을 채운다. 끝나면 닫는다. */
    private org.domi.furnace.server.world.BlockEditBuffer fillEdit(java.util.function.Consumer<org.domi.furnace.api.world.BlockEditor> edit) {
        java.util.Objects.requireNonNull(edit, "edit");
        var buf = new org.domi.furnace.server.world.BlockEditBuffer(world.dimensionType().minY(), world.dimensionType().maxY());
        Editor ed = new Editor(buf, world.blockRegistry());
        try {
            edit.accept(ed);
        } finally {
            ed.closed = true;
        }
        return buf;
    }

    /** BlockEditor 구현 — 같은 BlockData 를 거듭 넣는 반복(채우기)이 이름 조회를 칸마다 하지 않게 상태 번호를 캐시한다. */
    private static final class Editor implements org.domi.furnace.api.world.BlockEditor {
        private final org.domi.furnace.server.world.BlockEditBuffer buf;
        private final org.domi.furnace.server.world.BlockRegistry blocks;
        private final java.util.HashMap<BlockData, Integer> ids = new java.util.HashMap<>();
        private BlockData lastData;
        private int lastId;
        boolean closed;

        Editor(org.domi.furnace.server.world.BlockEditBuffer buf, org.domi.furnace.server.world.BlockRegistry blocks) {
            this.buf = buf;
            this.blocks = blocks;
        }

        @Override
        public void set(int x, int y, int z, BlockData data) {
            if (closed) throw new IllegalStateException("a BlockEditor cannot be used after the editBlocks function has returned");
            java.util.Objects.requireNonNull(data, "data");
            int id;
            if (data == lastData) {
                id = lastId;
            } else {
                Integer c = ids.get(data);
                if (c == null) ids.put(data, c = ApiConvert.stateId(data, blocks));
                lastData = data;
                lastId = id = c;
            }
            buf.set(x, y, z, id);
        }

        @Override
        public int size() { return buf.size(); }
    }

    /** 이 스레드가 그 칸을 가진 region 스레드여야 한다(아니면 ThreadOwnershipException). 그 region 을 돌려준다(없으면 null). */
    Region ownedRegion(BlockPos pos, String what) {
        ThreadOwner owner = (ThreadOwner) world.chunkScheduler(pos.chunkX(), pos.chunkZ());
        if (!owner.isOwnerThread()) {
            throw new ThreadOwnershipException(what + " can only be called on the region thread that owns that cell (current thread: " + Thread.currentThread().getName() + ") - use the Async variant");
        }
        Region r = world.regionizer().ownerOf(pos.chunkX(), pos.chunkZ());
        // 맞춤 지점(N2b-4, 설계 3.3-2 R10): 플러그인이 NMS 거울에 쓴 것이 들여오기를 기다리면 먼저 맞춘 뒤 읽고 쓴다 — 대기가 없으면 volatile 읽기 하나
        world.syncMirrorIfPending(r, pos.chunkX(), pos.chunkZ());
        return r;
    }

    /** 소유 스레드 전용 동기 쓰기(Block.set). */
    boolean setNow(BlockPos pos, BlockData data) {
        int state = ApiConvert.stateId(data, world.blockRegistry());
        Region r = ownedRegion(pos, "Block.set(" + pos + ")");
        return r != null && placeOwned(r, pos, state);
    }

    @Override
    public Optional<BlockSnapshot> blockNow(BlockPos pos) {
        Region r = ownedRegion(pos, "blockNow(" + pos + ")");
        return r == null ? Optional.empty() : read(r, pos);
    }

    @Override
    public TaskScheduler scheduler(BlockPos pos) { return host.profiled().wrap(world.chunkScheduler(pos.chunkX(), pos.chunkZ())); }

    // ------------------------------------------------------------------ 바이옴

    private List<String> biomeNames() {
        var regs = core().registries();
        if (regs == null) {
            var dp = core().dataPacks();
            return dp != null && dp.loot() != null ? dp.loot().registries().entries(GameRegistryStore.BIOME_REGISTRY) : List.of();
        }
        return regs.entries(GameRegistryStore.BIOME_REGISTRY);
    }

    @Override
    public CompletableFuture<Optional<String>> biomeAt(BlockPos pos) {
        ChunkPos cp = new ChunkPos(pos.chunkX(), pos.chunkZ());
        List<String> names = biomeNames();
        CompletableFuture<Optional<String>> f = world.submitAtOwnedChunk(cp, r -> {
            if (!inHeight(pos.y()) || !r.chunkLoaded(cp)) return Optional.<String>empty();
            int id = r.noiseBiomeIdAtBlock(pos.x(), pos.y(), pos.z());
            return id >= 0 && id < names.size() ? Optional.of(names.get(id)) : Optional.<String>empty();
        });
        return RegionFuture.wrap(f != null ? f : CompletableFuture.completedFuture(Optional.empty()));
    }

    /** 바이옴 레지스트리 키 목록(번호 순) — 청크 내부 다리(ChunkInternals.biomeKeys)가 쓴다. */
    List<String> biomeKeyList() { return biomeNames(); }

    /** 바이옴 이름의 레지스트리 번호. 모르거나 꺼진 플러그인의 바이옴이면 IllegalArgumentException. */
    int biomeId(String biome) {
        if (biome == null) throw new NullPointerException("biome");
        String key = biome.indexOf(':') < 0 ? "minecraft:" + biome : biome;
        int id = biomeNames().indexOf(key);
        if (id < 0) throw new IllegalArgumentException("unknown biome: " + biome);
        GameRegistryStore store = host.pluginsImpl().gameRegistries();
        GameRegistryStore.Entry<?> custom = store == null ? null : store.biome(key);
        if (custom != null && !custom.active()) throw new IllegalArgumentException("biome of a disabled plugin: " + biome);
        return id;
    }

    /** 바이옴 번호의 이름, 범위 밖이면 null. */
    String biomeName(int id) {
        List<String> names = biomeNames();
        return id >= 0 && id < names.size() ? names.get(id) : null;
    }

    @Override
    public CompletableFuture<Boolean> setBiome(BlockPos pos, String biome) {
        int id;
        try {
            id = biomeId(biome);
        } catch (IllegalArgumentException e) {
            return RegionFuture.wrap(CompletableFuture.failedFuture(e));
        }
        ChunkPos cp = new ChunkPos(pos.chunkX(), pos.chunkZ());
        CompletableFuture<Boolean> f = world.submitAtOwnedChunk(cp, r -> {
            if (!inHeight(pos.y()) || !r.chunkLoaded(cp)) return false;
            int qx = (pos.x() >> 2) << 2, qy = (pos.y() >> 2) << 2, qz = (pos.z() >> 2) << 2;
            return r.fillBiomes(qx, qy, qz, qx, qy, qz, id, b -> true) > 0;
        });
        return RegionFuture.wrap(f != null ? f : CompletableFuture.completedFuture(false));
    }

    @Override
    public Optional<String> biomeNow(BlockPos pos) {
        Region r = ownedRegion(pos, "biomeNow(" + pos + ")");
        ChunkPos cp = new ChunkPos(pos.chunkX(), pos.chunkZ());
        if (r == null || !r.chunkLoaded(cp)) return Optional.empty();   // 높이 밖은 끝 칸(LevelChunk.getNoiseBiome 처럼 죈다)
        return Optional.ofNullable(biomeName(r.noiseBiomeIdAtBlock(pos.x(), pos.y(), pos.z())));
    }

    @Override
    public boolean setBiomeNow(BlockPos pos, String biome) {
        int id = biomeId(biome);
        Region r = ownedRegion(pos, "setBiomeNow(" + pos + ")");
        ChunkPos cp = new ChunkPos(pos.chunkX(), pos.chunkZ());
        if (r == null || !r.chunkLoaded(cp)) return false;
        return r.setNoiseBiomeAtBlock(pos.x(), pos.y(), pos.z(), id);
    }

    // ------------------------------------------------------------------ 소리·입자

    @Override
    public void playSound(org.domi.furnace.api.audience.Sound sound, double x, double y, double z, long seed) {
        java.util.Objects.requireNonNull(sound, "sound");
        var pkt = ApiEffects.soundAt(sound, x, y, z, seed);
        double range = ApiEffects.range(sound.volume());
        // PlayerList.broadcast: 같은 차원에서 거리 제곱 < range² (죽음 화면·크레딧 중인 플레이어도 받는다)
        world.forEachConnectedPlayerHere(p -> {
            if (p.isDisconnected()) return;
            double dx = x - p.x(), dy = y - p.y(), dz = z - p.z();
            if (dx * dx + dy * dy + dz * dz < range * range) p.send(pkt);
        });
    }

    @Override
    public void playSound(org.domi.furnace.api.audience.Sound sound, Entity entity, long seed) {
        java.util.Objects.requireNonNull(sound, "sound");
        java.util.Objects.requireNonNull(entity, "entity");
        // Paper: TrackedEntity.broadcastAndSend — 그 엔티티를 보는 사람 + 플레이어면 자기 자신
        if (entity instanceof PlayerHandle ph) {
            var self = ph.handle();
            if (self.isDisconnected() || !world.dimension().equals(self.dimension())) return;
            var pkt = ApiEffects.soundOn(sound, self.entityId(), seed);
            world.broadcastToTrackers(self.entityId(), pkt);
            self.send(pkt);
        } else if (entity instanceof EntityHandle eh && eh.tasks().owner() instanceof org.domi.furnace.server.entity.Entity e) {
            if (e.isRemoved() || !world.dimension().equals(e.dimensionKey())) return;
            world.broadcastToTrackers(e.id(), ApiEffects.soundOn(sound, e.id(), seed));
        }
    }

    @Override
    public void spawnParticle(org.domi.furnace.api.world.ParticleEffect particle, java.util.Collection<? extends org.domi.furnace.api.entity.Player> receivers,
                              double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double speed, boolean force) {
        var opts = ApiEffects.particle(particle, world.blockRegistry(), world.itemRegistry());
        var pkt = new org.domi.furnace.server.net.protocol.play.LevelParticles(force, x, y, z, (float) offsetX, (float) offsetY, (float) offsetZ,
                (float) speed, count, opts);
        java.util.function.Consumer<org.domi.furnace.server.entity.Player> send = p -> {
            // ServerLevel.sendParticles(player, …): 플레이어가 이 레벨에 있고 칸 가운데가 범위 안
            if (p.isDisconnected() || !world.dimension().equals(p.dimension())) return;
            if (org.domi.furnace.server.block.ChunkLevel.particleInRange(p, force, x, y, z)) p.send(pkt);
        };
        if (receivers == null) {
            world.forEachPlayerHere(send);
        } else {
            for (var r : receivers) if (r instanceof PlayerHandle ph) send.accept(ph.handle());
        }
    }

    // ------------------------------------------------------------------ 청크

    @Override
    public ChunkHandle chunkAt(int chunkX, int chunkZ) { return new ChunkHandle(host, this, chunkX, chunkZ); }

    @Override
    public boolean isChunkLoaded(int chunkX, int chunkZ) { return world.isChunkLoaded(chunkX, chunkZ); }

    @Override
    public CompletableFuture<Boolean> loadChunkAsync(int chunkX, int chunkZ, boolean generate) {
        ChunkPos cp = new ChunkPos(chunkX, chunkZ);
        // 그 청크의 region 스레드에서 이미 올라와 있으면 곧바로(Paper: 주 스레드에서 올라와 있으면 완료된 Future)
        if (((ThreadOwner) world.chunkScheduler(chunkX, chunkZ)).isOwnerThread()) {
            Region here = world.regionizer().ownerOf(chunkX, chunkZ);
            if (here != null && here.chunkLoaded(cp)) {
                world.addTicket(chunkX, chunkZ, 0, PLUGIN_TICKET_TICKS);
                return CompletableFuture.completedFuture(true);
            }
        }
        if (generate) return loadOnOwner(cp);
        // 생성하지 않음: 올라와 있으면 그대로, 아니면 그 지역 파일 레인에서 디스크에 있는지 본다(앞선 저장 뒤에 줄 선다).
        // 사슬은 주인 작업 안에서 잇는다 — 호출 스레드에서 thenCompose 로 이으면 앞 단계가 먼저 끝났을 때 다음 단계와 결과가
        // 호출 스레드에서 돌고 끝난다(T-161: BukkitChunkAsyncTest 가 "없음(false)" 결과를 시험 스레드에서 받은 흔들림).
        CompletableFuture<Boolean> out = new CompletableFuture<>();
        world.submitAtChunk(cp, r -> {
            if (r.chunkLoaded(cp)) {
                loadOnOwner(cp, out);
                return null;
            }
            if (!(world.worldSource() instanceof org.domi.furnace.server.world.gen.AnvilWorldSource anvil)) {
                out.complete(false);
                return null;
            }
            CompletableFuture.supplyAsync(() -> {
                try {
                    return anvil.store().hasChunk(chunkX, chunkZ);
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }, world.ioLane(chunkX, chunkZ)).whenComplete((onDisk, ex) -> {
                if (ex != null) failOnOwner(cp, out, ex);   // 디스크 확인 실패도 주인 스레드에서 끝낸다(PR #401 검토 N2)
                else if (onDisk) loadOnOwner(cp, out);
                else completeOnOwner(cp, out, false);
            });
            return null;
        }).exceptionally(ex -> {   // 내린 월드·주인 작업이 던짐
            out.completeExceptionally(ex);
            return null;
        });
        return out;
    }

    /** 그 청크 주인 region 스레드에서 out 을 value 로 끝낸다. */
    private void completeOnOwner(ChunkPos cp, CompletableFuture<Boolean> out, boolean value) {
        world.submitAtChunk(cp, r -> out.complete(value)).exceptionally(ex -> {
            out.completeExceptionally(ex);
            return null;
        });
    }

    /** 그 청크 주인 region 스레드에서 out 을 cause 로 실패시킨다(내린 월드면 그 거절로). */
    private void failOnOwner(ChunkPos cp, CompletableFuture<Boolean> out, Throwable cause) {
        world.submitAtChunk(cp, r -> out.completeExceptionally(cause)).exceptionally(ex -> {
            out.completeExceptionally(cause);
            return null;
        });
    }

    @Override
    public boolean refreshChunk(int chunkX, int chunkZ) {
        if (!world.isChunkLoaded(chunkX, chunkZ)) return false;
        ChunkPos cp = new ChunkPos(chunkX, chunkZ);
        world.runAtChunk(cp, r -> { if (r.chunkLoaded(cp)) r.resendChunk(cp); });
        return true;
    }

    /**
     * Paper Add-Plugin-Tickets-to-API-Chunk-Methods: getChunkAtAsync 로 얻은 청크는 PLUGIN 티켓(레벨 33 — 올려만 두고 틱은 안 함)을
     * min(20, chunk-gc 주기) 틱 붙잡는다. 청크 퇴출 유예(chunk-eviction-grace-ticks)가 20 보다 짧아도 그만큼은 남게 같은 시간 티켓을 건다.
     */
    private static final long PLUGIN_TICKET_TICKS = 20;

    /**
     * 주인 region 에서 올리고 그 region 스레드에서 완료한다. 사슬을 주인 작업 안에서 잇는다 — 밖에서 thenCompose 로 이으면
     * 주인 작업이 붙이기보다 먼저 끝났을 때 돌려준 Future 가 호출 스레드에서 끝난다(CompletableFuture 규칙).
     */
    private CompletableFuture<Boolean> loadOnOwner(ChunkPos cp) {
        CompletableFuture<Boolean> out = new CompletableFuture<>();
        loadOnOwner(cp, out);
        return out;
    }

    private void loadOnOwner(ChunkPos cp, CompletableFuture<Boolean> out) {
        world.submitAtChunk(cp, r -> r.loadChunk(cp).whenComplete((col, ex) -> {
            if (ex != null) {
                out.completeExceptionally(ex);
                return;
            }
            if (col != null) world.addTicket(cp.x(), cp.z(), 0, PLUGIN_TICKET_TICKS);
            out.complete(col != null);
        })).exceptionally(ex -> {   // 내린 월드·주인 작업이 던짐
            out.completeExceptionally(ex);
            return null;
        });
    }

    // ------------------------------------------------------------------ 엔티티

    @Override
    public CompletableFuture<Entity> spawnEntity(String type, Location at) {
        String id = type.indexOf(':') < 0 ? "minecraft:" + type : type;
        if (!org.domi.furnace.server.command.arguments.EntityTypes.isKnown(id) || id.equals("minecraft:player") || id.equals("minecraft:fishing_bobber")) {
            return RegionFuture.wrap(CompletableFuture.failedFuture(new IllegalArgumentException("entity type that cannot be spawned: " + type)));
        }
        if (!at.world().equals(world.dimension())) {
            return RegionFuture.wrap(CompletableFuture.failedFuture(new IllegalArgumentException("position in another dimension: " + at.world())));
        }
        if (!Double.isFinite(at.x()) || !Double.isFinite(at.y()) || !Double.isFinite(at.z()) || Math.abs(at.x()) >= 3.0E7 || Math.abs(at.z()) >= 3.0E7) {
            return RegionFuture.wrap(CompletableFuture.failedFuture(new IllegalArgumentException("invalid position: " + at)));
        }
        ChunkPos cp = ChunkPos.ofBlock(at.x(), at.z());
        CompletableFuture<Entity> f = world.submitAtOwnedChunk(cp, r -> spawnOn(r, cp, id, at));
        return RegionFuture.wrap(f != null ? f : CompletableFuture.failedFuture(new IllegalStateException("the chunk is not loaded: " + cp)));
    }

    /** 소유 스레드에서: /summon 과 같은 경로로 만든다. 스폰 이벤트가 취소되면 null. */
    private Entity spawnOn(Region r, ChunkPos cp, String id, Location at) {
        if (!r.chunkLoaded(cp)) throw new IllegalStateException("the chunk is not loaded: " + cp);
        var level = r.level();
        // /summon 과 같은 경로(SummonCommand.summonAt): 종류 id 만 든 태그로 만들고, 몹은 finalizeSpawn(COMMAND)
        var tag = new org.domi.furnace.server.net.io.nbt.NbtCompound();
        tag.putString("id", id);
        var e = org.domi.furnace.server.entity.Entities.load(level, tag);
        if (e == null) throw new IllegalArgumentException("entity type that cannot be created: " + id);
        e.moveTo(at.x(), at.y(), at.z(), at.yaw(), at.pitch());
        if (e instanceof org.domi.furnace.server.entity.Mob mob) {
            if (mob instanceof org.domi.furnace.server.entity.Villager v) v.setFinalizeReason(org.domi.furnace.server.entity.spawn.SpawnReason.COMMAND);
            mob.finalizeSpawnGroup(level.random(), level.getCurrentDifficultyAt(mob.blockPosition()), null);
        }
        if (!level.addFreshEntityWithPassengers(e.spawnReason("CUSTOM"))) return null;   // EntitySpawnEvent 취소(또는 UUID 겹침)
        return EntityHandle.of(host, e);
    }

    private String spawnableId(String type, Location at) {
        String id = type.indexOf(':') < 0 ? "minecraft:" + type : type;
        if (!org.domi.furnace.server.command.arguments.EntityTypes.isKnown(id) || id.equals("minecraft:player") || id.equals("minecraft:fishing_bobber")) {
            throw new IllegalArgumentException("entity type that cannot be spawned: " + type);
        }
        if (!at.world().equals(world.dimension())) throw new IllegalArgumentException("position in another dimension: " + at.world());
        if (!Double.isFinite(at.x()) || !Double.isFinite(at.y()) || !Double.isFinite(at.z()) || Math.abs(at.x()) >= 3.0E7 || Math.abs(at.z()) >= 3.0E7) {
            throw new IllegalArgumentException("invalid position: " + at);
        }
        return id;
    }

    @Override
    public Entity spawnEntityNow(String type, Location at) {
        String id = spawnableId(type, at);
        ChunkPos cp = ChunkPos.ofBlock(at.x(), at.z());
        Region r = ownedRegion(new BlockPos(cp.x() << 4, 0, cp.z() << 4), "spawnEntityNow");
        if (r == null) throw new IllegalStateException("the chunk is not loaded: " + cp);
        return spawnOn(r, cp, id, at);
    }

    /** 소유 스레드에서: 구 안의 엔티티와 플레이어. */
    private List<Entity> nearbyOn(Region r, Location center, double radius) {
        double r2 = radius * radius;
        List<Entity> out = new ArrayList<>();
        var box = new org.domi.furnace.server.entity.AABB(center.x() - radius, center.y() - radius, center.z() - radius,
                center.x() + radius, center.y() + radius, center.z() + radius);
        for (var e : r.level().getEntities(null, box, null)) {
            if (e.isRemoved()) continue;
            double dx = e.x() - center.x(), dy = e.y() - center.y(), dz = e.z() - center.z();
            if (dx * dx + dy * dy + dz * dz <= r2) out.add(EntityHandle.of(host, e));
        }
        for (var p : r.players().values()) {
            if (p.isDisconnected()) continue;
            double dx = p.x() - center.x(), dy = p.y() - center.y(), dz = p.z() - center.z();
            if (dx * dx + dy * dy + dz * dz <= r2) out.add(host.player(p));
        }
        return out;
    }

    @Override
    public List<Entity> nearbyEntitiesNow(Location center, double radius) {
        if (!(radius >= 0 && radius <= 128)) throw new IllegalArgumentException("radius must be 0..128: " + radius);
        if (!center.world().equals(world.dimension())) return List.of();
        ChunkPos cp = ChunkPos.ofBlock(center.x(), center.z());
        Region r = ownedRegion(new BlockPos(cp.x() << 4, 0, cp.z() << 4), "nearbyEntitiesNow");
        if (r == null || !r.chunkLoaded(cp)) return List.of();
        return nearbyOn(r, center, radius);
    }

    /**
     * 상자를 읽을 수 있는 region 들. region 스레드: 지금 스레드가 돌리는 region 하나(소유 구역 밖은 이 스레드가 만질 수 없다 — 상자 가운데가 어디든).
     * 전역 스레드의 빌림 구간: 상자와 겹치는 region 을 모두 빌려 합친다. 그 밖의 스레드는 ThreadOwnershipException.
     */
    private List<Region> regionsForBox(org.domi.furnace.api.world.Aabb box, String what) {
        var rz = world.regionizer();
        List<Region> regions;
        Region own;
        if (!org.domi.furnace.server.world.RegionBorrow.active() && (own = rz.ownedByCurrentThread()) != null) {
            regions = List.of(own);
        } else if (org.domi.furnace.server.world.RegionBorrow.active()) {
            regions = new ArrayList<>();
            for (Region r : rz.ownersOverlapping((int) Math.floor(box.minX()) >> 4, (int) Math.floor(box.maxX()) >> 4,
                    (int) Math.floor(box.minZ()) >> 4, (int) Math.floor(box.maxZ()) >> 4)) {
                if (org.domi.furnace.server.world.RegionBorrow.claim(() -> org.domi.furnace.server.world.Regionizer.live(r))) {
                    Region now = org.domi.furnace.server.world.Regionizer.live(r);
                    if (now != null && !regions.contains(now)) regions.add(now);
                }
            }
        } else {
            throw new ThreadOwnershipException(what + " can only be called on a region thread (current thread: " + Thread.currentThread().getName() + ") - use the Async variant");
        }
        return regions;
    }

    @Override
    public List<Entity> entitiesInBoxNow(org.domi.furnace.api.world.Aabb box) {
        if (!Double.isFinite(box.minX() + box.minY() + box.minZ() + box.maxX() + box.maxY() + box.maxZ())) return List.of();
        List<Region> regions = regionsForBox(box, "entitiesInBoxNow");
        var bb = ApiBoxes.of(box);
        List<Entity> out = new ArrayList<>();
        for (Region r : regions) {
            if (r.level() == null) continue;
            for (var e : r.level().getEntities(null, bb, null)) {
                if (!e.isRemoved()) out.add(EntityHandle.of(host, e));
            }
            for (var p : r.players().values()) {
                if (!p.isDisconnected() && p.boundingBox().intersects(bb)) out.add(host.player(p));
            }
        }
        return out;
    }

    @Override
    public java.util.Optional<org.domi.furnace.api.world.BlockHit> rayTraceBlocksNow(double fx, double fy, double fz, double tx, double ty, double tz,
            org.domi.furnace.api.world.FluidPick fluid, boolean ignorePassable, java.util.function.Predicate<BlockPos> canCollide) {
        ChunkPos cp = ChunkPos.ofBlock(fx, fz);
        Region r = ownedRegion(new BlockPos(cp.x() << 4, 0, cp.z() << 4), "rayTraceBlocksNow");
        if (r == null || !Double.isFinite(fx + fy + fz + tx + ty + tz)) return java.util.Optional.empty();
        var mode = switch (fluid) {
            case NONE -> org.domi.furnace.server.block.Clip.FluidMode.NONE;
            case SOURCE_ONLY -> org.domi.furnace.server.block.Clip.FluidMode.SOURCE_ONLY;
            case ANY -> org.domi.furnace.server.block.Clip.FluidMode.ANY;
        };
        java.util.function.Predicate<org.domi.furnace.server.block.Pos> pass = canCollide == null ? null : p -> canCollide.test(new BlockPos(p.x(), p.y(), p.z()));
        var hit = org.domi.furnace.server.block.Clip.clipApi(r.level(), fx, fy, fz, tx, ty, tz, mode, ignorePassable, pass);
        if (hit.miss()) return java.util.Optional.empty();
        return java.util.Optional.of(new org.domi.furnace.api.world.BlockHit(new BlockPos(hit.pos().x(), hit.pos().y(), hit.pos().z()),
                org.domi.furnace.api.world.Direction.values()[hit.face()], hit.x(), hit.y(), hit.z(), hit.inside()));
    }

    @Override
    public CompletableFuture<List<Entity>> nearbyEntities(Location center, double radius) {
        if (!(radius >= 0 && radius <= 128)) return RegionFuture.wrap(CompletableFuture.failedFuture(new IllegalArgumentException("radius must be 0..128: " + radius)));
        if (!center.world().equals(world.dimension())) return RegionFuture.wrap(CompletableFuture.completedFuture(List.of()));
        ChunkPos cp = ChunkPos.ofBlock(center.x(), center.z());
        double r2 = radius * radius;
        CompletableFuture<List<Entity>> f = world.submitAtOwnedChunk(cp, r -> nearbyOn(r, center, radius));
        return RegionFuture.wrap(f != null ? f : CompletableFuture.completedFuture(List.of()));
    }

    // ------------------------------------------------------------------ id 로 찾기

    /** 이 월드에 접속해 있는 그 id 의 플레이어(플레이어는 엔티티 색인에 없다). */
    private Entity playerById(int id) {
        for (var p : host.core().players()) {
            if (p.entityId() == id && !p.isDisconnected() && world.dimension().equals(p.dimension())) return host.player(p);
        }
        return null;
    }

    @Override
    public Optional<java.util.UUID> entityUuidById(int entityId) {
        var e = world.entityIndex().get(entityId);
        if (e != null && !e.isRemoved()) return Optional.of(e.uuid());   // uuid 는 바뀌지 않는다(어느 스레드나 안전)
        Entity p = playerById(entityId);
        return p == null ? Optional.empty() : Optional.of(p.uniqueId());
    }

    @Override
    public Optional<Entity> entityByIdNow(int entityId) {
        var e = world.entityIndex().get(entityId);
        if (e == null || e.isRemoved()) return Optional.ofNullable(playerById(entityId));
        // 엔티티를 가진 region 스레드(빌림 구간이면 빌려서)에서만 — 아니면 낡은 값을 읽지 않고 소유 스레드가 아니라고 답한다.
        // region 사이를 넘어가는 중(옛 저장소에서 빠졌고 새 쪽이 아직 받기 전)에는 level 이 옛 쪽이라도 저장소 소속을 본다.
        boolean owned = (org.domi.furnace.server.world.RegionBorrow.claimEntity(e) || (e.level() != null && e.level().isOwnerThread()))
                && e.level() != null && e.level().entities().contains(e);
        if (!owned) {
            throw new ThreadOwnershipException("entityByIdNow can only be called on the region thread that owns that entity (current thread: "
                    + Thread.currentThread().getName() + ") - use entityById");
        }
        return e.isRemoved() ? Optional.empty() : Optional.of(EntityHandle.of(host, e));
    }

    @Override
    public CompletableFuture<Optional<Entity>> entityById(int entityId) {
        return RegionFuture.wrap(entityByIdAttempt(entityId, 0));
    }

    /**
     * 색인에서 찾은 엔티티를 가진 region 에서 읽는다. 청크 경계를 넘은 틱에는 엔티티가 새 청크 쪽에 가 있어도 옛 region 저장소에서 아직
     * 안 빠졌다(틱 끝 handOffEntities) — 새 칸의 주인에 없으면 지난 틱 칸(xo, zo)의 주인에서 한 번 더 찾고, 그래도 없으면(그 사이 옮겨 갔으면) 몇 번 다시 찾는다.
     */
    private CompletableFuture<Optional<Entity>> entityByIdAttempt(int id, int attempt) {
        var e = world.entityIndex().get(id);
        if (e == null || e.isRemoved()) return CompletableFuture.completedFuture(Optional.ofNullable(playerById(id)));
        ChunkPos now = ChunkPos.ofBlock(e.x(), e.z());
        ChunkPos before = ChunkPos.ofBlock(e.xo, e.zo);
        CompletableFuture<Optional<Entity>> f = lookupAt(now, id);
        if (f != null && !before.equals(now)) {
            f = f.thenCompose(found -> {
                if (found != null) return CompletableFuture.completedFuture(found);
                CompletableFuture<Optional<Entity>> g = lookupAt(before, id);
                return g != null ? g : CompletableFuture.<Optional<Entity>>completedFuture(null);
            });
        }
        if (f == null) {
            if (attempt >= MAX_LOOKUP_ATTEMPTS) return CompletableFuture.completedFuture(Optional.empty());
            return entityByIdAttempt(id, attempt + 1);
        }
        return f.thenCompose(found -> {
            if (found != null) return CompletableFuture.completedFuture(found);
            if (attempt >= MAX_LOOKUP_ATTEMPTS) return CompletableFuture.completedFuture(Optional.<Entity>empty());
            return entityByIdAttempt(id, attempt + 1);
        });
    }

    /** 그 청크의 주인 region 에서 id 의 엔티티를 읽는다. 저장소에 없으면 null 로 완료, 그 청크에 주인이 없으면 null(future 없음). */
    private CompletableFuture<Optional<Entity>> lookupAt(ChunkPos cp, int id) {
        return world.submitAtOwnedChunk(cp, r -> {
            var cur = r.level().entities().get(id);
            if (cur == null) return null;
            return cur.isRemoved() ? Optional.<Entity>empty() : Optional.<Entity>of(EntityHandle.of(host, cur));
        });
    }

    private static final int MAX_LOOKUP_ATTEMPTS = 3;

    // ------------------------------------------------------------------ UUID 로 찾기(T-040b)

    /** 이 월드에 접속해 있는 그 UUID 의 플레이어. */
    private Entity playerByUuid(java.util.UUID uuid) {
        for (var p : host.core().players()) {
            if (uuid.equals(p.uuid()) && !p.isDisconnected() && world.dimension().equals(p.dimension())) return host.player(p);
        }
        return null;
    }

    @Override
    public java.util.OptionalInt entityIdByUuid(java.util.UUID uuid) {
        java.util.Objects.requireNonNull(uuid, "uuid");
        var e = world.entityUuidIndex().get(uuid);
        // id 색인 확인: UUID 는 스폰 이벤트 전에 잡히지만 엔티티는 이벤트가 끝나야 레벨에 들어간다(Paper 는 처리기 안에서 못 찾는다)
        if (e != null && !e.isRemoved() && world.entityIndex().get(e.id()) == e) return java.util.OptionalInt.of(e.id());   // id 는 바뀌지 않는다(어느 스레드나 안전)
        Entity p = playerByUuid(uuid);
        return p == null ? java.util.OptionalInt.empty() : java.util.OptionalInt.of(p.entityId());
    }

    @Override
    public Optional<Entity> entityByUuidNow(java.util.UUID uuid) {
        java.util.Objects.requireNonNull(uuid, "uuid");
        var e = world.entityUuidIndex().get(uuid);
        if (e == null || e.isRemoved()) return Optional.ofNullable(playerByUuid(uuid));
        return entityByIdNow(e.id());   // 같은 소유 검사(빌림 구간 포함)·저장소 소속 검사
    }

    @Override
    public CompletableFuture<Optional<Entity>> entityByUuid(java.util.UUID uuid) {
        java.util.Objects.requireNonNull(uuid, "uuid");
        var e = world.entityUuidIndex().get(uuid);
        if (e == null || e.isRemoved()) return RegionFuture.wrap(CompletableFuture.completedFuture(Optional.ofNullable(playerByUuid(uuid))));
        return entityById(e.id());
    }

    // ------------------------------------------------------------------ 블록 칸

    @Override
    public CompletableFuture<Optional<Inventory>> inventoryAt(BlockPos pos) {
        ChunkPos cp = new ChunkPos(pos.chunkX(), pos.chunkZ());
        CompletableFuture<Optional<Inventory>> f = world.submitAtOwnedChunk(cp, r -> inventoryOn(r, pos));
        return RegionFuture.wrap(f != null ? f : CompletableFuture.completedFuture(Optional.empty()));
    }

    @Override
    public Optional<Inventory> inventoryAtNow(BlockPos pos) {
        Region r = ownedRegion(pos, "inventoryAtNow(" + pos + ")");
        return r == null ? Optional.empty() : inventoryOn(r, pos);
    }

    private Optional<Inventory> inventoryOn(Region r, BlockPos pos) {
        if (!inHeight(pos.y())) return Optional.empty();
        var col = r.ownedColumn(new ChunkPos(pos.chunkX(), pos.chunkZ()));
        if (col == null || !(col.blockEntityAt(pos.x(), pos.y(), pos.z()) instanceof org.domi.furnace.server.block.entity.ContainerBlockEntity be)
                || be instanceof org.domi.furnace.server.block.entity.CampfireBlockEntity) {   // 모닥불은 Bukkit 에서도 Inventory 가 아니다
            return Optional.empty();
        }
        return Optional.<Inventory>of(new OwnedInventory(host, be.type(), be.size(), blockRunner(pos, be)));
    }

    /** 그 칸의 region 에서 처음 찾은 그 블록 엔티티가 아직 있으면 메뉴 문맥(MenuBlockContext) 안에서 op 를 돌린다 — 바뀐 칸은 저장 표시·비교기 갱신까지 메뉴 클릭과 같다. */
    private OwnedInventory.Runner blockRunner(BlockPos pos, org.domi.furnace.server.block.entity.ContainerBlockEntity original) {
        ChunkPos cp = new ChunkPos(pos.chunkX(), pos.chunkZ());
        return new OwnedInventory.Runner() {
            @Override
            public <T> CompletableFuture<T> run(java.util.function.Function<OwnedInventory.Slots, T> op) {
                // 그 칸을 가진 region 스레드면 곧바로(Bukkit 블록 인벤토리처럼 동기로 읽고 쓰는 호출자용) — 결과는 끝난 Future
                if (((ThreadOwner) world.chunkScheduler(cp.x(), cp.z())).isOwnerThread()) {
                    Region here = world.regionizer().ownerOf(cp.x(), cp.z());
                    if (here == null) return CompletableFuture.failedFuture(new IllegalStateException("the chunk is not loaded: " + cp));
                    try {
                        return CompletableFuture.completedFuture(applyOn(here, op));
                    } catch (RuntimeException e) {
                        return CompletableFuture.failedFuture(e);
                    }
                }
                CompletableFuture<T> f = world.submitAtOwnedChunk(cp, r -> applyOn(r, op));
                return f != null ? f : CompletableFuture.failedFuture(new IllegalStateException("the chunk is not loaded: " + cp));
            }

            private <T> T applyOn(Region r, java.util.function.Function<OwnedInventory.Slots, T> op) {
                {
                    var col = r.ownedColumn(cp);
                    if (col == null || col.blockEntityAt(pos.x(), pos.y(), pos.z()) != original) throw new IllegalStateException("the container at that cell is gone: " + pos);
                    // 바닐라 RandomizableContainerBlockEntity: 칸을 읽거나 쓰기 전에 열지 않은 전리품 표를 먼저 푼다(unpackLootTable(null))
                    if (original.lootTable != null) {
                        core().unpackLoot(world, original, null);
                        r.markBlockEntityChanged(original);
                    }
                    var c = new org.domi.furnace.server.world.BlockEntityContainer(original);
                    Object[] out = new Object[1];
                    org.domi.furnace.server.world.MenuBlockContext.runIn(r, () -> out[0] = op.apply(new OwnedInventory.Slots() {
                        public int size() { return c.size(); }

                        public org.domi.furnace.server.entity.ItemStack get(int slot) { return c.get(slot); }

                        public void set(int slot, org.domi.furnace.server.entity.ItemStack s) { c.set(slot, s); }
                    }));
                    @SuppressWarnings("unchecked") T t = (T) out[0];
                    return t;
                }
            }
        };
    }

    // ------------------------------------------------------------------ 스폰 정책·엔티티 수

    @Override
    public org.domi.furnace.api.world.SpawnPolicy spawnPolicy() { return spawnPolicy; }

    @Override
    public CompletableFuture<org.domi.furnace.api.world.EntityCounts> entityCounts() {
        EntityTally total = new EntityTally();
        List<CompletableFuture<Void>> fs = world.forEachRegion(r -> {
            EntityTally local = new EntityTally();
            for (var e : r.level().entities().all()) if (!e.isRemoved()) local.add(e);
            for (var p : r.players().values()) if (!p.isDisconnected()) local.addPlayer();
            total.merge(local);
        });
        return RegionFuture.wrap(CompletableFuture.allOf(fs.toArray(new CompletableFuture[0])).thenApply(v -> total.build()));
    }

    // ------------------------------------------------------------------ 마을·POI·습격

    private <T> CompletableFuture<T> onBlock(BlockPos pos, java.util.function.BiFunction<Region, Pos, T> task, T notLoaded) {
        ChunkPos cp = new ChunkPos(pos.chunkX(), pos.chunkZ());
        CompletableFuture<T> f = world.submitAtOwnedChunk(cp, r -> r.chunkLoaded(cp) ? task.apply(r, new Pos(pos.x(), pos.y(), pos.z())) : notLoaded);
        return RegionFuture.wrap(f != null ? f : CompletableFuture.completedFuture(notLoaded));
    }

    @Override
    public CompletableFuture<Boolean> isVillage(BlockPos pos) {
        return onBlock(pos, (r, p) -> r.level().poiManager().isVillage(p), false);
    }

    @Override
    public CompletableFuture<Integer> sectionsToVillage(BlockPos pos) {
        return onBlock(pos, (r, p) -> r.level().poiManager().sectionsToVillage(p.x() >> 4, p.y() >> 4, p.z() >> 4),
                org.domi.furnace.server.world.poi.PoiManager.MAX_VILLAGE_DISTANCE + 1);
    }

    @Override
    public CompletableFuture<List<org.domi.furnace.api.world.PointOfInterest>> pointsOfInterest(BlockPos center, int radius,
            java.util.Set<String> types, org.domi.furnace.api.world.PoiOccupancy occupancy) {
        if (radius < 0 || radius > 128) return RegionFuture.wrap(CompletableFuture.failedFuture(new IllegalArgumentException("radius must be 0..128: " + radius)));
        java.util.Objects.requireNonNull(types, "types");
        java.util.Objects.requireNonNull(occupancy, "occupancy");
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (String t : types) keys.add(t.indexOf(':') < 0 ? "minecraft:" + t : t);
        var occ = org.domi.furnace.server.world.poi.PoiManager.Occupancy.valueOf(occupancy.name());
        return onBlock(center, (r, p) -> {
            var found = r.level().poiManager().findAllClosestFirstWithType(t -> keys.isEmpty() || keys.contains(t.name()), q -> true, p, radius, occ);
            List<org.domi.furnace.api.world.PointOfInterest> out = new ArrayList<>(found.size());
            for (var rec : found) {
                out.add(new org.domi.furnace.api.world.PointOfInterest(rec.type().name(), new BlockPos(rec.pos().x(), rec.pos().y(), rec.pos().z()),
                        rec.freeTickets(), rec.type().maxTickets()));
            }
            return out;
        }, List.of());
    }

    private org.domi.furnace.api.world.RaidInfo toApi(org.domi.furnace.server.world.raid.Raid r) {
        var status = switch (r.status()) {
            case ONGOING -> org.domi.furnace.api.world.RaidStatus.ONGOING;
            case VICTORY -> org.domi.furnace.api.world.RaidStatus.VICTORY;
            case LOSS -> org.domi.furnace.api.world.RaidStatus.LOSS;
            case STOPPED -> org.domi.furnace.api.world.RaidStatus.STOPPED;
        };
        var c = r.center();
        return new org.domi.furnace.api.world.RaidInfo(r.id(), world.dimension(), new BlockPos(c.x(), c.y(), c.z()), status, r.isStarted(), r.isActive(),
                r.raidOmenLevel(), r.groupsSpawned(), r.numGroups() + (r.raidOmenLevel() > 1 ? 1 : 0), r.getTotalRaidersAlive(),
                r.getHealthOfLivingRaiders(), r.raidCooldownTicks(), r.isBetweenWaves(), r.heroesOfTheVillage());
    }

    @Override
    public CompletableFuture<List<org.domi.furnace.api.world.RaidInfo>> raids() {
        List<org.domi.furnace.api.world.RaidInfo> all = java.util.Collections.synchronizedList(new ArrayList<>());
        List<CompletableFuture<Void>> fs = world.forEachRegion(r -> {
            for (var raid : r.level().raids().all().values()) if (raid.isActive() && !raid.isStopped()) all.add(toApi(raid));
        });
        return RegionFuture.wrap(CompletableFuture.allOf(fs.toArray(new CompletableFuture[0])).thenApply(v -> {
            List<org.domi.furnace.api.world.RaidInfo> out = new ArrayList<>(all);
            out.sort(java.util.Comparator.comparingInt(org.domi.furnace.api.world.RaidInfo::id));
            return out;
        }));
    }

    @Override
    public CompletableFuture<Optional<org.domi.furnace.api.world.RaidInfo>> raidAt(BlockPos pos) {
        return onBlock(pos, (r, p) -> {
            var raid = r.level().raids().getRaidAt(p);
            return raid == null ? Optional.<org.domi.furnace.api.world.RaidInfo>empty() : Optional.of(toApi(raid));
        }, Optional.empty());
    }

    @Override
    public CompletableFuture<Boolean> stopRaid(int id) {
        java.util.concurrent.atomic.AtomicBoolean found = new java.util.concurrent.atomic.AtomicBoolean();
        List<CompletableFuture<Void>> fs = world.forEachRegion(r -> {
            var raid = r.level().raids().get(id);
            if (raid != null && !raid.isStopped()) {
                raid.stop();
                found.set(true);
            }
        });
        return RegionFuture.wrap(CompletableFuture.allOf(fs.toArray(new CompletableFuture[0])).thenApply(v -> found.get()));
    }

    // ------------------------------------------------------------------ 블록 엔티티 NBT(/data block 경로)

    /** 그 칸 주인 region 에서: 로드돼 있고 블록 엔티티가 있으면 op, 아니면 none. 주인 region 이 없으면 none(region 을 만들지 않는다). */
    private <T> CompletableFuture<T> atBlockEntity(BlockPos pos, T none,
                                                   java.util.function.BiFunction<org.domi.furnace.server.world.RegionLevel, org.domi.furnace.server.block.entity.BlockEntity, T> op) {
        Pos p = new Pos(pos.x(), pos.y(), pos.z());
        CompletableFuture<T> f = world.submitAtOwnedChunk(new ChunkPos(pos.chunkX(), pos.chunkZ()), r -> {
            var level = r.level();
            if (level == null || !inHeight(pos.y()) || !level.hasChunkAt(p)) return none;
            return level.getBlockEntity(p) instanceof org.domi.furnace.server.block.entity.BlockEntity be ? op.apply(level, be) : none;
        });
        return RegionFuture.wrap(f != null ? f : CompletableFuture.completedFuture(none));
    }

    @Override
    public CompletableFuture<Optional<org.domi.furnace.api.nbt.NbtCompound>> blockEntityNbt(BlockPos pos) {
        Pos p = new Pos(pos.x(), pos.y(), pos.z());
        return atBlockEntity(pos, Optional.empty(),
                (level, be) -> Optional.of(ApiNbt.toApi(org.domi.furnace.server.command.builtin.DataCommands.blockEntityData(level, be, p))));
    }

    @Override
    public CompletableFuture<Boolean> editBlockEntityNbt(BlockPos pos, java.util.function.Consumer<org.domi.furnace.api.nbt.NbtCompound> edit) {
        java.util.Objects.requireNonNull(edit, "edit");
        Pos p = new Pos(pos.x(), pos.y(), pos.z());
        return atBlockEntity(pos, false, (level, be) -> {
            var nbt = ApiNbt.toApi(org.domi.furnace.server.command.builtin.DataCommands.blockEntityData(level, be, p));
            var before = nbt.copy();
            edit.accept(nbt);
            if (nbt.equals(before)) return false;
            org.domi.furnace.server.command.builtin.DataCommands.setBlockEntityData(level, be, p, ApiNbt.toServer(nbt));
            level.broadcastChanges();
            return true;
        });
    }

    @Override
    public Optional<org.domi.furnace.api.nbt.NbtCompound> blockEntityNbtNow(BlockPos pos) {
        Pos p = new Pos(pos.x(), pos.y(), pos.z());
        var level = ownedLevelAt(pos, "blockEntityNbtNow(" + pos + ")");
        if (level == null || !(level.getBlockEntity(p) instanceof org.domi.furnace.server.block.entity.BlockEntity be)) return Optional.empty();
        return Optional.of(ApiNbt.toApi(org.domi.furnace.server.command.builtin.DataCommands.blockEntityData(level, be, p)));
    }

    @Override
    public boolean setBlockEntityNbtNow(BlockPos pos, org.domi.furnace.api.nbt.NbtCompound nbt) {
        java.util.Objects.requireNonNull(nbt, "nbt");
        Pos p = new Pos(pos.x(), pos.y(), pos.z());
        var level = ownedLevelAt(pos, "setBlockEntityNbtNow(" + pos + ")");
        if (level == null || !(level.getBlockEntity(p) instanceof org.domi.furnace.server.block.entity.BlockEntity be)) return false;
        var tag = ApiNbt.toServer(nbt);
        be.beforeSnapshotLoad(tag);
        org.domi.furnace.server.command.builtin.DataCommands.setBlockEntityData(level, be, p, tag);
        level.broadcastChanges();
        return true;
    }

    @Override
    public boolean editBlockEntityPersistentDataNow(BlockPos pos, java.util.function.Consumer<org.domi.furnace.api.persistence.PersistentDataContainer> edit) {
        java.util.Objects.requireNonNull(edit, "edit");
        var level = ownedLevelAt(pos, "editBlockEntityPersistentDataNow(" + pos + ")");
        if (level == null || !(level.getBlockEntity(new Pos(pos.x(), pos.y(), pos.z())) instanceof org.domi.furnace.server.block.entity.BlockEntity be)) return false;
        editBukkitValues(level, be, edit);
        return true;
    }

    @Override
    public CompletableFuture<Boolean> editBlockEntityPersistentData(BlockPos pos, java.util.function.Consumer<org.domi.furnace.api.persistence.PersistentDataContainer> edit) {
        java.util.Objects.requireNonNull(edit, "edit");
        return atBlockEntity(pos, false, (level, be) -> {
            editBukkitValues(level, be, edit);
            return true;
        });
    }

    /** 블록 엔티티 영속 데이터만 고친다(Paper: 살아 있는 블록 엔티티 PDC). 바뀌지 않았으면 바뀜 표시도 하지 않는다. */
    private static void editBukkitValues(org.domi.furnace.server.block.BlockLevel level, org.domi.furnace.server.block.entity.BlockEntity be,
                                         java.util.function.Consumer<org.domi.furnace.api.persistence.PersistentDataContainer> edit) {
        var tag = be.bukkitValues(true);
        var before = tag.copy();
        try {
            edit.accept(new NbtPersistentData(tag, false));
        } finally {
            be.settleBukkitValues(level, !org.domi.furnace.server.net.io.nbt.NbtTags.equal(tag, before));
        }
    }

    /** 소유 스레드 확인 뒤 그 칸의 레벨(높이 밖·로드 안 됨이면 null). */
    private org.domi.furnace.server.world.RegionLevel ownedLevelAt(BlockPos pos, String what) {
        Region r = ownedRegion(pos, what);
        var level = r == null ? null : r.level();
        if (level == null || !inHeight(pos.y()) || !level.hasChunkAt(new Pos(pos.x(), pos.y(), pos.z()))) return null;
        return level;
    }

    // ------------------------------------------------------------------ 시간·날씨·스폰·폴더(T-141)

    /** 시간·날씨의 주인: 주 오버월드(모든 차원이 그 시계를 함께 쓴다 — World.shareServerState). */
    @Override
    public boolean ownsClock() { return world == core().world(); }

    @Override
    public boolean isFlat() { return world.isFlatLevel(); }

    /** 생성기가 쥔 바이옴 공급자(T-210 보통 월드, T-203 임시 월드). */
    @Override
    public org.domi.furnace.api.worldgen.BiomeProvider biomeProvider() {
        var ws = world.worldSource();
        if (ws instanceof org.domi.furnace.server.world.gen.AnvilWorldSource a) ws = a.base();
        else if (ws instanceof org.domi.furnace.server.world.gen.ScratchWorldSource t) ws = t.base();
        return ws instanceof org.domi.furnace.server.world.gen.NoiseWorldSource n ? n.biomeProvider() : null;
    }

    /** 만들 때 단 월드젠 훅 객체(T-217). */
    @Override
    public org.domi.furnace.api.worldgen.ChunkGenerationHooks generationHooksObject() {
        var ws = baseSource();
        org.domi.furnace.server.world.gen.GenHooks h = ws instanceof org.domi.furnace.server.world.gen.NoiseWorldSource n ? n.generationHooks()
                : ws instanceof org.domi.furnace.server.world.gen.SuperflatWorldSource f ? f.generationHooks() : null;
        return h == null ? null : h.object();
    }

    @Override
    public void addChunkDecorator(org.domi.furnace.api.worldgen.ChunkDecorator decorator) {
        java.util.Objects.requireNonNull(decorator, "decorator");
        var d = world.worldSource().chunkDecorators();
        if (d == null) throw new UnsupportedOperationException("this world's generator has no decoration step");
        d.add(decorator);
    }

    @Override
    public boolean removeChunkDecorator(org.domi.furnace.api.worldgen.ChunkDecorator decorator) {
        var d = world.worldSource().chunkDecorators();
        return d != null && d.remove(decorator);
    }

    private org.domi.furnace.server.world.gen.WorldSource baseSource() {
        var ws = world.worldSource();
        if (ws instanceof org.domi.furnace.server.world.gen.AnvilWorldSource a) ws = a.base();
        else if (ws instanceof org.domi.furnace.server.world.gen.ScratchWorldSource t) ws = t.base();
        return ws;
    }

    @Override
    public boolean generatesStructures() { return world.generatesStructures(); }

    /** 추가 월드도 주 월드 level.dat 값을 쓴다(WorldManager 가 그 값으로 만든다). */
    @Override
    public boolean isHardcore() {
        var ld = core().levelData();
        return ld != null && ld.hardcore;
    }

    @Override
    public long dayTime() { return world.clock().dayTime(); }

    @Override
    public long gameTime() { return world.clock().gameTime(); }

    @Override
    public boolean setDayTime(long time) {
        if (!ownsClock()) return false;
        var clock = world.clock();
        clock.setDayTime(time);
        // Paper CraftWorld#setFullTime: 바뀐 시계를 쓰는 접속자에게 곧바로 시각을 보낸다(다음 20틱 동기화를 기다리지 않는다).
        // Furnace 는 모든 차원이 이 시계를 함께 쓰므로 모든 접속자다(네더·엔드 클라이언트는 고정 시각이라 무해).
        var pkt = clock.timePacket(core().gameRules());
        for (var p : core().players()) if (!p.isDisconnected()) p.send(pkt);
        return true;
    }

    @Override
    public org.domi.furnace.api.world.WeatherState weather() {
        var c = world.clock();
        synchronized (c) {
            return new org.domi.furnace.api.world.WeatherState(c.rainingFlag(), c.thunderingFlag(), c.rainTime(), c.thunderTime(), c.clearWeatherTime());
        }
    }

    @Override
    public boolean setWeather(Boolean raining, Boolean thundering, Integer rainTime, Integer thunderTime, Integer clearWeatherTime) {
        if (!ownsClock()) return false;
        world.clock().setWeather(raining, thundering, rainTime, thunderTime, clearWeatherTime);
        return true;
    }

    @Override
    public Location spawnLocation() {
        var sp = world.defaultSpawn();
        return new Location(world.dimension(), sp.x(), sp.y(), sp.z(), sp.yaw(), 0f);
    }

    @Override
    public boolean setSpawnLocation(int x, int y, int z, float angle) {
        if (world == core().world()) {
            core().setWorldSpawn(x, y, z, angle);
            return true;
        }
        if (!world.isExtraWorld()) return false;   // 네더·엔드: 오버월드 스폰을 함께 쓴다(바닐라 DerivedLevelData)
        var old = world.defaultSpawn();
        if (old.x() == x && old.y() == y && old.z() == z && old.yaw() == angle) return true;
        world.defaultSpawn(new org.domi.furnace.server.world.SpawnPoint(x, y, z, angle));   // level.dat 에는 WorldManager 가 저장 때 옮긴다
        var pkt = new org.domi.furnace.server.net.protocol.play.SetDefaultSpawnPosition(x, y, z, angle);
        for (var p : core().players()) p.send(pkt);   // 바닐라 setDefaultSpawnPos: broadcastAll
        return true;
    }

    @Override
    public java.nio.file.Path folder() {
        // CraftBukkit getDimensionPath = DimensionType.getStorageFolder(레벨 폴더, 차원 종류): 청크 저장소와 같은 폴더
        // (네더 환경 추가 월드는 <이름>/DIM-1, 엔드 환경은 <이름>/DIM1).
        return world.dimensionType().storageDir(world.levelRoot());
    }

    @Override
    public List<Entity> entitiesSnapshot() {
        List<Entity> out = new ArrayList<>();
        // Paper 는 isValid 로 거른다: 지워졌거나 죽어 가는(체력 0) 생물·죽은 플레이어는 빠진다. 다른 스레드 읽기라 한 틱 경합은 허용.
        for (var e : world.entityIndex().values()) if (e.isAlive()) out.add(EntityHandle.of(host, e));
        for (var p : core().players()) {
            if (!p.isDisconnected() && p.isAlive() && world.dimension().equals(p.dimension())) out.add(host.player(p));
        }
        return out;
    }

    @Override
    public List<Entity> entitiesInChunkNow(int chunkX, int chunkZ) {
        // 소유를 먼저 확인한다 — region 스레드면 지금 도는 region 만 보이므로 남의 청크에서 조용히 빈 목록이 되지 않게
        if (!org.domi.furnace.server.world.RegionBorrow.active()) ownedRegion(new BlockPos(chunkX << 4, 0, chunkZ << 4), "entitiesInChunkNow");
        double minX = chunkX << 4, minZ = chunkZ << 4;
        var box = new org.domi.furnace.api.world.Aabb(minX, -1.0E7, minZ, minX + 16, 1.0E7, minZ + 16);
        List<Entity> out = new ArrayList<>();
        for (Region r : regionsForBox(box, "entitiesInChunkNow")) {
            if (r.level() == null) continue;
            // Paper ChunkEntitySlices.getChunkEntities: 그 청크 칸의 엔티티 중 isValid(살아 있음) 인 것
            for (var e : r.level().getEntities(null, ApiBoxes.of(box), null)) {
                if (e.isAlive() && inChunk(e.x(), e.z(), chunkX, chunkZ)) out.add(EntityHandle.of(host, e));
            }
            for (var p : r.players().values()) {
                if (!p.isDisconnected() && p.isAlive() && inChunk(p.x(), p.z(), chunkX, chunkZ)) out.add(host.player(p));
            }
        }
        return out;
    }

    private static boolean inChunk(double x, double z, int chunkX, int chunkZ) {
        return ((int) Math.floor(x) >> 4) == chunkX && ((int) Math.floor(z) >> 4) == chunkZ;
    }

    @Override
    public Entity dropItemNow(Location at, org.domi.furnace.api.item.ItemStack stack, boolean naturally, java.util.function.Consumer<Entity> beforeSpawn) {
        java.util.Objects.requireNonNull(at, "Location cannot be null");
        java.util.Objects.requireNonNull(stack, "ItemStack cannot be null");
        if (!at.world().equals(world.dimension())) throw new IllegalArgumentException("position in another dimension: " + at.world());
        ChunkPos cp = ChunkPos.ofBlock(at.x(), at.z());
        Region r = ownedRegion(new BlockPos(cp.x() << 4, 0, cp.z() << 4), "dropItemNow");
        if (r == null || !r.chunkLoaded(cp)) throw new IllegalStateException("the chunk is not loaded: " + cp);
        var level = r.level();
        double x = at.x(), y = at.y(), z = at.z();
        if (naturally) {   // Paper CraftWorld#dropItemNaturally: 월드 난수 nextFloat 셋(x·y·z 순)
            x += level.random().nextFloat() * 0.5F + 0.25D;
            y += level.random().nextFloat() * 0.5F + 0.25D;
            z += level.random().nextFloat() * 0.5F + 0.25D;
        }
        var item = new org.domi.furnace.server.entity.ItemEntity(level, level.nextEntityId(), x, y, z, ApiConvert.item(stack, world.itemRegistry()));
        item.setDefaultPickUpDelay();   // Paper: pickupDelay = 10
        Entity handle = EntityHandle.of(host, item);
        if (beforeSpawn != null) beforeSpawn.accept(handle);
        if (!level.addFreshEntityWithPassengers(item.spawnReason("CUSTOM"))) return null;
        return handle;
    }

    // ------------------------------------------------------------------ 블록 빛·드롭·부수기(T-141 C, Paper CraftBlock)

    /** 소유 스레드이고 청크가 로드된 칸의 레벨(아니면 ThreadOwnershipException / IllegalStateException). */
    private org.domi.furnace.server.world.RegionLevel loadedLevel(BlockPos pos, String what) {
        Region r = ownedRegion(pos, what + "(" + pos + ")");
        if (r == null || !r.chunkLoaded(new ChunkPos(pos.chunkX(), pos.chunkZ()))) throw new IllegalStateException("the chunk is not loaded: " + pos);
        return r.level();
    }

    @Override
    public int lightLevelNow(BlockPos pos) { return loadedLevel(pos, "lightLevelNow").getMaxLocalRawBrightness(new Pos(pos.x(), pos.y(), pos.z())); }

    @Override
    public int skyLightNow(BlockPos pos) { return loadedLevel(pos, "skyLightNow").getSkyLight(new Pos(pos.x(), pos.y(), pos.z())); }

    @Override
    public int blockLightNow(BlockPos pos) { return loadedLevel(pos, "blockLightNow").getBlockLight(new Pos(pos.x(), pos.y(), pos.z())); }

    /** CraftBlockData.isPreferredTool: 도구가 필요 없는 블록이거나 맞는 도구. */
    private static boolean preferredTool(int state, org.domi.furnace.server.entity.ItemStack tool) {
        return !org.domi.furnace.server.block.BlockInfo.get().requiresCorrectToolForDrops(org.domi.furnace.server.block.Blocks.get().blockOf(state))
                || org.domi.furnace.server.item.ItemTools.isCorrectToolForDrops(tool, state);
    }

    private org.domi.furnace.server.entity.ItemStack serverTool(org.domi.furnace.api.item.ItemStack tool) {
        return tool == null ? org.domi.furnace.server.entity.ItemStack.EMPTY : ApiConvert.item(tool, world.itemRegistry());
    }

    @Override
    public List<org.domi.furnace.api.item.ItemStack> dropsNow(BlockPos pos, org.domi.furnace.api.item.ItemStack tool) {
        var level = loadedLevel(pos, "dropsNow");
        Pos p = new Pos(pos.x(), pos.y(), pos.z());
        int state = level.getBlockState(p);
        var nms = serverTool(tool);
        if (tool != null && !preferredTool(state, nms)) return List.of();
        var drops = org.domi.furnace.server.block.BlockBehaviors.of(state).getDrops(state, level, p, level.getBlockEntity(p), null, nms);
        List<org.domi.furnace.api.item.ItemStack> out = new ArrayList<>(drops.size());
        for (var d : drops) out.add(ApiConvert.item(d, world.itemRegistry()));
        return out;
    }

    /**
     * Paper 1.21.1 CraftBlock#breakNaturally(item, triggerEffect, dropExperience) 순서 그대로: 드롭(경험치 빼고) → 효과 → 경험치 →
     * removeBlock → destroy → 얼음·거북알 뒷처리. 드롭 조건의 "공기"는 minecraft:air 하나뿐이다(동굴 공기는 조건을 통과한다).
     */
    @Override
    public boolean breakNaturallyNow(BlockPos pos, org.domi.furnace.api.item.ItemStack tool, boolean triggerEffect, boolean dropExperience) {
        var level = loadedLevel(pos, "breakNaturallyNow");
        Pos p = new Pos(pos.x(), pos.y(), pos.z());
        int state = level.getBlockState(p);
        var behavior = org.domi.furnace.server.block.BlockBehaviors.of(state);
        var nms = serverTool(tool);
        boolean result = false;
        if (org.domi.furnace.server.block.Blocks.get().blockOf(state) != AIR_BLOCK && (tool == null || preferredTool(state, nms))) {
            level.dropResources(state, p, level.getBlockEntity(p), null, nms, false);
            if (triggerEffect) {
                if (behavior instanceof org.domi.furnace.server.block.types.BaseFireBlock) level.levelEvent(null, 1009, p, 0);   // SOUND_EXTINGUISH_FIRE
                else level.levelEvent(null, 2001, p, state);                                                                  // PARTICLES_DESTROY_BLOCK
            }
            if (dropExperience) level.popExperience(p, behavior.expDrop(state, level, p, nms));
            result = true;
        }
        boolean destroyed = level.removeBlock(p, false);
        if (destroyed) behavior.destroy(level, p, state);
        if (result) {
            if (behavior instanceof org.domi.furnace.server.block.types.IceBlock ice) ice.afterDestroy(level, p, nms);
            else if (behavior instanceof org.domi.furnace.server.block.types.TurtleEggBlock egg) egg.decreaseEggs(level, p, state);
        }
        level.broadcastChanges();
        return destroyed && result;
    }

    // ------------------------------------------------------------------ 나무 생성(T-141 E5, Paper CraftRegionAccessor#generateTree)

    @Override
    public org.domi.furnace.api.world.FeaturePlacement placeFeatureNow(String configuredFeature, BlockPos pos, java.util.Random random, boolean apply) {
        java.util.Objects.requireNonNull(configuredFeature, "configuredFeature");
        var level = loadedLevel(pos, "placeFeatureNow");
        var r = level.placeFeatureWith(configuredFeature, new Pos(pos.x(), pos.y(), pos.z()),
                new org.domi.furnace.server.world.JavaRandomWorldgen(random), apply);
        if (r == null) throw new IllegalArgumentException("unknown configured feature: " + configuredFeature);
        return placement(r);
    }

    @Override
    public org.domi.furnace.api.world.FeaturePlacement growChorusPlantNow(BlockPos pos, java.util.Random random, boolean apply) {
        var level = loadedLevel(pos, "growChorusPlantNow");
        return placement(level.chorusPlantWith(new Pos(pos.x(), pos.y(), pos.z()), new org.domi.furnace.server.world.JavaRandomWorldgen(random), apply));
    }

    private org.domi.furnace.api.world.FeaturePlacement placement(org.domi.furnace.server.world.LiveFeatures.Placement r) {
        List<BlockSnapshot> out = new ArrayList<>(r.writes().size());
        for (long[] w : r.writes()) {
            out.add(new BlockSnapshot(world.dimension(), new BlockPos((int) w[0], (int) w[1], (int) w[2]),
                    ApiConvert.block((int) w[3], world.blockRegistry())));
        }
        return new org.domi.furnace.api.world.FeaturePlacement(r.placed(), out);
    }

    private static final int AIR_BLOCK = org.domi.furnace.server.block.Blocks.get().block("minecraft:air");
}
