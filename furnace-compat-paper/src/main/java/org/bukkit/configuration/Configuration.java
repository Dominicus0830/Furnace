package org.bukkit.configuration;

import java.util.Map;

/** 루트 설정 — 기본값 트리를 가진다. */
public interface Configuration extends ConfigurationSection {

    void addDefaults(Map<String, Object> defaults);

    void addDefaults(Configuration defaults);

    void setDefaults(Configuration defaults);

    Configuration getDefaults();

    ConfigurationOptions options();
}
