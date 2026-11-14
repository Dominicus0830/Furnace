package org.bukkit.entity;

import java.net.InetSocketAddress;
import org.bukkit.OfflinePlayer;

/** 접속 중인 플레이어. 소리·입자는 H4 묶음 C. */
public interface Player extends HumanEntity, org.bukkit.conversations.Conversable, OfflinePlayer, org.bukkit.plugin.messaging.PluginMessageRecipient {

    /** 채팅 서식 %1$s 에 쓰는 이름 — 정하지 않았으면 플레이어 이름 그대로(Paper·Spigot 과 같다, 바닐라 팀 서식은 붙지 않는다). */
    String getDisplayName();

    /** 표시 이름을 정한다(§ 서식 가능). null 이면 이름 그대로로 되돌린다. 접속을 끊으면 사라진다. */
    void setDisplayName(String name);

    /** 접속 상태 안에서만 쓸 수 있다. */
    InetSocketAddress getAddress();

    /** 이유와 함께 내보낸다(Bukkit 의 kickPlayer). */
    void kickPlayer(String message);

    int getLevel();

    void setLevel(int level);

    void giveExp(int amount);

    int getFoodLevel();

    void setFoodLevel(int value);

    /** 이 플레이어가 명령을 친 것처럼 실행한다. Furnace 는 결과를 알려 주지 않아 실행을 맡기면 true. */
    boolean performCommand(String command);

    int getPing();

    String getLocale();

    /** 클라이언트 언어(Paper). 클라이언트가 아직 알리지 않았으면 en_US. */
    java.util.Locale locale();

    /** 이 플레이어의 명령 트리를 다시 보낸다(권한이 바뀐 뒤). */
    void updateCommands();

    /**
     * 클라이언트가 인벤토리를 서버 값에 맞추게 한다. Furnace 는 서버 쪽 변경을 다음 틱 창 갱신에서 자동으로 보내므로 따로 할 일이 없다
     * (그래서 아무 일도 하지 않는다).
     */
    void updateInventory();

    // ---- 소리(H4 묶음 C) — 이 플레이어에게만. 분류를 적지 않으면 MASTER, 씨앗을 적지 않으면 서버 난수.

    default void playSound(org.bukkit.Location location, org.bukkit.Sound sound, float volume, float pitch) { playSound(location, sound, org.bukkit.SoundCategory.MASTER, volume, pitch); }

    default void playSound(org.bukkit.Location location, String sound, float volume, float pitch) { playSound(location, sound, org.bukkit.SoundCategory.MASTER, volume, pitch); }

    default void playSound(org.bukkit.Location location, org.bukkit.Sound sound, org.bukkit.SoundCategory category, float volume, float pitch) {
        playSound(location, sound, category, volume, pitch, java.util.concurrent.ThreadLocalRandom.current().nextLong());
    }

    default void playSound(org.bukkit.Location location, String sound, org.bukkit.SoundCategory category, float volume, float pitch) {
        playSound(location, sound, category, volume, pitch, java.util.concurrent.ThreadLocalRandom.current().nextLong());
    }

    /** 그 지점에서 나는 소리(거리는 클라이언트가 크기로 줄인다). 인자 중 null 이 있으면 아무 일도 없다. */
    void playSound(org.bukkit.Location location, org.bukkit.Sound sound, org.bukkit.SoundCategory category, float volume, float pitch, long seed);

    /** 이름(리소스 id)으로. 리소스 id 가 아니면 IllegalArgumentException. */
    void playSound(org.bukkit.Location location, String sound, org.bukkit.SoundCategory category, float volume, float pitch, long seed);

    default void playSound(Entity entity, org.bukkit.Sound sound, float volume, float pitch) { playSound(entity, sound, org.bukkit.SoundCategory.MASTER, volume, pitch); }

    default void playSound(Entity entity, String sound, float volume, float pitch) { playSound(entity, sound, org.bukkit.SoundCategory.MASTER, volume, pitch); }

    default void playSound(Entity entity, org.bukkit.Sound sound, org.bukkit.SoundCategory category, float volume, float pitch) {
        playSound(entity, sound, category, volume, pitch, java.util.concurrent.ThreadLocalRandom.current().nextLong());
    }

