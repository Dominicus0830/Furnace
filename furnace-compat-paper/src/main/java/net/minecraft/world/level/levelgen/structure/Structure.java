package net.minecraft.world.level.levelgen.structure;

/**
 * 구조물(Mojang 이름 shim, N2b-6). 레지스트리에 있는 구조물을 서로 구별하고 종류 이름을 읽는다. T-211 2단계: {@link #generate} 는 서버의 구조물
 * 생성 코드(바닐라 1.21.1 이식)로 시작을 만든다. 그 밖의 생성({@code findGenerationPoint})·설정({@code biomes()}·{@code step()})은 없다.
 */
public abstract class Structure {

    private final String typeId;

    /** 호환 모듈 전용 — 구조물 종류 id(예: minecraft:jigsaw). */
    protected Structure(String typeId) { this.typeId = typeId; }

    /** Furnace: 이 구조물의 종류 id(JSON 의 type). */
    public final String furnaceTypeId() { return typeId; }

    /**
     * 바닐라 Structure#generate: 그 월드 생성기로 시작 자리를 찾고, 그 자리 바이옴이 validBiome 을 지나면 조각을 만든다. 유효하지 않으면
     * {@link StructureStart#INVALID_START}. 생성기·바이옴 원천·난수 상태·구조물 관리자·시드·높이는 한 월드의 것이어야 한다
     * ({@code ServerLevel} 의 getChunkSource().getGenerator()·getBiomeSource()·randomState()·getStructureManager()·getSeed()) — 아니면
     * {@link org.domi.furnace.compat.nms.UnsupportedNmsCall}. 서버에 아직 없는 구조물 종류도 같다.
     */
    public StructureStart generate(net.minecraft.core.RegistryAccess registryAccess, net.minecraft.world.level.chunk.ChunkGenerator chunkGenerator,
                                   net.minecraft.world.level.biome.BiomeSource biomeSource, net.minecraft.world.level.levelgen.RandomState randomState,
                                   net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager structureTemplateManager, long seed,
                                   net.minecraft.world.level.ChunkPos chunkPos, int references, net.minecraft.world.level.LevelHeightAccessor heightAccessor,
                                   java.util.function.Predicate<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> validBiome) {
        return org.domi.furnace.compat.nms.NmsStructures.generate(this, registryAccess, chunkGenerator, biomeSource, randomState, structureTemplateManager,
                seed, chunkPos, references, heightAccessor, validBiome);
    }

    @Override
    public String toString() { return "Structure[" + typeId + "]"; }
}
