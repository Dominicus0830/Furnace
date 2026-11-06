// 바닐라 WanderingTraderSpawner(CustomSpawner): 하루(24000틱)마다 25~75% 확률(실패할 때마다 25씩 증가)로 플레이어 근처에 방랑 상인과 짐 라마 2마리를 낸다.
// 마을 종: 플레이어 근처 48칸의 MEETING POI(PoiManager.find, 첫 것)가 있으면 그 둘레, 없으면 플레이어 위치.
// 저장: 오버월드 월드가 공유 상태(WanderingTraderState)를 붙여 주면 지연·확률·상인 번호를 level.dat 에 저장·복원한다
//   (같은 게임 틱에는 한 region 만 타이머를 돌린다). 붙지 않으면(시험·단독 레벨) 이 스포너의 필드만 쓴다.
package org.domi.furnace.server.entity.spawn;

import org.domi.furnace.server.block.BlockInfo;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.Entities;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.TraderLlama;
import org.domi.furnace.server.entity.WanderingTrader;

public final class WanderingTraderSpawner {
    public static final int DEFAULT_TICK_DELAY = 1200;
    public static final int DEFAULT_SPAWN_DELAY = 24000;
    private static final int MIN_SPAWN_CHANCE = 25, MAX_SPAWN_CHANCE = 75, SPAWN_CHANCE_STEP = 25;
    private static final int NUMBER_OF_SPAWN_ATTEMPTS = 10;
    private static final int TRADER_TYPE_RANGE = 48;
    private static final String TRADER = "minecraft:wandering_trader", LLAMA = "minecraft:trader_llama";

    private final RandomSource random;
    private int tickDelay = DEFAULT_TICK_DELAY;
    private int spawnDelay = DEFAULT_SPAWN_DELAY;
    private int spawnChance = MIN_SPAWN_CHANCE;

    public WanderingTraderSpawner() { this(RandomSource.create()); }

    /** 난수 주입(시험). 바닐라도 스포너 전용 난수(RandomSource.create())를 따로 쓴다. */
    public WanderingTraderSpawner(RandomSource random) { this.random = random; }

    private org.domi.furnace.server.world.WanderingTraderState shared;

    /** 월드 공유 상태를 붙인다(한 번). 바닐라 생성자처럼 지연·확률이 0/0 이면 기본값으로 시작한다. */
    public void attach(org.domi.furnace.server.world.WanderingTraderState state) {
        if (shared == state) return;
        shared = state;
        state.normalize();
    }

    public int spawnDelay() { return spawnDelay; }

    public int spawnChance() { return spawnChance; }

    public void setState(int spawnDelay, int spawnChance) {
        this.spawnDelay = spawnDelay;
        this.spawnChance = spawnChance;
    }

    /** 낸 상인 수(0/1). players: 이 레벨(region)의 플레이어. */
    public int tick(ChunkLevel level, java.util.List<Player> players) {
        if (!level.gameRule("doTraderSpawning")) return 0;
        var st = shared;
        if (st == null) return tickTimer(level, players, null);
        // 플레이어가 없는 region 은 타이머를 소비하지 않는다(그 틱은 플레이어 있는 region 이 돌린다)
        if (players.isEmpty()) return 0;
        synchronized (st.lock()) {
            if (!st.claimTick(level.gameTime())) return 0;   // 같은 틱에 다른 region 이 이미 돌렸다
            tickDelay = st.tickDelay();
            spawnDelay = st.spawnDelay();
            spawnChance = st.spawnChance();
            try {
                return tickTimer(level, players, st);
            } finally {   // 살아 있는 값을 그대로 공유 상태(= level.dat)에 둔다
                st.setTickDelay(tickDelay);
                st.setSpawnDelay(spawnDelay);
                st.setSpawnChance(spawnChance);
            }
        }
    }

    private int tickTimer(ChunkLevel level, java.util.List<Player> players, org.domi.furnace.server.world.WanderingTraderState st) {
        if (--tickDelay > 0) return 0;
        tickDelay = DEFAULT_TICK_DELAY;
        spawnDelay -= DEFAULT_TICK_DELAY;
        if (spawnDelay > 0) return 0;
        spawnDelay = DEFAULT_SPAWN_DELAY;
        if (!level.gameRule("doMobSpawning")) return 0;
        int chance = spawnChance;
        spawnChance = Math.max(MIN_SPAWN_CHANCE, Math.min(MAX_SPAWN_CHANCE, spawnChance + SPAWN_CHANCE_STEP));
        if (random.nextInt(100) > chance) return 0;
        if (spawn(level, players)) {
            spawnChance = MIN_SPAWN_CHANCE;
            return 1;
        }
        return 0;
    }

