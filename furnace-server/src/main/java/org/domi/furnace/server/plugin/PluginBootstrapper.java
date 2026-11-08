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
import org.domi.furnace.api.plugin.PluginFormat;
import org.domi.furnace.api.plugin.bootstrap.BootstrapContext;
import org.domi.furnace.api.plugin.bootstrap.PluginBootstrap;
import org.domi.furnace.api.plugin.lifecycle.DatapackRegistrar;
import org.domi.furnace.api.plugin.lifecycle.LifecycleEventManager;
import org.domi.furnace.api.plugin.lifecycle.LifecycleEvents;
import org.domi.furnace.api.registry.PluginRegistries;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.data.PackRepository;
import org.domi.furnace.server.plugin.library.LibraryResolver;
import org.domi.furnace.server.plugin.library.LibrarySettings;
import org.domi.furnace.server.plugin.library.ServerLibraries;
import org.domi.furnace.server.plugin.library.SharedLibraryLoader;
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
    /** 다른 서버 소프트웨어 플러그인 형식 어댑터(예: Bukkit plugin.yml). 호환 모듈이 없으면 비어 있다. */
    private final List<PluginFormat> formats;
    /** 찾기·부트스트랩에서 실패한 플러그인(또는 jar) 이름 — plugins.fail-fast 의 근거. */
    private final List<String> failures = new CopyOnWriteArrayList<>();
    private final PluginRegistryStore registries = new PluginRegistryStore();
    private final LifecycleHooks lifecycle = new LifecycleHooks();
    private final GameRegistryStore gameRegistries = new GameRegistryStore();
    private final GenerationHookStore generationHooks = new GenerationHookStore();
    private volatile boolean generationHooksCollected;
    /** 같은 훅의 데이터 주도 몫(몹 변종·그림·마법부여·태그 더하기). 항목은 시작 때 한 번 모아 데이터 팩과 함께 읽힌다. */
    private final GameRegistryHooks dataRegistries = new GameRegistryHooks();
    private final List<org.domi.furnace.server.data.DynamicRegistries.Contribution> dataEntries = new CopyOnWriteArrayList<>();
    /** 부트스트랩을 마친 플러그인(의존 순서). 클래스로더끼리 서로 찾는 범위이기도 하다. */
    private final List<Prepared> prepared = new CopyOnWriteArrayList<>();

    /** 부트스트랩을 마친 플러그인 하나. bootstrap 은 bootstrapper 가 없으면 null. */
    record Prepared(PluginDescription description, Path file, PluginManagerImpl.PluginClassLoader loader, java.util.logging.Logger logger,
                    PluginBootstrap bootstrap, BootstrapContext context, PluginFormat format) {}

    /** 호환 플러그인용 서버 제공 라이브러리 목록(시험은 바꿔 넣는다). */
    private final ServerLibraries serverLibraries;

    private PluginBootstrapper(Path dir, PluginSettings settings, List<PluginFormat> formats, ServerLibraries serverLibraries) {
        this.dir = dir;
        this.settings = settings;
        this.formats = List.copyOf(formats);
        this.serverLibraries = serverLibraries;
    }

    /** plugins/ 를 훑어 의존 순서대로 부트스트랩까지 한다. 실패한 플러그인은 로그를 남기고 건너뛴다. */
    public static PluginBootstrapper bootstrap(Path dir) { return bootstrap(dir, PluginSettings.DEFAULT); }

    public static PluginBootstrapper bootstrap(Path dir, PluginSettings settings) {
        return bootstrap(dir, settings, settings.compat() ? loadFormats() : List.of());
    }

    /** 형식 어댑터를 직접 정해 시작한다(시험용). */
    public static PluginBootstrapper bootstrap(Path dir, PluginSettings settings, List<PluginFormat> formats) {
        return bootstrap(dir, settings, formats, ServerLibraries.DEFAULT);
    }

    /** 시험용: 서버 제공 라이브러리 목록을 바꿔 넣는다. */
    static PluginBootstrapper bootstrap(Path dir, PluginSettings settings, List<PluginFormat> formats, ServerLibraries serverLibraries) {
        PluginBootstrapper b = new PluginBootstrapper(dir, settings, formats, serverLibraries);
        b.run();
        return b;
    }

    /** 클래스패스의 호환 모듈이 ServiceLoader 로 알린 형식 어댑터. 하나가 깨져도 나머지는 쓴다. */
    static List<PluginFormat> loadFormats() {
        List<PluginFormat> out = new ArrayList<>();
        var it = java.util.ServiceLoader.load(PluginFormat.class, PluginBootstrapper.class.getClassLoader()).iterator();
        while (true) {
            try {
                if (!it.hasNext()) break;
                out.add(it.next());
            } catch (java.util.ServiceConfigurationError e) {
                log.error("플러그인 형식 어댑터를 불러오지 못했다", e);
            }
        }
        return out;
    }

    /** 찾기·부트스트랩에서 실패한 이름들. */
    List<String> failures() { return failures; }

    PluginSettings settings() { return settings; }

    public Path directory() { return dir; }

    List<Prepared> prepared() { return prepared; }

    PluginRegistryStore registryStore() { return registries; }

    LifecycleHooks lifecycle() { return lifecycle; }

    public GameRegistryStore gameRegistries() { return gameRegistries; }

    /** 플러그인 월드젠 단계 훅(W3). */
    public GenerationHookStore generationHooks() { return generationHooks; }

    /** 월드젠 훅(WORLD_GENERATION)을 돌린다 — 서버 시작 때 게임 레지스트리 다음, 월드를 만들기 전에 한 번. */
    public void collectGenerationHooks() {
        List<GenerationHookStore.Registrar> opened = new ArrayList<>();
        lifecycle.fire(LifecycleEvents.WORLD_GENERATION, owner -> {
            Prepared p = find(owner);
            GenerationHookStore.Registrar r = generationHooks.registrar(owner, p.description().namespace(), p.logger());
            opened.add(r);
            return r;
        });
        opened.forEach(GenerationHookStore.Registrar::close);
        generationHooksCollected = true;
    }

    /** 핫 리로드로 다시 부트스트랩한 플러그인 하나의 월드젠 훅을 돌린다(같은 키를 새 훅으로). */
    private void reregisterGenerationHooks(Prepared p) {
        if (!generationHooksCollected) return;
        GenerationHookStore.Registrar r = generationHooks.registrar(p.description().name(), p.description().namespace(), p.logger());
        try {
            lifecycle.fireOwner(p.description().name(), LifecycleEvents.WORLD_GENERATION, r);
        } finally {
            r.close();
        }
    }

    /**
     * 게임 레지스트리 훅(GAME_REGISTRIES)을 돌려 플러그인 바이옴·차원 타입을 모으고, base(바닐라) 뒤에 붙인 전송용 세트를 돌려 준다.
     * 서버 시작 때 한 번 — 부른 뒤 저장소는 고정되어 새 키는 더할 수 없다.
     */
    public org.domi.furnace.server.net.registry.RegistrySet collectGameRegistries(org.domi.furnace.server.net.registry.RegistrySet base) {
        gameRegistries.begin(base);
        List<GameRegistryStore.Registrar> opened = new ArrayList<>();
        lifecycle.fire(LifecycleEvents.GAME_REGISTRIES, owner -> {
            Prepared p = find(owner);
            GameRegistryStore.Registrar r = gameRegistries.registrar(owner, p.description().namespace(), p.logger())
                    .data(dataRegistries.registrar(p.description(), dataEntries));
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
        // 데이터 주도 항목은 시작 때 고정이라 버리고(번호가 이미 정해졌다) 태그 더하기만 다시 받는다
        GameRegistryStore.Registrar r = gameRegistries.registrar(p.description().name(), p.description().namespace(), p.logger())
                .data(dataRegistries.registrar(p.description(), new ArrayList<>()));
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
        generationHooks.removeOwner(name);   // 그 월드의 새 청크는 바닐라로(월드가 플러그인 클래스를 쥐지 않게)
        dataRegistries.removeOwner(name);   // 태그 더하기만 빠진다 — 레지스트리 항목은 다음 시작까지 남는다
    }

    /** 불러오기에 실패한 플러그인을 뺀다(정리 + 클래스로더 닫기). */
    void drop(Prepared p) {
        forget(p.description().name());
        prepared.remove(p);
        PluginManagerImpl.closeQuietly(p.loader());
    }

    // ------------------------------------------------------------------ 찾기·순서

    /** 설명 파일을 읽은 후보. */
    record Candidate(Path file, PluginDescription description, PluginFormat format) {}

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
        if (bukkit > 0 && formats.isEmpty()) {
            log.warn("plugins/{}/ 에 Paper/Bukkit 플러그인 {}개가 있다 — 호환 어댑터가 {} 불러오지 않는다", BUKKIT_FOLDER, bukkit,
                    settings.compat() ? "클래스패스에 없어" : "꺼져(plugins.compat.bukkit=false) 있어");
        }
        Map<String, Candidate> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Path jar : scanJars()) {
            Candidate c;
            try {
                c = readCandidate(jar, false);
            } catch (Exception e) {
                log.error("Could not load '{}' in folder '{}'", dir.relativize(jar), dir.getFileName(), e);
                failures.add(jar.getFileName().toString());
                continue;
            }
            if (c == null) {
                failures.add(jar.getFileName().toString());
                continue;
            }
            PluginDescription d = c.description();
            Candidate prev = byName.putIfAbsent(d.name(), c);
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

    /** 찾을 jar: plugins/ 의 jar, 형식 어댑터가 있으면 plugins/bukkit-plugins/ 의 jar 도(이름 순). */
    private List<Path> scanJars() {
        List<Path> out = new ArrayList<>(jars(dir));
        if (!formats.isEmpty()) out.addAll(jars(dir.resolve(BUKKIT_FOLDER)));
        return out;
    }

    /** jar 하나의 후보. Furnace 설명 파일이 먼저, 없으면 형식 어댑터 순서대로. 없으면(안내 로그 후) null. */
    private Candidate readCandidate(Path jar, boolean quiet) throws IOException {
        try (JarFile jf = new JarFile(jar.toFile())) {
            for (String n : DESCRIPTOR_NAMES) {
                var entry = jf.getEntry(n);
                if (entry == null) continue;
                try (InputStream in = jf.getInputStream(entry)) {
                    ConfigFormat f = ConfigFormat.fromFileName(n).orElse(ConfigFormat.YAML);
                    ConfigSection s = f.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    return new Candidate(jar, PluginDescription.read(s), null);
                }
            }
            for (PluginFormat format : formats) {
                for (String n : format.descriptorNames()) {
                    var entry = jf.getEntry(n);
                    if (entry == null) continue;
                    try (InputStream in = jf.getInputStream(entry)) {
                        String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                        return new Candidate(jar, format.readDescriptor(n, text), format);
                    } catch (RuntimeException e) {
                        if (quiet) return null;
                        throw e;
                    }
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
        if (c.format() == null && !apiVersionOk(d, c.file())) {
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
        List<Path> libraries = resolveLibraries(c);
        if (libraries == null) {
            failures.add(d.name());
            return null;
        }
        PluginManagerImpl.PluginClassLoader loader;
        try {
            Path jar = settings.devMode() ? HotReload.shadowCopy(dir, c.file()) : c.file();
            org.domi.furnace.api.plugin.ClassTransformer transformer = c.format() != null && settings.compatNms() ? c.format().transformer(d, jar) : null;
            ClassLoader parent = c.format() != null ? sharedLibrariesFor(c) : null;
            loader = new PluginManagerImpl.PluginClassLoader(jar, this, settings.devMode(), libraries, transformer, parent);
        } catch (IOException | RuntimeException e) {
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
        String cls = c.format() == null ? d.bootstrapper() : null;   // 다른 형식의 bootstrapper 는 Furnace PluginBootstrap 이 아니다
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
        Prepared out = new Prepared(d, c.file(), loader, logger, boot, ctx, c.format());
        prepared.add(out);
        return out;
    }

    /** 라이브러리 해석기(plugins.libraries.enabled=true 일 때 처음 필요한 순간에 만든다). */
    private volatile LibraryResolver libraryResolver;

    /**
     * 설명 파일의 libraries 를 받아 jar 경로를 돌려 준다(없거나 꺼져 있으면 빈 목록). 풀지 못하면 로그를 남기고 null — 그 플러그인은 불러오지 않는다.
     */
    private List<Path> resolveLibraries(Candidate c) {
        PluginDescription d = c.description();
        if (d.libraries().isEmpty()) return List.of();
        LibrarySettings ls = settings.libraries();
        // Paper·Spigot 은 plugin.yml 의 libraries 를 기본으로 받는다 — 호환 플러그인은 plugins.compat.libraries.declared(기본 켜짐)를 따른다
        boolean on = c.format() != null ? ls.enabled() || ls.compatDeclared() : ls.enabled();
        if (!on) {
            log.warn("Plugin {} 이 라이브러리 {}개({})를 요구하지만 {} 라 받지 않는다 — 켜거나, 라이브러리를 플러그인 jar 에 넣어라",
                    d.fullName(), d.libraries().size(), d.libraries().stream().map(PluginDescription.Library::coordinate).toList(),
                    c.format() != null ? "plugins.compat.libraries.declared=false" : "plugins.libraries.enabled=false");
            return List.of();
        }
        try {
            List<Path> jars = resolver().resolve(d.name(), d.libraries());
            log.info("Plugin {}: 라이브러리 jar {}개를 클래스패스에 붙인다", d.fullName(), jars.size());
            return jars;
        } catch (LibraryResolver.LibraryException e) {
            log.error("Could not load '{}' in folder '{}': 라이브러리를 준비하지 못했다 — {}", dir.relativize(c.file()), dir.getFileName(), e.getMessage());
            return null;
        }
    }

    private LibraryResolver resolver() {
        LibraryResolver r = libraryResolver;
        if (r == null) {
            synchronized (this) {
                r = libraryResolver;
                if (r == null) {
                    LibrarySettings ls = settings.libraries();
                    r = new LibraryResolver(ls, ls.cache() != null ? ls.cache() : dir.resolve(LibrarySettings.DEFAULT_CACHE_DIR), log);
                    libraryResolver = r;
                }
            }
        }
        return r;
    }

    // ------------------------------------------------------------------ 서버 제공 라이브러리(호환 플러그인, H4 묶음 B)

    /** 호환 플러그인들의 부모 로더(처음 필요할 때 만든다). */
    private SharedLibraryLoader sharedLoader;
    /** 준비를 시도한 묶음(성공·실패 모두 — 실패를 플러그인마다 다시 시도해 경고를 되풀이하지 않는다). */
    private final Set<String> sharedTried = new HashSet<>();
    /** 붙인 묶음. */
    private final Set<String> sharedReady = new java.util.LinkedHashSet<>();
    private boolean sharedOffWarned;

    /** 붙인 서버 제공 라이브러리 묶음 이름(목록 순서가 아니라 붙인 순서). */
    synchronized Set<String> sharedLibraryGroups() { return Set.copyOf(sharedReady); }

    /** 공유 로더(아직 없으면 null) — 시험·진단용. */
    synchronized ClassLoader sharedLibraryLoader() { return sharedLoader; }

    /**
     * 호환 플러그인의 부모 로더. Paper 가 서버 jar 에 싣는 라이브러리 중 이 플러그인이 쓰는 묶음(needed) 또는 전부(all)를 받아 공유 로더에 붙이고 그 로더를
     * 돌려 준다. off 면 null(서버 클래스로더). 준비하지 못한 묶음은 경고만 하고 플러그인은 그대로 올린다 — 그 클래스를 쓰는 순간 NoClassDefFoundError 가 난다.
     */
    private synchronized ClassLoader sharedLibrariesFor(Candidate c) {
        LibrarySettings ls = settings.libraries();
        if (ls.compat() == LibrarySettings.CompatMode.OFF) {
            if (!sharedOffWarned) {
                sharedOffWarned = true;
                log.warn("plugins.compat.libraries=off — Paper 가 싣고 있는 라이브러리(Guava·Adventure·SnakeYAML·Brigadier 등)를 주지 않는다. "
                        + "그것을 쓰는 Paper/Bukkit 플러그인은 NoClassDefFoundError 로 실패한다");
            }
            return null;
        }
        if (sharedLoader == null) sharedLoader = new SharedLibraryLoader(PluginHost.class.getClassLoader());
        java.util.Collection<String> want;
        if (ls.compat() == LibrarySettings.CompatMode.ALL) {
            want = serverLibraries.groups().stream().map(ServerLibraries.Group::id).toList();
        } else {
            try {
                want = serverLibraries.scan(c.file(), sharedTried);
            } catch (IOException e) {
                log.warn("Plugin {} 의 jar 를 훑지 못해 서버 제공 라이브러리 판단을 건너뛴다: {}", c.description().fullName(), e.getMessage());
                return sharedLoader;
            }
        }
        List<ServerLibraries.Group> todo = new ArrayList<>();
        for (ServerLibraries.Group g : serverLibraries.closure(want)) if (!sharedTried.contains(g.id())) todo.add(g);
        if (todo.isEmpty()) return sharedLoader;
        log.info("Paper/Bukkit 플러그인을 위해 서버 제공 라이브러리 {} 을(를) 준비한다(처음 {} 이 요구, plugins.compat.libraries={})",
                todo.stream().map(ServerLibraries.Group::id).toList(), c.description().fullName(), ls.compat().name().toLowerCase(Locale.ROOT));
        List<String> unreachable = new ArrayList<>();
        for (ServerLibraries.Group g : todo) {
            sharedTried.add(g.id());
            if (g.mojang() && !ls.compatMojang()) {
                log.warn("서버 제공 라이브러리 {}({})는 Mojang 저장소에만 있는데 plugins.compat.libraries.mojang=false 라 받지 않는다 — "
                        + "그것을 쓰는 Paper/Bukkit 플러그인(처음: {})은 NoClassDefFoundError 로 실패할 수 있다", g.id(), g.description(), c.description().fullName());
                continue;
            }
            List<Path> jars = new ArrayList<>();
            ServerLibraries.Artifact failed = null;
            String reason = null;
            boolean skipped = false;
            for (ServerLibraries.Artifact a : g.artifacts()) {
                try {
                    jars.add(resolver().ensurePinned(a));
                } catch (LibraryResolver.Unreachable e) {
                    skipped = true;
                    break;
                } catch (LibraryResolver.LibraryException e) {
                    failed = a;
                    reason = e.getMessage();
                    break;
                }
            }
            if (skipped) {
                unreachable.add(g.id());
                continue;
            }
            if (failed != null) {
                LibraryResolver r = resolver();
                log.warn("서버 제공 라이브러리 {}({})를 준비하지 못했다 — {}: {}. 그것을 쓰는 Paper/Bukkit 플러그인(처음: {})은 NoClassDefFoundError 로 실패할 수 있다. "
                                + "인터넷이 없는 서버라면 {} 의 파일을 {} 에 두라(sha256 {} 인 파일만 쓴다)",
                        g.id(), g.description(), failed.coordinate(), reason, c.description().fullName(), r.sourceOf(failed), r.cachedPath(failed), failed.sha256());
                continue;
            }
            for (Path j : jars) sharedLoader.add(j);
            sharedReady.add(g.id());
        }
        if (!unreachable.isEmpty()) {
            log.warn("저장소에 이번 시작에서 이미 닿지 못해 서버 제공 라이브러리 {} 도 받지 않았다(묶음마다 제한 시간을 기다리지 않는다) — "
                    + "인터넷이 없는 서버라면 jar 를 {} 아래에 두고 plugins.libraries.offline=true 로 켜라", unreachable, resolver().cacheRoot());
        }
        return sharedLoader;
    }

    /** 서버 종료 — 공유 로더를 닫는다(호환 플러그인 로더를 다 닫은 뒤). */
    synchronized void closeSharedLibraries() {
        if (sharedLoader == null) return;
        try {
            sharedLoader.close();
        } catch (IOException e) {
            log.debug("서버 제공 라이브러리 로더를 닫지 못했다", e);
        }
        sharedLoader = null;
        sharedTried.clear();   // 같은 부트스트래퍼를 다시 쓰면 새 로더에 다시 붙인다
        sharedReady.clear();
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

    /** plugins/ 에서 그 이름의 플러그인 jar 를 찾는다(없으면 null). 다른 jar 의 오류는 로그하지 않는다. */
    Candidate findOnDisk(String name) {
        for (Path jar : scanJars()) {
            Candidate c;
            try {
                c = readCandidate(jar, true);
            } catch (Exception e) {
                continue;
            }
            if (c != null && c.description().name().equalsIgnoreCase(name)) return c;
        }
        return null;
    }

    /** 그 jar 파일 하나의 후보(플러그인이 아니거나 읽을 수 없으면 null). */
    Candidate findOnDiskFile(Path jar) {
        try {
            return readCandidate(jar, true);
        } catch (Exception e) {
            return null;
        }
    }

    /** jar 를 다시 읽어 부트스트랩까지 한다(실패하면 로그 후 null). 의존 플러그인은 이미 준비돼 있어야 한다. */
    Prepared reprepare(Candidate c) {
        Prepared p = prepare(c);
        if (p != null) {
            reregisterGameRegistries(p);
            reregisterGenerationHooks(p);
        }
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

    // ------------------------------------------------------------------ 게임 레지스트리 훅

    /** collectGameRegistries 가 훅을 돌릴 때 플러그인이 더한 데이터 주도 항목(아직 돌지 않았으면 빈 목록). */
    public List<org.domi.furnace.server.data.DynamicRegistries.Contribution> gameRegistryEntries() { return List.copyOf(dataEntries); }

    /** 지금 남아 있는 플러그인 태그 더하기(데이터를 읽을 때마다 부른다). */
    public List<org.domi.furnace.server.data.DataPackManager.TagAddition> tagAdditions() { return dataRegistries.tagAdditions(); }

    /** 데이터 팩 매니저에 넘길 플러그인 훅 묶음. */
    public org.domi.furnace.server.data.DataPackManager.PluginHooks dataPackHooks() {
        return new org.domi.furnace.server.data.DataPackManager.PluginHooks(this::discoverPacks, this::gameRegistryEntries, this::tagAdditions);
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
