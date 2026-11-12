package org.bukkit;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.bukkit.block.Biome;
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

    // ---- 소리(H4 묶음 C). 분류를 적지 않으면 MASTER, 씨앗을 적지 않으면 서버 난수(Paper 와 같다).

    default void playSound(Location location, Sound sound, float volume, float pitch) { playSound(location, sound, SoundCategory.MASTER, volume, pitch); }

    default void playSound(Location location, String sound, float volume, float pitch) { playSound(location, sound, SoundCategory.MASTER, volume, pitch); }

    default void playSound(Location location, Sound sound, SoundCategory category, float volume, float pitch) {
        playSound(location, sound, category, volume, pitch, java.util.concurrent.ThreadLocalRandom.current().nextLong());
    }

    default void playSound(Location location, String sound, SoundCategory category, float volume, float pitch) {
        playSound(location, sound, category, volume, pitch, java.util.concurrent.ThreadLocalRandom.current().nextLong());
    }

    /** 그 지점에서 소리를 낸다: 이 월드 접속자 중 크기에 따른 거리(16칸, 크기가 1 을 넘으면 16×크기) 안의 사람에게. 인자 중 null 이 있으면 아무 일도 없다. */
    void playSound(Location location, Sound sound, SoundCategory category, float volume, float pitch, long seed);

    /** 이름(리소스 id, 리소스 팩 소리도 된다)으로. 이름이 리소스 id 가 아니면 IllegalArgumentException. */
    void playSound(Location location, String sound, SoundCategory category, float volume, float pitch, long seed);

    default void playSound(Entity entity, Sound sound, float volume, float pitch) { playSound(entity, sound, SoundCategory.MASTER, volume, pitch); }

    default void playSound(Entity entity, String sound, float volume, float pitch) { playSound(entity, sound, SoundCategory.MASTER, volume, pitch); }

    default void playSound(Entity entity, Sound sound, SoundCategory category, float volume, float pitch) {
        playSound(entity, sound, category, volume, pitch, java.util.concurrent.ThreadLocalRandom.current().nextLong());
    }

    default void playSound(Entity entity, String sound, SoundCategory category, float volume, float pitch) {
        playSound(entity, sound, category, volume, pitch, java.util.concurrent.ThreadLocalRandom.current().nextLong());
    }

    /** 엔티티에 붙어 따라가는 소리: 그 엔티티를 보는 사람과(플레이어면) 그 자신에게. 다른 월드의 엔티티거나 인자가 null 이면 아무 일도 없다. */
    void playSound(Entity entity, Sound sound, SoundCategory category, float volume, float pitch, long seed);

    void playSound(Entity entity, String sound, SoundCategory category, float volume, float pitch, long seed);

    // ---- 입자(H4 묶음 C). 퍼짐을 적지 않으면 0, extra(보통 속도)를 적지 않으면 1. force 를 적지 않으면 Paper 처럼 true(512칸, 클라이언트 입자 설정 무시).

    default void spawnParticle(Particle particle, Location location, int count) { spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count); }

    default void spawnParticle(Particle particle, double x, double y, double z, int count) { spawnParticle(particle, x, y, z, count, null); }

    default <T> void spawnParticle(Particle particle, Location location, int count, T data) { spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, data); }

    default <T> void spawnParticle(Particle particle, double x, double y, double z, int count, T data) { spawnParticle(particle, x, y, z, count, 0, 0, 0, data); }

    default void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ) {
        spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, offsetX, offsetY, offsetZ);
    }

    default void spawnParticle(Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ) {
        spawnParticle(particle, x, y, z, count, offsetX, offsetY, offsetZ, null);
    }

    default <T> void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, T data) {
        spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, offsetX, offsetY, offsetZ, data);
    }

    default <T> void spawnParticle(Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, T data) {
        spawnParticle(particle, x, y, z, count, offsetX, offsetY, offsetZ, 1, data);
    }

    default void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra) {
        spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, offsetX, offsetY, offsetZ, extra);
    }

    default void spawnParticle(Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double extra) {
        spawnParticle(particle, x, y, z, count, offsetX, offsetY, offsetZ, extra, null);
    }

    default <T> void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra, T data) {
        spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, offsetX, offsetY, offsetZ, extra, data);
    }

    /** Paper: force 를 적지 않으면 true 다(Bukkit 원래는 false). */
    default <T> void spawnParticle(Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double extra, T data) {
        spawnParticle(particle, null, null, x, y, z, count, offsetX, offsetY, offsetZ, extra, data, true);
    }

    default <T> void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra, T data, boolean force) {
        spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, offsetX, offsetY, offsetZ, extra, data, force);
    }

    default <T> void spawnParticle(Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double extra, T data, boolean force) {
        spawnParticle(particle, null, null, x, y, z, count, offsetX, offsetY, offsetZ, extra, data, force);
    }

    /** Paper: 받는 사람을 고른다(null 이면 이 월드 모두). force 는 true. */
    default <T> void spawnParticle(Particle particle, List<Player> receivers, Player source, double x, double y, double z, int count,
                                   double offsetX, double offsetY, double offsetZ, double extra, T data) {
        spawnParticle(particle, receivers, source, x, y, z, count, offsetX, offsetY, offsetZ, extra, data, true);
    }

    /**
     * Paper: receivers(null 이면 이 월드 접속자 모두) 중 칸 가운데가 32칸(force 면 512칸) 미만인 사람에게 보낸다. data 형이 {@link Particle#getDataType()}
     * 과 맞지 않거나 필요한데 null 이면 IllegalArgumentException. source(숨김 판정용)는 아직 쓰지 않는다 — Furnace 에 플레이어 숨기기가 없다.
     */
    <T> void spawnParticle(Particle particle, List<Player> receivers, Player source, double x, double y, double z, int count,
                           double offsetX, double offsetY, double offsetZ, double extra, T data, boolean force);

    // ---- 바이옴(H4 묶음 C). 그 칸을 가진 region 스레드에서만(아니면 ThreadOwnershipException). 플러그인 바이옴은 Biome.CUSTOM.

    /** 그 칸의 바이옴. 높이 밖이면 가까운 끝 칸(바닐라와 같다), 청크가 로드돼 있지 않으면 IllegalStateException. */
    Biome getBiome(int x, int y, int z);

    default Biome getBiome(Location location) { return getBiome(location.getBlockX(), location.getBlockY(), location.getBlockZ()); }

    /** @deprecated 높이 0 의 바이옴. */
    @Deprecated
    default Biome getBiome(int x, int z) { return getBiome(x, 0, z); }

    /** 그 칸이 든 4×4×4 바이옴 칸을 바꾸고 보는 플레이어에게 다시 보낸다. CUSTOM 이면 IllegalArgumentException. */
    void setBiome(int x, int y, int z, Biome biome);

    default void setBiome(Location location, Biome biome) { setBiome(location.getBlockX(), location.getBlockY(), location.getBlockZ(), biome); }

    /** @deprecated 그 열의 모든 높이. */
    @Deprecated
    default void setBiome(int x, int z, Biome biome) {
        for (int y = getMinHeight(); y < getMaxHeight(); y += 4) setBiome(x, y, z, biome);
    }

    enum Environment {
        NORMAL(0), NETHER(-1), THE_END(1), CUSTOM(0);

        private final int id;

        Environment(int id) { this.id = id; }

        public int getId() { return id; }
    }
}
