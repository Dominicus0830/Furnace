package org.bukkit.entity;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;
import org.bukkit.World;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.persistence.PersistentDataHolder;

/** 엔티티. 위치·상태는 이벤트가 만든 시점의 값일 수 있다(Furnace 엔티티 핸들 규칙). */
public interface Entity extends CommandSender, PersistentDataHolder {

    UUID getUniqueId();

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
     * Bukkit 의 teleport — Furnace 에는 "메인 스레드"가 없어 옮기기를 예약만 하고 true 를 돌려준다(결과는 알 수 없다).
     * 결과가 필요하면 teleportAsync.
     */
    boolean teleport(Location location);

    void remove();

    /** 이름 바꾸기는 API 가 아직 없다 — UnsupportedFeature. */
    void setCustomName(String name);
}
