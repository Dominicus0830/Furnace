// 평지 생성(바닐라 FlatLevelSource): 설정의 층을 아래(월드 최저 높이)부터 쌓는다. 기본은 bedrock/dirt×2/grass_block, 평원.
// 구조물(structure_overrides)·지형지물·호수는 아직 만들지 않는다.
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

    /** 바닐라 FlatLevelGeneratorSettings 기본값(JSON generator-settings 가 있으면 그 층·바이옴)을 level.dat 꼴 compound 로. */
    public static NbtCompound settingsNbt(JsonObject generatorSettings) {
        NbtCompound s = new NbtCompound();
        s.putString("biome", generatorSettings.has("biome") ? generatorSettings.get("biome").getAsString() : "minecraft:plains");
        s.putBoolean("features", generatorSettings.has("features") && generatorSettings.get("features").getAsBoolean());
        s.putBoolean("lakes", generatorSettings.has("lakes") && generatorSettings.get("lakes").getAsBoolean());
        NbtList layers = new NbtList(NbtIo.TAG_COMPOUND);
        if (generatorSettings.has("layers")) {
            for (JsonElement e : generatorSettings.getAsJsonArray("layers")) {
                JsonObject o = e.getAsJsonObject();
                layers.add(layer(o.get("block").getAsString(), o.get("height").getAsInt()));
            }
        } else {
            for (Layer l : DEFAULT_LAYERS) layers.add(layer(l.block(), l.height()));
        }
        s.putList("layers", layers);
        NbtList overrides = new NbtList(NbtIo.TAG_STRING);
        if (generatorSettings.has("structure_overrides")) {
            JsonElement so = generatorSettings.get("structure_overrides");
            if (so.isJsonArray()) for (JsonElement e : (JsonArray) so) overrides.add(e.getAsString());
            else overrides.add(so.getAsString());
        } else {
            overrides.add("minecraft:strongholds");
            overrides.add("minecraft:villages");
        }
        s.putList("structure_overrides", overrides);
        return s;
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
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int i = 0; i < column.length; i++) if (column[i] != 0) c.setBlock(x, minY + i, z, column[i]);
                c.setSurface(x, z, column.length);
            }
        }
        return c;
    }
}
