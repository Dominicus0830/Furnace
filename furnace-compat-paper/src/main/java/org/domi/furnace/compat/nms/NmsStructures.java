package org.domi.furnace.compat.nms;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.internal.FeatureInternals;
import org.domi.furnace.api.world.World;

/**
 * NMS 구조물(T-211 2단계): {@code Structure#generate}·{@code StructureStart#placeInChunk} 를 서버 구조물 코드(바닐라 1.21.1 이식)로 돌린다 —
 * 설계 {@code docs/feature-parity/compat-nms-t211-world-surface-2026-11-17.md} 7절.
 * <ul>
 *   <li>생성은 그 월드 생성기로 계산만 한다(아무 스레드).</li>
 *   <li>배치는 지형지물({@link NmsFeatures})과 같은 갈래다: 서버 레벨이고 쓰기 기록 중이 아니면 서버 월드에 바로, 그 밖은 그 레벨의 setBlock 으로
 *       (조각은 Paper 의 감싸개처럼 인자 4개·깊이 512, 템플릿과 afterPlace 는 인자 3개). 같은 레벨로 여러 청크에 놓으면 그 레벨이 앞서 받아들인 쓰기를
 *       다음 청크에서도 읽는다(레벨마다 세션 — 서버 레벨의 기록이면 지금 기록에서 새로 채운다).</li>
 *   <li>조각의 {@code world.getRandom()} 자리는 그 레벨의 getRandom() 을 뽑을 때마다 부른다(FAWE 는 배치 난수와 같은 원천을 준다).</li>
 *   <li>구조물 몹(T-221)은 바닐라처럼 만들어 {@code finalizeSpawn(world, world.getCurrentDifficultyAt(pos), STRUCTURE, null)} 한 뒤 그 레벨의
 *       {@code addFreshEntity} 로 넘긴다 — 난수·지역 난이도·달 밝기·바이옴은 그 레벨에, 고양이의 구조물 검사·난이도·시간은 서버 레벨에 묻는다.
 *       Paper 의 populator 처럼 그 레벨이 던지면(DummyGeneratorAccess 의 getCurrentDifficultyAt, 아기 좀비의 닭 찾기) 그대로 던진다.</li>
 * </ul>
 * 같은 결과를 낼 수 없으면 {@link UnsupportedNmsCall}: 서버에 없는 구조물 종류, 다른 월드의 생성기·관리자, 플러그인 레벨의 블록 엔티티 내용 바꾸기,
 * 엔티티를 받아들이는 플러그인 레벨(서버는 그 레벨에 넘길 NMS 엔티티 객체를 만들지 않는다).
 */
public final class NmsStructures {

    private NmsStructures() {}

    /** 레벨마다 세션(그 레벨이 받아들인 구조물 쓰기). 레벨이 사라지면 같이 사라진다. */
    private static final Map<WorldGenLevel, Object> SESSIONS = Collections.synchronizedMap(new WeakHashMap<>());

