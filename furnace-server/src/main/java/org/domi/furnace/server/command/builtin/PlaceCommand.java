// 바닐라 PlaceCommand: /place feature|jigsaw|structure|template. 월드젠 구현(world/gen/**)을 그대로 쓴다.
// 배치는 그 칸 주인 region 스레드에서: 실제 청크의 복사본 격자에 월드젠 코드를 돌린 뒤, 바뀐 칸만 setBlock(flag 2)으로 실제 월드에 옮긴다
// (생성된 블록 엔티티 NBT·엔티티도 함께). 결과 메시지는 일이 끝난 뒤 전역 스레드에서 보낸다.
// 정직성: 아직 구현하지 않은 feature(NoopFeature)·구조물 타입은 성공으로 위장하지 않고 미지원 오류로 끝낸다.
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.entity.BlockEntities;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.EntityOps;
import org.domi.furnace.server.command.PostMessages;
import org.domi.furnace.server.command.SharedSuggestions;
import org.domi.furnace.server.command.arguments.BlockPosArgument;
import org.domi.furnace.server.command.arguments.ResourceKeyArgument;
import org.domi.furnace.server.command.arguments.ResourceLocationArgument;
import org.domi.furnace.server.command.arguments.TemplateArguments;
import org.domi.furnace.server.command.brigadier.Arguments;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.DynamicCommandExceptionType;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.command.brigadier.SuggestionProvider;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.entity.Entities;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;
import org.domi.furnace.server.world.RegionLevel;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.domi.furnace.server.world.gen.carver.LegacyRandom;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.feature.ConfiguredFeature;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.domi.furnace.server.world.gen.feature.NoopFeature;
import org.domi.furnace.server.world.gen.structure.BoundingBox;
import org.domi.furnace.server.world.gen.structure.JigsawPiece;
import org.domi.furnace.server.world.gen.structure.JigsawStructure;
import org.domi.furnace.server.world.gen.structure.PostCtx;
import org.domi.furnace.server.world.gen.structure.StructureEngine;
import org.domi.furnace.server.world.gen.structure.StructureRegistry;
import org.domi.furnace.server.world.gen.structure.StructureStart;
import org.domi.furnace.server.world.gen.structure.template.PlaceSettings;
import org.domi.furnace.server.world.gen.structure.template.Processor;
import org.domi.furnace.server.world.gen.structure.template.Processors;
import org.domi.furnace.server.world.gen.structure.template.StructureTemplate;
import org.domi.furnace.server.world.gen.structure.template.TemplatePlacer;

public final class PlaceCommand {

    private PlaceCommand() {}

    private static final SimpleCommandExceptionType ERROR_FEATURE_FAILED = new SimpleCommandExceptionType(Text.translatable("commands.place.feature.failed"));
    private static final SimpleCommandExceptionType ERROR_JIGSAW_FAILED = new SimpleCommandExceptionType(Text.translatable("commands.place.jigsaw.failed"));
    private static final SimpleCommandExceptionType ERROR_STRUCTURE_FAILED = new SimpleCommandExceptionType(Text.translatable("commands.place.structure.failed"));
    private static final DynamicCommandExceptionType ERROR_TEMPLATE_INVALID =
            new DynamicCommandExceptionType(id -> Text.translatable("commands.place.template.invalid", id));
    private static final SimpleCommandExceptionType ERROR_TEMPLATE_FAILED = new SimpleCommandExceptionType(Text.translatable("commands.place.template.failed"));
    // ResourceKeyArgument.getRegistryKey 의 "없는 id" 오류
    private static final DynamicCommandExceptionType ERROR_INVALID_FEATURE =
            new DynamicCommandExceptionType(id -> Text.translatable("commands.place.feature.invalid", id));
    private static final DynamicCommandExceptionType ERROR_INVALID_STRUCTURE =
            new DynamicCommandExceptionType(id -> Text.translatable("commands.place.structure.invalid", id));
    private static final DynamicCommandExceptionType ERROR_INVALID_JIGSAW =
            new DynamicCommandExceptionType(id -> Text.translatable("commands.place.jigsaw.invalid", id));
    // Furnace 전용: 바닐라에는 있지만 아직 구현하지 않은 것(성공으로 위장하지 않는다)
    private static final DynamicCommandExceptionType ERROR_UNSUPPORTED =
            new DynamicCommandExceptionType(what -> Text.text("Placing " + what + " is not supported yet"));

