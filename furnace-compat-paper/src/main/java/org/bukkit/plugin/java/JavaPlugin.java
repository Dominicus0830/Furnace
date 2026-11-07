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
import java.util.List;
import java.util.Locale;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginBase;
import org.bukkit.plugin.PluginDescriptionFile;
import org.domi.furnace.compat.paper.BukkitPluginAdapter;
import org.domi.furnace.compat.paper.PluginInit;
import org.domi.furnace.compat.paper.PluginLoaders;

/**
 * Bukkit 플러그인의 바탕 클래스. 인스턴스는 호환 모듈이 플러그인 jar 의 메인 클래스를 만들 때 환경을 걸어 두고 만든다 —
 * 직접 {@code new} 하면 IllegalStateException.
 */
public abstract class JavaPlugin extends PluginBase {

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

    /**
     * plugin.yml 의 {@code commands:} 에 적은 명령(없으면 null). 켜지기 직전에 만들어지므로 onLoad 에서는 아직 null 이다.
     * 실행기를 정하지 않으면 이 플러그인({@link #onCommand})이 실행기다.
     */
    public PluginCommand getCommand(String name) {
        String alias = name.toLowerCase(Locale.ROOT);
        PluginCommand command = getServer().getPluginCommand(alias);
        if (command == null || command.getPlugin() != this) {
            command = getServer().getPluginCommand(init.description().getName().toLowerCase(Locale.ROOT) + ":" + alias);
        }
        if (command != null && command.getPlugin() == this) return command;
        return null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) { return false; }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) { return null; }

    /** 이 클래스를 만든 플러그인 인스턴스. 정적 초기화 중이라면 IllegalStateException. */
    public static <T extends JavaPlugin> T getPlugin(Class<T> clazz) {
        if (clazz == null) throw new IllegalArgumentException("Null class cannot have a plugin");
        if (!JavaPlugin.class.isAssignableFrom(clazz)) throw new IllegalArgumentException(clazz + " does not extend " + JavaPlugin.class);
        return clazz.cast(providing(clazz));
    }

    /** 이 클래스를 올린 플러그인. 플러그인 jar 의 클래스가 아니면 IllegalArgumentException. */
    public static JavaPlugin getProvidingPlugin(Class<?> clazz) {
        if (clazz == null) throw new IllegalArgumentException("Null class cannot have a plugin");
        return providing(clazz);
    }

    private static JavaPlugin providing(Class<?> clazz) {
        BukkitPluginAdapter adapter = PluginLoaders.of(clazz.getClassLoader());
        if (adapter == null) throw new IllegalArgumentException(clazz + " is not provided by a Bukkit plugin class loader");
        JavaPlugin plugin = adapter.javaPlugin();
        if (plugin == null) throw new IllegalStateException("Cannot get plugin for " + clazz + " from a static initializer");
        return plugin;
    }

    @Override
    public String toString() { return init.description().getFullName(); }
}
