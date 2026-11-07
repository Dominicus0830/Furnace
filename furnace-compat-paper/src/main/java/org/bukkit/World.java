package org.bukkit;

import java.util.List;
import java.util.UUID;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/** 월드(차원 하나). 1차 호환 범위의 조회 메서드만 있다 — 스폰·시간·블록 쓰기·엔티티 목록은 H3. */
public interface World extends Keyed {

    String getName();

    UUID getUID();

    Environment getEnvironment();

    long getSeed();

    Block getBlockAt(int x, int y, int z);

    default Block getBlockAt(Location location) { return getBlockAt(location.getBlockX(), location.getBlockY(), location.getBlockZ()); }

    Chunk getChunkAt(int x, int z);

    boolean isChunkLoaded(int x, int z);

    List<Player> getPlayers();

    int getMinHeight();

    int getMaxHeight();

    /** 게임 규칙 값(문자열), 모르는 이름이면 null. */
    String getGameRuleValue(String rule);

    enum Environment {
        NORMAL(0), NETHER(-1), THE_END(1), CUSTOM(0);

        private final int id;

        Environment(int id) { this.id = id; }

        public int getId() { return id; }
    }
}