    private static final String DATA = "worldgen/1.21.1/";

    /** 바닐라 SUGGEST_TEMPLATES: 템플릿 관리자가 아는 모든 id. */
    private static final SuggestionProvider<CommandSource> SUGGEST_TEMPLATES =
            (c, b) -> SharedSuggestions.suggest(resourceIds(DATA + "structure", ".nbt"), b);

    public static void register(CommandDispatcher<CommandSource> d) {
        d.register(literal("place").requires(s -> s.hasPermission(2))
                .then(literal("feature")
                        .then(argument("feature", ResourceKeyArgument.key("minecraft:worldgen/configured_feature",
                                        () -> resourceIds(DATA + "worldgen/configured_feature", ".json")))
                                .executes(c -> placeFeature(c, null))
                                .then(argument("pos", BlockPosArgument.blockPos()).executes(c -> placeFeature(c, "pos")))))
                .then(literal("jigsaw")
                        .then(argument("pool", ResourceKeyArgument.key("minecraft:worldgen/template_pool",
                                        () -> resourceIds(DATA + "worldgen/template_pool", ".json")))
                                .then(argument("target", ResourceLocationArgument.id())
                                        .then(argument("max_depth", Arguments.integer(1, 20))
                                                .executes(c -> placeJigsaw(c, null))
                                                .then(argument("position", BlockPosArgument.blockPos()).executes(c -> placeJigsaw(c, "position")))))))
                .then(literal("structure")
                        .then(argument("structure", ResourceKeyArgument.key("minecraft:worldgen/structure",
                                        () -> StructureRegistry.structureJsons().keySet()))
                                .executes(c -> placeStructure(c, null))
                                .then(argument("pos", BlockPosArgument.blockPos()).executes(c -> placeStructure(c, "pos")))))
                .then(literal("template")
                        .then(argument("template", ResourceLocationArgument.id()).suggests(SUGGEST_TEMPLATES)
                                .executes(c -> placeTemplate(c, false, false, false, false, false))
                                .then(argument("pos", BlockPosArgument.blockPos())
                                        .executes(c -> placeTemplate(c, true, false, false, false, false))
                                        .then(argument("rotation", TemplateArguments.rotation())
                                                .executes(c -> placeTemplate(c, true, true, false, false, false))
                                                .then(argument("mirror", TemplateArguments.mirror())
                                                        .executes(c -> placeTemplate(c, true, true, true, false, false))
                                                        .then(argument("integrity", Arguments.floatArg(0.0F, 1.0F))
                                                                .executes(c -> placeTemplate(c, true, true, true, true, false))
                                                                .then(argument("seed", Arguments.integer())
                                                                        .executes(c -> placeTemplate(c, true, true, true, true, true))))))))));
    }

    // ------------------------------------------------------------------ feature

