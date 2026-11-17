package net.minecraft.world.level.levelgen.structure;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.domi.furnace.api.internal.FeatureInternals;

/**
 * 구조물 시작(Mojang 이름 shim, T-211 2단계) — {@link Structure#generate} 의 결과. 조각은 서버가 들고 있다(바닐라 1.21.1 이식):
 * 이 객체는 그 손잡이와 바닐라 값(시작 청크·참조 수·구조물)을 든다. 조각 목록({@code getPieces})·저장({@code createTag})은 없다.
 */
public final class StructureStart {

    public static final String INVALID_START_ID = "INVALID";
    public static final StructureStart INVALID_START = new StructureStart(null, new ChunkPos(0, 0), 0, null, null);

    private final Structure structure;
    private final ChunkPos chunkPos;
    private int references;
    private final org.domi.furnace.api.world.World world;
    private final FeatureInternals.StructureStartHandle handle;
    private volatile BoundingBox cachedBoundingBox;

    /** 호환 모듈 전용: 서버가 만든 시작(handle 이 null 이면 유효하지 않은 시작). */
    public StructureStart(Structure structure, ChunkPos chunkPos, int references, org.domi.furnace.api.world.World world,
                          FeatureInternals.StructureStartHandle handle) {
        this.structure = structure;
        this.chunkPos = chunkPos;
        this.references = references;
        this.world = world;
        this.handle = handle;
    }

    /** 바닐라 getBoundingBox: 조각을 감싸는 상자를 구조물이 넓힌 것(지형 보정). 처음 계산한 객체를 다시 준다. 조각이 없으면 바닐라처럼 IllegalStateException. */
    public BoundingBox getBoundingBox() {
        BoundingBox b = cachedBoundingBox;
        if (b == null) {
            if (handle == null) throw new IllegalStateException("Unable to calculate boundingbox without pieces");
            int[] v = handle.boundingBox();
            cachedBoundingBox = b = new BoundingBox(v[0], v[1], v[2], v[3], v[4], v[5]);
        }
        return b;
    }

    /**
     * 바닐라(Paper) placeInChunk: 청크 상자에 닿는 조각을 놓고 구조물의 afterPlace 를 부른다. 서버 레벨이고 쓰기 기록 중이 아니면 서버 월드에 바로
     * (/place structure 와 같은 반영), 그 밖(FAWE populator·BlockStateListPopulator·기록 중인 서버 레벨)은 그 레벨의 setBlock 으로 —
     * {@link org.domi.furnace.compat.nms.NmsStructures#placeInChunk}.
     */
    public void placeInChunk(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random,
                             BoundingBox chunkBox, ChunkPos chunkPos) {
        if (handle == null) return;   // 바닐라: 조각이 없으면 아무것도 하지 않는다
        org.domi.furnace.compat.nms.NmsStructures.placeInChunk(this.world, handle, world, structureAccessor, chunkGenerator, random, chunkBox, chunkPos);
    }

    public boolean isValid() { return handle != null; }

    public ChunkPos getChunkPos() { return chunkPos; }

    public boolean canBeReferenced() { return references < getMaxReferences(); }

    public void addReference() { references++; }

    public int getReferences() { return references; }

    protected int getMaxReferences() { return 1; }

    public Structure getStructure() { return structure; }
}
