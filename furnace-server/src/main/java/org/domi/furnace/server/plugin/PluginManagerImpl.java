// 플러그인 불러오기·켜기·끄기. plugins/*.jar 중 furnace-plugin.(yml|yaml|toml|json) 을 가진 것만 Furnace 플러그인이다.
// Paper/Bukkit 플러그인은 plugins/bukkit-plugins/ 에 두고 어댑터가 불러온다(여기서는 세기만 한다).
// 켜는 순서는 depend·softdepend·loadbefore 로 정하고(Bukkit 과 같은 규칙), 끄는 순서는 그 반대다.
package org.domi.furnace.server.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.jar.JarFile;
import java.util.logging.Filter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Stream;
import org.domi.furnace.api.ApiVersion;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.config.ConfigFormat;
import org.domi.furnace.api.config.ConfigSection;
import org.domi.furnace.api.event.server.PluginDisableEvent;
import org.domi.furnace.api.event.server.PluginEnableEvent;
import org.domi.furnace.api.permission.Permission;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.plugin.PluginContext;
import org.domi.furnace.api.plugin.PluginDescription;
import org.domi.furnace.api.plugin.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PluginManagerImpl implements PluginManager {

    private static final Logger log = LoggerFactory.getLogger("Furnace");
    /** 설명 파일 이름(우선순위 순). */
    static final List<String> DESCRIPTOR_NAMES = List.of("furnace-plugin.yml", "furnace-plugin.yaml", "furnace-plugin.toml", "furnace-plugin.json");
    /** Paper/Bukkit 플러그인 폴더(plugins 아래). */
    public static final String BUKKIT_FOLDER = "bukkit-plugins";

    private final PluginHost host;
    private final Path dir;
    /** 켜는 순서대로. */
    private final List<Loaded> plugins = new CopyOnWriteArrayList<>();

    private volatile PluginSettings settings = PluginSettings.DEFAULT;
    /** 불러오기·켜기에서 실패한 플러그인 이름(plugins.fail-fast 가 켜져 있으면 시작을 멈추는 근거). */
    private final List<String> failures = new CopyOnWriteArrayList<>();

    public void settings(PluginSettings s) { this.settings = s; }

    public PluginSettings settings() { return settings; }

    /** plugins.fail-fast=true 이고 실패가 있었으면 던진다 — 서버 시작이 멈춘다. */
    private void failFastCheck(String stage) {
        if (settings.failFast() && !failures.isEmpty()) {
            throw new IllegalStateException("plugins.fail-fast: 플러그인 " + stage + " 실패 — " + failures);
        }
    }

    record Loaded(FurnacePlugin plugin, PluginDescription description, Path file, PluginClassLoader loader) {}

    PluginManagerImpl(PluginHost host, Path dir) {
        this.host = host;
        this.dir = dir;
    }

    // ------------------------------------------------------------------ 불러오기

    /** 설명 파일을 읽은 후보. */
    private record Candidate(Path file, PluginDescription description) {}

    /** plugins/ 를 훑어 의존 순서대로 불러오고 onLoad 까지 부른다. 실패한 플러그인은 로그를 남기고 건너뛴다. */
    public void loadAll() {
        try {
            Files.createDirectories(dir);
            Files.createDirectories(dir.resolve(BUKKIT_FOLDER));
        } catch (IOException e) {
            log.error("plugins 폴더를 만들 수 없다: {}", dir, e);
            return;
        }
        long bukkit = countJars(dir.resolve(BUKKIT_FOLDER));
        if (bukkit > 0) {
            log.warn("plugins/{}/ 에 Paper/Bukkit 플러그인 {}개가 있다 — 호환 어댑터가 아직 없어 불러오지 않는다", BUKKIT_FOLDER, bukkit);
        }
        Map<String, Candidate> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Path jar : jars(dir)) {
            PluginDescription d;
            try {
                d = readDescription(jar);
            } catch (Exception e) {
                log.error("Could not load '{}' in folder '{}'", dir.relativize(jar), dir.getFileName(), e);
                failures.add(jar.getFileName().toString());
                continue;
            }
            if (d == null) {
                failures.add(jar.getFileName().toString());
                continue;
            }
            Candidate prev = byName.putIfAbsent(d.name(), new Candidate(jar, d));
            if (prev != null) {
                log.error("Ambiguous plugin name `{}' for files `{}' and `{}' in `{}'", d.name(), prev.file(), jar, dir.getFileName());
                failures.add(d.name());
            }
        }
        for (Candidate c : order(byName)) load(c);
        failFastCheck("불러오기");
    }

    private static long countJars(Path d) {
        try (Stream<Path> s = Files.list(d)) {
            return s.filter(p -> p.toString().endsWith(".jar")).count();
        } catch (IOException e) {
            return 0;
        }
    }

    private static List<Path> jars(Path d) {
        try (Stream<Path> s = Files.list(d)) {
            return s.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".jar")).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** 설명 파일을 읽는다. Furnace 설명이 없으면(Bukkit 플러그인이면 안내 후) null. */
    private PluginDescription readDescription(Path jar) throws IOException {
        try (JarFile jf = new JarFile(jar.toFile())) {
            for (String n : DESCRIPTOR_NAMES) {
                var entry = jf.getEntry(n);
                if (entry == null) continue;
                try (InputStream in = jf.getInputStream(entry)) {
                    ConfigFormat f = ConfigFormat.fromFileName(n).orElse(ConfigFormat.YAML);
                    ConfigSection s = f.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    return PluginDescription.read(s);
                }
            }
            if (jf.getEntry("plugin.yml") != null || jf.getEntry("paper-plugin.yml") != null) {
                log.error("Could not load '{}' in folder '{}': Paper/Bukkit 플러그인이다 — plugins/{}/ 에 넣어야 한다",
                        dir.relativize(jar), dir.getFileName(), BUKKIT_FOLDER);
                return null;
            }
            log.error("Could not load '{}' in folder '{}': 설명 파일({})이 없다", dir.relativize(jar), dir.getFileName(), DESCRIPTOR_NAMES.get(0));
            return null;
        }
    }

    /** depend·softdepend·loadbefore 로 위상 정렬(같은 단계는 이름 순). 빠진 필수 의존·순환은 그 플러그인을 뺀다. */
    private List<Candidate> order(Map<String, Candidate> byName) {
        Map<String, Candidate> ok = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        ok.putAll(byName);
        // 필수 의존이 없는 플러그인을 (연쇄적으로) 뺀다
        boolean changed = true;
        while (changed) {
            changed = false;
            for (var it = ok.values().iterator(); it.hasNext(); ) {
                Candidate c = it.next();
                List<String> missing = new ArrayList<>();
                for (String dep : c.description().depend()) if (!ok.containsKey(dep)) missing.add(dep);
                if (!missing.isEmpty()) {
                    log.error("Could not load '{}' in folder '{}': Unknown/missing dependency plugins: {}. Please download and install these plugins to run '{}'.",
                            dir.relativize(c.file()), dir.getFileName(), missing, c.description().name());
                    it.remove();
                    failures.add(c.description().name());
                    changed = true;
                }
            }
        }
        // 앞서야 하는 것들
        Map<String, Set<String>> before = new HashMap<>();
        for (Candidate c : ok.values()) {
            String n = c.description().name().toLowerCase(Locale.ROOT);
            Set<String> req = before.computeIfAbsent(n, k -> new HashSet<>());
            for (String d : c.description().depend()) req.add(d.toLowerCase(Locale.ROOT));
            for (String d : c.description().softDepend()) if (ok.containsKey(d)) req.add(d.toLowerCase(Locale.ROOT));
            for (String lb : c.description().loadBefore()) {
                if (ok.containsKey(lb)) before.computeIfAbsent(lb.toLowerCase(Locale.ROOT), k -> new HashSet<>()).add(n);
            }
        }
        List<Candidate> out = new ArrayList<>();
        Set<String> done = new HashSet<>();
        Map<String, Candidate> remaining = new LinkedHashMap<>();
        for (Candidate c : ok.values()) remaining.put(c.description().name().toLowerCase(Locale.ROOT), c);
        while (!remaining.isEmpty()) {
            String pick = null;
            for (String n : remaining.keySet()) {
                if (done.containsAll(before.getOrDefault(n, Set.of()))) {
                    pick = n;
                    break;
                }
            }
            if (pick == null) {
                for (Candidate c : remaining.values()) {
                    log.error("Could not load '{}' in folder '{}': 의존 관계가 순환한다", dir.relativize(c.file()), dir.getFileName());
                    failures.add(c.description().name());
                }
                break;
            }
            out.add(remaining.remove(pick));
            done.add(pick);
        }
        return out;
    }

    private void load(Candidate c) {
        PluginDescription d = c.description();
        if (!apiVersionOk(d, c.file())) {
            failures.add(d.name());
            return;
        }
        PluginClassLoader loader;
        try {
            loader = new PluginClassLoader(c.file(), this);
        } catch (MalformedURLException e) {
            log.error("Could not load '{}' in folder '{}'", dir.relativize(c.file()), dir.getFileName(), e);
            return;
        }
        FurnacePlugin plugin;
        try {
            Class<?> main = Class.forName(d.main(), true, loader);
            if (!FurnacePlugin.class.isAssignableFrom(main)) throw new IllegalArgumentException("main 클래스가 FurnacePlugin 을 상속하지 않는다: " + d.main());
            plugin = (FurnacePlugin) main.getDeclaredConstructor().newInstance();
        } catch (Throwable t) {
            log.error("Could not load '{}' in folder '{}'", dir.relativize(c.file()), dir.getFileName(), t);
            failures.add(d.name());
            closeQuietly(loader);
            return;
        }
        java.util.logging.Logger jul = pluginLogger(d);
        Level start = settings.levelFor(d.name());
        if (start != null) jul.setLevel(start);
        plugin.init(new Context(d, c.file(), loader, jul));
        Loaded l = new Loaded(plugin, d, c.file(), loader);
        plugins.add(l);
        jul.info("Loading " + d.fullName());
        try {
            plugin.onLoad();
        } catch (Throwable t) {
            log.error("Error occurred while loading {} (Is it up to date?)", d.fullName(), t);
            failures.add(d.name());
        }
    }

    /**
     * api-version 검사. 없거나 읽을 수 없으면 경고, 서버보다 새 API(major 가 다르거나 minor 가 큼)를 요구하면 불러오지 않는다,
     * 서버가 받아 주는 가장 오래된 버전보다 낮으면 경고한다.
     */
    private boolean apiVersionOk(PluginDescription d, Path file) {
        if (d.apiVersion() == null) {
            log.warn("Plugin {} 에 api-version 이 없다 — 지금 API({}) 로 간주한다. furnace-plugin 설명 파일에 api-version 을 적어라", d.fullName(), ApiVersion.CURRENT);
            return true;
        }
        ApiVersion v = ApiVersion.parse(d.apiVersion());
        if (v == null) {
            log.warn("Plugin {} 의 api-version '{}' 을 읽을 수 없다 — 지금 API({}) 로 간주한다", d.fullName(), d.apiVersion(), ApiVersion.CURRENT);
            return true;
        }
        if (!ApiVersion.serverSatisfies(v)) {
            log.error("Could not load '{}' in folder '{}': 플러그인이 요구하는 API {} 를 이 서버({})가 제공하지 않는다", dir.relativize(file), dir.getFileName(),
                    v, ApiVersion.CURRENT);
            return false;
        }
        if (v.compareTo(ApiVersion.OLDEST_SUPPORTED) < 0) {
            log.warn("Plugin {} 은 오래된 API {} 로 만들어졌다(서버 {}, 지원 하한 {}) — 업데이트를 받아라", d.fullName(), v, ApiVersion.CURRENT, ApiVersion.OLDEST_SUPPORTED);
        }
        return true;
    }

    /** "[접두어] 메시지" 로 서버 로그(slf4j)에 쓰는 JUL 로거. */
    private static java.util.logging.Logger pluginLogger(PluginDescription d) {
        return new PluginLogger(d.name(), "[" + d.logPrefix() + "] ", LoggerFactory.getLogger(d.name()));
    }

    /**
     * Bukkit PluginLogger 처럼 LogManager 에 등록하지 않고 log(LogRecord) 에서 바로 넘긴다 —
     * 등록된 로거의 핸들러는 JVM 종료 훅(LogManager.reset)이 떼어 버려 onDisable 로그가 사라진다.
     */
    private static final class PluginLogger extends java.util.logging.Logger {
        private final String prefix;
        private final String pluginName;
        private final Logger out;

        PluginLogger(String name, String prefix, Logger out) {
            super("furnace.plugin." + name, null);
            this.pluginName = name;
            this.prefix = prefix;
            this.out = out;
            setLevel(Level.INFO);
        }

        @Override
        public void log(LogRecord r) {
            if (!isLoggable(r.getLevel())) return;
            Filter f = getFilter();
            if (f != null && !f.isLoggable(r)) return;
            String msg = prefix + FORMAT.formatMessage(r);
            int lv = r.getLevel().intValue();
            // 로그 패턴의 %X{plugin} 으로 어느 플러그인의 줄인지 구조적으로 걸러 볼 수 있다
            org.slf4j.MDC.put("plugin", pluginName);
            try {
                if (lv >= Level.SEVERE.intValue()) out.error(msg, r.getThrown());
                else if (lv >= Level.WARNING.intValue()) out.warn(msg, r.getThrown());
                else if (lv >= Level.INFO.intValue()) out.info(msg, r.getThrown());
                else out.debug(msg, r.getThrown());
            } finally {
                org.slf4j.MDC.remove("plugin");
            }
        }
    }

    private static final java.util.logging.SimpleFormatter FORMAT = new java.util.logging.SimpleFormatter();

    private final class Context implements PluginContext {
        private final PluginDescription d;
        private final Path file;
        private final ClassLoader loader;
        private final java.util.logging.Logger logger;

        Context(PluginDescription d, Path file, ClassLoader loader, java.util.logging.Logger logger) {
            this.d = d;
            this.file = file;
            this.loader = loader;
            this.logger = logger;
        }

        public PluginDescription description() { return d; }

        public Furnace server() { return host; }

        public java.util.logging.Logger logger() { return logger; }

        public Path configFolder() { return dir.resolve(d.name()); }

        public Path dataFolder() {
            Path root = settings.dataRoot();
            return root == null ? configFolder() : root.resolve(d.name());
        }

        public Path file() { return file; }

        public ClassLoader classLoader() { return loader; }
    }

    // ------------------------------------------------------------------ 켜기·끄기

    /** 불러온 플러그인을 순서대로 켠다. */
    public void enableAll() {
        for (Loaded l : plugins) enable(l);
        failFastCheck("켜기");
    }

    private void enable(Loaded l) {
        FurnacePlugin p = l.plugin();
        if (p.isEnabled()) return;
        p.logger().info("Enabling " + l.description().fullName());
        for (Permission perm : l.description().permissions()) {
            try {
                host.permissionsImpl().register(perm);
            } catch (IllegalArgumentException e) {
                p.logger().warning("Plugin " + l.description().fullName() + " tried to register permission '" + perm.name() + "' but it's already registered");
            }
        }
        try {
            p.setEnabled(true);
        } catch (Throwable t) {
            log.error("Error occurred while enabling {} (Is it up to date?)", l.description().fullName(), t);
            failures.add(l.description().name());
            disable(p);
            return;
        }
        host.eventsImpl().call(new PluginEnableEvent(p));
    }

    /** 켠 순서의 반대로 모두 끈다. */
    public void disableAll() {
        List<Loaded> rev = new ArrayList<>(plugins);
        java.util.Collections.reverse(rev);
        for (Loaded l : rev) disable(l.plugin());
    }

    @Override
    public void disable(FurnacePlugin p) {
        Loaded l = find(p);
        if (l == null) return;
        if (p.isEnabled()) {
            host.eventsImpl().call(new PluginDisableEvent(p));
            p.logger().info("Disabling " + l.description().fullName());
            try {
                p.setEnabled(false);
            } catch (Throwable t) {
                log.error("Error occurred while disabling {} (Is it up to date?)", l.description().fullName(), t);
            }
        }
        host.eventsImpl().unregisterAll(p);
        host.commandsImpl().unregisterAll(p);
        host.removeAttachments(p);
        host.removeChannels(p);
        host.recipes().removeAll(p);
        host.packetsImpl().unregisterAll(p);
        for (Permission perm : l.description().permissions()) host.permissionsImpl().unregister(perm.name());
    }

    /** 서버 종료 마지막 — 클래스로더를 닫는다. */
    public void closeAll() {
        for (Loaded l : plugins) closeQuietly(l.loader());
        plugins.clear();
    }

    private static void closeQuietly(URLClassLoader l) {
        try {
            l.close();
        } catch (IOException ignored) {
        }
    }

    private Loaded find(FurnacePlugin p) {
        for (Loaded l : plugins) if (l.plugin() == p) return l;
        return null;
    }

    /** 다른 플러그인 jar 에서 클래스를 찾는다(의존 플러그인의 API 클래스를 쓰기 위해 — Bukkit 과 같다). */
    Class<?> findInOthers(String name, PluginClassLoader asking) {
        for (Loaded l : plugins) {
            if (l.loader() == asking) continue;
            Class<?> c = l.loader().findLocal(name);
            if (c != null) return c;
        }
        return null;
    }

    // ------------------------------------------------------------------ PluginManager

    @Override
    public Collection<FurnacePlugin> plugins() {
        List<FurnacePlugin> out = new ArrayList<>(plugins.size());
        for (Loaded l : plugins) out.add(l.plugin());
        return out;
    }

    @Override
    public void setLogLevel(String pluginName, Level level) {
        FurnacePlugin p = plugin(pluginName);
        if (p == null) throw new IllegalArgumentException("그런 플러그인이 없다: " + pluginName);
        p.logger().setLevel(level);
    }

    @Override
    public FurnacePlugin plugin(String name) {
        for (Loaded l : plugins) if (l.description().name().equalsIgnoreCase(name)) return l.plugin();
        return null;
    }

    @Override
    public boolean isEnabled(String name) {
        FurnacePlugin p = plugin(name);
        return p != null && p.isEnabled();
    }

    /** 이 클래스로더로 불러온 플러그인 이름(아니면 null). */
    String pluginNameOf(ClassLoader cl) {
        for (Loaded l : plugins) if (l.loader() == cl) return l.description().name();
        return null;
    }

    /** 플러그인 jar 하나의 클래스로더: 서버(부모) → 자기 jar → 다른 플러그인 jar 순으로 찾는다. */
    static final class PluginClassLoader extends URLClassLoader {
        static {
            ClassLoader.registerAsParallelCapable();
        }

        private final PluginManagerImpl manager;

        PluginClassLoader(Path jar, PluginManagerImpl manager) throws MalformedURLException {
            super(new URL[]{jar.toUri().toURL()}, PluginHost.class.getClassLoader());
            this.manager = manager;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            try {
                return super.findClass(name);
            } catch (ClassNotFoundException e) {
                Class<?> c = manager.findInOthers(name, this);
                if (c != null) return c;
                throw e;
            }
        }

        /** 이 jar 에서만 찾는다(다른 로더가 부를 때 — 서로 묻다가 무한히 도는 것을 막는다). */
        Class<?> findLocal(String name) {
            synchronized (getClassLoadingLock(name)) {
                Class<?> c = findLoadedClass(name);
                if (c != null) return c;
                try {
                    return super.findClass(name);
                } catch (ClassNotFoundException e) {
                    return null;
                }
            }
        }
    }
}
