package net.minecraft.world.level.chunk.status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * 청크 생성 단계(Mojang 이름, N2a). 1.21.1 순서 그대로(empty … full). Furnace 는 이 단계를 그대로 거치지 않으므로
 * 서버가 돌려주는 청크는 늘 {@link #FULL}(로드된 청크) 이다 — 생성 중 청크(ProtoChunk)는 플러그인에게 보이지 않는다.
 * 이름·비교·번호만 바닐라와 같다(생성 작업·하이트맵 묶음은 없다).
 */
public class ChunkStatus {

    public static final int MAX_STRUCTURE_DISTANCE = 8;

    private static final List<ChunkStatus> ALL = new ArrayList<>();

    public static final ChunkStatus EMPTY = register("empty", null);
    public static final ChunkStatus STRUCTURE_STARTS = register("structure_starts", EMPTY);
    public static final ChunkStatus STRUCTURE_REFERENCES = register("structure_references", STRUCTURE_STARTS);
    public static final ChunkStatus BIOMES = register("biomes", STRUCTURE_REFERENCES);
    public static final ChunkStatus NOISE = register("noise", BIOMES);
    public static final ChunkStatus SURFACE = register("surface", NOISE);
    public static final ChunkStatus CARVERS = register("carvers", SURFACE);
    public static final ChunkStatus FEATURES = register("features", CARVERS);
    public static final ChunkStatus INITIALIZE_LIGHT = register("initialize_light", FEATURES);
    public static final ChunkStatus LIGHT = register("light", INITIALIZE_LIGHT);
    public static final ChunkStatus SPAWN = register("spawn", LIGHT);
    public static final ChunkStatus FULL = register("full", SPAWN);

    private final int index;
    private final ChunkStatus parent;
    private final ResourceLocation name;

    private static ChunkStatus register(String name, ChunkStatus parent) {
        ChunkStatus s = new ChunkStatus(ALL.size(), parent, ResourceLocation.withDefaultNamespace(name));
        ALL.add(s);
        return s;
    }

    private ChunkStatus(int index, ChunkStatus parent, ResourceLocation name) {
        this.index = index;
        this.parent = parent == null ? this : parent;   // 바닐라: empty 의 부모는 자기 자신
        this.name = name;
    }

    /** empty … full 순서의 목록. */
    public static List<ChunkStatus> getStatusList() { return Collections.unmodifiableList(ALL); }

    public int getIndex() { return index; }

    public ChunkStatus getParent() { return parent; }

    /** 바닐라: 레지스트리에서 찾고 없으면 기본값(empty). */
    public static ChunkStatus byName(String name) {
        ResourceLocation loc = ResourceLocation.tryParse(name);
        if (loc != null) for (ChunkStatus s : ALL) if (s.name.equals(loc)) return s;
        return EMPTY;
    }

    public boolean isOrAfter(ChunkStatus other) { return index >= other.index; }

    public boolean isAfter(ChunkStatus other) { return index > other.index; }

    public boolean isOrBefore(ChunkStatus other) { return index <= other.index; }

    public boolean isBefore(ChunkStatus other) { return index < other.index; }

    public static ChunkStatus max(ChunkStatus a, ChunkStatus b) { return a.isAfter(b) ? a : b; }

    /** 바닐라: 레지스트리 키 문자열(minecraft:full). */
    public String getName() { return name.toString(); }

    @Override
    public String toString() { return getName(); }
}
