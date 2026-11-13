// 플러그인 불러오기·켜기·끄기. jar 찾기·순서·부트스트랩은 PluginBootstrapper 가 먼저 하고(레지스트리·데이터 팩 전),
// 여기서는 그 결과로 메인 클래스를 만들어 onLoad·onEnable·onDisable 을 부른다.
// 켜는 순서는 depend·softdepend·loadbefore 로 정하고(Bukkit 과 같은 규칙), 끄는 순서는 그 반대다.
package org.domi.furnace.server.plugin;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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
import org.domi.furnace.api.profiler.PluginProfiler;
import org.domi.furnace.api.profiler.Timing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PluginManagerImpl implements PluginManager {

    private static final Logger log = LoggerFactory.getLogger("Furnace");
    /** Paper/Bukkit 플러그인 폴더(plugins 아래). */
    public static final String BUKKIT_FOLDER = PluginBootstrapper.BUKKIT_FOLDER;

    private final PluginHost host;
    private volatile Path dir;
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

    /** 플러그인 폴더를 바꾼다(시험 하니스가 임시 폴더를 쓸 때 — 부트스트랩에 준 경로와 맞춘다). 불러오기 전에만 부른다. */
    public void directory(Path dir) { this.dir = dir; }

    // ------------------------------------------------------------------ 불러오기

    /** 부트스트랩부터 onLoad 까지 한 번에(서버가 부트스트랩을 따로 돌리지 않았을 때 — 시험 등). */
    public void loadAll() { loadAll(PluginBootstrapper.bootstrap(dir, settings)); }

    /** 부트스트랩을 마친 플러그인을 의존 순서대로 만들고 onLoad 까지 부른다. 실패한 플러그인은 로그를 남기고 건너뛴다. */
    /** 플러그인이 등록한 게임 레지스트리 항목(바이옴·차원 타입). 부트스트랩 전이면 null. */
    public GameRegistryStore gameRegistries() {
        PluginBootstrapper b = boot;
        return b == null ? null : b.gameRegistries();
    }

    /** 플러그인 월드젠 훅 저장소(W3). 부트스트랩 전이면 null. */
    public GenerationHookStore generationHooks() {
        PluginBootstrapper b = boot;
        return b == null ? null : b.generationHooks();
    }

    /** 다른 서버 소프트웨어 형식(Bukkit 등) 플러그인을 한 번이라도 올렸는가 — world.tick-when-empty=auto 가 Paper 쪽으로 간다. */
    private volatile boolean foreignLoaded;

    public boolean foreignPluginsLoaded() { return foreignLoaded; }

    public void loadAll(PluginBootstrapper boot) {
        this.boot = boot;
        failures.addAll(boot.failures());   // 찾기·순서·api-version·부트스트랩 단계 실패
        for (PluginBootstrapper.Prepared p : List.copyOf(boot.prepared())) load(p);
        failFastCheck("불러오기");
    }

    private Loaded load(PluginBootstrapper.Prepared c) {
        PluginDescription d = c.description();
        PluginClassLoader loader = c.loader();
        FurnacePlugin plugin;
        Context ctx = new Context(d, c.file(), loader, c.logger(), boot.registries(d));
        try {
            plugin = c.bootstrap() != null ? c.bootstrap().createPlugin(c.context()) : null;
            if (plugin == null && c.format() != null) {
                // 다른 서버 소프트웨어 형식(예: Bukkit): 형식 어댑터가 메인 클래스로 껍데기 플러그인을 만든다
                plugin = c.format().create(ctx, Class.forName(d.main(), true, loader));
            }
            if (plugin == null) {
                Class<?> main = Class.forName(d.main(), true, loader);
                if (!FurnacePlugin.class.isAssignableFrom(main)) throw new IllegalArgumentException("main 클래스가 FurnacePlugin 을 상속하지 않는다: " + d.main());
                plugin = (FurnacePlugin) main.getDeclaredConstructor().newInstance();
            }
            loader.plugin = plugin;
            if (c.format() != null && c.bootstrap() == null) foreignLoaded = true;
        } catch (Throwable t) {
            log.error("Could not load '{}' in folder '{}'", dir.relativize(c.file()), dir.getFileName(), t);
            failures.add(d.name());
            boot.drop(c);
            return null;
        }
        java.util.logging.Logger jul = c.logger();
        Level start = settings.levelFor(d.name());
        if (start != null) jul.setLevel(start);
        plugin.init(ctx);
        Loaded l = new Loaded(plugin, d, c.file(), loader);
        plugins.add(l);
        jul.info("Loading " + d.fullName());
        Timing timing = host.profiler().timing(plugin, PluginProfiler.Kind.LIFECYCLE, "onLoad");
        long t0 = timing.start();
        try {
            plugin.onLoad();
        } catch (Throwable t) {
            log.error("Error occurred while loading {} (Is it up to date?)", d.fullName(), t);
            failures.add(d.name());
        } finally {
            timing.stop(t0);
        }
        return l;
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

        /** 관리자가 이 플러그인에 건 1회 토큰을 쓴다 — setEnabled 머리에서 한 번만 참이다(onEnable 안에서 부른 setEnabled(false) 는 거짓). */
        @Override
        public boolean lifecycleCall() {
            FurnacePlugin t = LIFECYCLE.get();
            if (t == null || t.description() != d) return false;
            LIFECYCLE.remove();
            return true;
        }
    }

    /**
     * 관리자가 지금 setEnabled 를 부르려는 플러그인(1회 토큰). 그 밖의 setEnabled(플러그인 자신·다른 플러그인, onEnable 안의 setEnabled(false)
     * 포함)는 관리자 enable/disable 로 넘어가 자동 정리까지 돈다(A14-08).
     */
    private static final ThreadLocal<FurnacePlugin> LIFECYCLE = new ThreadLocal<>();

    private static void setEnabledByManager(FurnacePlugin p, boolean v) {
        FurnacePlugin was = LIFECYCLE.get();
        LIFECYCLE.set(p);
        try {
            p.setEnabled(v);
        } finally {
            if (was == null) LIFECYCLE.remove();
            else LIFECYCLE.set(was);
        }
    }

    // ------------------------------------------------------------------ 켜기·끄기

    /** 불러온 플러그인을 순서대로 켠다. */
    public void enableAll() {
        for (Loaded l : plugins) enable(l);
        failFastCheck("켜기");
    }

    @Override
    public void enable(FurnacePlugin p) {
        Loaded l = find(p);
        if (l != null) enable(l);
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
        Timing timing = host.profiler().timing(p, PluginProfiler.Kind.LIFECYCLE, "onEnable");
        long t0 = timing.start();
        try {
            setEnabledByManager(p, true);
        } catch (Throwable t) {
            log.error("Error occurred while enabling {} (Is it up to date?)", l.description().fullName(), t);
            failures.add(l.description().name());
            disable(p);
            return;
        } finally {
            timing.stop(t0);
        }
        host.eventsImpl().call(new PluginEnableEvent(p));
    }

    /** 켠 순서의 반대로 모두 끈다. */
    public void disableAll() {
        List<Loaded> rev = new ArrayList<>(plugins);
        java.util.Collections.reverse(rev);
        for (Loaded l : rev) disable(l.plugin());
        PluginBootstrapper b = boot;
        if (b != null) b.afterAllDisabled(host);   // 형식 어댑터가 남은 일(Bukkit 비동기 작업 등)을 마무리할 시간
    }

    @Override
    public void disable(FurnacePlugin p) {
        Loaded l = find(p);
        if (l == null) return;
        if (p.isEnabled()) {
            host.eventsImpl().call(new PluginDisableEvent(p));
            p.logger().info("Disabling " + l.description().fullName());
            Timing timing = host.profiler().timing(p, PluginProfiler.Kind.LIFECYCLE, "onDisable");
            long t0 = timing.start();
            try {
                setEnabledByManager(p, false);
            } catch (Throwable t) {
                log.error("Error occurred while disabling {} (Is it up to date?)", l.description().fullName(), t);
            } finally {
                timing.stop(t0);
            }
        }
        host.profiled().retire(p);   // 어느 스케줄러로 건 작업이든 꺼지기 전에 건 것은 다시 켜도 돌지 않는다
        host.eventsImpl().unregisterAll(p);
        host.commandsImpl().unregisterAll(p);
        host.removeAttachments(p);
        host.removeChannels(p);
        host.recipes().removeAll(p);
        host.gameRules().unregisterAll(p);
        host.difficulty().unregisterAll(p);
        org.domi.furnace.server.loot.EnchantmentToggles.removeOwner(p.name());   // 이 플러그인이 끈 마법부여
        host.packetsImpl().unregisterAll(p);
        host.services().unregisterAll(p);   // 이 플러그인이 올린 서비스 제공자
        host.translations().unregisterAll(p);   // 이 플러그인이 올린 번역 저장소
        host.virtualImpl().removeAll(p);   // 가상 엔티티·가짜 탭 줄
        host.entityViewsImpl().removeAll(p);   // 보이기·숨기기·변장 규칙
        host.playerViewsImpl().removeAll(p);   // 가상 블록·바이옴 층, 장비 덮어쓰기, 개인 보더
        try {
            for (var w : host.core().worlds()) w.spawnPolicy().removeFiltersOf(p);   // 스폰 필터(A14-06)
        } catch (RuntimeException e) {
            log.warn("{} 의 스폰 필터 정리 실패", l.description().name(), e);
        }
        try {
            host.lightSourcesImpl().removeAll(p);   // 가짜 광원
        } catch (RuntimeException e) {
            log.warn("{} 의 가짜 광원 정리 실패", l.description().name(), e);
        }
        try {
            host.chunkTicketsImpl().releaseAll(p);   // 청크 티켓
        } catch (RuntimeException e) {
            log.warn("{} 의 청크 티켓 정리 실패", l.description().name(), e);
        }
        // 설명 파일의 그 노드가 아직 올라 있을 때만 지운다 — 다른 플러그인이 같은 이름을 갈아 끼웠으면 그것은 남긴다
        for (Permission perm : l.description().permissions()) host.permissionsImpl().unregister(perm);
        PluginBootstrapper b = boot;
        if (b != null) b.forget(l.description().name());   // 플러그인 레지스트리 몫·수명주기 훅
    }

    /** 서버 종료 마지막 — 클래스로더를 닫는다. */
    public void closeAll() {
        HotReload.Watcher w = watcher;
        watcher = null;
        if (w != null) w.stop();
        synchronized (reloadLock) {
            for (Loaded l : plugins) closeQuietly(l.loader());
            plugins.clear();
            PluginBootstrapper b = boot;
            if (b != null) b.closeSharedLibraries();
            if (settings.devMode()) HotReload.clearShadowDir(dir);
        }
    }

    // ------------------------------------------------------------------ 핫 리로드(개발 모드, D4)

    /** 다시 읽기는 한 번에 하나(전역 스레드 + 종료 경로). */
    private final Object reloadLock = new Object();
    private volatile HotReload.Watcher watcher;
    /** 옛 클래스로더를 닫기까지 기다리는 틱(그 사이 다른 스레드에서 끝나 가는 옛 작업이 클래스를 마저 읽게). 시험은 0. */
    volatile long retireDelayTicks = 100;
    /** 리로드 때 끈 플러그인의 비동기 일을 기다리는 가장 긴 시간(ms) — Paper CraftServer.reload 와 같은 50ms×50 = 2.5초(종료는 5초). 시험은 줄인다. */
    volatile long asyncWaitMillis = 2_500;
    /** 옛 클래스로더가 회수됐는지 볼 때까지의 시간(ms, 음수면 보지 않는다). */
    volatile long leakCheckMillis = 15_000;

    @Override
    public boolean devMode() { return settings.devMode(); }

    @Override
    public CompletableFuture<List<String>> reload(String name) {
        if (!settings.devMode()) return PluginManager.super.reload(name);
        if (name == null || name.isBlank()) return CompletableFuture.failedFuture(new IllegalArgumentException("플러그인 이름이 없다"));
        // 전역 스레드의 다음 차례에 돈다 — 플러그인이 자기 명령 처리기 안에서 자기를 다시 읽게 해도 자기 코드가 스택에 없을 때 바뀐다
        return host.scheduler().global().submit(() -> reloadNow(name));
    }

    /** 개발 모드 jar 감시를 켠다(plugins.dev-mode.auto-reload). enableAll 뒤에 한 번. */
    public void startWatcher() { startWatcher(1000); }

    void startWatcher(long periodMillis) {
        if (!settings.autoReload() || watcher != null) return;
        HotReload.Watcher w = new HotReload.Watcher(dir, periodMillis, this::jarChanged);
        watcher = w;
        w.start();
        log.info("[핫 리로드] plugins/*.jar 변경을 감시한다");
    }

    HotReload.Watcher watcher() { return watcher; }

    /** 감시가 알린 jar 변경 → 그 jar 의 플러그인(없어졌으면 그 파일에서 읽었던 플러그인)을 다시 읽는다. */
    private void jarChanged(Path jar) {
        String name = null;
        PluginBootstrapper b = boot;
        if (Files.exists(jar) && b != null) {
            PluginBootstrapper.Candidate c = b.findOnDiskFile(jar);
            if (c != null) name = c.description().name();
        } else {
            for (Loaded l : plugins) if (l.file().equals(jar)) name = l.description().name();
        }
        if (name == null) return;
        String n = name;
        log.info("[핫 리로드] {} 가 바뀌었다 — {} 를 다시 읽는다", dir.relativize(jar), n);
        reload(n).exceptionally(t -> {
            log.error("[핫 리로드] {} 다시 읽기 실패", n, t);
            return List.of();
        });
    }

    /**
     * 지금 스레드에서 다시 읽는다(전역 스레드에서 부른다). 순서: 새 jar 의 설명 파일 확인 → 대상과 기대는 플러그인을 켠 순서의 반대로 끄기
     * (구독·명령·작업·권한·채널·레지스트리·훅·맡긴 자원 자동 정리) → 클래스 찾기 범위에서 빼기 → 켠 순서대로 새 클래스로더로 부트스트랩·onLoad·onEnable
     * → 옛 클래스로더는 retireDelayTicks 뒤에 닫는다.
     */
    List<String> reloadNow(String name) {
        if (!settings.devMode()) throw new IllegalStateException("핫 리로드는 개발 모드(plugins.dev-mode=true)에서만 된다");
        PluginBootstrapper b = boot;
        if (b == null) throw new IllegalStateException("아직 플러그인을 불러오지 않았다");
        synchronized (reloadLock) {
            long t0 = System.nanoTime();
            Loaded target = findLoaded(name);
            PluginBootstrapper.Candidate fresh = b.findOnDisk(name);
            if (target == null && fresh == null) throw new IllegalArgumentException("그런 플러그인이 없다: " + name);
            if (target != null && fresh == null && Files.exists(target.file())) {
                // 덮어쓰는 도중이거나 깨진 jar — 옛 것을 그대로 둔다
                throw new IllegalStateException(target.file().getFileName() + " 에서 " + name + " 의 설명 파일을 읽을 수 없다 — 지금 것을 그대로 둔다");
            }
            // 1. 다시 읽을 묶음: 대상 + 그것에 (연쇄로) 기대는 플러그인, 켠 순서대로
            List<Loaded> group = target == null ? List.of() : dependentsOf(target);
            List<String> names = new ArrayList<>();
            if (target == null) names.add(fresh.description().name());
            for (Loaded l : group) names.add(l.description().name());
            // 2. 끄기(역순) + 범위에서 빼기
            List<Loaded> rev = new ArrayList<>(group);
            java.util.Collections.reverse(rev);
            for (Loaded l : rev) {
                disable(l.plugin());
                plugins.remove(l);
                host.profiler().forget(l.plugin());   // 프로파일러 구간이 옛 인스턴스를 쥐고 있지 않게
                PluginBootstrapper.Prepared prep = b.find(l.description().name());
                if (prep != null && prep.loader() == l.loader()) b.detach(prep);
            }
            // 2b. 끈 플러그인의 남은 비동기 일(Bukkit 비동기 작업 등)이 끝나기를 기다린다 — 서버 종료와 같은 규칙(최대 asyncWaitMillis=Paper reload 의 2.5초, 남으면 Nag)
            if (!rev.isEmpty()) b.awaitPluginTasks(host, rev.stream().map(Loaded::plugin).toList(), asyncWaitMillis);
            // 3. 다시 읽기(켠 순서). 대상 jar 가 없어졌으면 대상은 내리기만 한다
            List<String> back = new ArrayList<>();
            for (String n : names) {
                PluginBootstrapper.Candidate c = n.equalsIgnoreCase(name) ? fresh : b.findOnDisk(n);
                if (c == null) {
                    log.warn("[핫 리로드] {} 의 jar 가 plugins/ 에 없다 — 내리기만 한다", n);
                    continue;
                }
                PluginBootstrapper.Prepared prep = b.reprepare(c);
                if (prep == null) continue;
                Loaded l = load(prep);
                if (l == null) continue;
                enable(l);
                if (l.plugin().isEnabled()) back.add(l.description().name());
            }
            // 4. 옛 클래스로더 정리(늦게 — 다른 스레드에서 끝나 가는 옛 작업이 있을 수 있다)
            for (Loaded l : group) retireLater(l);
            log.info("[핫 리로드] {} 다시 읽음 — 켜짐 {} ({} ms)", names, back, (System.nanoTime() - t0) / 1_000_000);
            return back;
        }
    }

    /** 대상과, 대상에 (연쇄로) 기대는 불러온 플러그인 — 켠 순서. */
    private List<Loaded> dependentsOf(Loaded target) {
        Set<String> set = new java.util.HashSet<>();
        set.add(key(target.description().name()));
        boolean grew = true;
        while (grew) {
            grew = false;
            for (Loaded l : plugins) {
                String k = key(l.description().name());
                if (set.contains(k)) continue;
                if (reliesOn(l, set)) {
                    set.add(k);
                    grew = true;
                }
            }
        }
        List<Loaded> out = new ArrayList<>();
        for (Loaded l : plugins) if (set.contains(key(l.description().name()))) out.add(l);
        return out;
    }

    /** l 이 set 의 어느 플러그인 뒤에 켜져야 하거나 그 클래스를 쓰는가. */
    private boolean reliesOn(Loaded l, Set<String> set) {
        PluginDescription d = l.description();
        for (String dep : d.depend()) if (set.contains(key(dep))) return true;
        for (String dep : d.softDepend()) if (set.contains(key(dep))) return true;
        for (String s : set) {
            Loaded o = findLoaded(s);
            if (o != null) for (String lb : o.description().loadBefore()) if (key(lb).equals(key(d.name()))) return true;
        }
        for (String borrowed : l.loader().borrowed()) if (set.contains(borrowed)) return true;
        return false;
    }

    private static String key(String name) { return name.toLowerCase(Locale.ROOT); }

    private Loaded findLoaded(String name) {
        for (Loaded l : plugins) if (l.description().name().equalsIgnoreCase(name)) return l;
        return null;
    }

    private void retireLater(Loaded l) {
        String n = l.description().name();
        long delay = retireDelayTicks;
        if (delay <= 0) {
            HotReload.retire(n, l.loader(), leakCheckMillis);
            return;
        }
        host.scheduler().global().executeDelayed(() -> HotReload.retire(n, l.loader(), leakCheckMillis), delay);
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

    /** 그 클래스를 정의한 플러그인(플러그인 jar 의 클래스·그 안에서 만든 람다). 서버·API·JDK 클래스면 null. */
    static FurnacePlugin pluginOf(Class<?> c) {
        return c.getClassLoader() instanceof PluginClassLoader l ? l.plugin : null;
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

        private final PluginBootstrapper group;
        /** 개발 모드에서 읽는 jar 사본(닫은 뒤 지운다). 아니면 null. */
        private final Path shadow;
        /** 이 로더가 클래스를 빌려 온 플러그인 이름 — 그 플러그인을 다시 읽으면 이 플러그인도 다시 읽어야 한다. */
        private final Set<String> borrowed = ConcurrentHashMap.newKeySet();
        /** 이 jar 의 플러그인(메인 클래스를 만든 뒤 정해진다) — 프로파일러가 작업 클래스로 주인을 찾을 때 쓴다. */
        volatile FurnacePlugin plugin;

        PluginClassLoader(Path jar, PluginBootstrapper group) throws MalformedURLException { this(jar, group, false); }

        PluginClassLoader(Path jar, PluginBootstrapper group, boolean shadowCopy) throws MalformedURLException {
            this(jar, group, shadowCopy, List.of());
        }

        /** libraries: 자동 로딩이 받아 둔 라이브러리 jar(플러그인 jar 뒤에 붙는다 — 플러그인 자기 클래스가 먼저). */
        PluginClassLoader(Path jar, PluginBootstrapper group, boolean shadowCopy, List<Path> libraries) throws MalformedURLException {
            this(jar, group, shadowCopy, libraries, null);
        }

        /** transformer: 플러그인 형식이 준 바이트 변환기(없으면 null) — 플러그인 jar 의 클래스에만 쓴다(libraries jar 는 그대로). */
        PluginClassLoader(Path jar, PluginBootstrapper group, boolean shadowCopy, List<Path> libraries,
                          org.domi.furnace.api.plugin.ClassTransformer transformer) throws MalformedURLException {
            this(jar, group, shadowCopy, libraries, transformer, null);
        }

        /**
         * parent: 부모 로더(null 이면 서버 클래스로더). 호환 플러그인은 서버 제공 라이브러리 공유 로더(SharedLibraryLoader — 그 부모가 서버 클래스로더)를
         * 부모로 받아, Paper 처럼 모든 호환 플러그인이 같은 Guava·Adventure 클래스를 본다.
         */
        PluginClassLoader(Path jar, PluginBootstrapper group, boolean shadowCopy, List<Path> libraries,
                          org.domi.furnace.api.plugin.ClassTransformer transformer, ClassLoader parent) throws MalformedURLException {
            this(jar, group, shadowCopy, libraries, transformer, parent, false);
        }

        /**
         * hideCompat: true 면 호환 모듈 클래스(org.bukkit.* 등 — {@link CompatPackages})를 부모(서버 jar)에서 찾지 않는다. Furnace 네이티브 플러그인용:
         * 서버 jar 에 호환 모듈이 함께 실려 있어도 네이티브 플러그인에게는 보이지 않아 Furnace API 만 쓰게 되고, 자기 jar 에 같은 이름의 클래스(다른 버전 ASM 등)를 넣어도 서버 것에 가려지지 않는다.
         */
        PluginClassLoader(Path jar, PluginBootstrapper group, boolean shadowCopy, List<Path> libraries,
                          org.domi.furnace.api.plugin.ClassTransformer transformer, ClassLoader parent, boolean hideCompat) throws MalformedURLException {
            this(jar, group, shadowCopy, libraries, transformer, parent, hideCompat, false);
        }

        /**
         * ownResources: true 면 리소스(getResource·getResources)를 이 jar(+ libraries jar)에서만 찾는다 — Paper PluginClassLoader 처럼 서버 jar 의
         * 같은 이름 파일(lang/en_us.json, META-INF/MANIFEST.MF 등)이 플러그인 것을 가리거나, 플러그인에 없는 서버 파일이 보이지 않게(A15-02).
         * 호환(Bukkit) 플러그인만 켠다. 클래스 찾기 순서는 바꾸지 않는다.
         */
        PluginClassLoader(Path jar, PluginBootstrapper group, boolean shadowCopy, List<Path> libraries,
                          org.domi.furnace.api.plugin.ClassTransformer transformer, ClassLoader parent, boolean hideCompat,
                          boolean ownResources) throws MalformedURLException {
            super(urls(jar, libraries), parent != null ? parent : PluginHost.class.getClassLoader());
            this.ownResources = ownResources;
            this.hideCompat = hideCompat;
            this.group = group;
            this.shadow = shadowCopy ? jar : null;
            this.transformer = transformer;
            this.jarPath = jar;
            this.jarUrl = jar.toUri().toURL();
        }

        private final boolean hideCompat;
        private final boolean ownResources;
        private final org.domi.furnace.api.plugin.ClassTransformer transformer;
        private final Path jarPath;
        private final URL jarUrl;
        /** 변환기가 있을 때만 연다(클래스 바이트를 직접 읽는다). close 에서 닫는다. */
        private java.util.jar.JarFile jarFile;
        /** close 뒤에는 jar 를 다시 열지 않는다(URLClassLoader 처럼 찾기가 실패한다). */
        private boolean closed;
        private volatile boolean transformWarned;

        private synchronized java.util.jar.JarFile jar() throws IOException {
            if (closed) throw new IOException("닫힌 플러그인 로더: " + jarPath.getFileName());
            if (jarFile == null) jarFile = new java.util.jar.JarFile(jarPath.toFile(), true, java.util.zip.ZipFile.OPEN_READ, Runtime.version());
            return jarFile;
        }

        /**
         * 이 jar 에서 찾는다. 변환기가 있으면 플러그인 jar 의 클래스 바이트를 읽어 변환기를 거쳐 정의한다 — 코드 출처(URL·서명자)와 패키지 정의는
         * URLClassLoader 와 같게 둔다(getProtectionDomain().getCodeSource() 로 자기 jar 를 찾는 플러그인이 있다).
         */
        private Class<?> findOwn(String name) throws ClassNotFoundException {
            if (transformer == null) return super.findClass(name);
            String path = name.replace('.', '/').concat(".class");
            java.util.jar.JarFile jf;
            java.util.jar.JarEntry entry;
            byte[] raw;
            try {
                jf = jar();
                entry = jf.getJarEntry(path);
                if (entry == null) return super.findClass(name);   // libraries jar 의 클래스
                try (java.io.InputStream in = jf.getInputStream(entry)) {
                    raw = in.readAllBytes();
                }
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
            byte[] bytes = raw;
            try {
                byte[] t = transformer.transform(name, raw);
                if (t != null) bytes = t;
            } catch (UnsupportedOperationException refused) {
                throw refused;   // 이 서버에서 정의할 수 없는 클래스(ClassTransformer 계약) — 원래 바이트로 올리지 않는다
            } catch (RuntimeException e) {
                // 변환기가 못 읽는 클래스(이상한 바이트코드)는 원래 바이트로 — 그 클래스가 변환이 필요한 이름을 쓰면 그 자리에서 실패한다
                if (!transformWarned) {
                    transformWarned = true;
                    log.warn("플러그인 jar {} 의 클래스 {} 를 변환하지 못해 원래 바이트로 올린다(이 jar 의 다음 실패는 DEBUG)", jarPath.getFileName(), name, e);
                } else {
                    log.debug("클래스 {} 변환 실패 — 원래 바이트로", name, e);
                }
            }
            int dot = name.lastIndexOf('.');
            if (dot > 0) {
                String pkg = name.substring(0, dot);
                if (getDefinedPackage(pkg) == null) {
                    try {
                        java.util.jar.Manifest man = jf.getManifest();
                        if (man != null) definePackage(pkg, man, jarUrl);
                        else definePackage(pkg, null, null, null, null, null, null, null);
                    } catch (IllegalArgumentException race) {
                        // 다른 스레드가 먼저 정의했다
                    } catch (IOException e) {
                        throw new ClassNotFoundException(name, e);
                    }
                }
            }
            return defineClass(name, bytes, 0, bytes.length, new java.security.CodeSource(jarUrl, entry.getCodeSigners()));
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                synchronized (this) {
                    closed = true;
                    if (jarFile != null) {
                        jarFile.close();
                        jarFile = null;
                    }
                }
            }
        }

        private static URL[] urls(Path jar, List<Path> libraries) throws MalformedURLException {
            URL[] out = new URL[1 + libraries.size()];
            out[0] = jar.toUri().toURL();
            for (int i = 0; i < libraries.size(); i++) out[i + 1] = libraries.get(i).toUri().toURL();
            return out;
        }

        void borrowedFrom(String plugin) { borrowed.add(plugin.toLowerCase(Locale.ROOT)); }

        Set<String> borrowed() { return borrowed; }

        Path shadow() { return shadow; }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (hideCompat && CompatPackages.isCompat(name)) {
                // 부모(서버 jar)는 건너뛴다: 자기 jar → 다른 플러그인 jar 에서만 찾는다
                synchronized (getClassLoadingLock(name)) {
                    Class<?> c = findLoadedClass(name);
                    if (c == null) c = findClass(name);
                    if (resolve) resolveClass(c);
                    return c;
                }
            }
            return super.loadClass(name, resolve);
        }

        @Override
        public URL getResource(String name) {
            return ownResources ? findResource(name) : super.getResource(name);
        }

        @Override
        public java.util.Enumeration<URL> getResources(String name) throws IOException {
            return ownResources ? findResources(name) : super.getResources(name);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            try {
                return findOwn(name);
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
                    return findOwn(name);
                } catch (ClassNotFoundException e) {
                    return null;
                }
            }
        }
    }
}
