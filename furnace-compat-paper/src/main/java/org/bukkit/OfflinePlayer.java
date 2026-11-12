package org.bukkit;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.permissions.ServerOperator;

/**
 * 접속 중이 아닐 수도 있는 플레이어. ConfigurationSerializable 이라 설정에 넣을 수 있다 — 별칭 {@code Player}, 키 {@code UUID}
 * (접속 중인 {@link Player} 는 {@code name} 키로 저장되고 같은 별칭으로 되읽힌다. Paper 와 같다, A15-01).
 */
public interface OfflinePlayer extends ServerOperator, org.bukkit.configuration.serialization.ConfigurationSerializable {

    boolean isOnline();

    String getName();

    UUID getUniqueId();

    /** 접속 중이면 Player, 아니면 null. */
    Player getPlayer();
}
