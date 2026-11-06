// 플러그인 불러오기·켜기·끄기. jar 찾기·순서·부트스트랩은 PluginBootstrapper 가 먼저 하고(레지스트리·데이터 팩 전),
// 여기서는 그 결과로 메인 클래스를 만들어 onLoad·onEnable·onDisable 을 부른다.
// 켜는 순서는 depend·softdepend·loadbefore 로 정하고(Bukkit 과 같은 규칙), 끄는 순서는 그 반대다.
package org.domi.furnace.server.plugin;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Filter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.domi.furnace.api.Furnace;
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
    /** Paper/Bukkit 플러그인 폴더(plugins 아래). */
    public static final String BUKKIT_FOLDER = PluginBootstrapper.BUKKIT_FOLDER;

    private final PluginHost host;
    private final Path dir;
    /** 켜는 순서대로. */
    private final List<Loaded> plugins = new CopyOnWriteArrayList<>();
    /** 이 관리자가 이어받은 부트스트랩 결과(loadAll 전에는 null). */
    private volatile PluginBootstrapper boot;

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

    /** 부트스트랩부터 onLoad 까지 한 번에(서버가 부트스트랩을 따로 돌리지 않았을 때 — 시험 등). */
    public void loadAll() { loadAll(PluginBootstrapper.bootstrap(dir, settings)); }

    /** 부트스트랩을 마친 플러그인을 의존 순서대로 만들고 onLoad 까지 부른다. 실패한 플러그인은 로그를 남기고 건너뛴다. */
    public void loadAll(PluginBootstrapper boot) {
        this.boot = boot;
        failures.addAll(boot.failures());   // 찾기·순서·api-version·부트스트랩 단계 실패
        for (PluginBootstrapper.Prepared p : List.copyOf(boot.prepared())) load(p);
        failFastCheck("불러오기");
    }

    private void load(PluginBootstrapper.Prepared c) {
        PluginDescription d = c.description();
        PluginClassLoader loader = c.loader();
        FurnacePlugin plugin;
        try {
            plugin = c.bootstrap() != null ? c.bootstrap().createPlugin(c.context()) : null;
            if (plugin == null) {
                Class<?> main = Class.forName(d.main(), true, loader);
                if (!FurnacePlugin.class.isAssignableFrom(main)) throw new IllegalArgumentException("main 클래스가 FurnacePlugin 을 상속하지 않는다: " + d.main());
                plugin = (FurnacePlugin) main.getDeclaredConstructor().newInstance();
            }
        } catch (Throwable t) {
            log.error("Could not load '{}' in folder '{}'", dir.relativize(c.file()), dir.getFileName(), t);
            failures.add(d.name());
            boot.drop(c);
            return;
        }
        java.util.logging.Logger jul = c.logger();
        Level start = settings.levelFor(d.name());
        if (start != null) jul.setLevel(start);
        plugin.init(new Context(d, c.file(), loader, jul, boot.registries(d)));
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

    /** "[접두어] 메시지" 로 서버 로그(slf4j)에 쓰는 JUL 로거. */
    static java.util.logging.Logger pluginLogger(PluginDescription d) {
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
        private final org.domi.furnace.api.registry.PluginRegistries registries;

        Context(PluginDescription d, Path file, ClassLoader loader, java.util.logging.Logger logger,
                org.domi.furnace.api.registry.PluginRegistries registries) {
            this.d = d;
            this.file = file;
            this.loader = loader;
            this.logger = logger;
            this.registries = registries;
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

        public org.domi.furnace.api.registry.PluginRegistries registries() { return registries; }
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
        for (Permission perm : l.description().permissions()) host.permissionsImpl().unregister(perm.name());
        PluginBootstrapper b = boot;
        if (b != null) b.forget(l.description().name());   // 플러그인 레지스트리 몫·수명주기 훅
    }

    /** 서버 종료 마지막 — 클래스로더를 닫는다. */
    public void closeAll() {
        for (Loaded l : plugins) closeQuietly(l.loader());
        plugins.clear();
    }

    static void closeQuietly(URLClassLoader l) {
        try {
            l.close();
        } catch (IOException ignored) {
        }
    }

    private Loaded find(FurnacePlugin p) {
        for (Loaded l : plugins) if (l.plugin() == p) return l;
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

    /** 플러그인 jar 하나의 클래스로더: 서버(부모) → 자기 jar → 다른 플러그인 jar 순으로 찾는다. */
    static final class PluginClassLoader extends URLClassLoader {
        static {
            ClassLoader.registerAsParallelCapable();
        }

        private final PluginBootstrapper group;

        PluginClassLoader(Path jar, PluginBootstrapper group) throws MalformedURLException {
            super(new URL[]{jar.toUri().toURL()}, PluginHost.class.getClassLoader());
            this.group = group;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            try {
                return super.findClass(name);
            } catch (ClassNotFoundException e) {
                Class<?> c = group.findInOthers(name, this);
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
