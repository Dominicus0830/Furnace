package org.bukkit.entity;

import org.bukkit.GameMode;

public interface HumanEntity extends LivingEntity {

    GameMode getGameMode();

    void setGameMode(GameMode mode);
}
