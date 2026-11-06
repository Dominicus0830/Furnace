// 바닐라 VillageSiege(CustomSpawner, 오버월드): 밤이 한가운데(getTimeOfDay == 0.5)가 되는 순간 1/10 확률로 그날 밤 포위를 정한다.
// 포위 밤에는 마을(POI 섹션 거리 1 이내)에 있는 첫 비관전 플레이어 둘레 32칸 원 위의 한 점을 잡고, 3틱마다 좀비를 한 마리씩 최대 20마리 낸다.
// 좀비는 그 점 둘레 16x16 안에서 10번 뽑은 마을 안의 어둡고 설 수 있는 칸에 EVENT 로 난다.
// 서버에서는 차원(오버월드)에 하나만 두고 전역 틱이 게임 틱마다 한 번 돌린다(DimensionSpawners) — 바닐라처럼 밤마다 굴림 한 번,
// 플레이어 순서는 차원 전체. 칸을 읽는 일(마을 판정·스폰)은 Host 가 그 칸을 가진 region 스레드로 넘긴다(다음 region 틱 머리에 돈다).
// region 은 틱마다 자기 플레이어 중 마을 안에 있는 사람을 알려 준다(report) — 포위를 정할 첫 플레이어는 그 보고로 고른다.
// 가정: finalizeSpawn 을 moveTo 보다 먼저 부르므로 난이도는 원점 칸(0,0,0) 기준이다(바닐라 코드 순서 그대로, 원점 청크가 그 region 에 없으면 머문 시간 0).
package org.domi.furnace.server.entity.spawn;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.Entities;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Zombie;
import org.domi.furnace.server.entity.ai.control.Mth;
import org.domi.furnace.server.world.GameMode;

public final class VillageSiege {
    private static final String ZOMBIE = "minecraft:zombie";
    /** 보고가 이 게임 틱 수보다 오래되면 후보로 보지 않는다(region 이 한두 틱 늦게 돌 수 있다). */
    private static final long REPORT_FRESH_TICKS = 2L;
    /** 포위 정하기를 맡긴 플레이어 region 이 이 틱 수 안에 답하지 않으면(접속 종료 등) 맡김을 버리고 다시 고른다. */
    private static final long SETUP_TIMEOUT_TICKS = 40L;

    /** 바닐라 State: SIEGE_CAN_ACTIVATE(자정 전까지 대기), SIEGE_TONIGHT(오늘 밤), SIEGE_DONE(없음/끝). */
    public enum State { CAN_ACTIVATE, TONIGHT, DONE }

    /**
     * 포위가 칸·플레이어에 닿는 길. 단독 레벨(시험)은 그 자리에서 하고(of), 서버는 칸을 가진 region 스레드로 넘긴다.
     * work 에는 그 레벨이, 닿을 수 없으면(주인 없음·다른 차원) null 이 들어간다 — 넘긴 일은 늦게 돌 수 있다.
     */
    public interface Host {
        boolean isDay();

        /** DimensionType.timeOfDay(dayTime). */
        float timeOfDay();

        long gameTime();

        /** 자정 굴림 난수(바닐라 level.random). */
        RandomSource random();

        /** tryToSetupSiege 가 고를 플레이어: 바닐라 level.players() 순서에서 비관전이고 마을 안(바이옴 제외)인 첫 사람. 없으면 null. */
        Player candidate(VillageSiege siege);

        /** 그 플레이어를 가진 레벨 스레드에서. */
        void onPlayer(Player p, Consumer<ChunkLevel> work);

        /** 그 칸 청크를 가진 레벨 스레드에서. */
        void atBlock(int x, int z, Consumer<ChunkLevel> work);

        /** 한 레벨·그 플레이어들로 그 자리에서 하는 길(바닐라와 같은 한 스레드). */
        static Host of(ChunkLevel level, List<Player> players) {
            return new Host() {
                @Override public boolean isDay() { return level.isDay(); }
                @Override public float timeOfDay() { return VillageSiege.timeOfDay(level.dayTime()); }
                @Override public long gameTime() { return level.gameTime(); }
                @Override public RandomSource random() { return level.random(); }
                @Override public Player candidate(VillageSiege siege) {
                    for (Player p : players) if (qualifies(level, p)) return p;
                    return null;
                }
                @Override public void onPlayer(Player p, Consumer<ChunkLevel> work) { work.accept(level); }
                @Override public void atBlock(int x, int z, Consumer<ChunkLevel> work) { work.accept(level); }
            };
        }
    }

