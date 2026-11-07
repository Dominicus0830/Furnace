// 블록 레벨이 서버에서 가져다 쓰는 것(게임룰·난이도·보더·날씨·시간·플레이어 방송). ServerCore 가 World 에 설치한다.
package org.domi.furnace.server.block;

import java.util.function.Consumer;
import org.domi.furnace.server.entity.Player;

public interface LevelServices {
    boolean gameRule(String name);

    int gameRuleInt(String name);

    int difficulty();

    /** 서버 설정 pvp(server.properties). 서버 없이 쓰는 레벨은 켜짐. */
    default boolean pvp() { return true; }

    /** 서버 점수판(팀 충돌 규칙 조회용). 서버 없이 쓰는 레벨은 null. */
    default org.domi.furnace.server.scores.Scoreboard scoreboard() { return null; }

    /** 월드 기본 게임 모드(level.dat GameType)가 모험인가 — adjustSpawnLocation 이 모험이면 흩뿌리지 않는다. */
    default boolean worldAdventure() { return false; }

    boolean withinBorder(int x, int z);

    /** 보더 {minX, minZ, maxX, maxZ}(기본은 최대 크기). */
    default double[] borderBounds() { return new double[] {-2.9999984E7, -2.9999984E7, 2.9999984E7, 2.9999984E7}; }

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

    /** 이 레벨의 차원 타입(높이·천장·좌표 비). 기본: 차원 키가 바닐라 세 차원이면 그 타입, 아니면 오버월드. */
    default org.domi.furnace.server.world.DimensionType dimensionType() {
        var t = org.domi.furnace.server.world.DimensionType.byLevelKey(dimension());
        return t != null ? t : org.domi.furnace.server.world.DimensionType.OVERWORLD;
    }

    /** server.properties max-chained-neighbor-updates. */
    int maxChainedNeighborUpdates();

    /** 같은 차원의 접속자 전원에게. */
    void forEachPlayer(Consumer<Player> c);

    /** MinecraftServer.getLevel(key): 이 서버가 띄운 다른 차원의 레벨. 띄우지 않았으면 null(바닐라도 null 이면 포털이 아무것도 안 한다). */
    default ChunkLevel otherLevel(String dimension) { return null; }

    /**
     * 다른 region 스레드 소유의 차원으로 옮기는 서버 쪽 경로(없으면 null — 시험 레벨은 otherLevel 로 같은 스레드에서 옮긴다).
     */
    default DimensionTravel dimensionTravel() { return null; }

    /** 차원 이동 경로(World 가 구현). */
    interface DimensionTravel {
        /** MinecraftServer.isLevelEnabled + 그 차원을 띄웠는가. */
        boolean isLevelEnabled(String dimension);

        /**
         * 목적 차원에서 plan.complete 로 목적지를 확정하고 arrive(목적 레벨, 목적지)를 목적 레벨 스레드에서 부른다.
         * 목적 차원이 없거나 목적지가 없으면 arrive(null, null) — 부르는 쪽이 되돌린다. 어느 스레드에서 불러도 된다.
         */
        void travel(org.domi.furnace.server.entity.PortalTravel plan,
                    java.util.function.BiConsumer<ChunkLevel, org.domi.furnace.server.entity.DimensionTransition> arrive);

        /** 원천 레벨 스레드에서: 되돌릴 때 그 칸 주인 region 에서 task(그 레벨). */
        void runAtSource(int blockX, int blockZ, java.util.function.Consumer<ChunkLevel> task);

        /**
         * 이 차원(원천 레벨과 같은 차원)에서: (centerX, centerZ) 둘레 createRadius 블록 안 청크를 생성해서라도 올리고, 그 청크들이
         * 한 region 에 모이면 그 region 스레드에서 task(레벨). 청크를 준비하지 못하면 task(null). 어느 스레드에서 불러도 된다.
         */
        default void withLoadedChunks(int centerX, int centerZ, int createRadius, java.util.function.Consumer<ChunkLevel> task) { task.accept(null); }
    }

    /** 그 칸의 바이옴(Level.getBiome — 확대 지터 포함)이 태그에 드는가. 모르면 거짓. */
    default boolean biomeIs(int x, int y, int z, String tag) { return false; }

