// 구조물 몹을 만들고 finalizeSpawn 하는 동안만 쓰는 레벨(T-221). 청크는 없고, 몹 코드가 레벨에 묻는 것을 배치 길의 world 로 답한다:
// world 인자 쪽(달 밝기·바이옴·엔티티 찾기·엔티티 더하기·레벨 난수·지역 난이도)과 서버 레벨 쪽(난이도·시간·레지스트리·구조물 관리자).
// 이 레벨의 엔티티는 저장(NBT)하거나 플러그인 레벨에 넘기고 버린다 — 틱·방송·플러그인 이벤트는 없다(바닐라 월드젠도 이벤트가 없다).
package org.domi.furnace.server.world.gen.structure;

import java.util.List;
import java.util.function.Predicate;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.DifficultyInstance;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.world.ChunkColumn;

public final class DetachedMobLevel extends ChunkLevel {

    /** 이 레벨이 묻는 곳. */
    public interface Calls {
        /** 서버 레벨의 난이도·시간·레지스트리(바닐라 this.level()·world.getLevel()). */
        LevelServices services();

        /** world.getMoonBrightness(). */
        float moonBrightness();

        /** world.getBiome(pos) 의 이름. */
        String biomeAt(int x, int y, int z);

        /** world.getEntitiesOfClass(cls, box, filter). */
        <T extends Entity> List<T> entities(Class<T> cls, AABB box, Predicate<? super T> filter);

        /** world.addFreshEntity(e). */
        boolean addFreshEntity(Entity e);

        /** world.getLevel().structureManager().getStructureWithPieceAt(pos, 구조물) — 없으면 null. */
        ChunkStructures.Ref structureWithPieceAt(Pos pos, String structureId);

        /** world.getCurrentDifficultyAt(pos). */
        DifficultyInstance difficultyAtForLevel(Pos pos);

        /** world.getRandom(). */
        RandomSource levelRandom();
    }

    private static final float[] MOON_BRIGHTNESS_PER_PHASE = {1.0F, 0.75F, 0.5F, 0.25F, 0.0F, 0.25F, 0.5F, 0.75F};

    /** DimensionType.MOON_BRIGHTNESS_PER_PHASE[moonPhase(dayTime)]. */
    static float moonBrightness(long dayTime) { return MOON_BRIGHTNESS_PER_PHASE[(int) (dayTime / 24000L % 8L + 8L) % 8]; }

    private final Calls calls;

    DetachedMobLevel(Calls calls) {
        super(1, 0L);
        this.calls = calls;
    }

    @Override protected ChunkColumn chunk(int chunkX, int chunkZ) { return null; }

    @Override protected void markDirty(int chunkX, int chunkZ) {}

    @Override protected void broadcastToChunk(int chunkX, int chunkZ, Packet packet) {}

    @Override protected LevelServices services() { return calls.services(); }

    @Override public org.domi.furnace.server.plugin.GameEvents gameEvents() { return org.domi.furnace.server.plugin.GameEvents.NONE; }

    @Override public RandomSource random() { return calls.levelRandom(); }

    @Override public float moonBrightness() { return calls.moonBrightness(); }

    @Override public String biomeAt(int x, int y, int z) { return calls.biomeAt(x, y, z); }

    @Override
    public <T extends Entity> List<T> getEntitiesOfClass(Class<T> cls, AABB box, Predicate<? super T> filter) {
        return calls.entities(cls, box, filter);
    }

    @Override public boolean addFreshEntity(Entity e) { return calls.addFreshEntity(e); }

    @Override
    public ChunkStructures.Ref structureWithPieceAt(Pos pos, String structureId) { return calls.structureWithPieceAt(pos, structureId); }

    @Override public DifficultyInstance getCurrentDifficultyAt(Pos pos) { return calls.difficultyAtForLevel(pos); }
}
