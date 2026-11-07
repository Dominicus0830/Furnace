package org.bukkit.entity;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

/** 엔티티. 위치·상태는 이벤트가 만든 시점의 값일 수 있다(Furnace 엔티티 핸들 규칙). */
public interface Entity extends CommandSender {

    UUID getUniqueId();

    Location getLocation();

    World getWorld();

    boolean isValid();

    boolean isDead();

    String getCustomName();

    /** 마인크래프트 종류 키(예: "minecraft:zombie"). Bukkit 의 getType()(EntityType)은 H3. */
    String getTypeKey();

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
}