    /**
     * 블록 전리품(Block.getDrops — BLOCK 문맥): 그 전리품 표를 origin·tool·block_state·this_entity·explosion_radius 로 평가한다.
     * entity 는 Player(없으면 null), explosionRadius 가 null 이면 폭발 아님. 데이터 팩이 없으면 빈 목록.
     */
    default java.util.List<org.domi.furnace.server.entity.ItemStack> blockDrops(String lootTable, int state, double x, double y, double z,
            org.domi.furnace.server.entity.ItemStack tool, Object entity, Float explosionRadius, java.util.Map<String, Object> blockEntity) { return java.util.List.of(); }

    /**
     * 엔티티 사망 전리품(LivingEntity.dropFromLootTable): 표를 this_entity·origin·damage_source·attacking_entity·last_damage_player 로 평가.
     * attacker/lastPlayer 는 Player(없으면 null). 데이터 팩이 없으면 빈 목록.
     */
    default java.util.List<org.domi.furnace.server.entity.ItemStack> entityDrops(String lootTable, org.domi.furnace.server.loot.LootEntity self,
            double x, double y, double z, String damageType, Player attacker, Player lastPlayer, long seed) { return java.util.List.of(); }

    /**
     * 낚시 전리품(FishingHook.retrieve — FISHING 문맥): gameplay/fishing 을 origin·tool·this_entity(낚시찌)·luck 로 평가.
     * 데이터 팩이 없으면 빈 목록.
     */
    default java.util.List<org.domi.furnace.server.entity.ItemStack> fishingLoot(org.domi.furnace.server.loot.LootEntity hook, double x, double y,
            double z, org.domi.furnace.server.entity.ItemStack tool, float luck) { return java.util.List.of(); }

    /** 선물 전리품(GIFT 문맥: origin·this_entity). 표의 random_sequence 가 없으면 월드 난수. 데이터 팩이 없으면 빈 목록. */
    default java.util.List<org.domi.furnace.server.entity.ItemStack> giftLoot(String lootTable, org.domi.furnace.server.loot.LootEntity self,
            double x, double y, double z) { return java.util.List.of(); }

    /**
     * 일반 전리품 평가(시련 방·금고·장비 표): origin·this_entity·luck 로 평가한다. seed != 0 이면 그 시드(Legacy), 아니면 random 이 있으면 그 난수,
     * 아니면 표의 random_sequence/월드 난수. 최대 스택으로 나눈 결과. 데이터 팩이 없으면 빈 목록.
     */
    default java.util.List<org.domi.furnace.server.entity.ItemStack> lootItems(String lootTable, double[] origin,
            org.domi.furnace.server.loot.LootEntity thisEntity, float luck, long seed, org.domi.furnace.server.block.RandomSource random) {
        return java.util.List.of();
    }

    /**
     * 고고학 전리품: 표를 CHEST 문맥(origin, this_entity=플레이어, luck)과 시드로 평가한다(바닐라 BrushableBlockEntity.unpackLootTable).
     * player 는 Player(없으면 null). 데이터 팩이 없으면 빈 목록.
     */
    default java.util.List<org.domi.furnace.server.entity.ItemStack> chestLoot(String lootTable, long seed, double x, double y, double z,
            Object player, float luck) { return java.util.List.of(); }

    /** Level.getBiome(pos) 의 이름(확대 지터 포함). 모르면 null. */
    default String biomeAt(int x, int y, int z) { return null; }

    /** NaturalSpawner.getRoughBiome: 확대 지터 없이 그 칸이 든 quart 의 바이옴 이름(높이는 월드 안으로 자른다). 기본은 biomeAt. */
    default String roughBiomeAt(int x, int y, int z) { return biomeAt(x, y, z); }

    /** 월드 시드(슬라임 청크 …). */
    default long seed() { return 0L; }

    /** 월드 스폰 지점 {x, y, z}. */
    default int[] sharedSpawnPos() { return new int[] {0, 64, 0}; }

    /** server.properties spawn-monsters / spawn-animals. */
    default boolean spawnMonsters() { return true; }

    default boolean spawnAnimals() { return true; }

    /** server.properties spawn-npcs(끄면 마을 주민·떠돌이 상인 같은 Npc 를 틱 때 지운다). */
    default boolean spawnNpcs() { return true; }

    /** server.properties simulation-distance. */
    default int simulationDistance() { return 10; }

    /** server.properties player-idle-timeout(분, 0 이면 끔). */
    default int playerIdleTimeoutMinutes() { return 0; }