    private static int placeFeature(CommandContext<CommandSource> c, String posArg) throws CommandSyntaxException {
        CommandSource source = c.getSource();
        // 인자 순서: ResourceKeyArgument.getConfiguredFeature → (pos) getLoadedBlockPos
        ResourceId id = ResourceKeyArgument.get(c, "feature");
        String key = id.toString();
        NoiseWorldSource gen = NoiseWorldSource.of(source.level().worldSource());
        if (!Resources.has(DATA + "worldgen/configured_feature/" + id.path() + ".json", id)) throw ERROR_INVALID_FEATURE.create(key);
        if (gen == null) throw ERROR_UNSUPPORTED.create("features in this world type");
        ConfiguredFeature feature = gen.featurePlacer().biomeData().configuredFeatureById(key);
        if (feature == null || feature instanceof NoopFeature) throw ERROR_UNSUPPORTED.create("feature " + key);
        int[] p = posArg == null ? sourceBlockPos(source) : BlockPosArgument.getBlockPos(c, posArg);
        boolean loadedCheck = posArg != null;
        PostMessages post = new PostMessages();
        return post.finish(source, EntityOps.atRegion(source.level(), p[0], p[2], region -> {
            RegionLevel level = region.level();
            if (loadedCheck) checkLoadedPos(level, p);
            int cx = p[0] >> 4, cz = p[2] >> 4;
            checkLoaded(level, cx - 1, cz - 1, cx + 1, cz + 1);
            // Level.getRandom(): 서버 레벨 난수에서 한 번 뽑은 시드의 LCG(바닐라는 레벨 난수를 직접 쓴다 — 시드가 없는 난수라 관측 차이 없음)
            WorldgenRandom random = new WorldgenRandom();
            random.setSeed(level.random().nextLong());
            LiveGrid grid = new LiveGrid(level, cx, cz, 2, gen.seed());
            if (!feature.place(grid.view, random, p[0], p[1], p[2])) throw ERROR_FEATURE_FAILED.create();
            grid.commit();
            post.success(source, () -> Text.translatable("commands.place.feature.success", key, p[0], p[1], p[2]), true);
            return 1;
        }));
    }

    // ------------------------------------------------------------------ jigsaw

    private static int placeJigsaw(CommandContext<CommandSource> c, String posArg) throws CommandSyntaxException {
        CommandSource source = c.getSource();
        ResourceId poolId = ResourceKeyArgument.get(c, "pool");
        NoiseWorldSource gen = NoiseWorldSource.of(source.level().worldSource());
        String pool = poolId.toString();
        if (!Resources.has(DATA + "worldgen/template_pool/" + poolId.path() + ".json", poolId)) throw ERROR_INVALID_JIGSAW.create(pool);
        if (gen == null) throw ERROR_UNSUPPORTED.create("jigsaw structures in this world type");
        String target = ResourceLocationArgument.getId(c, "target").toString();
        int maxDepth = c.getArgument("max_depth", Integer.class);
        int[] p = posArg == null ? sourceBlockPos(source) : BlockPosArgument.getBlockPos(c, posArg);
        boolean loadedCheck = posArg != null;
        StructureEngine engine = gen.structureEngine();
        PostMessages post = new PostMessages();
        return post.finish(source, EntityOps.atRegion(source.level(), p[0], p[2], region -> {
            RegionLevel level = region.level();
            if (loadedCheck) checkLoadedPos(level, p);
            int cx = p[0] >> 4, cz = p[2] >> 4;
            checkLoaded(level, cx, cz, cx, cz);
            // JigsawPlacement.generateJigsaw
            List<JigsawPiece> pieces = JigsawStructure.generateForCommand(engine, gen, pool, target, maxDepth, p[0], p[1], p[2]);
            if (pieces == null) throw ERROR_JIGSAW_FAILED.create();
            // 바닐라는 조각이 닿는 청크를 그 자리에서 불러온다. 여기선 모두 이 region 에 로드돼 있어야 한다(아니면 일부만 놓지 않고 실패).
            int minCx = cx, minCz = cz, maxCx = cx, maxCz = cz;
            for (JigsawPiece piece : pieces) {
                BoundingBox b = piece.boundingBox();
                minCx = Math.min(minCx, b.minX() >> 4); minCz = Math.min(minCz, b.minZ() >> 4);
                maxCx = Math.max(maxCx, b.maxX() >> 4); maxCz = Math.max(maxCz, b.maxZ() >> 4);
            }
            checkLoaded(level, minCx, minCz, maxCx, maxCz);
            LiveGrid grid = LiveGrid.covering(level, minCx, minCz, maxCx, maxCz, gen.seed());
            WorldgenRandom random = new WorldgenRandom();
            random.setSeed(level.random().nextLong());
            BoundingBox infinite = new BoundingBox(Integer.MIN_VALUE / 2, Integer.MIN_VALUE / 2, Integer.MIN_VALUE / 2,
                    Integer.MAX_VALUE / 2, Integer.MAX_VALUE / 2, Integer.MAX_VALUE / 2);
            PostCtx ctx = new PostCtx(engine, grid.view, random, random, infinite, cx, cz, p[0], p[1], p[2]);
            for (JigsawPiece piece : pieces) piece.place(ctx, false);
            grid.commit();
            post.success(source, () -> Text.translatable("commands.place.jigsaw.success", p[0], p[1], p[2]), true);
            return 1;
        }));
    }

