package org.bukkit;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/** 월드(차원 하나). 1차 호환 범위의 조회 메서드만 있다 — 스폰·시간·블록 쓰기·엔티티 목록은 H3. */
public interface World extends Keyed, org.bukkit.metadata.Metadatable {

    String getName();

    UUID getUID();

    Environment getEnvironment();

    long getSeed();

    Block getBlockAt(int x, int y, int z);

    default Block getBlockAt(Location location) { return getBlockAt(location.getBlockX(), location.getBlockY(), location.getBlockZ()); }

    Chunk getChunkAt(int x, int z);

    default Chunk getChunkAt(Location location) { return getChunkAt(location.getBlockX() >> 4, location.getBlockZ() >> 4); }

    default Chunk getChunkAt(Block block) { return getChunkAt(block.getX() >> 4, block.getZ() >> 4); }

    /**
     * loc 에 엔티티를 만든다(바닐라 /summon 과 같은 경로). 소유 region 스레드에서만 된다(아니면 ThreadOwnershipException).
     * 청크가 로드돼 있지 않으면 IllegalStateException, 다른 플러그인이 스폰 이벤트를 취소하면 IllegalStateException.
     * Furnace API 가 소환할 수 없는 종류(플레이어·낚시찌, EntityType.UNKNOWN)는 IllegalArgumentException.
     */
    Entity spawnEntity(Location loc, EntityType type);

    /**
     * clazz 형의 엔티티를 만든다(spawnEntity 와 같은 경로·스레드 규칙). Furnace 가 그 형으로 감싸는 종류(ArmorStand·Pig)만 된다 —
     * Entity·LivingEntity 처럼 종류가 정해지지 않은 형은 IllegalArgumentException(Paper 와 같다), 그 밖의 Bukkit 형은 UnsupportedFeature.
     */
    <T extends Entity> T spawn(Location location, Class<T> clazz);

    /**
     * spawn 뒤 같은 스레드에서 곧바로 function 을 부른다 — 같은 틱 안이라 보는 플레이어는 고친 뒤 모습을 처음부터 본다.
     * Paper 는 월드에 넣기 전에 부르므로 스폰 이벤트 처리기가 고친 모습을 보지만 여기서는 고치기 전 모습을 본다.
     */
    <T extends Entity> T spawn(Location location, Class<T> clazz, java.util.function.Consumer<? super T> function);

    /** loc 둘레(x·y·z 반 길이 상자) 안의 엔티티(플레이어 포함). 위치 기준 판정과 소유 스레드 조건은 Entity#getNearbyEntities 와 같다. */
    Collection<Entity> getNearbyEntities(Location loc, double x, double y, double z);

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
