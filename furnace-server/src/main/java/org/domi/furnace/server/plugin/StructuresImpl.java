// 구조물 템플릿 API 구현(W4/43번): 저장소·배치기·구조물 찾기는 구조물 블록·/place template·/locate 가 쓰는 것을 그대로 쓴다.
// 놓기·저장은 영역의 청크를 임시 티켓으로 붙잡아 모두 올린 뒤, 시작 청크를 가진 region 스레드에서 한 번에 한다.
package org.domi.furnace.server.plugin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.structure.PlaceOptions;
import org.domi.furnace.api.structure.PlaceResult;
import org.domi.furnace.api.structure.SaveOptions;
import org.domi.furnace.api.structure.SaveResult;
import org.domi.furnace.api.structure.StructureLocation;
import org.domi.furnace.api.structure.StructureTemplateInfo;
import org.domi.furnace.api.structure.Structures;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.server.block.entity.StructureBlockEntity;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.LiveGrid;
import org.domi.furnace.server.world.RegionLevel;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.block.BlockShapes;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.domi.furnace.server.world.gen.carver.LegacyRandom;
import org.domi.furnace.server.world.gen.structure.StructureRegistry;
import org.domi.furnace.server.world.gen.structure.template.BlockStateTable;
import org.domi.furnace.server.world.gen.structure.template.PlaceSettings;
import org.domi.furnace.server.world.gen.structure.template.Processor;
import org.domi.furnace.server.world.gen.structure.template.Processors;
import org.domi.furnace.server.world.gen.structure.template.StructureTemplate;
import org.domi.furnace.server.world.gen.structure.template.TemplateManager;
import org.domi.furnace.server.world.gen.structure.template.TemplatePlacer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StructuresImpl implements Structures {

    private static final Logger log = LoggerFactory.getLogger(StructuresImpl.class);
    /** 구조물 블록과 같은 한 변 상한. */
    private static final int SAVE_MAX = StructureBlockEntity.MAX_SIZE;
    /** 영역의 청크가 한 region 에 모이기를 기다리는 시도 횟수와 간격(합쳐지기는 region 이 쉬는 동안 일어난다). */
    private static final int ATTEMPTS = 20;
    private static final long RETRY_DELAY_MS = 100;
    private static final int MAX_LOCATE_RADIUS = 100;

    private final PluginHost host;

    /** 찾기는 구조물 시작점을 여러 개 계산할 수 있어 틱 스레드 밖에서 한다(LocateCommand 와 같다). */
    private static final ExecutorService LOCATE = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "furnace-structure-locate");
        t.setDaemon(true);
        return t;
    });

    StructuresImpl(PluginHost host) { this.host = host; }

    private static World server(org.domi.furnace.api.world.World w) {
        Objects.requireNonNull(w, "world");
        if (w instanceof WorldHandle h) return h.handle();
        throw new IllegalArgumentException("이 서버의 월드가 아니다: " + w);
    }

    private static TemplateManager manager(World w) {
        if (w.blockRegistry() == null || w.worldDir() == null) throw new IllegalStateException("블록 레지스트리가 없는 월드는 구조물 템플릿을 못 쓴다: " + w.dimension());
        return TemplateManager.forWorld(w.worldDir(), w.blockRegistry());
    }

    private static ResourceId idOf(String id) {
        Objects.requireNonNull(id, "id");
        return ResourceId.tryParse(id);
    }

    // ------------------------------------------------------------------ 저장소

    @Override
    public List<String> templates(org.domi.furnace.api.world.World world) {
        List<String> out = new ArrayList<>();
        for (ResourceId id : manager(server(world)).listTemplates()) out.add(id.toString());
        return out;
    }

    @Override
    public boolean hasTemplate(org.domi.furnace.api.world.World world, String id) {
        ResourceId r = idOf(id);
        return r != null && manager(server(world)).get(r).isPresent();
    }

    private Optional<StructureTemplate> template(org.domi.furnace.api.world.World world, String id) {
        ResourceId r = idOf(id);
        return r == null ? Optional.empty() : manager(server(world)).get(r);
    }

    private static StructureTemplateInfo info(TemplateManager mgr, ResourceId id, StructureTemplate t) {
        boolean onDisk;
        try {
            onDisk = Files.isRegularFile(mgr.pathOf(id));
        } catch (IllegalArgumentException e) {
            onDisk = false;
        }
        return new StructureTemplateInfo(id.toString(), t.sizeX, t.sizeY, t.sizeZ, t.palettes().size(),
                t.palettes().isEmpty() ? 0 : t.palettes().get(0).size(), t.entities().size(), t.author(), onDisk);
    }

    @Override
    public Optional<StructureTemplateInfo> templateInfo(org.domi.furnace.api.world.World world, String id) {
        ResourceId r = idOf(id);
        if (r == null) return Optional.empty();
        TemplateManager mgr = manager(server(world));
        return mgr.get(r).map(t -> info(mgr, r, t));
    }

    @Override
    public Optional<NbtCompound> exportTemplate(org.domi.furnace.api.world.World world, String id) {
        ResourceId r = idOf(id);
        if (r == null) return Optional.empty();
        TemplateManager mgr = manager(server(world));
        return mgr.get(r).map(t -> ApiNbt.toApi(t.toNbt(mgr.table())));
    }

    @Override
    public Optional<byte[]> exportTemplateFile(org.domi.furnace.api.world.World world, String id) {
        ResourceId r = idOf(id);
        if (r == null) return Optional.empty();
        TemplateManager mgr = manager(server(world));
        Optional<StructureTemplate> t = mgr.get(r);
        if (t.isEmpty()) return Optional.empty();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            NbtIo.writeGzip(out, t.get().toNbt(mgr.table()), "");
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return Optional.of(out.toByteArray());
    }

    @Override
    public boolean importTemplate(org.domi.furnace.api.world.World world, String id, NbtCompound nbt, boolean toDisk) {
        Objects.requireNonNull(nbt, "nbt");
        return define(world, id, ApiNbt.toServer(nbt), toDisk);
    }

    @Override
    public boolean importTemplateFile(org.domi.furnace.api.world.World world, String id, byte[] gzipNbt, boolean toDisk) {
        Objects.requireNonNull(gzipNbt, "gzipNbt");
        org.domi.furnace.server.net.io.nbt.NbtCompound root;
        try {
            root = NbtIo.readGzip(new ByteArrayInputStream(gzipNbt));
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("gzip NBT 를 읽을 수 없다: " + e, e);
        }
        return define(world, id, root, toDisk);
    }

    private boolean define(org.domi.furnace.api.world.World world, String id, org.domi.furnace.server.net.io.nbt.NbtCompound root, boolean toDisk) {
        ResourceId r = idOf(id);
        if (r == null) throw new IllegalArgumentException("올바르지 않은 구조물 이름: " + id);
        TemplateManager mgr = manager(server(world));
        mgr.define(r, root);
        return !toDisk || mgr.save(r);
    }

    @Override
    public boolean deleteTemplate(org.domi.furnace.api.world.World world, String id) {
        ResourceId r = idOf(id);
        if (r == null) return false;
        TemplateManager mgr = manager(server(world));
        boolean memory = mgr.hasInMemory(r);
        boolean file;
        try {
            file = mgr.deleteGenerated(r);
        } catch (IllegalArgumentException e) {
            return false;
        }
        mgr.remove(r);
        return memory || file;
    }

    // ------------------------------------------------------------------ 영역 붙잡기·다시 시도

    /** 청크 범위의 가운데·반경(LiveGrid.covering 과 같은 정사각 격자). */
    private record Cover(int cx, int cz, int radius) {
        static Cover of(int minCx, int minCz, int maxCx, int maxCz) {
            int cx = Math.floorDiv(minCx + maxCx, 2), cz = Math.floorDiv(minCz + maxCz, 2);
            return new Cover(cx, cz, Math.max(Math.max(cx - minCx, maxCx - cx), Math.max(cz - minCz, maxCz - cz)));
        }
    }

    /** 반복 시도의 결과: done 이 거짓이면 청크가 아직 한 region 에 모이지 못한 것이라 다시 한다. */
    private record Attempt<T>(boolean done, T value) {}

    /**
     * 영역을 임시 티켓(틱 없이 붙잡기만)으로 올려 두고 body 를 시작 청크의 region 에서 돌린다. body 가 done=false 를 돌려주면(청크가 다른
     * region 이거나 아직 합치는 중) 잠깐 뒤 다시 하고, 끝내 안 되면 exhausted 를 돌려준다. 티켓은 끝나면 놓는다.
     */
    private <T> CompletableFuture<T> withRegion(World w, Cover c, ChunkPos start, java.util.function.Function<RegionLevel, Attempt<T>> body, T exhausted) {
        CompletableFuture<Void> loaded;
        try {
            loaded = w.addPluginTicket(c.cx, c.cz, c.radius, false);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        CompletableFuture<T> result = loaded.thenCompose(v -> tryRegion(w, start, body, exhausted, ATTEMPTS));
        result.whenComplete((v, t) -> w.removePluginTicket(c.cx, c.cz, c.radius, false));
        return RegionFuture.wrap(result);
    }

    private <T> CompletableFuture<T> tryRegion(World w, ChunkPos start, java.util.function.Function<RegionLevel, Attempt<T>> body, T exhausted, int left) {
        CompletableFuture<Attempt<T>> f = w.submitAtChunk(start, r -> body.apply(r.level()));
        return f.thenCompose(a -> {
            if (a.done()) return CompletableFuture.completedFuture(a.value());
            if (left <= 1) return CompletableFuture.completedFuture(exhausted);
            Supplier<CompletableFuture<T>> again = () -> tryRegion(w, start, body, exhausted, left - 1);
            return CompletableFuture.supplyAsync(again, CompletableFuture.delayedExecutor(RETRY_DELAY_MS, TimeUnit.MILLISECONDS)).thenCompose(x -> x);
        });
    }

    private static boolean allOwned(RegionLevel level, int minCx, int minCz, int maxCx, int maxCz) {
        for (int cx = minCx; cx <= maxCx; cx++) for (int cz = minCz; cz <= maxCz; cz++) if (level.listenerChunk(cx, cz) == null) return false;
        return true;
    }

    // ------------------------------------------------------------------ 놓기

    @Override
    public CompletableFuture<PlaceResult> place(org.domi.furnace.api.world.World world, String id, BlockPos origin, PlaceOptions options) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(options, "options");
        World w = server(world);
        ResourceId rid = idOf(id);
        if (rid == null) return CompletableFuture.completedFuture(new PlaceResult(PlaceResult.Status.TEMPLATE_NOT_FOUND, null, null));
        TemplateManager mgr = manager(w);
        // 템플릿 읽기는 파일 입출력이라 부른 스레드(region 일 수 있다)가 아니라 비동기 실행기에서
        return CompletableFuture.supplyAsync(() -> mgr.get(rid), host.asyncExecutor()).thenCompose(opt -> {
            if (opt.isEmpty()) return CompletableFuture.completedFuture(new PlaceResult(PlaceResult.Status.TEMPLATE_NOT_FOUND, null, null));
            return placeTemplate(w, mgr, opt.get(), origin, options);
        });
    }

    private CompletableFuture<PlaceResult> placeTemplate(World w, TemplateManager mgr, StructureTemplate whole, BlockPos origin, PlaceOptions o) {
        StructureTemplate t = whole;
        if (o.palette() >= 0) {
            if (o.palette() >= whole.palettes().size()) return CompletableFuture.completedFuture(new PlaceResult(PlaceResult.Status.INVALID_PALETTE, null, null));
            t = whole.onlyPalette(o.palette());
        }
        if (t.sizeX < 1 || t.sizeY < 1 || t.sizeZ < 1 || t.palettes().isEmpty()) {
            return CompletableFuture.completedFuture(new PlaceResult(PlaceResult.Status.EMPTY_TEMPLATE, null, null));
        }
        final StructureTemplate tpl = t;
        int rotation = o.rotation().ordinal(), mirror = o.mirror().ordinal();   // 열거 순서가 BlockStateTable 번호와 같다(NONE, CW90, CW180, CCW90 / NONE, LEFT_RIGHT, FRONT_BACK)
        int ox = origin.x(), oy = origin.y(), oz = origin.z();
        int[] bb = tpl.boundingBox(ox, oy, oz, rotation, 0, 0, mirror);
        if (bb[1] < w.dimensionType().minY() || bb[4] >= w.dimensionType().maxY()) {
            return CompletableFuture.completedFuture(new PlaceResult(PlaceResult.Status.OUT_OF_WORLD, null, null));
        }
        // 가장자리 블록의 모양 갱신이 영역 바로 바깥 한 칸을 읽으므로, 그 칸의 청크까지 올라와 있어야 한다(바닐라는 그 자리에서 불러온다)
        int minCx = Math.min(ox >> 4, (bb[0] - 1) >> 4), minCz = Math.min(oz >> 4, (bb[2] - 1) >> 4);
        int maxCx = Math.max(ox >> 4, (bb[3] + 1) >> 4), maxCz = Math.max(oz >> 4, (bb[5] + 1) >> 4);
        Cover cover = Cover.of(minCx, minCz, maxCx, maxCz);
        if (cover.radius > LiveGrid.MAX_GRID_RADIUS) return CompletableFuture.completedFuture(new PlaceResult(PlaceResult.Status.TOO_LARGE, null, null));
        PlaceResult notLoaded = new PlaceResult(PlaceResult.Status.NOT_LOADED, null, null);
        BlockPos min = new BlockPos(bb[0], bb[1], bb[2]), max = new BlockPos(bb[3], bb[4], bb[5]);
        return withRegion(w, cover, new ChunkPos(ox >> 4, oz >> 4), level -> {
            if (!allOwned(level, minCx, minCz, maxCx, maxCz)) return new Attempt<>(false, notLoaded);
            PlaceSettings st = new PlaceSettings();
            st.mirror = mirror;
            st.rotation = rotation;
            st.ignoreEntities = !o.includeEntities();
            if (o.integrity() < 1.0F) {
                st.processors.clear();
                st.processors.add(new Processors.BlockRot(null, o.integrity()));
                st.random = random(o.seed());
            }
            try {
                LiveGrid grid = LiveGrid.covering(level, minCx, minCz, maxCx, maxCz, 0L);
                BlockShapes shapes = BlockShapes.of(mgr.registry());
                BlockStateTable table = mgr.table();
                boolean ok = TemplatePlacer.placeInWorld(tpl, grid.view, shapes, table, new Processor.Ctx(grid.view, table),
                        ox, oy, oz, ox, oy, oz, st, random(o.seed()), 2);
                if (!ok) return new Attempt<>(true, new PlaceResult(PlaceResult.Status.FAILED, null, null));
                grid.checkComplete();
                grid.commit(2);
                return new Attempt<>(true, new PlaceResult(PlaceResult.Status.PLACED, min, max));
            } catch (LiveGrid.GridException e) {
                return new Attempt<>(true, new PlaceResult(e.tooLarge ? PlaceResult.Status.TOO_LARGE : PlaceResult.Status.FAILED, null, null));
            }
        }, notLoaded);
    }

    /** StructureBlockEntity.createRandom: 0 이면 지금 시각. */
    private static LegacyRandom random(long seed) { return StructureBlockEntity.createRandom(seed); }

    // ------------------------------------------------------------------ 저장

    @Override
    public CompletableFuture<SaveResult> save(org.domi.furnace.api.world.World world, String id, BlockPos min, int sizeX, int sizeY, int sizeZ, SaveOptions options) {
        Objects.requireNonNull(min, "min");
        Objects.requireNonNull(options, "options");
        World w = server(world);
        ResourceId rid = idOf(id);
        TemplateManager mgr = manager(w);
        if (rid == null) return CompletableFuture.completedFuture(new SaveResult(SaveResult.Status.INVALID_NAME, null));
        try {
            mgr.pathOf(rid);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(new SaveResult(SaveResult.Status.INVALID_NAME, null));
        }
        if (sizeX < 1 || sizeY < 1 || sizeZ < 1 || sizeX > SAVE_MAX || sizeY > SAVE_MAX || sizeZ > SAVE_MAX) {
            return CompletableFuture.completedFuture(new SaveResult(SaveResult.Status.INVALID_SIZE, null));
        }
        int ox = min.x(), oy = min.y(), oz = min.z();
        if (oy < w.dimensionType().minY() || oy + sizeY - 1 >= w.dimensionType().maxY()) {
            return CompletableFuture.completedFuture(new SaveResult(SaveResult.Status.OUT_OF_WORLD, null));
        }
        int minCx = ox >> 4, minCz = oz >> 4, maxCx = (ox + sizeX - 1) >> 4, maxCz = (oz + sizeZ - 1) >> 4;
        Cover cover = Cover.of(minCx, minCz, maxCx, maxCz);
        SaveResult notLoaded = new SaveResult(SaveResult.Status.NOT_LOADED, null);
        return withRegion(w, cover, new ChunkPos(minCx, minCz), level -> {
            if (!allOwned(level, minCx, minCz, maxCx, maxCz)) return new Attempt<SaveResult>(false, notLoaded);
            StructureTemplate t = StructureBlockEntity.fillFromWorld(level, mgr.table(), ox, oy, oz, sizeX, sizeY, sizeZ, options.includeEntities());
            if (t == null) return new Attempt<SaveResult>(false, notLoaded);
            t.author(options.author());
            mgr.put(rid, t);
            return new Attempt<SaveResult>(true, null);   // 저장소를 갈아 끼웠다 — 파일 쓰기는 region 밖에서
        }, notLoaded).thenCompose(r -> {
            if (r != null) return CompletableFuture.completedFuture(r);
            if (!options.toDisk()) return CompletableFuture.completedFuture(new SaveResult(SaveResult.Status.SAVED, info(mgr, rid, mgr.get(rid).orElseThrow())));
            return CompletableFuture.supplyAsync(() -> {
                boolean ok = mgr.save(rid);
                StructureTemplateInfo info = info(mgr, rid, mgr.get(rid).orElseThrow());
                return new SaveResult(ok ? SaveResult.Status.SAVED : SaveResult.Status.WRITE_FAILED, info);
            }, host.asyncExecutor());
        });
    }

    // ------------------------------------------------------------------ 찾기

    @Override
    public List<String> structureIds() { return new ArrayList<>(new TreeSet<>(StructureRegistry.structureJsons().keySet())); }

    /** 구조물 id 와 '#태그' 를 구조물 id 목록으로. 모르면 IllegalArgumentException. */
    private static List<String> resolve(Collection<String> structures) {
        if (structures.isEmpty()) throw new IllegalArgumentException("structures 가 비었다");
        List<String> out = new ArrayList<>();
        for (String s : structures) {
            Objects.requireNonNull(s, "structure");
            boolean tag = s.startsWith("#");
            String raw = tag ? s.substring(1) : s;
            ResourceId id = ResourceId.tryParse(raw);
            if (id == null) throw new IllegalArgumentException("올바르지 않은 구조물 이름: " + s);
            if (tag) {
                List<String> members = StructureRegistry.structureTag(id.toString());
                if (members == null || members.isEmpty()) throw new IllegalArgumentException("모르는 구조물 태그: " + s);
                out.addAll(members);
            } else {
                if (!StructureRegistry.structureJsons().containsKey(id.toString())) throw new IllegalArgumentException("모르는 구조물: " + s);
                out.add(id.toString());
            }
        }
        return out;
    }

    @Override
    public CompletableFuture<Optional<StructureLocation>> locate(org.domi.furnace.api.world.World world, Collection<String> structures, BlockPos from, int radius) {
        Objects.requireNonNull(from, "from");
        World w = server(world);
        List<String> ids = resolve(structures);
        int r = Math.max(1, Math.min(MAX_LOCATE_RADIUS, radius));
        NoiseWorldSource gen = NoiseWorldSource.of(w.worldSource());
        if (gen == null) return CompletableFuture.completedFuture(Optional.empty());
        return RegionFuture.wrap(CompletableFuture.supplyAsync(() -> {
            try {
                var f = gen.structureLocator().findNearest(ids, from.x(), from.y(), from.z(), r);
                return f == null ? Optional.<StructureLocation>empty() : Optional.of(new StructureLocation(f.structureId(), f.x(), f.y(), f.z()));
            } catch (RuntimeException e) {
                log.warn("구조물 찾기 실패", e);
                throw e;
            }
        }, LOCATE));
    }
}
