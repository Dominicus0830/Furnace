package org.bukkit.craftbukkit.util;

import org.bukkit.Bukkit;

/** Bukkit 판 문자열(예: {@code 1.21.1-R0.1-SNAPSHOT}). 서버에 물어서 준다. */
public final class Versioning {
    public static String getBukkitVersion() {
        return Bukkit.getBukkitVersion();
    }
}
