package org.bukkit.entity;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;
import org.bukkit.World;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.metadata.Metadatable;
import org.bukkit.persistence.PersistentDataHolder;

/**
 * 엔티티. 위치는 이벤트가 만든 시점의 값일 수 있다(Furnace 엔티티 핸들 규칙). 이름·플래그·탑승은 엔티티를 가진 region 스레드에서
 * 지금 값을 읽고 쓴다 — 다른 스레드에서는 읽기가 ThreadOwnershipException, 쓰기는 소유 스레드로 예약된다.
 */
public interface Entity extends Metadatable, CommandSender, PersistentDataHolder {

    UUID getUniqueId();

    /** Paper(Folia) 엔티티 스케줄러: 엔티티를 따라다니는 region 스레드에서 돈다. */
    io.papermc.paper.threadedregions.scheduler.EntityScheduler getScheduler();

    Location getLocation();

    World getWorld();

    boolean isValid();

    boolean isDead();

    String getCustomName();

    /** 마인크래프트 종류 키(예: "minecraft:zombie"). */
    String getTypeKey();

    /** 종류. 서버가 아는 종류가 EntityType 에 없으면(플러그인이 더한 종류) UnsupportedFeature. */
    EntityType getType();

    /**
     * 이 엔티티 둘레(x·y·z 반 길이 상자) 안의 다른 엔티티. 소유 region 스레드에서만 된다(아니면 ThreadOwnershipException).
     * Bukkit 은 엔티티의 충돌 상자가 겹치는지 보지만 Furnace API 는 엔티티 위치만 알려 주어 **위치**가 상자 안인지로 판단한다
     * (가장자리에서 Bukkit 보다 적게 잡힐 수 있다).
     */
    List<Entity> getNearbyEntities(double x, double y, double z);

    /**
     * 비동기로 옮긴다(Folia·Paper 의 teleportAsync). 대상 월드·region 이 다르면 소유 스레드로 넘어가 실행된다.
     */
    CompletableFuture<Boolean> teleportAsync(Location location);

    /**
     * Bukkit 의 teleport(Paper 와 같은 결과 규칙). 엔티티를 가진 region 스레드(플레이어는 어느 스레드나)에서는 곧바로 옮기고 진짜 결과를
     * 돌려준다 — 사라졌거나 승객이 타고 있거나 PlayerTeleportEvent 가 취소됐거나 다른 차원·region 이면 false, 탈것은 먼저 내린다.
     * 소유 스레드가 아니면 옮기기를 맡기고 핸들이 살아 있으면 true(결과는 알 수 없다). 결과가 필요하면 teleportAsync.
     */
    boolean teleport(Location location);

    /**
     * Paper 의 원인 인자 텔레포트. Furnace 텔레포트 이벤트는 원인을 실어 나르지 않아 원인은 버려지고
     * PlayerTeleportEvent#getCause 는 UNKNOWN 이다(PaperLib teleportAsync 가 Paper 경로에서 부르는 꼴).
     */
    default CompletableFuture<Boolean> teleportAsync(Location location, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause) {
        return teleportAsync(location);
    }

    /** {@link #teleport(Location)} 와 같다. 원인은 위와 같이 버려진다. */
    default boolean teleport(Location location, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause) {
        return teleport(location);
    }

    void remove();

    /** 이름을 바꾼다(§ 서식, null 이면 지운다, 256자에서 자른다 — Paper 와 같다). */
    void setCustomName(String name);

    boolean isCustomNameVisible();

    void setCustomNameVisible(boolean flag);

    boolean hasGravity();

    void setGravity(boolean gravity);

    boolean isInvulnerable();

    void setInvulnerable(boolean flag);

    boolean isSilent();

    void setSilent(boolean flag);

    /** 발광(NBT Glowing 태그 또는 발광 효과). */
    boolean isGlowing();

    /** 발광 태그를 바꾼다(발광 효과는 그대로). */
    void setGlowing(boolean flag);

    // ---- 탑승

    /** 승객(타는 순서). 소유 스레드에서만. */
    List<Entity> getPassengers();

    /**
     * passenger 를 태운다(Paper: startRiding(force=true) — 승객 수 제한을 보지 않는다). 탔으면 true. 승객은 같은 region 에 있어야 한다.
     * 소유 스레드에서만(결과가 필요하다).
     */
    boolean addPassenger(Entity passenger);

    /** passenger 를 내린다. Paper 처럼 늘 true. */
    boolean removePassenger(Entity passenger);

    /** 승객이 없는가. */
    boolean isEmpty();

    /** 승객을 모두 내린다. 승객이 있었으면 true. */
    boolean eject();

    /** 탄 탈것(없으면 null). */
    Entity getVehicle();

    boolean isInsideVehicle();

    /** 탈것에서 내린다. 타고 있었으면 true. */
    boolean leaveVehicle();
}
