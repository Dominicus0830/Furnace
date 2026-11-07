package org.bukkit.plugin;

import java.io.File;
import java.io.InputStream;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;

/** Bukkit 플러그인. 본체는 명령의 기본 실행기·완성기이기도 하다({@link TabExecutor}) — {@code JavaPlugin#getCommand} 의 기본 실행기. */
public interface Plugin extends TabExecutor {

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