    private boolean hasSetupSiege;
    private State siegeState = State.DONE;
    private int zombiesToSpawn;
    private int nextSpawnTime;
    private int spawnX, spawnY, spawnZ;
    /** 포위 정하기를 맡겨 두고 답을 기다리는 중(맡김 번호·맡긴 시각). */
    private boolean setupPending;
    private int setupToken;
    private long setupSince;
    /** region 이 알린 마을 안 플레이어 → 알린 게임 시각. */
    private final Map<Player, Long> reports = new ConcurrentHashMap<>();
    /** 포위를 정할 플레이어를 찾는 중인가(region 이 보고할지 가볍게 본다). */
    private volatile boolean wantsCandidates;

    public synchronized State state() { return siegeState; }
    public synchronized int zombiesToSpawn() { return zombiesToSpawn; }
    public synchronized boolean hasSetupSiege() { return hasSetupSiege; }
    /** 포위 중심점(마지막으로 정한 점). */
    public synchronized Pos spawnPos() { return new Pos(spawnX, spawnY, spawnZ); }

    /** 단독 레벨: 낸 틱이면 1(바닐라 반환값). spawnEnemies: 적대 몹 스폰 허용. players: 바닐라 level.players(). */
    public int tick(ChunkLevel level, List<Player> players, boolean spawnEnemies) {
        return tick(Host.of(level, players), spawnEnemies);
    }

    /** 바닐라 VillageSiege.tick(게임 틱마다 한 번). 낸 틱이면 1. */
    public synchronized int tick(Host host, boolean spawnEnemies) {
        try {
            if (!host.isDay() && spawnEnemies) {
                float time = host.timeOfDay();
                if ((double) time == 0.5) {
                    siegeState = host.random().nextInt(10) == 0 ? State.TONIGHT : State.DONE;
                }
                if (siegeState == State.DONE) return 0;
                if (!hasSetupSiege) {
                    if (setupPending) {
                        if (host.gameTime() - setupSince <= SETUP_TIMEOUT_TICKS) return 0;
                        setupPending = false;   // 맡은 region 이 답하지 않는다 — 버리고 다시 고른다
                        setupToken++;
                    }
                    Player p = host.candidate(this);
                    if (p == null) return 0;
                    setupPending = true;
                    setupSince = host.gameTime();
                    int token = ++setupToken;
                    host.onPlayer(p, level -> setup(level, p, token));   // 단독 레벨은 그 자리에서 — 바닐라처럼 같은 틱에 첫 좀비까지
                    if (!hasSetupSiege) return 0;
                }
                if (nextSpawnTime > 0) {
                    nextSpawnTime--;
                    return 0;
                }
                nextSpawnTime = 2;
                if (zombiesToSpawn > 0) {
                    int x = spawnX, y = spawnY, z = spawnZ;
                    host.atBlock(x, z, level -> { if (level != null) trySpawn(level, new Pos(x, y, z)); });
                    zombiesToSpawn--;
                } else {
                    siegeState = State.DONE;
                }
                return 1;
            }
            siegeState = State.DONE;
            if (hasSetupSiege || setupPending) setupToken++;   // 날이 밝았다 — 늦게 오는 정하기 답은 버린다
            hasSetupSiege = false;
            setupPending = false;
            return 0;
        } finally {
            boolean want = siegeState == State.TONIGHT && !hasSetupSiege;
            if (!want && !reports.isEmpty()) reports.clear();
            wantsCandidates = want;
        }
    }

    /** DimensionType.timeOfDay(dayTime) — 고정 시간 차원은 오버월드가 아니라 다루지 않는다. */
    public static float timeOfDay(long dayTime) {
        double x = (double) dayTime / 24000.0 - 0.25;
        long fl = (long) x;
        double d = x - (x < fl ? fl - 1 : fl);   // Mth.frac
        double e = 0.5 - Math.cos(d * Math.PI) / 2.0;
        return (float) (d * 2.0 + e) / 3.0F;
    }

