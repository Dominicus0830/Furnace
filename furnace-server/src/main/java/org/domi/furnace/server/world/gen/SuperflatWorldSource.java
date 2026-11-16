// 단순 평지 생성: 설정의 층을 아래(월드 최저 높이)부터 쌓기만 한다. 기본은 bedrock/dirt×2/grass_block, 평원.
// 서버의 level-type=flat 은 바닐라 FlatLevelSource 처럼 장식·구조물까지 하는 NoiseWorldSource.flat 을 쓴다. 이 클래스는 시험 월드·
// 플러그인 추가 월드(높이가 다른 차원 타입)용으로 남아 있고, 구조물·지형지물·호수를 만들지 않는다.
package org.domi.furnace.server.world.gen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;

public final class SuperflatWorldSource implements WorldSource {

    /** 층(블록 상태, 두께). */
    public record Layer(String block, int height) {}

    /** 바닐라 FlatLevelGeneratorSettings.getDefault 의 층. */
    public static final List<Layer> DEFAULT_LAYERS = List.of(new Layer("minecraft:bedrock", 1), new Layer("minecraft:dirt", 2),
            new Layer("minecraft:grass_block", 1));

    private final int[] column;   // 월드 최저 높이부터 층별로 펼친 상태
    private final int biomeId;
    private final int minY, sectionCount, directBits;   // 차원 높이(기본 오버월드 -64, 24 구역)

    public SuperflatWorldSource(BlockRegistry reg, int biomeId) {
        this(reg, DEFAULT_LAYERS, biomeId);
    }

    public SuperflatWorldSource(BlockRegistry reg, List<Layer> layers, int biomeId) {
        this(reg, layers, biomeId, ChunkColumn.MIN_Y, ChunkColumn.SECTION_COUNT);
    }

    /** 높이가 다른 차원 타입(추가 월드 — 플러그인 차원 타입)의 평지·빈 월드. 층은 그 차원 최저 높이부터 쌓고 위로 넘치면 자른다. */
    public SuperflatWorldSource(BlockRegistry reg, List<Layer> layers, int biomeId, int minY, int sectionCount) {
        List<Integer> col = new ArrayList<>();
        for (Layer l : layers) {
            int state = reg.defaultStateId(l.block());
            for (int i = 0; i < l.height() && col.size() < sectionCount * 16; i++) col.add(state);
        }
        this.column = col.stream().mapToInt(Integer::intValue).toArray();
        this.biomeId = biomeId;
        this.minY = minY;
        this.sectionCount = sectionCount;
        this.directBits = reg.directBits();
    }

    /** 플러그인 월드젠 단계 훅(W3). 평지·빈 월드는 NOISE(층 쌓기)만 바닐라가 있고, 장식 훅은 그 청크 안에만 쓸 수 있다. */
    private volatile GenHooks genHooks;

    public void generationHooks(GenHooks hooks) { this.genHooks = hooks; }

    public GenHooks generationHooks() { return genHooks; }

    /** FixedBiomeSource 의 바이옴 레지스트리 id. */
    public int biomeId() { return biomeId; }

    /** 바닐라 설정 compound(level.dat 의 flat 생성기 settings)의 층. */
    public static List<Layer> layersOf(NbtCompound settings) {
        if (settings == null || !settings.contains("layers")) return DEFAULT_LAYERS;
        List<Layer> out = new ArrayList<>();
        for (Object o : settings.getList("layers").items()) {
            NbtCompound l = (NbtCompound) o;
            out.add(new Layer(l.getString("block"), ((Number) l.tags().get("height")).intValue()));
        }
        return out;
    }

    /**
     * level.dat 평지 settings → 평지 생성기 설정(바닐라 FlatLevelGeneratorSettings 코덱: lakes·features 없으면 거짓, biome 없으면 plains,
     * structure_overrides 가 없으면 null = 레지스트리의 모든 구조물 세트).
     */
    public static NoiseWorldSource.FlatSpec flatSpecOf(NbtCompound settings) {
        String biome = settings != null && settings.tags().get("biome") instanceof String b ? b : "minecraft:plains";
        boolean features = settings != null && settings.contains("features") && settings.getBoolean("features");
        boolean lakes = settings != null && settings.contains("lakes") && settings.getBoolean("lakes");
        List<String> overrides = null;
        if (settings != null && settings.contains("structure_overrides")) {
            Object so = settings.tags().get("structure_overrides");
            overrides = new ArrayList<>();
            if (so instanceof String one) overrides.add(one);
            else for (Object o : settings.getList("structure_overrides").items()) if (o instanceof String id) overrides.add(id);
        }
        return new NoiseWorldSource.FlatSpec(layersOf(settings), biome, features, lakes, overrides);
    }

