package org.bukkit.configuration.file;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Map;
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
        ConfigSection root = furnaceRoot();
        ConfigSection defaults = root.getDefaults();
        // Paper 는 각 단계 getValues(false) 로 쓴다: 기본값을 데이터로 옮기지 않고, 기본값에만 있는 키가 앞에 온다
        ConfigSection toWrite = options().copyDefaults() && defaults != null ? merged(root, defaults) : root;
        if (toWrite != root) {
            toWrite.setHeader(root.getHeader());
            toWrite.setFooter(root.getFooter());
        }
        String dump = ConfigFormat.YAML.write(toWrite);
        return BLANK_CONFIG.equals(dump) ? "" : dump;   // Paper 처럼 빈 설정은 빈 파일(A15-11)
    }

    /** 쓰기용 합친 보기(새 섹션): 기본값에만 있는 키 → 자기 키 순서, 둘 다 섹션이면 안쪽도 같게. 주석은 값이 온 쪽 것을 쓴다. */
    private static ConfigSection merged(ConfigSection own, ConfigSection defaults) {
        ConfigSection out = new ConfigSection();
        Map<String, Object> ownMap = own.toMap();
        Map<String, Object> defMap = defaults.toMap();
        for (var e : defMap.entrySet()) {
            if (!ownMap.containsKey(e.getKey())) put(out, defaults, e.getKey(), e.getValue());
        }
        for (var e : ownMap.entrySet()) {
            String k = e.getKey();
            if (k.indexOf('.') < 0 && own.get(k) instanceof ConfigSection s && defaults.get(k) instanceof ConfigSection ds) {
                out.setLiteral(k, merged(s, ds));
                copyComments(own, out, k);
            } else {
                put(out, own, k, e.getValue());
            }
        }
        return out;
    }

    /** from 의 키 k 를 out 에 옮긴다. 점 없는 키는 섹션째(안쪽 주석 유지), 점 있는 literal 키는 평범한 값으로. */
    private static void put(ConfigSection out, ConfigSection from, String k, Object plain) {
        if (k.indexOf('.') >= 0) {
            out.setLiteral(k, plain);
            return;
        }
        out.setLiteral(k, from.get(k));
        copyComments(from, out, k);
    }

    private static void copyComments(ConfigSection from, ConfigSection to, String k) {
        if (k.indexOf('.') >= 0) return;
        List<String> c = from.getComments(k);
        if (!c.isEmpty()) to.setComments(k, c);
        String ic = from.getInlineComment(k);
        if (ic != null) to.setInlineComment(k, ic);
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
