// 바닐라 WanderingTraderSpawner(CustomSpawner): 하루(24000틱)마다 25~75% 확률(실패할 때마다 25씩 증가)로 플레이어 근처에 방랑 상인과 짐 라마 2마리를 낸다.
// 마을 종: 플레이어 근처 48칸의 MEETING POI(PoiManager.find, 첫 것)가 있으면 그 둘레, 없으면 플레이어 위치.
// 저장: 오버월드 월드가 공유 상태(WanderingTraderState)를 붙여 주면 지연·확률·상인 번호를 level.dat 에 저장·복원한다.
//   붙지 않으면(시험·단독 레벨) 이 스포너의 필드만 쓴다.
// 서버: 차원(오버월드)에 스포너 하나를 두고 전역 틱이 게임 틱마다 한 번 돌린다(DimensionSpawners) — 바닐라처럼 플레이어가 없어도 타이머가 돌고,
//   상인 대상은 차원 전체 살아 있는 플레이어 중에서 뽑는다. 상인을 찾고 세우는 일은 그 플레이어 region 스레드로 넘긴다(다음 region 틱 머리).
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

    private volatile org.domi.furnace.server.world.WanderingTraderState shared;

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

    /**
     * 스포너가 레벨·플레이어에 닿는 길. 단독 레벨(시험)은 그 자리에서(of), 서버는 차원 하나에 스포너 하나를 두고 전역 틱이 돌리며
     * 스폰은 고른 플레이어의 region 스레드로 넘긴다(그 플레이어를 가진 레벨이 work 에 들어간다, 닿지 못하면 null — 늦게 돌 수 있다).
     */
    public interface Host {
        boolean gameRule(String name);

        long gameTime();

        /** ServerLevel.getRandomPlayer 의 레벨 난수. */
        RandomSource levelRandom();

        /** 바닐라 level.players() — 차원 전체, 그 순서. */
        java.util.List<Player> players();

        void onPlayer(Player p, java.util.function.Consumer<ChunkLevel> work);

        /** 넘긴 일이 쓸 스포너 난수: 그 자리에서 하면 스포너 난수 그대로, 다른 스레드로 넘기면 거기서 갈라 낸 새 난수(난수 객체를 스레드끼리 나누지 않게). */
        default RandomSource workRandom(RandomSource spawner) { return spawner; }

        static Host of(ChunkLevel level, java.util.List<Player> players) {
            return new Host() {
                @Override public boolean gameRule(String name) { return level.gameRule(name); }
                @Override public long gameTime() { return level.gameTime(); }
                @Override public RandomSource levelRandom() { return level.random(); }
                @Override public java.util.List<Player> players() { return players; }
                @Override public void onPlayer(Player p, java.util.function.Consumer<ChunkLevel> work) { work.accept(level); }
            };
        }
    }

    /** 단독 레벨: 낸 상인 수(0/1). players: 바닐라 level.players(). */
    public int tick(ChunkLevel level, java.util.List<Player> players) { return tick(Host.of(level, players)); }

    /** 바닐라 WanderingTraderSpawner.tick(게임 틱마다 한 번 — 플레이어가 없어도 타이머는 돈다). 그 자리에서 낸 상인 수(0/1). */
    public int tick(Host host) {
        if (!host.gameRule("doTraderSpawning")) return 0;
        var st = shared;
        if (st == null) {
            synchronized (this) { return tickTimer(host, null); }
        }
        synchronized (st.lock()) {
            if (!st.claimTick(host.gameTime())) return 0;   // 같은 게임 틱에 이미 돌렸다
            tickDelay = st.tickDelay();
            spawnDelay = st.spawnDelay();
            spawnChance = st.spawnChance();
            try {
                return tickTimer(host, st);
            } finally {   // 살아 있는 값을 그대로 공유 상태(= level.dat)에 둔다
                st.setTickDelay(tickDelay);
                st.setSpawnDelay(spawnDelay);
                st.setSpawnChance(spawnChance);
            }
        }
    }

    private int tickTimer(Host host, org.domi.furnace.server.world.WanderingTraderState st) {
        if (--tickDelay > 0) return 0;
        tickDelay = DEFAULT_TICK_DELAY;
        spawnDelay -= DEFAULT_TICK_DELAY;
        if (spawnDelay > 0) return 0;
        spawnDelay = DEFAULT_SPAWN_DELAY;
        if (!host.gameRule("doMobSpawning")) return 0;
        int chance = spawnChance;
        spawnChance = Math.max(MIN_SPAWN_CHANCE, Math.min(MAX_SPAWN_CHANCE, spawnChance + SPAWN_CHANCE_STEP));
        if (random.nextInt(100) > chance) return 0;
        if (spawn(host, st)) {
            spawnChance = MIN_SPAWN_CHANCE;
            return 1;
        }
        return 0;
    }

    /** 바닐라 spawn: 플레이어가 없으면 참. 스폰이 다른 스레드로 넘어가면 지금은 거짓이고, 성공하면 그쪽에서 확률을 25 로 되돌린다. */
    private boolean spawn(Host host, org.domi.furnace.server.world.WanderingTraderState st) {
        Player player = randomPlayer(host);
        if (player == null) return true;
        if (random.nextInt(10) != 0) return false;
        RandomSource rnd = host.workRandom(random);
        boolean[] returned = {false}, inline = {false};
        host.onPlayer(player, level -> {
            boolean ok = level != null && spawnNear(level, player, rnd, st);
            if (!returned[0]) inline[0] = ok;
            else if (ok && st != null) {
                synchronized (st.lock()) { st.setSpawnChance(MIN_SPAWN_CHANCE); }
            }
        });
        returned[0] = true;
        return inline[0];
    }

    /** spawn 의 나머지(플레이어를 가진 레벨 스레드에서): 마을 종 둘레(없으면 플레이어 자리)에 상인 + 짐 라마 2. */
    private static boolean spawnNear(ChunkLevel level, Player player, RandomSource random, org.domi.furnace.server.world.WanderingTraderState st) {
        Pos playerPos = player.blockPosition();
        Pos meeting = level.poiManager().find(t -> t == org.domi.furnace.server.world.poi.PoiTypes.MEETING, p -> true, playerPos,
                TRADER_TYPE_RANGE, org.domi.furnace.server.world.poi.PoiManager.Occupancy.ANY);
        Pos villagePos = meeting != null ? meeting : playerPos;
        Pos spawnPos = findSpawnPositionNear(level, villagePos, TRADER_TYPE_RANGE, random);
        if (spawnPos == null || !hasEnoughSpace(level, spawnPos)) return false;
        if (level.biomeIs(spawnPos, "minecraft:without_wandering_trader_spawns")) return false;
        if (!(spawnEvent(level, TRADER, spawnPos) instanceof WanderingTrader trader)) return false;
        for (int i = 0; i < 2; i++) tryToSpawnLlamaFor(level, trader, 4, random);
        if (st != null) st.setTraderId(trader.uuid());
        trader.setDespawnDelay(48000);
        trader.setWanderTarget(villagePos);
        trader.restrictTo(villagePos, 16);
        return true;
    }

    /** ServerLevel.getRandomPlayer: 살아 있는 플레이어 중 레벨 난수로 하나(없으면 null). */
    private static Player randomPlayer(Host host) {
        java.util.List<Player> alive = host.players().stream().filter(p -> !p.vitals().isDeadOrDying()).toList();
        return alive.isEmpty() ? null : alive.get(host.levelRandom().nextInt(alive.size()));
    }

    private static void tryToSpawnLlamaFor(ChunkLevel level, WanderingTrader trader, int range, RandomSource random) {
        Pos pos = findSpawnPositionNear(level, trader.blockPosition(), range, random);
        if (pos == null) return;
        if (spawnEvent(level, LLAMA, pos) instanceof TraderLlama llama) llama.setLeashedTo(trader, true);
    }

    /** 높이 맵 위의 칸을 range 둘레에서 최대 10번 무작위로 골라 방랑 상인이 설 수 있는 첫 칸. 난수는 x 다음 z. */
    public Pos findSpawnPositionNear(ChunkLevel level, Pos around, int range) { return findSpawnPositionNear(level, around, range, random); }

    private static Pos findSpawnPositionNear(ChunkLevel level, Pos around, int range, RandomSource random) {
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
