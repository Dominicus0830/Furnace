package org.bukkit.entity;

import java.util.UUID;

/** Paper 1.21.1 AnimalTamer(길들인 주인 — 플레이어·오프라인 플레이어). */
public interface AnimalTamer {

    /** 주인 이름(없으면 null). */
    String getName();

    UUID getUniqueId();
}
