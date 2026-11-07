package org.bukkit.plugin;

/** 같은 서비스를 여러 플러그인이 제공할 때의 우선순위(Bukkit ServicePriority). 높을수록 먼저 쓰인다. */
public enum ServicePriority {
    Lowest, Low, Normal, High, Highest
}
