// 추가 월드를 만들거나 이미 있는 폴더를 불러오는 설명서(Bukkit WorldCreator 에 대응). 값 객체이며 빌더로 만든다. 버전 중립.
package org.domi.furnace.api.world;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.regex.Pattern;

/**
 * @param name               월드 이름 = 서버 폴더 안의 폴더 이름({@code <이름>/}, Paper 와 같은 배치). 영문·숫자·{@code _ . -}
 * @param key                레벨(차원) 키. 명령의 {@code /execute in <키>} 와 이벤트의 world() 가 이것이다. 기본 {@code minecraft:<소문자 이름>}(Paper 와 같다)
 * @param environment        저장 폴더 배치와 기본 차원 타입·생성기
 * @param generator          지형 생성기
 * @param dimensionType      차원 타입 키(minecraft:overworld … 또는 플러그인이 등록한 것). null 이면 환경의 기본
 * @param seed               생성 시드. 비면 주 월드 시드(바닐라: 모든 차원이 같은 시드). 이미 있는 월드는 그 level.dat 의 시드가 이긴다
 * @param generateStructures 구조물(마을·요새 …)을 만드는가. 노이즈 생성기에만 해당
 * @param flatLayers         평지 층(아래부터). 비면 바닐라 기본 층. FLAT 에서만
 * @param flatBiome          평지 바이옴. FLAT 에서만
 * @param generationHooks    월드젠 단계 훅 키(LifecycleEvents.WORLD_GENERATION 에서 등록한 것). 빈 문자열이면 없음(바닐라)
 * @param flatGeneratorSettings 평지 설정 JSON(바닐라 server.properties {@code generator-settings} 와 같은 형식·규칙). 빈 문자열이면
 *                           flatLayers·flatBiome 에 지형지물·호수·구조물 없음. 새 FLAT 월드에서만 쓴다(이미 있는 월드는 level.dat 의 설정)
 * @param biomeProvider      바이옴 공급자(T-210). null 이면 생성기 자신의 바이옴
 * @param biomeProviderName  공급자 이름(level.dat 에 남는 표시). biomeProvider 가 있으면 그 {@code name()}. 저장된 설명서에서 읽은 것이면
 *                           공급자 없이 이름만 있다 — 그대로는 열 수 없고 {@link Builder#biomeProvider} 로 공급자를 다시 붙이거나 지운다
 * @param generationHooksObject     월드젠 단계 훅 객체(T-217 — 키 대신 객체를 직접 단다. Bukkit ChunkGenerator 다리가 쓴다). null 이면 없음.
 *                                  generationHooks(키)와 함께 쓸 수 없다
 * @param generationHooksObjectName 훅 객체 이름(level.dat 에 남는 표시). 객체가 있으면 그 {@code name()}. 저장된 설명서에서 읽은 것이면 객체 없이
 *                                  이름만 있다 — 그대로는 열 수 없고 {@link Builder#generationHooksObject} 로 다시 붙이거나 지운다
 */