    // ------------------------------------------------------------------ structure

    private static int placeStructure(CommandContext<CommandSource> c, String posArg) throws CommandSyntaxException {
        CommandSource source = c.getSource();
        ResourceId id = ResourceKeyArgument.get(c, "structure");
        String key = id.toString();
        if (!StructureRegistry.structureJsons().containsKey(key)) throw ERROR_INVALID_STRUCTURE.create(key);
        NoiseWorldSource gen = NoiseWorldSource.of(source.level().worldSource());
        if (gen == null) throw ERROR_UNSUPPORTED.create("structures in this world type");
        int[] p = posArg == null ? sourceBlockPos(source) : BlockPosArgument.getBlockPos(c, posArg);
        boolean loadedCheck = posArg != null;
        StructureEngine engine = gen.structureEngine();
        PostMessages post = new PostMessages();
        return post.finish(source, EntityOps.atRegion(source.level(), p[0], p[2], region -> {
            RegionLevel level = region.level();
            if (loadedCheck) checkLoadedPos(level, p);
            // Structure.generate(..., new ChunkPos(pos), 0, level, biome -> true)
            StructureEngine.CommandStart cs = engine.generateForCommand(key, p[0] >> 4, p[2] >> 4, gen);
            if (!cs.supported()) throw ERROR_UNSUPPORTED.create("structure " + key);
            StructureStart start = cs.start();
            if (start == null) throw ERROR_STRUCTURE_FAILED.create();
            BoundingBox box = start.boundingBox();
            int minCx = box.minX() >> 4, minCz = box.minZ() >> 4, maxCx = box.maxX() >> 4, maxCz = box.maxZ() >> 4;
            checkLoaded(level, minCx, minCz, maxCx, maxCz);
            LiveGrid grid = LiveGrid.covering(level, minCx, minCz, maxCx, maxCz, gen.seed());
            WorldgenRandom random = new WorldgenRandom();
            random.setSeed(level.random().nextLong());
            // ChunkPos.rangeClosed: x 가 먼저 돈다
            for (int z = minCz; z <= maxCz; z++) {
                for (int x = minCx; x <= maxCx; x++) {
                    BoundingBox chunkBox = new BoundingBox(x * 16, level.minBuildHeight(), z * 16, x * 16 + 15, level.maxBuildHeight(), z * 16 + 15);
                    synchronized (start) {
                        start.placeInChunk(engine, grid.view, random, random, chunkBox, x, z);
                    }
                }
            }
            grid.commit();
            post.success(source, () -> Text.translatable("commands.place.structure.success", key, p[0], p[1], p[2]), true);
            return 1;
        }));
    }

    // ------------------------------------------------------------------ template