    private boolean spawn(ChunkLevel level, java.util.List<Player> players) {
        Player player = randomPlayer(level, players);
        if (player == null) return true;
        if (random.nextInt(10) != 0) return false;
        Pos playerPos = player.blockPosition();
        Pos meeting = level.poiManager().find(t -> t == org.domi.furnace.server.world.poi.PoiTypes.MEETING, p -> true, playerPos,
                TRADER_TYPE_RANGE, org.domi.furnace.server.world.poi.PoiManager.Occupancy.ANY);
        Pos villagePos = meeting != null ? meeting : playerPos;
        Pos spawnPos = findSpawnPositionNear(level, villagePos, TRADER_TYPE_RANGE);
        if (spawnPos == null || !hasEnoughSpace(level, spawnPos)) return false;
        if (level.biomeIs(spawnPos, "minecraft:without_wandering_trader_spawns")) return false;
        if (!(spawnEvent(level, TRADER, spawnPos) instanceof WanderingTrader trader)) return false;
        for (int i = 0; i < 2; i++) tryToSpawnLlamaFor(level, trader, 4);
        if (shared != null) shared.setTraderId(trader.uuid());
        trader.setDespawnDelay(48000);
        trader.setWanderTarget(villagePos);
        trader.restrictTo(villagePos, 16);
        return true;
    }

    /** ServerLevel.getRandomPlayer: 살아 있는 플레이어 중 레벨 난수로 하나(없으면 null). */
    private static Player randomPlayer(ChunkLevel level, java.util.List<Player> players) {
        java.util.List<Player> alive = players.stream().filter(p -> !p.vitals().isDeadOrDying()).toList();
        return alive.isEmpty() ? null : alive.get(level.random().nextInt(alive.size()));
    }

    private void tryToSpawnLlamaFor(ChunkLevel level, WanderingTrader trader, int range) {
        Pos pos = findSpawnPositionNear(level, trader.blockPosition(), range);
        if (pos == null) return;
        if (spawnEvent(level, LLAMA, pos) instanceof TraderLlama llama) llama.setLeashedTo(trader, true);
    }

    /** 높이 맵 위의 칸을 range 둘레에서 최대 10번 무작위로 골라 방랑 상인이 설 수 있는 첫 칸. 난수는 x 다음 z. */
    public Pos findSpawnPositionNear(ChunkLevel level, Pos around, int range) {
        Pos result = null;
        for (int i = 0; i < NUMBER_OF_SPAWN_ATTEMPTS; i++) {
            int x = around.x() + random.nextInt(range * 2) - range;
            int z = around.z() + random.nextInt(range * 2) - range;
            int y = level.getWorldSurfaceHeight(x, z);
            Pos pos = new Pos(x, y, z);
            if (SpawnPlacements.isSpawnPositionOk(TRADER, level, pos)) {
                result = pos;
                break;
            }
        }
        return result;
    }

    /** pos 에서 (+1, +2, +1) 까지의 칸이 모두 충돌이 없다. */
    static boolean hasEnoughSpace(ChunkLevel level, Pos pos) {
        for (int x = pos.x(); x <= pos.x() + 1; x++)
            for (int y = pos.y(); y <= pos.y() + 2; y++)
                for (int z = pos.z(); z <= pos.z() + 1; z++)
                    if (!BlockInfo.get().collisionShape(level.getBlockState(new Pos(x, y, z))).isEmpty()) return false;
        return true;
    }

    /** EntityType.spawn(level, pos, EVENT): create → 무작위 방향 → finalizeSpawn(레벨 난수) → 추가 → 주변 소리. */
    private static org.domi.furnace.server.entity.Entity spawnEvent(ChunkLevel level, String type, Pos pos) {
        if (!(Entities.create(level, type) instanceof Mob mob)) return null;
        mob.moveTo(pos.x() + 0.5, pos.y(), pos.z() + 0.5,
                org.domi.furnace.server.entity.ai.control.Mth.wrapDegrees(level.random().nextFloat() * 360.0F), 0.0F);
        mob.setYHeadRot(mob.yRot());
        mob.setYBodyRot(mob.yRot());
        mob.finalizeSpawnGroup(level.random(), level.getCurrentDifficultyAt(mob.blockPosition()), null);
        level.addFreshEntity(mob.spawnReason("NATURAL"));
        mob.makeSound(mob.ambientSoundForSpawn());
        return mob;
    }
}
