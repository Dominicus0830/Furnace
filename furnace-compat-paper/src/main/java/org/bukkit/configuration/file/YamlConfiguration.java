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
import org.domi.furnace.compat.paper.ConfigPaths;

/** YAML 설정(Furnace 의 YAML 파서·작성기 — 주석을 보존한다). */
public class YamlConfiguration extends FileConfiguration {

    /** 빈 설정을 쓰면 나오는 글(Paper YamlConfiguration.BLANK_CONFIG). */
    protected static final String BLANK_CONFIG = "{}\n";

    private YamlConfigurationOptions options;

    public YamlConfiguration() {}

    @Override
    public String saveToString() {
        ConfigSection root = furnaceRoot();
        // 보관한 직렬화 객체는 set 한 뒤 바뀌었을 수 있다 — Paper 처럼 쓰는 순간의 serialize() 를 쓴다
        root.forEachAttached(s -> s.replaceChildren(org.domi.furnace.compat.paper.ConfigValues.lowerSerializable(
                (org.bukkit.configuration.serialization.ConfigurationSerializable) s.attachment())));
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

    /**
     * 쓰기용 합친 보기(새 섹션): 기본값에만 있는 키 → 자기 키 순서, 둘 다 섹션이면 안쪽도 같게. 주석은 값이 온 쪽 것을 쓴다.
     * 키는 점이 들어 있어도 그대로 한 키로 다룬다(literal 키 단계 접근 — PR #254 검토 R2).
     */
    private static ConfigSection merged(ConfigSection own, ConfigSection defaults) {
        ConfigSection out = new ConfigSection();
        for (String k : defaults.keys(false)) {
            if (!own.containsAt(List.of(k), true)) put(out, defaults, k);
        }
        for (String k : own.keys(false)) {
            List<String> step = List.of(k);
            if (own.getAt(step) instanceof ConfigSection s && defaults.containsAt(step, true) && defaults.getAt(step) instanceof ConfigSection ds) {
                out.setLiteral(k, merged(s, ds));
                copyComments(own, out, k);
            } else {
                put(out, own, k);
            }
        }
        return out;
    }

    /** from 의 키 k(점이 든 literal 키도 한 키)를 값·주석째 out 에 옮긴다. */
    private static void put(ConfigSection out, ConfigSection from, String k) {
        out.setLiteral(k, from.getAt(List.of(k)));
        copyComments(from, out, k);
    }

    private static void copyComments(ConfigSection from, ConfigSection to, String k) {
        List<String> step = List.of(k);
        List<String> c = from.getCommentsAt(step);
        if (!c.isEmpty()) to.setCommentsAt(step, c);
        String ic = from.getInlineCommentAt(step);
        if (ic != null) to.setInlineCommentAt(step, ic);
    }

    private static boolean isSerialized(ConfigSection s) { return s.containsAt(List.of("=="), true); }

    private static boolean hasSeparatorKey(ConfigSection s, char sep) {
        for (String k : s.keys(false)) {
            if (k.indexOf(sep) >= 0) return true;
            if (s.getAt(List.of(k)) instanceof ConfigSection c && !isSerialized(c) && hasSeparatorKey(c, sep)) return true;
        }
        return false;
    }

    /**
     * 구분자가 든 키를 그 구분자로 쪼갠 새 트리(주석·머리말은 옮긴다). 직렬화 객체(== 키가 있는 구역)의 안쪽은 건드리지 않고,
     * 끝 구분자로 끝나는 키는 쪼개지 않고 그대로 둔다. 쪼갤 키가 없으면 같은 트리를 돌려준다.
     */
    private static ConfigSection resplit(ConfigSection src, char sep) {
        if (!hasSeparatorKey(src, sep)) return src;
        return resplitTree(src, sep);
    }

    private static ConfigSection resplitTree(ConfigSection src, char sep) {
        ConfigSection out = new ConfigSection();
        for (String k : src.keys(false)) {
            List<String> step = List.of(k);
            Object v = src.getAt(step);
            if (v instanceof ConfigSection c && !isSerialized(c)) v = resplitTree(c, sep);
            ConfigPaths.Parsed p = ConfigPaths.parse(k, sep);
            List<String> keys = p.endsAtSection() || p.keys().isEmpty() ? step : p.keys();
            out.setAt(keys, v);
            List<String> comments = src.getCommentsAt(step);
            if (!comments.isEmpty()) out.setCommentsAt(keys, comments);
            String inline = src.getInlineCommentAt(step);
            if (inline != null) out.setInlineCommentAt(keys, inline);
        }
        out.setHeader(src.getHeader());
        out.setFooter(src.getFooter());
        return out;
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
        char sep = options().pathSeparator();
        if (sep != '.') fresh = resplit(fresh, sep);   // Paper 는 읽을 때 구분자가 든 키를 쪼갠다(PR #254 검토 R5)
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