    private static int placeTemplate(CommandContext<CommandSource> c, boolean hasPos, boolean hasRot, boolean hasMirror, boolean hasIntegrity, boolean hasSeed)
            throws CommandSyntaxException {
        CommandSource source = c.getSource();
        ResourceId id = ResourceLocationArgument.getId(c, "template");
        int[] p = hasPos ? BlockPosArgument.getBlockPos(c, "pos") : sourceBlockPos(source);
        int rotation = hasRot ? TemplateArguments.get(c, "rotation") : org.domi.furnace.server.world.gen.structure.template.BlockStateTable.ROT_NONE;
        int mirror = hasMirror ? TemplateArguments.get(c, "mirror") : org.domi.furnace.server.world.gen.structure.template.BlockStateTable.NONE;
        float integrity = hasIntegrity ? c.getArgument("integrity", Float.class) : 1.0F;
        int seed = hasSeed ? c.getArgument("seed", Integer.class) : 0;
        NoiseWorldSource gen = NoiseWorldSource.of(source.level().worldSource());
        PostMessages post = new PostMessages();
        return post.finish(source, EntityOps.atRegion(source.level(), p[0], p[2], region -> {
            RegionLevel level = region.level();
            if (hasPos) checkLoadedPos(level, p);
            // StructureTemplateManager.get: 이 서버는 내장 템플릿만 안다(월드 generated/·데이터팩 템플릿은 아직 — 없는 것으로 본다).
            if (!Resources.has(DATA + "structure/" + id.path() + ".nbt", id)) throw ERROR_TEMPLATE_INVALID.create(id.toString());
            if (gen == null) throw ERROR_UNSUPPORTED.create("templates in this world type");
            StructureEngine engine = gen.structureEngine();
            StructureTemplate template = engine.template(id.toString());
            // checkLoaded(pos, pos + size)
            int minCx = p[0] >> 4, minCz = p[2] >> 4, maxCx = (p[0] + template.sizeX) >> 4, maxCz = (p[2] + template.sizeZ) >> 4;
            checkLoaded(level, minCx, minCz, maxCx, maxCz);
            // 회전·대칭으로 반대쪽으로 뻗는 부분도 이 region 에 로드돼 있어야 한다(바닐라는 그 자리에서 불러온다)
            if (template.sizeX > 0 && template.sizeZ > 0) {
                int[] bb = template.boundingBox(p[0], p[1], p[2], rotation, 0, 0, mirror);
                int[] r = {Math.min(minCx, bb[0] >> 4), Math.min(minCz, bb[2] >> 4), Math.max(maxCx, bb[3] >> 4), Math.max(maxCz, bb[5] >> 4)};
                checkLoaded(level, r[0], r[1], r[2], r[3]);
                minCx = r[0]; minCz = r[1]; maxCx = r[2]; maxCz = r[3];
            }
            PlaceSettings st = new PlaceSettings();
            st.mirror = mirror;
            st.rotation = rotation;
            if (integrity < 1.0F) {
                st.processors.clear();
                st.processors.add(new Processors.BlockRot(null, integrity));
                st.random = createRandom(seed);
            }
            LiveGrid grid = LiveGrid.covering(level, minCx, minCz, maxCx, maxCz, gen.seed());
            boolean ok = TemplatePlacer.placeInWorld(template, grid.view, engine.shapes(), engine.table(), new Processor.Ctx(grid.view, engine.table()),
                    p[0], p[1], p[2], p[0], p[1], p[2], st, createRandom(seed), 2);
            if (!ok) throw ERROR_TEMPLATE_FAILED.create();
            grid.commit();
            post.success(source, () -> Text.translatable("commands.place.template.success", id.toString(), p[0], p[1], p[2]), true);
            return 1;
        }));
    }

    /** StructureBlockEntity.createRandom: 0 이면 현재 시각 시드. */
    private static LegacyRandom createRandom(long seed) {
        return new LegacyRandom(seed == 0L ? System.currentTimeMillis() : seed);
    }

    // ------------------------------------------------------------------ 공통

    /** BlockPos.containing(source.getPosition()). */
    private static int[] sourceBlockPos(CommandSource s) {
        return new int[]{(int) Math.floor(s.x()), (int) Math.floor(s.y()), (int) Math.floor(s.z())};
    }

