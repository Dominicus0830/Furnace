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

    long dayTime();

    long gameTime();

    String dimension();

    boolean ultraWarm();

    /** server.properties max-chained-neighbor-updates. */
    int maxChainedNeighborUpdates();

    /** 같은 차원의 접속자 전원에게. */
    void forEachPlayer(Consumer<Player> c);

    /** 기본값(서버 없이 쓰는 테스트·도구용). */
    LevelServices DEFAULT = new LevelServices() {
        public boolean gameRule(String name) { return true; }
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
        public long dayTime() { return d.dayTime(); }
        public long gameTime() { return d.gameTime(); }
        public String dimension() { return d.dimension(); }
        public boolean ultraWarm() { return d.ultraWarm(); }
        public int maxChainedNeighborUpdates() { return d.maxChainedNeighborUpdates(); }
        public void forEachPlayer(Consumer<Player> c) { d.forEachPlayer(c); }
    }
}