    default void playSound(Entity entity, String sound, org.bukkit.SoundCategory category, float volume, float pitch) {
        playSound(entity, sound, category, volume, pitch, java.util.concurrent.ThreadLocalRandom.current().nextLong());
    }

    /** 그 엔티티에 붙어 따라가는 소리(이 플레이어에게만). */
    void playSound(Entity entity, org.bukkit.Sound sound, org.bukkit.SoundCategory category, float volume, float pitch, long seed);

    void playSound(Entity entity, String sound, org.bukkit.SoundCategory category, float volume, float pitch, long seed);

    default void stopSound(org.bukkit.Sound sound) { stopSound(sound, null); }

    default void stopSound(String sound) { stopSound(sound, null); }

    default void stopSound(org.bukkit.Sound sound, org.bukkit.SoundCategory category) { stopSound(sound.getKey().getKey(), category); }

    /** 그 소리를 멈춘다. 분류가 null 이면 MASTER 분류만(CraftBukkit 과 같다). 리소스 id 가 아니면 IllegalArgumentException. */
    void stopSound(String sound, org.bukkit.SoundCategory category);

    /** 그 분류의 소리를 모두 멈춘다. */
    void stopSound(org.bukkit.SoundCategory category);

    void stopAllSounds();

    // ---- 입자(H4 묶음 C) — 이 플레이어에게만, 거리와 관계없이. 퍼짐 0·extra 1·force false 가 기본(Bukkit Player 와 같다).

    default void spawnParticle(org.bukkit.Particle particle, org.bukkit.Location location, int count) { spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count); }

    default void spawnParticle(org.bukkit.Particle particle, double x, double y, double z, int count) { spawnParticle(particle, x, y, z, count, null); }

    default <T> void spawnParticle(org.bukkit.Particle particle, org.bukkit.Location location, int count, T data) { spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, data); }

    default <T> void spawnParticle(org.bukkit.Particle particle, double x, double y, double z, int count, T data) { spawnParticle(particle, x, y, z, count, 0, 0, 0, data); }

    default void spawnParticle(org.bukkit.Particle particle, org.bukkit.Location location, int count, double offsetX, double offsetY, double offsetZ) {
        spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, offsetX, offsetY, offsetZ);
    }

    default void spawnParticle(org.bukkit.Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ) {
        spawnParticle(particle, x, y, z, count, offsetX, offsetY, offsetZ, null);
    }

    default <T> void spawnParticle(org.bukkit.Particle particle, org.bukkit.Location location, int count, double offsetX, double offsetY, double offsetZ, T data) {
        spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, offsetX, offsetY, offsetZ, data);
    }

    default <T> void spawnParticle(org.bukkit.Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, T data) {
        spawnParticle(particle, x, y, z, count, offsetX, offsetY, offsetZ, 1, data);
    }

    default void spawnParticle(org.bukkit.Particle particle, org.bukkit.Location location, int count, double offsetX, double offsetY, double offsetZ, double extra) {
        spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, offsetX, offsetY, offsetZ, extra);
    }

    default void spawnParticle(org.bukkit.Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double extra) {
        spawnParticle(particle, x, y, z, count, offsetX, offsetY, offsetZ, extra, null);
    }

    default <T> void spawnParticle(org.bukkit.Particle particle, org.bukkit.Location location, int count, double offsetX, double offsetY, double offsetZ, double extra, T data) {
        spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, offsetX, offsetY, offsetZ, extra, data);
    }

    default <T> void spawnParticle(org.bukkit.Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double extra, T data) {
        spawnParticle(particle, x, y, z, count, offsetX, offsetY, offsetZ, extra, data, false);
    }

    default <T> void spawnParticle(org.bukkit.Particle particle, org.bukkit.Location location, int count, double offsetX, double offsetY, double offsetZ, double extra, T data, boolean force) {
        spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, offsetX, offsetY, offsetZ, extra, data, force);
    }

    /** data 형이 {@link org.bukkit.Particle#getDataType()} 과 맞지 않거나 필요한데 null 이면 IllegalArgumentException. */
    <T> void spawnParticle(org.bukkit.Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double extra, T data, boolean force);
}