    /** BlockPosArgument.getLoadedBlockPos 의 확인(로드·월드 높이). */
    private static void checkLoadedPos(RegionLevel level, int[] p) throws CommandSyntaxException {
        Pos pos = new Pos(p[0], p[1], p[2]);
        if (!level.hasChunkAt(pos)) throw BlockPosArgument.ERROR_NOT_LOADED.create();
        if (!level.isInWorldBounds(pos)) throw BlockPosArgument.ERROR_OUT_OF_WORLD.create();
    }

    /** PlaceCommand.checkLoaded: 청크 범위가 모두 (이 region 에) 로드돼 있어야. */
    private static void checkLoaded(RegionLevel level, int minCx, int minCz, int maxCx, int maxCz) throws CommandSyntaxException {
        for (int x = minCx; x <= maxCx; x++) {
            for (int z = minCz; z <= maxCz; z++) {
                if (level.listenerChunk(x, z) == null) throw BlockPosArgument.ERROR_NOT_LOADED.create();
            }
        }
    }

    /**
     * 실제 청크의 복사본 격자. 월드젠 코드는 복사본(view)에만 쓰고, commit 이 바뀐 칸을 실제 월드에 옮긴다.
     * 반경 안에서 이 region 에 없는 청크는 null(읽으면 공기, 쓰면 버려진다).
     */
    static final class LiveGrid {
        private final RegionLevel level;
        private final int centerX, centerZ, radius, size;
        private final ChunkColumn[] live, copy;
        final FeatureWorldView view;