public record WorldCreator(String name, String key, WorldEnvironment environment, WorldGenerator generator, String dimensionType,
                           OptionalLong seed, boolean generateStructures, List<FlatLayer> flatLayers, String flatBiome,
                           String generationHooks, String flatGeneratorSettings, org.domi.furnace.api.worldgen.BiomeProvider biomeProvider,
                           String biomeProviderName, org.domi.furnace.api.worldgen.ChunkGenerationHooks generationHooksObject,
                           String generationHooksObjectName) {

    /** 훅 객체 없는 설명서(T-210 판과 같은 모양). */
    public WorldCreator(String name, String key, WorldEnvironment environment, WorldGenerator generator, String dimensionType,
                        OptionalLong seed, boolean generateStructures, List<FlatLayer> flatLayers, String flatBiome,
                        String generationHooks, String flatGeneratorSettings, org.domi.furnace.api.worldgen.BiomeProvider biomeProvider,
                        String biomeProviderName) {
        this(name, key, environment, generator, dimensionType, seed, generateStructures, flatLayers, flatBiome, generationHooks,
                flatGeneratorSettings, biomeProvider, biomeProviderName, null, "");
    }

    /** 공급자·평지 JSON 없는 설명서(이전 버전과 같은 모양). */
    public WorldCreator(String name, String key, WorldEnvironment environment, WorldGenerator generator, String dimensionType,
                        OptionalLong seed, boolean generateStructures, List<FlatLayer> flatLayers, String flatBiome, String generationHooks) {
        this(name, key, environment, generator, dimensionType, seed, generateStructures, flatLayers, flatBiome, generationHooks, "", null, "");
    }

    /** 훅 없는 설명서(이전 버전과 같은 모양). */
    public WorldCreator(String name, String key, WorldEnvironment environment, WorldGenerator generator, String dimensionType,
                        OptionalLong seed, boolean generateStructures, List<FlatLayer> flatLayers, String flatBiome) {
        this(name, key, environment, generator, dimensionType, seed, generateStructures, flatLayers, flatBiome, "");
    }

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.-]{1,64}");
    private static final Pattern KEY = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    /** 평지 한 층. block 은 블록 id(minecraft:stone), height 는 1..4064. */
    public record FlatLayer(String block, int height) {
        public FlatLayer {
            block = full("block", block);
            if (height < 1 || height > 4064) throw new IllegalArgumentException("layer height must be 1..4064: " + height);
        }
    }

    /** 이름 규칙(영문·숫자·{@code _ . -} 1~64자, "."·".." 는 안 됨)에 맞는가. 예외 문구를 읽지 않고 미리 걸러낼 때 쓴다. */
    public static boolean isValidName(String name) {
        return name != null && NAME.matcher(name).matches() && !name.equals(".") && !name.equals("..");
    }

    public WorldCreator {
        Objects.requireNonNull(name, "name");
        if (!isValidName(name)) {
            throw new IllegalArgumentException("World name must be 1-64 characters of letters, digits, '_', '.' or '-': " + name);
        }
        key = full("key", key == null ? "minecraft:" + name.toLowerCase(Locale.ROOT) : key);
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(generator, "generator");
        if ((generator == WorldGenerator.LARGE_BIOMES || generator == WorldGenerator.AMPLIFIED || generator == WorldGenerator.SINGLE_BIOME_SURFACE
                || generator == WorldGenerator.DEBUG_ALL_BLOCK_STATES) && environment != WorldEnvironment.NORMAL) {
            throw new IllegalArgumentException("the " + generator + " generator can only be used in the NORMAL environment");
        }
        dimensionType = full("dimensionType", dimensionType == null ? environment.defaultDimensionType() : dimensionType);
        Objects.requireNonNull(seed, "seed");
        flatLayers = List.copyOf(flatLayers);
        flatBiome = full("flatBiome", flatBiome);
        generationHooks = generationHooks == null || generationHooks.isEmpty() ? "" : full("generationHooks", generationHooks);
        flatGeneratorSettings = flatGeneratorSettings == null ? "" : flatGeneratorSettings;
        if (biomeProvider != null) {
            String n = biomeProvider.name();
            if (n == null || n.isBlank()) throw new IllegalArgumentException("BiomeProvider#name must not be blank");
            biomeProviderName = n;
        } else if (biomeProviderName == null) {
            biomeProviderName = "";
        }
        if (generationHooksObject != null) {
            String n = generationHooksObject.name();
            if (n == null || n.isBlank()) throw new IllegalArgumentException("ChunkGenerationHooks#name must not be blank");
            generationHooksObjectName = n;
        } else if (generationHooksObjectName == null) {
            generationHooksObjectName = "";
        }
        if (!generationHooks.isEmpty() && !generationHooksObjectName.isEmpty()) {
            throw new IllegalArgumentException("a world can have generation hooks by key or a hooks object, not both");
        }
    }

    /** level.dat 에 공급자 표시만 있고 공급자가 없다 — 이대로는 열 수 없다(공급자를 다시 붙이거나 지울 것). */
    public boolean needsBiomeProvider() { return biomeProvider == null && !biomeProviderName.isEmpty(); }

    /** level.dat 에 훅 객체 표시만 있고 객체가 없다 — 이대로는 열 수 없다(객체를 다시 붙이거나 지울 것). */
    public boolean needsGenerationHooksObject() { return generationHooksObject == null && !generationHooksObjectName.isEmpty(); }

    private static String full(String what, String id) {
        if (id == null) throw new NullPointerException(what);
        String f = id.indexOf(':') < 0 ? "minecraft:" + id : id;
        if (!KEY.matcher(f).matches()) throw new IllegalArgumentException(what + " has an invalid format (namespace:path): " + id);
        return f;
    }

    /** 이름만 정한 빌더(NORMAL·DEFAULT·주 월드 시드·구조물 켬). */
    public static Builder named(String name) { return new Builder(name); }

    public Builder toBuilder() {
        Builder b = new Builder(name).key(key).environment(environment).generator(generator).dimensionType(dimensionType)
                .generateStructures(generateStructures).flatLayers(flatLayers).flatBiome(flatBiome).generationHooks(generationHooks)
                .flatGeneratorSettings(flatGeneratorSettings);
        b.biomeProvider = biomeProvider;
        b.biomeProviderName = biomeProviderName;
        b.generationHooksObject = generationHooksObject;
        b.generationHooksObjectName = generationHooksObjectName;
        if (seed.isPresent()) b.seed(seed.getAsLong());
        return b;
    }

    public static final class Builder {
        private final String name;
        private String key;
        private WorldEnvironment environment = WorldEnvironment.NORMAL;
        private WorldGenerator generator = WorldGenerator.DEFAULT;
        private String dimensionType;
        private OptionalLong seed = OptionalLong.empty();
        private boolean generateStructures = true;
        private List<FlatLayer> flatLayers = List.of();
        private String flatBiome = "minecraft:plains";
        private String generationHooks = "";
        private String flatGeneratorSettings = "";
        private org.domi.furnace.api.worldgen.BiomeProvider biomeProvider;
        private String biomeProviderName = "";
        private org.domi.furnace.api.worldgen.ChunkGenerationHooks generationHooksObject;
        private String generationHooksObjectName = "";

        private Builder(String name) { this.name = name; }

        public Builder key(String key) { this.key = key; return this; }

        public Builder environment(WorldEnvironment environment) { this.environment = environment; return this; }

        public Builder generator(WorldGenerator generator) { this.generator = generator; return this; }

        /** 차원 타입 키(바닐라 또는 플러그인 등록). null 이면 환경의 기본. */
        public Builder dimensionType(String key) { this.dimensionType = key; return this; }

        public Builder seed(long seed) { this.seed = OptionalLong.of(seed); return this; }

        /** 주 월드 시드를 쓴다(기본). */
        public Builder sameSeedAsMainWorld() { this.seed = OptionalLong.empty(); return this; }

        public Builder generateStructures(boolean v) { this.generateStructures = v; return this; }

        public Builder flatLayers(List<FlatLayer> layers) { this.flatLayers = layers; return this; }

        public Builder flatBiome(String biome) { this.flatBiome = biome; return this; }

        /**
         * 월드젠 단계 훅 키({@link org.domi.furnace.api.plugin.lifecycle.LifecycleEvents#WORLD_GENERATION} 에서 등록한 것).
         * null·빈 문자열이면 없음. level.dat 설명서에 남아 다시 불러올 때도 같은 키를 쓴다. 그때 키가 등록돼 있지 않으면 바닐라로 생성한다.
         */
        public Builder generationHooks(String key) { this.generationHooks = key == null ? "" : key; return this; }

        /**
         * 평지 설정 JSON — 바닐라 server.properties {@code generator-settings}(Bukkit {@code WorldCreator#generatorSettings})와 같은 형식·규칙:
         * {@code layers} 가 없으면 바닐라 기본 프리셋(기반암·흙 2·잔디, 평원, 요새·마을), {@code structure_overrides} 가 없으면 모든 구조물 세트.
         * 주면 flatLayers·flatBiome 대신 이것을 쓴다. null·빈 문자열이면 없음. 새 FLAT 월드에서만 쓴다.
         */
        public Builder flatGeneratorSettings(String json) { this.flatGeneratorSettings = json == null ? "" : json; return this; }

        /**
         * 바이옴 공급자(T-210 — CraftBukkit CustomWorldChunkManager 와 같은 자리). 노이즈 생성기(DEFAULT·LARGE_BIOMES·AMPLIFIED·
         * SINGLE_BIOME_SURFACE, 모든 환경)와 오버월드 높이의 FLAT 만 받는다. 공급자 객체는 저장되지 않고 이름만 level.dat 에 남아,
         * 서버가 스스로 다시 열 때(worlds.autoload·/worlds load)는 공급자를 다시 줄 때까지 열지 않는다. null 이면 공급자와 표시를 지운다.
         */
        public Builder biomeProvider(org.domi.furnace.api.worldgen.BiomeProvider provider) {
            this.biomeProvider = provider;
            this.biomeProviderName = "";
            return this;
        }

        /** 공급자 없이 표시(이름)만 — 저장된 설명서를 읽을 때 서버가 쓴다. */
        public Builder biomeProviderName(String name) {
            this.biomeProvider = null;
            this.biomeProviderName = name == null ? "" : name;
            return this;
        }

        /**
         * 월드젠 단계 훅 객체(T-217 — Bukkit ChunkGenerator 다리와 같은 자리). 키 훅({@link #generationHooks(String)})과 함께 쓸 수 없다.
         * 객체는 저장되지 않고 이름({@link org.domi.furnace.api.worldgen.ChunkGenerationHooks#name()})만 level.dat 에 남아, 서버가 스스로 다시 열 때
         * (worlds.autoload·/worlds load)는 객체를 다시 줄 때까지 열지 않는다(바이옴 공급자와 같은 규칙). null 이면 객체와 표시를 지운다.
         * 월드가 사는 동안 서버가 객체를 쥔다.
         */
        public Builder generationHooksObject(org.domi.furnace.api.worldgen.ChunkGenerationHooks hooks) {
            this.generationHooksObject = hooks;
            this.generationHooksObjectName = "";
            return this;
        }

        /** 훅 객체 없이 표시(이름)만 — 저장된 설명서를 읽을 때 서버가 쓴다. */
        public Builder generationHooksObjectName(String name) {
            this.generationHooksObject = null;
            this.generationHooksObjectName = name == null ? "" : name;
            return this;
        }

        public WorldCreator build() {
            return new WorldCreator(name, key, environment, generator, dimensionType, seed, generateStructures, flatLayers, flatBiome,
                    generationHooks, flatGeneratorSettings, biomeProvider, biomeProviderName, generationHooksObject, generationHooksObjectName);
        }
    }
}
