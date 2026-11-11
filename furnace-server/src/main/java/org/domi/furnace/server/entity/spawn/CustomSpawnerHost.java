// 팬텀·순찰·고양이 스포너(바닐라 CustomSpawner)가 차원 쪽에서 보는 것: 레벨 난수·게임 규칙·시간·하늘과 차원 전체 플레이어 목록.
// 바닐라는 오버월드 ServerLevel 에 스포너가 하나씩이고 한 스레드에서 다 한다. Furnace 는 차원에 하나(DimensionSpawners)를 두고 전역 틱에서
// 타이머·플레이어 고르기까지 하고, 그 플레이어 둘레 칸을 읽고 몹을 세우는 일은 그 플레이어를 가진 region 스레드로 넘긴다(A10-05).
// of(level, players) 는 한 레벨에서 그 자리에서 다 하는 길(시험·바닐라와 같은 한 스레드 순서, 난수도 레벨 난수 하나).
package org.domi.furnace.server.entity.spawn;

import java.util.List;
import java.util.function.BiConsumer;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.Player;

public interface CustomSpawnerHost {

    /** 바닐라 level.random: 타이머·확률·플레이어 고르기. */
    RandomSource random();

    boolean gameRule(String name);

    long dayTime();

    /** Level.isDay. */
    boolean isDay();

    /** Level.skyDarken. */
    int skyDarken();

    boolean hasSkyLight();

    /** 바닐라 level.players(): 차원 전체, 그 순서. */
    List<Player> players();

    /**
     * 그 플레이어를 가진 레벨 스레드에서 work(level, random). level 은 그 플레이어의 레벨(이 차원을 떠났으면 null — 아무것도 하지 않는다).
     * random 은 그 자리에서 하면 레벨 난수 그대로, 다른 스레드로 넘기면 레벨 난수에서 갈라 낸 새 난수(난수 객체를 스레드끼리 나누지 않게).
     */
    void atPlayer(Player p, BiConsumer<ChunkLevel, RandomSource> work);

    /** 한 레벨·그 플레이어들로 그 자리에서 하는 길. */
    static CustomSpawnerHost of(ChunkLevel level, List<Player> players) {
        return new CustomSpawnerHost() {
            @Override public RandomSource random() { return level.random(); }
            @Override public boolean gameRule(String name) { return level.gameRule(name); }
            @Override public long dayTime() { return level.dayTime(); }
            @Override public boolean isDay() { return level.isDay(); }
            @Override public int skyDarken() { return level.skyDarken(); }
            @Override public boolean hasSkyLight() { return level.hasSkyLight(); }
            @Override public List<Player> players() { return players; }
            @Override public void atPlayer(Player p, BiConsumer<ChunkLevel, RandomSource> work) { work.accept(level, level.random()); }
        };
    }
}
