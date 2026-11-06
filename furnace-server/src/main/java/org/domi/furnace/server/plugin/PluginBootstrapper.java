// 플러그인 부트스트랩 단계: plugins/*.jar 를 훑어 설명 파일을 읽고 의존 순서를 정한 뒤, 플러그인마다 클래스로더를 만들고
// 설명 파일의 bootstrapper 를 부른다. 서버는 이것을 블록·아이템·동기화 레지스트리와 데이터 팩을 읽기 "전"에 돌린다
// (Paper 부트스트랩과 같은 자리). 메인 클래스 인스턴스·onLoad 는 그 뒤 PluginManagerImpl.loadAll 이 이어서 한다.
// furnace-plugin.(yml|yaml|toml|json) 을 가진 jar 만 Furnace 플러그인이다. Paper/Bukkit 플러그인은 plugins/bukkit-plugins/ 에 둔다.
package org.domi.furnace.server.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
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
    private final PluginRegistryStore registries = new PluginRegistryStore();
    private final LifecycleHooks lifecycle = new LifecycleHooks();
    /** 부트스트랩을 마친 플러그인(의존 순서). 클래스로더끼리 서로 찾는 범위이기도 하다. */
    private final List<Prepared> prepared = new CopyOnWriteArrayList<>();

    /** 부트스트랩을 마친 플러그인 하나. bootstrap 은 bootstrapper 가 없으면 null. */
    record Prepared(PluginDescription description, Path file, PluginManagerImpl.PluginClassLoader loader, java.util.logging.Logger logger,
                    PluginBootstrap bootstrap, BootstrapContext context) {}

    private PluginBootstrapper(Path dir) {
        this.dir = dir;
    }

    /** plugins/ 를 훑어 의존 순서대로 부트스트랩까지 한다. 실패한 플러그인은 로그를 남기고 건너뛴다. */
    public static PluginBootstrapper bootstrap(Path dir) {
        PluginBootstrapper b = new PluginBootstrapper(dir);
        b.run();
        return b;
    }

    public Path directory() { return dir; }

    List<Prepared> prepared() { return prepared; }

    PluginRegistryStore registryStore() { return registries; }

    LifecycleHooks lifecycle() { return lifecycle; }

    /** 그 플러그인에 묶인 레지스트리 보기. */
    PluginRegistries registries(PluginDescription d) { return registries.view(d.name(), d.namespace()); }

    /** 플러그인이 꺼지거나 불러오기에 실패했다 — 레지스트리 몫과 수명주기 훅을 정리한다. */
    void forget(String name) {
        registries.removeOwner(name);
        lifecycle.removeOwner(name);
    }

    /** 불러오기에 실패한 플러그인을 뺀다(정리 + 클래스로더 닫기). */
    void drop(Prepared p) {
        forget(p.description().name());
        prepared.remove(p);
        PluginManagerImpl.closeQuietly(p.loader());
    }

    // ------------------------------------------------------------------ 찾기·순서

    /** 설명 파일을 읽은 후보. */
    private record Candidate(Path file, PluginDescription description) {}

    private void run() {
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
                continue;
            }
            if (d == null) continue;
            Candidate prev = byName.putIfAbsent(d.name(), new Candidate(jar, d));
            if (prev != null) {
                log.error("Ambiguous plugin name `{}' for files `{}' and `{}' in `{}'", d.name(), prev.file(), jar, dir.getFileName());
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
                }
                break;
            }
            out.add(remaining.remove(pick));
            done.add(pick);
        }
        return out;
    }

    // ------------------------------------------------------------------ 부트스트랩

    private void prepare(Candidate c) {
        PluginDescription d = c.description();
        List<String> failed = new ArrayList<>();
        for (String dep : d.depend()) if (find(dep) == null) failed.add(dep);
        if (!failed.isEmpty()) {
            log.error("Could not load '{}' in folder '{}': 의존 플러그인 {} 을(를) 불러오지 못했다", dir.relativize(c.file()), dir.getFileName(), failed);
            return;
        }
        PluginManagerImpl.PluginClassLoader loader;
        try {
            loader = new PluginManagerImpl.PluginClassLoader(c.file(), this);
        } catch (MalformedURLException e) {
            log.error("Could not load '{}' in folder '{}'", dir.relativize(c.file()), dir.getFileName(), e);
            return;
        }
        java.util.logging.Logger logger = PluginManagerImpl.pluginLogger(d);
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
                hooks.close();
                forget(d.name());
                PluginManagerImpl.closeQuietly(loader);
                return;
            }
        }
        hooks.close();
        prepared.add(new Prepared(d, c.file(), loader, logger, boot, ctx));
    }

    private Prepared find(String name) {
        for (Prepared p : prepared) if (p.description().name().equalsIgnoreCase(name)) return p;
        return null;
    }

    /** 다른 플러그인 jar 에서 클래스를 찾는다(의존 플러그인의 API 클래스를 쓰기 위해 — Bukkit 과 같다). */
    Class<?> findInOthers(String name, PluginManagerImpl.PluginClassLoader asking) {
        for (Prepared p : prepared) {
            if (p.loader() == asking) continue;
            Class<?> c = p.loader().findLocal(name);
            if (c != null) return c;
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

        public Path dataFolder() { return dir.resolve(d.name()); }

        public Path file() { return file; }

        public LifecycleEventManager lifecycle() { return hooks; }

        public PluginRegistries registries() { return regs; }
    }
}
