package org.bukkit;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.permissions.ServerOperator;

public interface OfflinePlayer extends ServerOperator {

    boolean isOnline();

    String getName();

    UUID getUniqueId();

    /** 접속 중이면 Player, 아니면 null. */
    Player getPlayer();
}
