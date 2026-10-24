// 블록 레벨이 서버에서 가져다 쓰는 것(게임룰·난이도·보더·날씨·시간·플레이어 방송). ServerCore 가 World 에 설치한다.
package org.domi.furnace.server.block;

import java.util.function.Consumer;
import org.domi.furnace.server.entity.Player;

public interface LevelServices {
    boolean gameRule(String name);

    int gameRuleInt(String name);

    int difficulty();

    boolean withinBorder(int x, int z);

    boolean isRaining();

    boolean isThundering();

    /** Level.getRainLevel(1.0F)(0..1, 날씨가 바뀔 때 서서히 변한다). */
    default float rainLevel() { return isRaining() ? 1f : 0f; }

    /** 천둥 세기(비 세기를 곱하기 전, 0..1). */
    default float thunderLevel() { return isThundering() ? 1f : 0f; }

    long dayTime();

    long gameTime();

    String dimension();

    boolean ultraWarm();

    /** DimensionType.hasSkyLight. */
    default boolean hasSkyLight() { return true; }

    /** server.properties max-chained-neighbor-updates. */
    int maxChainedNeighborUpdates();

    /** 같은 차원의 접속자 전원에게. */
    void forEachPlayer(Consumer<Player> c);

    /** 그 칸의 바이옴(Level.getBiome — 확대 지터 포함)이 태그에 드는가. 모르면 거짓. */
    default boolean biomeIs(int x, int y, int z, String tag) { return false; }

    /**
     * 블록 전리품(Block.getDrops — BLOCK 문맥): 그 전리품 표를 origin·tool·block_state·this_entity·explosion_radius 로 평가한다.
     * entity 는 Player(없으면 null), explosionRadius 가 null 이면 폭발 아님. 데이터 팩이 없으면 빈 목록.
     */
    default java.util.List<org.domi.furnace.server.entity.ItemStack> blockDrops(String lootTable, int state, double x, double y, double z,
            org.domi.furnace.server.entity.ItemStack tool, Object entity, Float explosionRadius) { return java.util.List.of(); }

    /** Level.getBiome(pos) 의 이름(확대 지터 포함). 모르면 null. */
    default String biomeAt(int x, int y, int z) { return null; }

    /** server.properties simulation-distance. */
    default int simulationDistance() { return 10; }

    /** RandomizableContainer.unpackLootTable(null): 열지 않은 전리품 상자를 채운다(부술 때). 데이터 팩이 없으면 아무것도. */
    default void unpackContainerLoot(Object container) {}

    /** 서버 레시피 매니저(RecipeManager, 없으면 null) — 화로 굽기 등. */
    default Object recipes() { return null; }

    /** server.properties entity-broadcast-range-percentage(엔티티 추적 거리 비율). */
    default int entityBroadcastRangePercentage() { return 100; }

    /** 바닐라 기본값이 false 인 참/거짓 게임룰(DEFAULT 가 참으로 답하지 않을 것들). */
    java.util.Set<String> DEFAULT_FALSE_RULES = java.util.Set.of("lavaSourceConversion", "doImmediateRespawn", "doLimitedCrafting",
            "universalAnger", "reducedDebugInfo", "disableElytraMovementCheck", "disableRaids");

    /** 기본값(서버 없이 쓰는 테스트·도구용). */
    LevelServices DEFAULT = new LevelServices() {
        public boolean gameRule(String name) { return !DEFAULT_FALSE_RULES.contains(name); }
        public int gameRuleInt(String name) { return name.equals("randomTickSpeed") ? 3 : 0; }
        public int difficulty() { return 2; }
        public boolean withinBorder(int x, int z) { return true; }
        public boolean isRaining() { return false; }
        public boolean isThundering() { return false; }
        public long dayTime() { return 0; }
        public long gameTime() { return 0; }
        public String dimension() { return "minecraft:overworld"; }
        public boolean ultraWarm() { return false; }
        public int maxChainedNeighborUpdates() { return 1000000; }
        public void forEachPlayer(Consumer<Player> c) {}
    };

    /** 일부만 바꿔 쓰는 위임. */
    class Delegating implements LevelServices {
        private final LevelServices d;

        public Delegating(LevelServices d) { this.d = d; }

        public boolean gameRule(String name) { return d.gameRule(name); }
        public int gameRuleInt(String name) { return d.gameRuleInt(name); }
        public int difficulty() { return d.difficulty(); }
        public boolean withinBorder(int x, int z) { return d.withinBorder(x, z); }
        public boolean isRaining() { return d.isRaining(); }
        public boolean isThundering() { return d.isThundering(); }
        public float rainLevel() { return d.rainLevel(); }
        public float thunderLevel() { return d.thunderLevel(); }
        public long dayTime() { return d.dayTime(); }
        public long gameTime() { return d.gameTime(); }
        public String dimension() { return d.dimension(); }
        public boolean ultraWarm() { return d.ultraWarm(); }
        public boolean hasSkyLight() { return d.hasSkyLight(); }
        public int maxChainedNeighborUpdates() { return d.maxChainedNeighborUpdates(); }
        public void forEachPlayer(Consumer<Player> c) { d.forEachPlayer(c); }
        public java.util.List<org.domi.furnace.server.entity.ItemStack> blockDrops(String lootTable, int state, double x, double y, double z,
                org.domi.furnace.server.entity.ItemStack tool, Object entity, Float explosionRadius) {
            return d.blockDrops(lootTable, state, x, y, z, tool, entity, explosionRadius);
        }
        public String biomeAt(int x, int y, int z) { return d.biomeAt(x, y, z); }
        public int simulationDistance() { return d.simulationDistance(); }
        public int entityBroadcastRangePercentage() { return d.entityBroadcastRangePercentage(); }
        public void unpackContainerLoot(Object container) { d.unpackContainerLoot(container); }
        public Object recipes() { return d.recipes(); }
        public boolean biomeIs(int x, int y, int z, String tag) { return d.biomeIs(x, y, z, tag); }
    }
}
