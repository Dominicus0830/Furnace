package com.destroystokyo.paper.brigadier;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;

/** Paper API: Brigadier 명령 출처에서 Bukkit 객체를 얻는 인터페이스(paper-api 1.21.1 와 같은 멤버). */
public interface BukkitBrigadierCommandSource {

    Entity getBukkitEntity();

    World getBukkitWorld();

    Location getBukkitLocation();

    CommandSender getBukkitSender();
}
