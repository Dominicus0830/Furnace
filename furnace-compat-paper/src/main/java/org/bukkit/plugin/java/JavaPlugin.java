package org.bukkit.plugin.java;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.domi.furnace.compat.paper.PluginInit;

/**
 * Bukkit 플러그인의 바탕 클래스. 인스턴스는 호환 모듈이 플러그인 jar 의 메인 클래스를 만들 때 환경을 걸어 두고 만든다 —
 * 직접 {@code new} 하면 IllegalStateException.
 */
public abstract class JavaPlugin implements Plugin {

    private final PluginInit init;
    private FileConfiguration newConfig;
    private File configFile;

    public JavaPlugin() {
        PluginInit i = PluginInit.take();
        if (i == null) throw new IllegalStateException("JavaPlugin requires to be created by Furnace's Bukkit compatibility loader");
        this.init = i;
        this.configFile = new File(i.dataFolder(), "config.yml");
    }

    @Override
    public final File getDataFolder() { return init.dataFolder(); }

    @Override
    public final PluginDescriptionFile getDescription() { return init.description(); }

    @Override
    public FileConfiguration getConfig() {
        if (newConfig == null) reloadConfig();
        return newConfig;
    }

    @Override
    public void reloadConfig() {
        newConfig = YamlConfiguration.loadConfiguration(configFile);
        InputStream def = getResource("config.yml");
        if (def == null) return;
        try (Reader r = new InputStreamReader(def, StandardCharsets.UTF_8)) {
            newConfig.setDefaults(YamlConfiguration.loadConfiguration(r));
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "기본 config.yml 을 읽지 못했다", e);
        }
    }

    @Override
    public void saveConfig() {
        try {
            getConfig().save(configFile);
        } catch (IOException ex) {
            getLogger().log(Level.SEVERE, "Could not save config to " + configFile, ex);
        }
    }

    @Override
    public void saveDefaultConfig() {
        if (!configFile.exists()) saveResource("config.yml", false);
    }

    @Override
    public void saveResource(String resourcePath, boolean replace) {
        if (resourcePath == null || resourcePath.isEmpty()) throw new IllegalArgumentException("ResourcePath cannot be null or empty");
        resourcePath = resourcePath.replace('\\', '/');
        InputStream in = getResource(resourcePath);
        if (in == null) throw new IllegalArgumentException("The embedded resource '" + resourcePath + "' cannot be found in " + init.jar());
        File outFile = new File(getDataFolder(), resourcePath);
        // 데이터 폴더 밖으로 나가는 경로(../)는 막는다
        try {
            if (!outFile.getCanonicalPath().startsWith(getDataFolder().getCanonicalPath() + File.separator)) {
                in.close();
                throw new IllegalArgumentException("resourcePath 가 데이터 폴더 밖이다: " + resourcePath);
            }
            if (outFile.exists() && !replace) {
                getLogger().log(Level.WARNING, "Could not save " + outFile.getName() + " to " + outFile + " because " + outFile.getName() + " already exists.");
                in.close();
                return;
            }
            File parent = outFile.getParentFile();
            if (parent != null) Files.createDirectories(parent.toPath());
            try (in) {
                Files.copy(in, outFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            getLogger().log(Level.SEVERE, "Could not save " + outFile.getName() + " to " + outFile, ex);
        }
    }

    @Override
    public InputStream getResource(String filename) {
        if (filename == null) throw new IllegalArgumentException("Filename cannot be null");
        return init.loader().getResourceAsStream(filename);
    }

    /** jar 안의 텍스트 파일(없으면 null). */
    public Reader getTextResource(String file) {
        InputStream in = getResource(file);
        return in == null ? null : new InputStreamReader(in, StandardCharsets.UTF_8);
    }

    protected final File getFile() { return init.jar(); }

    protected final ClassLoader getClassLoader() { return init.loader(); }

    @Override
    public final Server getServer() { return Bukkit.getServer(); }

    @Override
    public final boolean isEnabled() { return init.owner().isEnabled(); }

    @Override
    public void onLoad() {}

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() {}

    @Override
    public final Logger getLogger() { return init.logger(); }

    @Override
    public final String getName() { return init.description().getName(); }

    @Override
    public String toString() { return init.description().getFullName(); }
}
