// 차원에 하나뿐인 특수 스포너(바닐라 ServerLevel.customSpawners 중 마을 포위·방랑 상인)를 전역 틱에서 게임 틱마다 한 번 돌린다.
// 바닐라는 차원당 스포너 하나라 상태(오늘 밤 포위, 상인 타이머)도 하나이고, 플레이어 목록은 차원 전체다. region 마다 따로 돌리면
// region 수만큼 굴림이 늘고(포위 확률), 상인 대상이 그 region 플레이어로 좁아진다 — 그래서 여기로 모았다.
// 칸을 읽고 몹을 세우는 일은 그 칸(플레이어)을 가진 region 스레드로 넘긴다(다음 region 틱 머리에 돈다 — 바닐라보다 한 틱 안쪽으로 늦다).
// 플레이어 순서(바닐라 level.players(), 레벨에 들어온 순): 엔티티 번호 순으로 대신한다(접속 순과 같다, 차원 이동 뒤 순서는 다를 수 있다 — 가정).
package org.domi.furnace.server.world;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.spawn.VillageSiege;
import org.domi.furnace.server.entity.spawn.WanderingTraderSpawner;

public final class DimensionSpawners {

    private final World world;
    private final VillageSiege siege = new VillageSiege();
    private final WanderingTraderSpawner trader = new WanderingTraderSpawner();
    private final RandomSource random = RandomSource.create();
    private final WanderingTraderState ownTraderState = new WanderingTraderState();

    DimensionSpawners(World world) { this.world = world; }

    public VillageSiege siege() { return siege; }

    public WanderingTraderSpawner trader() { return trader; }

    /**
     * 전역 틱(시간이 한 틱 간 뒤): 바닐라 ServerChunkCache.tickChunks 의 tickCustomSpawners 처럼 doMobSpawning 일 때만
     * (/tick freeze 중에는 전역 틱이 이것을 부르지 않는다). 포위 → 상인 순서(바닐라 목록 순서).
     */
    public void tick() {
        LevelServices s = world.levelServices();
        if (!s.gameRule("doMobSpawning")) return;
        boolean spawnMonsters = s.spawnMonsters() && s.difficulty() != 0;
        List<Player> players = players();
        Host host = new Host(s, players);
        siege.tick(host, spawnMonsters);
        WanderingTraderState st = world.wanderingTraderState();
        trader.attach(st != null ? st : ownTraderState);
        trader.tick(host);
    }

    /** region 틱(그 region 스레드): 포위를 정할 플레이어를 찾는 중이면 이 region 플레이어를 알린다. */
    void report(ChunkLevel level, List<Player> here) { siege.report(level, here); }

    /** 바닐라 level.players() 대신: 이 차원 접속자를 엔티티 번호 순으로. */
    private List<Player> players() {
        List<Player> list = world.playersHere();
        list.sort(Comparator.comparingInt(Player::entityId));
        return list;
    }

    /** 바닐라 Level.isDay: 고정 시간이 아니고 하늘 어둠(updateSkyBrightness) < 4. */
    static boolean isDay(LevelServices s) {
        if (!s.hasSkyLight()) return false;
        return org.domi.furnace.server.block.BlockLevel.skyDarken(s.rainLevel(), s.thunderLevel(), VillageSiege.timeOfDay(s.dayTime())) < 4;
    }

    private final class Host implements VillageSiege.Host, WanderingTraderSpawner.Host {
        private final LevelServices s;
        private final List<Player> players;

        Host(LevelServices s, List<Player> players) {
            this.s = s;
            this.players = players;
        }

        @Override public boolean isDay() { return DimensionSpawners.isDay(s); }
        @Override public float timeOfDay() { return VillageSiege.timeOfDay(s.dayTime()); }
        @Override public long gameTime() { return s.gameTime(); }
        @Override public RandomSource random() { return random; }
        @Override public RandomSource levelRandom() { return random; }
        @Override public boolean gameRule(String name) { return s.gameRule(name); }
        @Override public List<Player> players() { return players; }
        @Override public Player candidate(VillageSiege v) { return v.reportedCandidate(players, s.gameTime()); }
        @Override public RandomSource workRandom(RandomSource spawner) { return RandomSource.create(spawner.nextLong()); }

        @Override
        public void onPlayer(Player p, Consumer<ChunkLevel> work) {
            String dim = world.dimension();
            p.runOnOwner(() -> {
                ChunkLevel lv = p.currentLevel;
                work.accept(lv != null && dim.equals(lv.dimension()) ? lv : null);
            });
        }

        @Override
        public void atBlock(int x, int z, Consumer<ChunkLevel> work) {
            var f = world.submitAtOwnedChunk(new ChunkPos(x >> 4, z >> 4), r -> {
                work.accept(r.level());
                return null;
            });
            if (f == null) work.accept(null);   // 그 칸을 가진 region 이 없다(로드 안 됨)
        }
    }
}
