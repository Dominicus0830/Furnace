// 플러그인 부트스트랩 단계: plugins/*.jar 를 훑어 설명 파일을 읽고 의존 순서를 정한 뒤, 플러그인마다 클래스로더를 만들고
// 설명 파일의 bootstrapper 를 부른다. 서버는 이것을 블록·아이템·동기화 레지스트리와 데이터 팩을 읽기 "전"에 돌린다
// (Paper 부트스트랩과 같은 자리). 메인 클래스 인스턴스·onLoad 는 그 뒤 PluginManagerImpl.loadAll 이 이어서 한다.
// furnace-plugin.(yml|yaml|toml|json) 을 가진 jar 만 Furnace 플러그인이다. Paper/Bukkit 플러그인은 plugins/bukkit-plugins/ 에 둔다.
package org.domi.furnace.server.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
import java.util.stream.Stream;
import org.domi.furnace.api.ApiVersion;
import org.domi.furnace.api.config.ConfigFormat;
import org.domi.furnace.api.config.ConfigSection;
import org.domi.furnace.api.plugin.PluginDescription;
import org.domi.furnace.api.plugin.bootstrap.BootstrapContext;
import org.domi.furnace.api.plugin.bootstrap.PluginBootstrap;
import org.domi.furnace.api.plugin.lifecycle.DatapackRegistrar;
import org.domi.furnace.api.plugin.lifecycle.LifecycleEventManager;
import org.domi.furnace.api.plugin.lifecycle.LifecycleEvents;
import org.domi.furnace.api.registry.PluginRegistries;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.data.PackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PluginBootstrapper {

    private static final Logger log = LoggerFactory.getLogger("Furnace");
    /** 설명 파일 이름(우선순위 순). */
    static final List<String> DESCRIPTOR_NAMES = List.of("furnace-plugin.yml", "furnace-plugin.yaml", "furnace-plugin.toml", "furnace-plugin.json");
    /** Paper/Bukkit 플러그인 폴더(plugins 아래). */
    public static final String BUKKIT_FOLDER = "bukkit-plugins";

    private final Path dir;
    private final PluginSettings settings;
    /** 찾기·부트스트랩에서 실패한 플러그인(또는 jar) 이름 — plugins.fail-fast 의 근거. */
    private final List<String> failures = new CopyOnWriteArrayList<>();
    private final PluginRegistryStore registries = new PluginRegistryStore();
    private final LifecycleHooks lifecycle = new LifecycleHooks();
    private final GameRegistryStore gameRegistries = new GameRegistryStore();
    /** 부트스트랩을 마친 플러그인(의존 순서). 클래스로더끼리 서로 찾는 범위이기도 하다. */
    private final List<Prepared> prepared = new CopyOnWriteArrayList<>();

    /** 부트스트랩을 마친 플러그인 하나. bootstrap 은 bootstrapper 가 없으면 null. */
    record Prepared(PluginDescription description, Path file, PluginManagerImpl.PluginClassLoader loader, java.util.logging.Logger logger,
                    PluginBootstrap bootstrap, BootstrapContext context) {}

    private PluginBootstrapper(Path dir, PluginSettings settings) {
        this.dir = dir;
        this.settings = settings;
    }

    /** plugins/ 를 훑어 의존 순서대로 부트스트랩까지 한다. 실패한 플러그인은 로그를 남기고 건너뛴다. */
    public static PluginBootstrapper bootstrap(Path dir) { return bootstrap(dir, PluginSettings.DEFAULT); }

    public static PluginBootstrapper bootstrap(Path dir, PluginSettings settings) {
        PluginBootstrapper b = new PluginBootstrapper(dir, settings);
        b.run();
        return b;
    }

    /** 찾기·부트스트랩에서 실패한 이름들. */
    List<String> failures() { return failures; }

    PluginSettings settings() { return settings; }

    public Path directory() { return dir; }

    List<Prepared> prepared() { return prepared; }

    PluginRegistryStore registryStore() { return registries; }

    LifecycleHooks lifecycle() { return lifecycle; }

    public GameRegistryStore gameRegistries() { return gameRegistries; }

    /**
     * 게임 레지스트리 훅(GAME_REGISTRIES)을 돌려 플러그인 바이옴·차원 타입을 모으고, base(바닐라) 뒤에 붙인 전송용 세트를 돌려 준다.
     * 서버 시작 때 한 번 — 부른 뒤 저장소는 고정되어 새 키는 더할 수 없다.
     */
    public org.domi.furnace.server.net.registry.RegistrySet collectGameRegistries(org.domi.furnace.server.net.registry.RegistrySet base) {
        gameRegistries.begin(base);
        List<GameRegistryStore.Registrar> opened = new ArrayList<>();
        lifecycle.fire(LifecycleEvents.GAME_REGISTRIES, owner -> {
            Prepared p = find(owner);
            GameRegistryStore.Registrar r = gameRegistries.registrar(owner, p.description().namespace(), p.logger());
            opened.add(r);
            return r;
        });
        opened.forEach(GameRegistryStore.Registrar::close);
        gameRegistries.freeze();
        return gameRegistries.extend(base);
    }

    /** 핫 리로드로 다시 부트스트랩한 플러그인 하나의 게임 레지스트리 훅을 돌린다(이미 있는 자기 키를 갱신·재활성만 한다). */
    private void reregisterGameRegistries(Prepared p) {
        if (!gameRegistries.isFrozen()) return;   // 시작 때 훅이 아직 안 돌았다(그때 한꺼번에 돈다)
        GameRegistryStore.Registrar r = gameRegistries.registrar(p.description().name(), p.description().namespace(), p.logger());
        try {
            lifecycle.fireOwner(p.description().name(), LifecycleEvents.GAME_REGISTRIES, r);
        } finally {
            r.close();
        }
    }

    /** 그 플러그인에 묶인 레지스트리 보기. */
    PluginRegistries registries(PluginDescription d) { return registries.view(d.name(), d.namespace()); }

    /** 플러그인이 꺼지거나 불러오기에 실패했다 — 레지스트리 몫과 수명주기 훅을 정리한다. */
    void forget(String name) {
        registries.removeOwner(name);
        lifecycle.removeOwner(name);
        gameRegistries.deactivateOwner(name);
    }

    /** 불러오기에 실패한 플러그인을 뺀다(정리 + 클래스로더 닫기). */
    void drop(Prepared p) {
        forget(p.description().name());
        prepared.remove(p);
        PluginManagerImpl.closeQuietly(p.loader());
    }

    // ------------------------------------------------------------------ 찾기·순서

    /** 설명 파일을 읽은 후보. */
    record Candidate(Path file, PluginDescription description) {}

    private void run() {
        try {
            Files.createDirectories(dir);
            Files.createDirectories(dir.resolve(BUKKIT_FOLDER));
        } catch (IOException e) {
            log.error("plugins 폴더를 만들 수 없다: {}", dir, e);
            return;
        }
        if (settings.devMode()) {
            log.warn("plugins.dev-mode=true — 플러그인 핫 리로드가 켜졌다. 운영 서버에서는 끄라");
            HotReload.clearShadowDir(dir);
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
        for (Candidate c : order(byName)) prepare(c);
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
    private PluginDescription readDescription(Path jar) throws IOException { return readDescription(jar, false); }

    private PluginDescription readDescription(Path jar, boolean quiet) throws IOException {
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
            if (quiet) return null;
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

    // ------------------------------------------------------------------ 부트스트랩

    private Prepared prepare(Candidate c) {
        PluginDescription d = c.description();
        if (!apiVersionOk(d, c.file())) {
            failures.add(d.name());
            return null;
        }
        List<String> failed = new ArrayList<>();
        for (String dep : d.depend()) if (find(dep) == null) failed.add(dep);
        if (!failed.isEmpty()) {
            log.error("Could not load '{}' in folder '{}': 의존 플러그인 {} 을(를) 불러오지 못했다", dir.relativize(c.file()), dir.getFileName(), failed);
            failures.add(d.name());
            return null;
        }
        PluginManagerImpl.PluginClassLoader loader;
        try {
            loader = settings.devMode()
                    ? new PluginManagerImpl.PluginClassLoader(HotReload.shadowCopy(dir, c.file()), this, true)
                    : new PluginManagerImpl.PluginClassLoader(c.file(), this);
        } catch (IOException e) {
            log.error("Could not load '{}' in folder '{}'", dir.relativize(c.file()), dir.getFileName(), e);
            failures.add(d.name());
            return null;
        }
        java.util.logging.Logger logger = PluginManagerImpl.pluginLogger(d);
        java.util.logging.Level start = settings.levelFor(d.name());
        if (start != null) logger.setLevel(start);
        LifecycleHooks.Registrar hooks = lifecycle.registrar(d.name(), logger);
        Context ctx = new Context(d, c.file(), logger, hooks, registries(d));
        PluginBootstrap boot = null;
        String cls = d.bootstrapper();
        if (cls != null && !cls.isBlank()) {
            try {
                Class<?> type = Class.forName(cls, true, loader);
                if (!PluginBootstrap.class.isAssignableFrom(type)) throw new IllegalArgumentException("bootstrapper 가 PluginBootstrap 을 구현하지 않는다: " + cls);
                boot = (PluginBootstrap) type.getDeclaredConstructor().newInstance();
                logger.info("Bootstrapping " + d.fullName());
                boot.bootstrap(ctx);
            } catch (Throwable t) {
                log.error("Could not bootstrap '{}' in folder '{}'", dir.relativize(c.file()), dir.getFileName(), t);
                failures.add(d.name());
                hooks.close();
                forget(d.name());
                PluginManagerImpl.closeQuietly(loader);
                return null;
            }
        }
        hooks.close();
        Prepared out = new Prepared(d, c.file(), loader, logger, boot, ctx);
        prepared.add(out);
        return out;
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

    // ------------------------------------------------------------------ 핫 리로드(개발 모드)

    /** plugins/ 에서 그 이름의 Furnace 플러그인 jar 를 찾는다(없으면 null). 다른 jar 의 오류는 로그하지 않는다. */
    Candidate findOnDisk(String name) {
        for (Path jar : jars(dir)) {
            PluginDescription d;
            try {
                d = readDescription(jar, true);
            } catch (Exception e) {
                continue;
            }
            if (d != null && d.name().equalsIgnoreCase(name)) return new Candidate(jar, d);
        }
        return null;
    }

    /** 그 jar 파일 하나의 설명(Furnace 플러그인이 아니거나 읽을 수 없으면 null). */
    Candidate findOnDiskFile(Path jar) {
        try {
            PluginDescription d = readDescription(jar, true);
            return d == null ? null : new Candidate(jar, d);
        } catch (Exception e) {
            return null;
        }
    }

    /** jar 를 다시 읽어 부트스트랩까지 한다(실패하면 로그 후 null). 의존 플러그인은 이미 준비돼 있어야 한다. */
    Prepared reprepare(Candidate c) {
        Prepared p = prepare(c);
        if (p != null) reregisterGameRegistries(p);
        return p;
    }

    /** 다시 읽기 전에 빼 둔다: 레지스트리 몫·훅을 정리하고 클래스 찾기 범위에서 뺀다. 클래스로더는 닫지 않는다(호출자가 나중에). */
    void detach(Prepared p) {
        forget(p.description().name());
        prepared.remove(p);
    }

    Prepared find(String name) {
        for (Prepared p : prepared) if (p.description().name().equalsIgnoreCase(name)) return p;
        return null;
    }

    /** 다른 플러그인 jar 에서 클래스를 찾는다(의존 플러그인의 API 클래스를 쓰기 위해 — Bukkit 과 같다). */
    Class<?> findInOthers(String name, PluginManagerImpl.PluginClassLoader asking) {
        for (Prepared p : prepared) {
            if (p.loader() == asking) continue;
            Class<?> c = p.loader().findLocal(name);
            if (c != null) {
                asking.borrowedFrom(p.description().name());   // 핫 리로드 때 함께 다시 읽을 대상
                return c;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ 데이터 팩 찾기 훅

    /** 데이터 팩 찾기 훅을 돌려 플러그인이 알린 팩을 모은다(팩 저장소가 찾을 때마다 부른다). */
    public List<PackRepository.JarPack> discoverPacks() {
        List<PackRepository.JarPack> out = new ArrayList<>();
        lifecycle.fire(LifecycleEvents.DATAPACK_DISCOVERY, owner -> {
            Prepared p = find(owner);
            return new Datapacks(p.description(), p.file(), out);
        });
        return out;
    }

    private record Datapacks(PluginDescription d, Path jar, List<PackRepository.JarPack> out) implements DatapackRegistrar {

        @Override
        public String discover(String pathInJar, String id) { return discover(pathInJar, id, null); }

        @Override
        public String discover(String pathInJar, String id, Text title) {
            if (id == null || !id.matches("[a-z0-9_.-]+")) throw new IllegalArgumentException("잘못된 데이터 팩 id: " + id);
            String prefix = jarPrefix(pathInJar);
            String packId = "plugin/" + d.namespace() + "/" + id;
            out.add(new PackRepository.JarPack(packId, title != null ? title : Text.text(d.name() + "/" + id), jar, prefix));
            return packId;
        }
    }

    /** jar 안 폴더 경로 → "" 또는 "a/b/". '..'·빈 칸은 받지 않는다. */
    static String jarPrefix(String path) {
        if (path == null) throw new NullPointerException("pathInJar");
        String s = path.replace('\\', '/');
        while (s.startsWith("/")) s = s.substring(1);
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (s.isEmpty()) return "";
        for (String seg : s.split("/", -1)) {
            if (seg.isEmpty() || seg.equals(".") || seg.equals("..")) throw new IllegalArgumentException("잘못된 jar 안 경로: " + path);
        }
        return s + "/";
    }

    // ------------------------------------------------------------------ 부트스트랩 환경

    private final class Context implements BootstrapContext {
        private final PluginDescription d;
        private final Path file;
        private final java.util.logging.Logger logger;
        private final LifecycleEventManager hooks;
        private final PluginRegistries regs;

        Context(PluginDescription d, Path file, java.util.logging.Logger logger, LifecycleEventManager hooks, PluginRegistries regs) {
            this.d = d;
            this.file = file;
            this.logger = logger;
            this.hooks = hooks;
            this.regs = regs;
        }

        public PluginDescription description() { return d; }

        public java.util.logging.Logger logger() { return logger; }

        public Path configFolder() { return dir.resolve(d.name()); }

        public Path dataFolder() {
            Path root = settings.dataRoot();
            return root == null ? configFolder() : root.resolve(d.name());
        }

        public Path file() { return file; }

        public LifecycleEventManager lifecycle() { return hooks; }

        public PluginRegistries registries() { return regs; }
    }
}