    /**
     * server.properties generator-settings(JSON) → level.dat 평지 settings. 바닐라 DedicatedServerProperties: FlatLevelGeneratorSettings
     * 코덱으로 읽어(resultOrPartial) 부분 결과도 없으면(layers 없음) 기본 프리셋(기반암·흙 2·잔디, 평원, 요새·마을, 지형지물·호수 꺼짐).
     * 읽히면 lakes·features 없으면 거짓, biome 없으면 평원, structure_overrides 가 없으면 필드도 없다(= 레지스트리의 모든 구조물 세트).
     */
    public static NbtCompound settingsNbt(JsonObject generatorSettings) {
        // 바닐라 DedicatedServerProperties: CODEC.parse(...).resultOrPartial — 부분 결과가 있으면 그것을 쓴다.
        // layers 가 없거나 배열이 아니면 부분 결과가 없다(기본 프리셋). 층 하나가 깨지면(height 없음·0..4064 밖·객체 아님) 그 층만 빠지고,
        // block 이 없으면 공기. 높이 합 > 4064 는 DataResult.error(msg, 값) 이라 그 값을 그대로 쓴다.
        JsonElement layersEl = generatorSettings == null ? null : generatorSettings.get("layers");
        NbtCompound s = new NbtCompound();
        if (layersEl == null || !layersEl.isJsonArray()) {
            s.putString("biome", "minecraft:plains");
            s.putBoolean("features", false);
            s.putBoolean("lakes", false);
            NbtList layers = new NbtList(NbtIo.TAG_COMPOUND);
            for (Layer l : DEFAULT_LAYERS) layers.add(layer(l.block(), l.height()));
            s.putList("layers", layers);
            NbtList overrides = new NbtList(NbtIo.TAG_STRING);
            overrides.add("minecraft:strongholds");
            overrides.add("minecraft:villages");
            s.putList("structure_overrides", overrides);
            return s;
        }
        s.putString("biome", generatorSettings.get("biome") instanceof com.google.gson.JsonPrimitive p && p.isString() ? p.getAsString() : "minecraft:plains");
        s.putBoolean("features", jsonBool(generatorSettings.get("features")));
        s.putBoolean("lakes", jsonBool(generatorSettings.get("lakes")));
        NbtList layers = new NbtList(NbtIo.TAG_COMPOUND);
        for (JsonElement e : layersEl.getAsJsonArray()) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            if (!(o.get("height") instanceof com.google.gson.JsonPrimitive hp) || !hp.isNumber()) continue;
            int h = hp.getAsInt();
            if (h < 0 || h > 4064) continue;
            String block = o.get("block") instanceof com.google.gson.JsonPrimitive bp && bp.isString() ? bp.getAsString() : "minecraft:air";
            layers.add(layer(block, h));
        }
        s.putList("layers", layers);
        // structure_overrides 는 lenientOptionalFieldOf: 읽지 못하면(모르는 세트 id·태그, 문자열 아님) 필드가 없는 것 = 레지스트리 전부
        if (generatorSettings.has("structure_overrides")) {
            NbtList overrides = new NbtList(NbtIo.TAG_STRING);
            JsonElement so = generatorSettings.get("structure_overrides");
            boolean ok = true;
            for (JsonElement e : so.isJsonArray() ? (JsonArray) so : List.of(so)) {
                if (!(e instanceof com.google.gson.JsonPrimitive p) || !p.isString()
                        || !org.domi.furnace.server.world.gen.structure.StructureRegistry.isKnownSet(p.getAsString())) {
                    ok = false;
                    break;
                }
                String id = p.getAsString();
                overrides.add(id.indexOf(':') < 0 ? "minecraft:" + id : id);
            }
            if (ok) s.putList("structure_overrides", overrides);
        }
        return s;
    }

    /** Codec.BOOL(JsonOps): JSON 불리언만 — 그 밖은 orElse(false). */
    private static boolean jsonBool(JsonElement e) {
        return e instanceof com.google.gson.JsonPrimitive p && p.isBoolean() && p.getAsBoolean();
    }

    private static NbtCompound layer(String block, int height) {
        NbtCompound l = new NbtCompound();
        l.putString("block", block);
        l.putInt("height", height);
        return l;
    }

    /** 맨 위 층 바로 위(스폰 높이 기준). */
    public int surfaceY() { return minY + column.length; }

    @Override
    public ChunkColumn generate(int chunkX, int chunkZ) {
        ChunkColumn c = minY == ChunkColumn.MIN_Y && sectionCount == ChunkColumn.SECTION_COUNT
                ? new ChunkColumn(biomeId)
                : new ChunkColumn(biomeId, directBits, ChunkColumn.DEFAULT_BIOME_DIRECT_BITS, minY, sectionCount);
        GenHooks hooks = genHooks;
        if (hooks == null || hooks.vanilla(org.domi.furnace.api.worldgen.GenerationStage.NOISE, chunkX, chunkZ)) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int i = 0; i < column.length; i++) if (column[i] != 0) c.setBlock(x, minY + i, z, column[i]);
                    c.setSurface(x, z, column.length);
                }
            }
        }
        if (hooks != null) {
            hooks.chunkStage(org.domi.furnace.api.worldgen.GenerationStage.NOISE, c, chunkX, chunkZ);
            hooks.chunkStage(org.domi.furnace.api.worldgen.GenerationStage.SURFACE, c, chunkX, chunkZ);
            hooks.chunkStage(org.domi.furnace.api.worldgen.GenerationStage.CARVERS, c, chunkX, chunkZ);
            int[] biomeIds = new int[c.sectionCount() * 64];
            java.util.Arrays.fill(biomeIds, biomeId);
            var view = new org.domi.furnace.server.world.gen.feature.FeatureWorldView(c, chunkX, chunkZ, biomeIds, hooks.seed());
            hooks.decorate(view, chunkX, chunkZ, GenHooks.decorationSeed(hooks.seed(), chunkX, chunkZ));
            c.rebuildHeightmapsAfterSurface();
        }
        return c;
    }
}