    /** RandomizableContainer.unpackLootTable(null): 열지 않은 전리품 상자를 채운다(부술 때). 데이터 팩이 없으면 아무것도. */
    default void unpackContainerLoot(Object container) {}

    /** 서버 레시피 매니저(RecipeManager, 없으면 null) — 화로 굽기 등. */
    default Object recipes() { return null; }

    /** 탐험 지도 만들기 지원(구조물 찾기·바이옴). 월드 생성기가 없으면 null — 지도 거래는 만들어지지 않는다. */
    default org.domi.furnace.server.map.ExplorerMaps.Backend explorerMaps() { return null; }

    /** 데이터 팩 전리품 데이터(인챈트 정의·마법 부여 제공자). 데이터 팩이 없으면 null. */
    default org.domi.furnace.server.loot.LootData loot() { return null; }

    /** server.properties entity-broadcast-range-percentage(엔티티 추적 거리 비율). */
    default int entityBroadcastRangePercentage() { return 100; }

    /** server.properties allow-flight(MinecraftServer.isFlightAllowed) — 참이면 공중에 떠 있어도 비행 킥이 없다. */
    default boolean flightAllowed() { return false; }

    /** ServerTickRateManager.runsNormally — 틱이 멈춘(/tick freeze) 동안은 거짓. 이동 속도 검사가 이때 꺼진다. */
    default boolean tickRunsNormally() { return true; }

    /** 바닐라 기본값이 false 인 참/거짓 게임룰(DEFAULT 가 참으로 답하지 않을 것들). */
    java.util.Set<String> DEFAULT_FALSE_RULES = java.util.Set.of("lavaSourceConversion", "doImmediateRespawn", "doLimitedCrafting",
            "universalAnger", "reducedDebugInfo", "disableElytraMovementCheck", "disableRaids");

    /** 표지판 클릭 명령(BlockLevel.runSignCommand). player 는 Player(없으면 null). */
    default void runSignCommand(Player player, int x, int y, int z, String command) {}

    /** 표지판 글 풀기(BlockLevel.resolveSignText). 서버 없는 레벨은 그대로. */
    default org.domi.furnace.api.text.Text resolveSignText(int x, int y, int z, org.domi.furnace.api.text.Text text) { return text; }

    /** server.properties enable-command-block(MinecraftServer.isCommandBlockEnabled). 서버 없는 레벨은 꺼짐(바닐라 기본값). */
    default boolean commandBlockEnabled() { return false; }

    /**
     * 명령 블록·명령 블록 수레의 명령 실행: 전역 스레드에서 sink 를 출력 대상(권한 2)으로 돌리고, 명령 컨텍스트가 모두 끝나면
     * (지연 결과 포함) 성공 콜백이 불린 횟수로 done 을 부른다 — done 은 어느 스레드에서든 불릴 수 있다(받는 쪽이 소유 region 으로 넘긴다).
     * 서버 없는 레벨은 아무것도 안 돌리고 0.
     */
    default void runBlockCommand(BlockCommandSink sink, String command, java.util.function.IntConsumer done) { done.accept(0); }

    /** 플러그인 이벤트 입구. 서버 없는 레벨은 아무것도 안 부른다. */
    default org.domi.furnace.server.plugin.GameEvents gameEvents() { return org.domi.furnace.server.plugin.GameEvents.NONE; }

