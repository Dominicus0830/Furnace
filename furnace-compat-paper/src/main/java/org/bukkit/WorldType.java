package org.bukkit;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Paper 1.21.1 WorldType(새 월드의 오버월드 생성기 — WorldCreator#type). 이름은 옛 level-type 문자열. */
public enum WorldType {
    NORMAL("DEFAULT"),
    FLAT("FLAT"),
    LARGE_BIOMES("LARGEBIOMES"),
    AMPLIFIED("AMPLIFIED");

    private static final Map<String, WorldType> BY_NAME = new HashMap<>();
    private final String name;

    WorldType(String name) { this.name = name; }

    public String getName() { return name; }

    /** 옛 이름(DEFAULT, FLAT, LARGEBIOMES, AMPLIFIED — 대소문자 무시)으로, 없으면 null. */
    public static WorldType getByName(String name) { return BY_NAME.get(name.toUpperCase(Locale.ROOT)); }

    static {
        for (WorldType type : values()) BY_NAME.put(type.name, type);
    }
}