        LiveGrid(RegionLevel level, int centerX, int centerZ, int radius, long seed) {
            this.level = level;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.radius = radius;
            this.size = 2 * radius + 1;
            live = new ChunkColumn[size * size];
            copy = new ChunkColumn[size * size];
            int[][] biomes = new int[size * size][];
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    int i = (dz + radius) * size + (dx + radius);
                    ChunkColumn col = level.listenerChunk(centerX + dx, centerZ + dz);
                    if (col == null) continue;
                    live[i] = col;
                    copy[i] = col.copy();
                    biomes[i] = biomeIds(col);
                }
            }
            view = FeatureWorldView.liveGrid(copy, biomes, centerX, centerZ, radius, seed);
        }

        /** 청크 범위를 덮는 가장 작은 정사각 격자. */
        static LiveGrid covering(RegionLevel level, int minCx, int minCz, int maxCx, int maxCz, long seed) {
            int cx = Math.floorDiv(minCx + maxCx, 2), cz = Math.floorDiv(minCz + maxCz, 2);
            int r = Math.max(Math.max(cx - minCx, maxCx - cx), Math.max(cz - minCz, maxCz - cz));
            return new LiveGrid(level, cx, cz, Math.max(r, 1), seed);
        }

        /** FeatureWorldView 의 biome 배열 형식: (quartY * 4 + quartZ) * 4 + quartX, quartY 는 열 전체 기준. */
        private static int[] biomeIds(ChunkColumn col) {
            ChunkSection[] sections = col.sections();
            int[] ids = new int[sections.length * 64];
            for (int s = 0; s < sections.length; s++) {
                for (int qy = 0; qy < 4; qy++) {
                    for (int qz = 0; qz < 4; qz++) {
                        for (int qx = 0; qx < 4; qx++) ids[((s * 4 + qy) * 4 + qz) * 4 + qx] = sections[s].getBiome(qx, qy, qz);
                    }
                }
            }
            return ids;
        }

        /** 바뀐 칸을 실제 월드에 놓는다(flag 2). 생성된 블록 엔티티 NBT 를 붙이고 엔티티를 세계에 넣는다. 바뀐 칸 수. */
        int commit() {
            int changed = 0;
            for (int i = 0; i < live.length; i++) {
                ChunkColumn l = live[i], cp = copy[i];
                if (l == null) continue;
                int ox = (centerX + (i % size) - radius) * 16, oz = (centerZ + (i / size) - radius) * 16;
                for (int y = l.minY(); y <= l.maxY(); y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            int s = cp.getBlock(x, y, z);
                            if (s == l.getBlock(x, y, z)) continue;
                            level.setBlock(new Pos(ox + x, y, oz + z), s, 2);
                            changed++;
                        }
                    }
                }
            }
            for (int i = 0; i < live.length; i++) {
                ChunkColumn l = live[i], cp = copy[i];
                if (l == null) continue;
                int ox = (centerX + (i % size) - radius) * 16, oz = (centerZ + (i / size) - radius) * 16;
                cp.forEachGeneratedBlockEntity((lx, y, lz, nbt) -> adoptBlockEntity(l, ox + lx, y, oz + lz, nbt));
                for (NbtCompound e : cp.generatedEntities()) spawn(e);
            }
            return changed;
        }

        private void adoptBlockEntity(ChunkColumn col, int x, int y, int z, NbtCompound nbt) {
            Pos pos = new Pos(x, y, z);
            NbtCompound tag = nbt.copy();
            tag.putInt("x", x);
            tag.putInt("y", y);
            tag.putInt("z", z);
            String id = tag.tags().get("id") instanceof String s ? s : null;
            if (id != null && BlockEntities.known(id)) {
                var be = BlockEntities.loadStatic(pos, level.getBlockState(pos), tag);
                if (be != null) level.setBlockEntity(be);
                return;
            }
            // 상자·아직 모르는 종류: 청크 로드 때와 같이 생성 NBT 로 남긴다(상자는 처음 열 때 loot table 상자가 된다)
            col.removeBlockEntityAt(x, y, z);
            col.putGeneratedBlockEntity(x & 15, y, z & 15, tag);
        }

        private void spawn(NbtCompound nbt) {
            Entity e = Entities.load(level, nbt.copy());
            if (e != null) level.addFreshEntityWithPassengers(e);
        }
    }

    // ------------------------------------------------------------------ 내장 리소스 목록

    /** 내장 데이터(1.21.1)에 그 리소스가 있는가 — minecraft 이름공간만. */
    private static final class Resources {
        static boolean has(String path, ResourceId id) {
            if (!"minecraft".equals(id.namespace())) return false;
            try (InputStream in = PlaceCommand.class.getClassLoader().getResourceAsStream(path)) {
                return in != null;
            } catch (IOException e) {
                return false;
            }
        }
    }

    private static final Map<String, List<String>> LISTINGS = new ConcurrentHashMap<>();

    /** 클래스패스 디렉터리(파일·jar) 아래의 ext 파일들을 "minecraft:상대경로" id 로(정렬). */
    static List<String> resourceIds(String dir, String ext) {
        return LISTINGS.computeIfAbsent(dir + "|" + ext, k -> {
            TreeSet<String> out = new TreeSet<>();
            try {
                URL url = PlaceCommand.class.getClassLoader().getResource(dir);
                if (url == null) return List.of();
                URI uri = url.toURI();
                if ("jar".equals(uri.getScheme())) {
                    FileSystem fs;
                    try {
                        fs = FileSystems.getFileSystem(uri);
                    } catch (FileSystemNotFoundException e) {
                        fs = FileSystems.newFileSystem(uri, Map.of());
                    }
                    collect(fs.getPath(dir), ext, out);
                } else {
                    collect(Path.of(uri), ext, out);
                }
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(PlaceCommand.class).warn("리소스 목록 읽기 실패: {}", dir, e);
            }
            return List.copyOf(out);
        });
    }

    private static void collect(Path root, String ext, TreeSet<String> out) throws IOException {
        try (Stream<Path> s = Files.walk(root)) {
            s.filter(f -> f.getFileName() != null && f.getFileName().toString().endsWith(ext)).forEach(f -> {
                String rel = root.relativize(f).toString().replace('\\', '/');
                out.add("minecraft:" + rel.substring(0, rel.length() - ext.length()));
            });
        }
    }
}
