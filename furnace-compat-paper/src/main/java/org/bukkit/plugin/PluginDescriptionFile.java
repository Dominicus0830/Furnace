package org.bukkit.plugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.domi.furnace.api.config.ConfigFormat;
import org.domi.furnace.api.config.ConfigSection;

/** plugin.yml 한 개의 내용. */
public final class PluginDescriptionFile {

    private static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9 _.-]+$");

    private String name;
    private String main;
    private String version;
    private String description;
    private String website;
    private String prefix;
    private String apiVersion;
    private PluginLoadOrder order = PluginLoadOrder.POSTWORLD;
    private List<String> authors = List.of();
    private List<String> contributors = List.of();
    private List<String> depend = List.of();
    private List<String> softDepend = List.of();
    private List<String> loadBefore = List.of();
    private List<String> provides = List.of();
    private List<String> libraries = List.of();
    private Map<String, Map<String, Object>> commands = Map.of();

    public PluginDescriptionFile(InputStream stream) throws InvalidDescriptionException {
        this(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    public PluginDescriptionFile(Reader reader) throws InvalidDescriptionException {
        try {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[2048];
            int n;
            while ((n = reader.read(buf)) >= 0) sb.append(buf, 0, n);
            ConfigSection s = ConfigFormat.YAML.parse(sb.toString());
            load(s.toMap());
        } catch (InvalidDescriptionException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidDescriptionException(e);
        }
    }

    public PluginDescriptionFile(String pluginName, String pluginVersion, String mainClass) {
        this.name = pluginName.replace(' ', '_');
        this.version = pluginVersion;
        this.main = mainClass;
        if (!VALID_NAME.matcher(pluginName).matches()) throw new IllegalArgumentException("플러그인 이름에 쓸 수 없는 글자가 있다: " + pluginName);
    }

    /** 이미 읽은 YAML 맵에서 만든다(호환 모듈이 paper-plugin.yml 을 plugin.yml 모양으로 바꿔 넘길 때). */
    public PluginDescriptionFile(Map<?, ?> yaml) throws InvalidDescriptionException {
        load(yaml);
    }

    private void load(Map<?, ?> map) throws InvalidDescriptionException {
        String rawName = str(map, "name");
        if (rawName == null) throw new InvalidDescriptionException("name is not defined");
        if (!VALID_NAME.matcher(rawName).matches()) throw new InvalidDescriptionException("name '" + rawName + "' contains invalid characters.");
        name = rawName.replace(' ', '_');
        version = str(map, "version");
        if (version == null) throw new InvalidDescriptionException("version is not defined");
        main = str(map, "main");
        if (main == null) throw new InvalidDescriptionException("main is not defined");
        if (main.startsWith("org.bukkit.")) throw new InvalidDescriptionException("main may not be within the org.bukkit namespace");
        String load = str(map, "load");
        if (load != null) {
            try {
                order = PluginLoadOrder.valueOf(load.toUpperCase(Locale.ROOT).replaceAll("\\W", ""));
            } catch (IllegalArgumentException e) {
                throw new InvalidDescriptionException(e, "load is not a valid choice");
            }
        }
        depend = list(map.get("depend"));
        softDepend = list(map.get("softdepend"));
        loadBefore = list(map.get("loadbefore"));
        provides = list(map.get("provides"));
        description = str(map, "description");
        website = str(map, "website");
        prefix = str(map, "prefix");
        apiVersion = str(map, "api-version");
        List<String> a = new ArrayList<>();
        String author = str(map, "author");
        if (author != null) a.add(author);
        a.addAll(list(map.get("authors")));
        authors = List.copyOf(a);
        contributors = list(map.get("contributors"));
        libraries = list(map.get("libraries"));
        Object cmds = map.get("commands");
        if (cmds instanceof Map<?, ?> cm) {
            Map<String, Map<String, Object>> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : cm.entrySet()) {
                Map<String, Object> info = new LinkedHashMap<>();
                if (e.getValue() instanceof Map<?, ?> m) for (Map.Entry<?, ?> x : m.entrySet()) info.put(String.valueOf(x.getKey()), x.getValue());
                out.put(String.valueOf(e.getKey()), Collections.unmodifiableMap(info));
            }
            commands = Collections.unmodifiableMap(out);
        }
    }

    private static String str(Map<?, ?> map, String key) {
        Object v = map.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static List<String> list(Object v) {
        if (v == null) return List.of();
        List<String> out = new ArrayList<>();
        if (v instanceof Iterable<?> it) {
            for (Object o : it) if (o != null) out.add(String.valueOf(o));
        } else {
            out.add(String.valueOf(v));
        }
        return List.copyOf(out);
    }

    public String getName() { return name; }

    public String getVersion() { return version; }

    public String getMain() { return main; }

    public String getDescription() { return description; }

    public String getWebsite() { return website; }

    public String getPrefix() { return prefix; }

    public String getAPIVersion() { return apiVersion; }

    public PluginLoadOrder getLoad() { return order; }

    public List<String> getAuthors() { return authors; }

    public List<String> getContributors() { return contributors; }

    public List<String> getDepend() { return depend; }

    public List<String> getSoftDepend() { return softDepend; }

    public List<String> getLoadBefore() { return loadBefore; }

    public List<String> getProvides() { return provides; }

    public List<String> getLibraries() { return libraries; }

    /** commands 항목 그대로(라벨 → 설명·별칭·권한·사용법 맵). 실제 등록은 H2. */
    public Map<String, Map<String, Object>> getCommands() { return commands; }

    public String getFullName() { return name + " v" + version; }
}