    /** tryToSetupSiege 의 플레이어 조건: 비관전, 발 칸이 마을(POI 섹션 거리 1), 바이옴이 without_zombie_sieges 가 아님. */
    static boolean qualifies(ChunkLevel level, Player player) {
        if (player.gameMode() == GameMode.SPECTATOR) return false;
        Pos pos = player.blockPosition();
        return level.isCloseToVillage(pos, 1) && !level.biomeIs(pos, "minecraft:without_zombie_sieges");
    }

    // ------------------------------------------------------------------ region 보고(서버)

    /** 그 region 스레드에서: 포위를 정할 사람을 찾는 중이면 이 region 플레이어 중 조건을 채우는 사람을 알린다. */
    public void report(ChunkLevel level, List<Player> here) {
        if (!wantsCandidates) return;
        long now = level.gameTime();
        for (Player p : here) {
            if (qualifies(level, p)) reports.put(p, now);
            else reports.remove(p);
        }
    }

    /** 보고 중 바닐라 순서(players)에서 가장 앞의 플레이어(최근 보고만). 전역 스레드의 Host.candidate 가 쓴다. */
    public Player reportedCandidate(List<Player> players, long gameTime) {
        if (reports.isEmpty()) return null;
        for (Player p : players) {
            Long at = reports.get(p);
            if (at != null && gameTime - at <= REPORT_FRESH_TICKS) return p;
        }
        return null;
    }

    // ------------------------------------------------------------------ 정하기·스폰

    /** tryToSetupSiege 의 나머지(조건을 채운 플레이어에서): 32칸 원 위 점을 10번까지 잡는다. 플레이어 레벨 스레드에서. */
    private void setup(ChunkLevel level, Player player, int token) {
        synchronized (this) {
            if (token != setupToken || !setupPending) return;   // 늦은 답(날이 밝았거나 다시 맡겼다)
            setupPending = false;
            if (siegeState != State.TONIGHT || hasSetupSiege) return;
            if (level == null || !qualifies(level, player)) {   // 그 사이 마을을 떠났다 — 다음 틱에 다시 고른다
                reports.remove(player);
                return;
            }
            RandomSource random = level.random();
            Pos pos = player.blockPosition();
            for (int i = 0; i < 10; i++) {
                float f = random.nextFloat() * (float) (Math.PI * 2);
                spawnX = pos.x() + Mth.floor(Mth.cos(f) * 32.0F);
                spawnY = pos.y();
                spawnZ = pos.z() + Mth.floor(Mth.sin(f) * 32.0F);
                if (findRandomSpawnPos(level, new Pos(spawnX, spawnY, spawnZ)) != null) {
                    nextSpawnTime = 0;
                    zombiesToSpawn = 20;
                    break;
                }
            }
            hasSetupSiege = true;
            wantsCandidates = false;
            reports.clear();
        }
    }

    private static void trySpawn(ChunkLevel level, Pos around) {
        double[] v = findRandomSpawnPos(level, around);
        if (v == null) return;
        if (!(Entities.create(level, ZOMBIE) instanceof Zombie zombie)) return;
        zombie.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(zombie.blockPosition()));   // moveTo 전이라 원점 기준(바닐라 순서)
        zombie.moveTo(v[0], v[1], v[2], level.random().nextFloat() * 360.0F, 0.0F);
        level.addFreshEntityWithPassengers(zombie.spawnReason("VILLAGE_INVASION"));
    }

    /** 둘레 16x16 에서 10번: x 다음 z 를 뽑고, 마을 안이며 좀비 EVENT 스폰 규칙을 만족하는 첫 칸의 바닥 가운데. 난수는 칸마다 x, z(+ 규칙 안의 어둠 굴림). */
    static double[] findRandomSpawnPos(ChunkLevel level, Pos around) {
        RandomSource random = level.random();
        for (int i = 0; i < 10; i++) {
            int x = around.x() + random.nextInt(16) - 8;
            int z = around.z() + random.nextInt(16) - 8;
            int y = level.getWorldSurfaceHeight(x, z);
            Pos pos = new Pos(x, y, z);
            if (level.isCloseToVillage(pos, 1) && SpawnPlacements.checkSpawnRules(ZOMBIE, level, SpawnReason.EVENT, pos, random)) {
                return new double[] {x + 0.5, y, z + 0.5};
            }
        }
        return null;
    }
}
