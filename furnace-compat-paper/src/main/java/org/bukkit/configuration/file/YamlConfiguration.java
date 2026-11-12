package org.bukkit.configuration.file;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.InvalidConfigurationException;
import org.domi.furnace.api.config.ConfigFormat;
import org.domi.furnace.api.config.ConfigParseException;
import org.domi.furnace.api.config.ConfigSection;

/** YAML 설정(Furnace 의 YAML 파서·작성기 — 주석을 보존한다). */
public class YamlConfiguration extends FileConfiguration {

    /** 빈 설정을 쓰면 나오는 글(Paper YamlConfiguration.BLANK_CONFIG). */
    protected static final String BLANK_CONFIG = "{}\n";

    private YamlConfigurationOptions options;

    public YamlConfiguration() {}

    @Override
    public String saveToString() {
        if (options().copyDefaults()) furnaceRoot().copyDefaults();
        String dump = ConfigFormat.YAML.write(furnaceRoot());
        return BLANK_CONFIG.equals(dump) ? "" : dump;   // Paper 처럼 빈 설정은 빈 파일(A15-11)
    }

    @Override
    public void loadFromString(String contents) throws InvalidConfigurationException {
        if (contents == null) throw new IllegalArgumentException("contents 가 null");
        ConfigSection fresh;
        try {
            fresh = contents.isBlank() ? new ConfigSection() : ConfigFormat.YAML.parse(contents);
        } catch (ConfigParseException e) {
            throw new InvalidConfigurationException(e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new InvalidConfigurationException(e);
        }
        fresh.setDefaults(furnaceRoot().getDefaults());
        furnaceRoot(fresh);
    }

    @Override
    public YamlConfigurationOptions options() {
        if (options == null) options = new YamlConfigurationOptions(this);
        return options;
    }

    @Override
    List<String> getHeaderLines() { return furnaceRoot().getHeader(); }

    @Override
    void setHeaderLines(List<String> lines) { furnaceRoot().setHeader(lines); }

    @Override
    List<String> getFooterLines() { return furnaceRoot().getFooter(); }

    @Override
    void setFooterLines(List<String> lines) { furnaceRoot().setFooter(lines); }

    public static YamlConfiguration loadConfiguration(File file) {
        if (file == null) throw new IllegalArgumentException("File 이 null");
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (FileNotFoundException ex) {
            // 파일이 없으면 빈 설정(Bukkit 도 같다)
        } catch (IOException | InvalidConfigurationException ex) {
            log("Cannot load " + file, ex);
        }
        return config;
    }

    public static YamlConfiguration loadConfiguration(Reader reader) {
        if (reader == null) throw new IllegalArgumentException("Reader 가 null");
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(reader);
        } catch (IOException | InvalidConfigurationException ex) {
            log("Cannot load configuration from stream", ex);
        }
        return config;
    }

    private static void log(String msg, Throwable t) {
        if (Bukkit.getServer() != null) Bukkit.getLogger().log(Level.SEVERE, msg, t);
        else java.util.logging.Logger.getLogger("Bukkit").log(Level.SEVERE, msg, t);
    }
}