    /** 기본값(서버 없이 쓰는 테스트·도구용). */
    LevelServices DEFAULT = new LevelServices() {
        public boolean gameRule(String name) { return !DEFAULT_FALSE_RULES.contains(name); }
        public int gameRuleInt(String name) {
            return switch (name) {
                case "randomTickSpeed" -> 3;
                case "playersSleepingPercentage" -> 100;
                default -> 0;
            };
        }
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
        public boolean pvp() { return d.pvp(); }
        public org.domi.furnace.server.plugin.GameEvents gameEvents() { return d.gameEvents(); }
        public org.domi.furnace.server.scores.Scoreboard scoreboard() { return d.scoreboard(); }
        public boolean worldAdventure() { return d.worldAdventure(); }
        public boolean withinBorder(int x, int z) { return d.withinBorder(x, z); }
        public double[] borderBounds() { return d.borderBounds(); }
        public boolean isRaining() { return d.isRaining(); }
        public boolean isThundering() { return d.isThundering(); }
        public float rainLevel() { return d.rainLevel(); }
        public float thunderLevel() { return d.thunderLevel(); }
        public long dayTime() { return d.dayTime(); }
        public long gameTime() { return d.gameTime(); }
        public String dimension() { return d.dimension(); }
        public boolean ultraWarm() { return d.ultraWarm(); }
        public boolean hasSkyLight() { return d.hasSkyLight(); }
        public org.domi.furnace.server.world.DimensionType dimensionType() { return d.dimensionType(); }
        public int maxChainedNeighborUpdates() { return d.maxChainedNeighborUpdates(); }
        public void forEachPlayer(Consumer<Player> c) { d.forEachPlayer(c); }
        public ChunkLevel otherLevel(String dimension) { return d.otherLevel(dimension); }
        public DimensionTravel dimensionTravel() { return d.dimensionTravel(); }
        public java.util.List<org.domi.furnace.server.entity.ItemStack> blockDrops(String lootTable, int state, double x, double y, double z,
                org.domi.furnace.server.entity.ItemStack tool, Object entity, Float explosionRadius, java.util.Map<String, Object> blockEntity) {
            return d.blockDrops(lootTable, state, x, y, z, tool, entity, explosionRadius, blockEntity);
        }
        public java.util.List<org.domi.furnace.server.entity.ItemStack> entityDrops(String lootTable, org.domi.furnace.server.loot.LootEntity self,
                double x, double y, double z, String damageType, Player attacker, Player lastPlayer, long seed) {
            return d.entityDrops(lootTable, self, x, y, z, damageType, attacker, lastPlayer, seed);
        }
        public java.util.List<org.domi.furnace.server.entity.ItemStack> fishingLoot(org.domi.furnace.server.loot.LootEntity hook, double x, double y,
                double z, org.domi.furnace.server.entity.ItemStack tool, float luck) {
            return d.fishingLoot(hook, x, y, z, tool, luck);
        }
        public java.util.List<org.domi.furnace.server.entity.ItemStack> giftLoot(String lootTable, org.domi.furnace.server.loot.LootEntity self,
                double x, double y, double z) {
            return d.giftLoot(lootTable, self, x, y, z);
        }
        public java.util.List<org.domi.furnace.server.entity.ItemStack> lootItems(String lootTable, double[] origin,
                org.domi.furnace.server.loot.LootEntity thisEntity, float luck, long seed, RandomSource random) {
            return d.lootItems(lootTable, origin, thisEntity, luck, seed, random);
        }

        public java.util.List<org.domi.furnace.server.entity.ItemStack> chestLoot(String lootTable, long seed, double x, double y, double z,
                Object player, float luck) {
            return d.chestLoot(lootTable, seed, x, y, z, player, luck);
        }
        public String biomeAt(int x, int y, int z) { return d.biomeAt(x, y, z); }
        public String roughBiomeAt(int x, int y, int z) { return d.roughBiomeAt(x, y, z); }
        public int simulationDistance() { return d.simulationDistance(); }
        public int playerIdleTimeoutMinutes() { return d.playerIdleTimeoutMinutes(); }
        public int entityBroadcastRangePercentage() { return d.entityBroadcastRangePercentage(); }
        public boolean flightAllowed() { return d.flightAllowed(); }
        public boolean tickRunsNormally() { return d.tickRunsNormally(); }
        public void unpackContainerLoot(Object container) { d.unpackContainerLoot(container); }
        public Object recipes() { return d.recipes(); }
        public org.domi.furnace.server.loot.LootData loot() { return d.loot(); }
        public boolean biomeIs(int x, int y, int z, String tag) { return d.biomeIs(x, y, z, tag); }
        public long seed() { return d.seed(); }
        public int[] sharedSpawnPos() { return d.sharedSpawnPos(); }
        public boolean spawnMonsters() { return d.spawnMonsters(); }
        public boolean spawnAnimals() { return d.spawnAnimals(); }
        public boolean spawnNpcs() { return d.spawnNpcs(); }
        public void runSignCommand(Player player, int x, int y, int z, String command) { d.runSignCommand(player, x, y, z, command); }
        public org.domi.furnace.api.text.Text resolveSignText(int x, int y, int z, org.domi.furnace.api.text.Text text) { return d.resolveSignText(x, y, z, text); }
        public boolean commandBlockEnabled() { return d.commandBlockEnabled(); }
        public void runBlockCommand(BlockCommandSink sink, String command, java.util.function.IntConsumer done) { d.runBlockCommand(sink, command, done); }
    }
}
