package org.bukkit.plugin;

import java.io.File;
import java.io.InputStream;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;

/** Bukkit 플러그인. 명령 실행기(getCommand/onCommand)는 H2 에서 더한다. */
public interface Plugin {

    File getDataFolder();

    PluginDescriptionFile getDescription();

    FileConfiguration getConfig();

    /** jar 안의 파일(없으면 null). */
    InputStream getResource(String filename);

    void saveConfig();

    void saveDefaultConfig();

    void saveResource(String resourcePath, boolean replace);

    void reloadConfig();

    Server getServer();

    boolean isEnabled();

    void onDisable();

    void onLoad();

    void onEnable();

    Logger getLogger();

    String getName();
}