    /** {@link Structure#generate}. */
    public static StructureStart generate(Structure structure, RegistryAccess registryAccess, ChunkGenerator generator, BiomeSource biomeSource,
                                          RandomState randomState, StructureTemplateManager templates, long seed, ChunkPos chunkPos, int references,
                                          LevelHeightAccessor heightAccessor, Predicate<Holder<Biome>> validBiome) {
        Objects.requireNonNull(chunkPos, "chunkPos");
        Objects.requireNonNull(validBiome, "validBiome");
        var key = NmsWorldgen.structures().getKey(structure);
        if (key == null) throw new UnsupportedNmsCall("Structure#generate", "the structure is not one of the server's registered structures");
        String target = "Structure#generate(" + key + ")";
        World world = generator == null ? null : generator.furnaceWorld();
        if (world == null) throw new UnsupportedNmsCall(target, "the chunk generator must be the one of a loaded world (ServerLevel#getChunkSource().getGenerator())");
        if (registryAccess != RegistryAccess.SERVER) throw new UnsupportedNmsCall(target, "the registry access must be the server's (ServerLevel#registryAccess())");
        if (biomeSource == null || !Objects.equals(biomeSource.furnaceWorld(), world)) {
            throw new UnsupportedNmsCall(target, "the biome source must be the generator's (ChunkGenerator#getBiomeSource())");
        }
        if (randomState == null || !Objects.equals(randomState.furnaceWorld(), world)) {
            throw new UnsupportedNmsCall(target, "the random state must be the world's (ServerChunkCache#randomState())");
        }
        if (templates == null || !templates.furnaceCurrent()) {
            throw new UnsupportedNmsCall(target, "the structure template manager must be the server's (ServerLevel#getStructureManager())");
        }
        if (seed != world.seed()) throw new UnsupportedNmsCall(target, "the seed must be the world's seed (" + world.seed() + "), not " + seed);
        var chunks = NmsChunks.bridge();
        if (heightAccessor.getMinBuildHeight() != chunks.minY(world) || heightAccessor.getHeight() != chunks.height(world)) {
            throw new UnsupportedNmsCall(target, "the height accessor must have the world's build height");
        }
        FeatureInternals.StructureStartHandle h;
        try {
            h = Furnace.server().internals().features().generateStructure(world, key.toString(), chunkPos.x, chunkPos.z, id -> {
                Holder<Biome> biome = NmsChunks.biome(id);
                if (biome == null) throw new IllegalStateException("biome missing from the NMS biome table: " + id);
                try {
                    return validBiome.test(biome);
                } catch (RuntimeException e) {
                    throw new NmsFeatures.LevelFailure(e);
                }
            });
        } catch (NmsFeatures.LevelFailure f) {
            throw (RuntimeException) f.getCause();
        } catch (UnsupportedNmsCall e) {
            throw e;
        } catch (UnsupportedOperationException e) {
            throw new UnsupportedNmsCall(target, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new UnsupportedNmsCall(target, "the server cannot generate it: " + e.getMessage());
        }
        return h == null ? StructureStart.INVALID_START : new StructureStart(structure, chunkPos, references, world, h);
    }

    /** {@link StructureStart#placeInChunk} — 유효한 시작만. */
    public static void placeInChunk(World startWorld, FeatureInternals.StructureStartHandle start, WorldGenLevel level, StructureManager structureManager,
                                    ChunkGenerator generator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(random, "random");
        Objects.requireNonNull(chunkBox, "chunkBox");
        Objects.requireNonNull(chunkPos, "chunkPos");
        String target = "StructureStart#placeInChunk(" + start.structureId() + ")";
        ServerLevel server;
        try {
            server = level.getMinecraftWorld();
        } catch (UnsupportedOperationException e) {
            throw new UnsupportedNmsCall(target, "the level (" + level.getClass().getName() + ") does not tell which server level it belongs to");
        }
        if (server == null) throw new UnsupportedNmsCall(target, "the level (" + level.getClass().getName() + ") does not belong to a server level");
        World world = server.getWorld().furnaceHandle();
        if (!Objects.equals(world, startWorld)) throw new UnsupportedNmsCall(target, "the structure start was generated for another world");
        if (generator == null || !Objects.equals(generator.furnaceWorld(), world)) {
            throw new UnsupportedNmsCall(target, "the chunk generator must be the one of the level's world (getChunkSource().getGenerator())");
        }
        if (structureManager == null || !Objects.equals(structureManager.furnaceWorld(), world)) {
            throw new UnsupportedNmsCall(target, "the structure manager must be the level's (ServerLevel#structureManager())");
        }
        boolean capturing = server.captureTreeGeneration && NmsFeatures.captureAppliesHere();
        boolean direct = level == server && !capturing;
        if (direct && server.captureBlockStates && NmsFeatures.captureAppliesHere()) {
            throw new UnsupportedNmsCall(target + "(captureBlockStates)",
                    "writing to the world without neighbour updates while recording the old states does not exist on the server yet");
        }
        var features = Furnace.server().internals().features();
        FeatureInternals.LevelSink sink = direct ? null : NmsFeatures.levelSink(level);
        Object session = direct ? null : session(features, level, server);
        int[] box = {chunkBox.minX(), chunkBox.minY(), chunkBox.minZ(), chunkBox.maxX(), chunkBox.maxY(), chunkBox.maxZ()};
        try {
            features.placeStructureNow(world, start, box, chunkPos.x, chunkPos.z, new NmsFeatures.RandomSourceAsJava(random), () -> {
                RandomSource r;
                try {
                    r = level.getRandom();
                } catch (RuntimeException e) {
                    throw new NmsFeatures.LevelFailure(e);
                }
                return new NmsFeatures.RandomSourceAsJava(r);
            }, session, sink);
        } catch (NmsFeatures.LevelFailure f) {
            throw (RuntimeException) f.getCause();
        } catch (UnsupportedNmsCall e) {
            throw e;
        } catch (UnsupportedOperationException e) {
            throw new UnsupportedNmsCall(target, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new UnsupportedNmsCall(target, e.getMessage());
        }
    }

    /**
     * 그 레벨의 세션. 서버 레벨 자신(쓰기 기록 중)이면 Paper 의 읽기가 기록을 돌려주므로 지금 기록({@code capturedBlockStates})에서 새로 채운다 — 기록은
     * 지워지고 다시 쌓일 수 있어 남겨 두지 않는다. 그 밖의 레벨은 그 레벨이 살아 있는 동안 같은 세션(받아들인 쓰기가 쌓인다).
     */
    private static Object session(FeatureInternals features, WorldGenLevel level, ServerLevel server) {
        if (level == server) {
            Object s = features.newStructureSession();
            synchronized (server.capturedBlockStates) {
                for (var e : server.capturedBlockStates.entrySet()) {
                    var p = e.getKey();
                    features.structureSessionWrite(s, p.getX(), p.getY(), p.getZ(), e.getValue().getHandle().furnaceStateId());
                }
            }
            return s;
        }
        synchronized (SESSIONS) {
            return SESSIONS.computeIfAbsent(level, k -> features.newStructureSession());
        }
    }
}
