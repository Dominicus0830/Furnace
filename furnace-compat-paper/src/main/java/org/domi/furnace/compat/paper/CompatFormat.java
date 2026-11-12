// Bukkit·Paper 플러그인 형식(plugin.yml, paper-plugin.yml) 어댑터. 서버의 PluginBootstrapper 가 ServiceLoader 로 찾아 쓴다.
package org.domi.furnace.compat.paper;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.domi.furnace.api.config.ConfigFormat;
import org.domi.furnace.api.config.ConfigSection;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.plugin.PluginContext;
import org.domi.furnace.api.plugin.PluginDescription;
import org.domi.furnace.api.plugin.PluginFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CompatFormat implements PluginFormat {

    private static final Logger LOG = LoggerFactory.getLogger("Furnace");

    /** 이 호환 어댑터가 받아 주는 가장 높은 Bukkit API 버전(마인크래프트 1.21.1). */
    static final int SUPPORTED_MINOR = 21;
    static final int SUPPORTED_PATCH = 1;

    private final Map<String, PluginDescriptionFile> descriptors = new ConcurrentHashMap<>();

    @Override
    public String id() { return "bukkit"; }

    /** Paper 1.21.1 (Wait-for-Async-Tasks-during-shutdown): 끌 때 돌고 있는 비동기 작업을 최대 5초 기다리고, 남으면 "Nag author(s)" 로그. */
    @Override
    public void afterPluginsDisabled(org.domi.furnace.api.Furnace server) {
        // Bukkit 플러그인을 하나라도 만든 서버만 — 설명 파일 없는 jar 때문에 어댑터만 올라온 서버는 Compat(과 따라오는 Craft* 클래스)를 올리지 않는다
        if (createdPlugin) Compat.awaitAsyncTasksAtShutdown(server, SHUTDOWN_ASYNC_WAIT_MILLIS);
    }

    /** 개발 모드 핫 리로드: 끈 Bukkit 플러그인의 비동기 작업을 종료 때와 같은 규칙으로 기다린다(Compat 은 Bukkit 플러그인이 있을 때만 건드린다). */
    @Override
    public void awaitPluginTasks(org.domi.furnace.api.Furnace server, java.util.Collection<FurnacePlugin> plugins, long maxMillis) {
        if (createdPlugin) Compat.awaitPluginAsyncTasks(server, plugins, maxMillis);
    }

    private volatile boolean createdPlugin;

    static final long SHUTDOWN_ASYNC_WAIT_MILLIS = 5_000;

    /** paper-plugin.yml 이 있으면 그것을 먼저 읽는다(Paper 와 같다). */
    @Override
    public List<String> descriptorNames() { return List.of("paper-plugin.yml", "plugin.yml"); }

    @Override
    public PluginDescription readDescriptor(String fileName, String text) {
        Map<String, Object> map;
        try {
            map = ConfigFormat.YAML.parse(text).toMap();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(fileName + " 을 읽을 수 없다: " + e.getMessage(), e);
        }
        if (fileName.equals("paper-plugin.yml")) map = fromPaper(map);
        PluginDescriptionFile pdf;
        try {
            pdf = new PluginDescriptionFile(map);
        } catch (InvalidDescriptionException e) {
            throw new IllegalArgumentException(fileName + ": " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()), e);
        }
        // Paper SpigotPluginProviderFactory.build: 예약 이름과 공백 든 이름은 불러올 때 거부한다(공백은 getName() 에서 '_' 로 바뀌므로 원래 이름으로 본다)
        String rawName = String.valueOf(map.get("name"));
        if (PluginDescription.RESERVED_NAMES.contains(rawName.toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException(fileName + ": Restricted name, cannot use " + rawName + " as a plugin name.");
        if (rawName.indexOf(' ') != -1)
            throw new IllegalArgumentException(fileName + ": Restricted name, cannot use 0x20 (space character) in a plugin name.");
        checkApiVersion(pdf);
        // Furnace 설명으로: api-version 은 Bukkit 의 것이라 서버의 ApiVersion 검사에 넘기지 않고, bootstrapper 는 Furnace 의 PluginBootstrap 이 아니다
        ConfigSection fs = ConfigSection.fromMap(map);
        fs.setLiteral("name", pdf.getName());
        fs.remove("api-version");
        fs.remove("bootstrapper");
        PluginDescription d = PluginDescription.read(fs);
        descriptors.put(key(d.name(), d.main()), pdf);
        return d;
    }

    /** api-version "1.21" 이하만 받는다(Paper 도 서버보다 새 API 를 요구하는 플러그인은 거절한다). 없으면 레거시 — 켤 때 경고한다. */
    static void checkApiVersion(PluginDescriptionFile pdf) {
        String v = pdf.getAPIVersion();
        if (v == null) return;
        if (isNewerThanServerApi(v)) throw new IllegalArgumentException("Unsupported API version " + v + " (이 서버는 Bukkit API 1." + SUPPORTED_MINOR + "." + SUPPORTED_PATCH + " 까지 받는다)");
    }

    /** api-version 문자열("1.21", "1.20.6")이 이 서버의 Bukkit API(1.21.1)보다 새로운가. 읽을 수 없으면 IllegalArgumentException. */
    public static boolean isNewerThanServerApi(String v) {
        String[] p = v.split("\\.");
        try {
            int major = Integer.parseInt(p[0]);
            int minor = p.length > 1 ? Integer.parseInt(p[1]) : 0;
            int patch = p.length > 2 ? Integer.parseInt(p[2]) : 0;
            return major > 1 || (major == 1 && (minor > SUPPORTED_MINOR || (minor == SUPPORTED_MINOR && patch > SUPPORTED_PATCH)));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("api-version '" + v + "' 을 읽을 수 없다");
        }
    }

    /**
     * paper-plugin.yml → plugin.yml 모양. 의존은 {@code dependencies.server.<이름>.{load: BEFORE|AFTER|OMIT, required}} 로 적는다:
     * BEFORE = 그 플러그인이 먼저 켜진다(required 면 depend, 아니면 softdepend), AFTER = 이 플러그인이 먼저(loadbefore), OMIT = 순서 없음.
     * bootstrap 의존·bootstrapper·loader 는 읽지 않고 경고한다.
     */
    static Map<String, Object> fromPaper(Map<String, Object> paper) {
        Map<String, Object> out = new LinkedHashMap<>(paper);
        List<String> depend = new ArrayList<>(), soft = new ArrayList<>(), before = new ArrayList<>();
        Object deps = paper.get("dependencies");
        if (deps instanceof Map<?, ?> dm) {
            if (dm.get("server") instanceof Map<?, ?> server) {
                for (Map.Entry<?, ?> e : server.entrySet()) {
                    String name = String.valueOf(e.getKey());
                    String load = "OMIT";
                    boolean required = true;
                    if (e.getValue() instanceof Map<?, ?> cfg) {
                        if (cfg.get("load") != null) load = String.valueOf(cfg.get("load")).toUpperCase(Locale.ROOT);
                        if (cfg.get("required") != null) required = Boolean.parseBoolean(String.valueOf(cfg.get("required")));
                    }
                    if (load.equals("AFTER")) before.add(name);
                    else if (required) depend.add(name);
                    else soft.add(name);
                }
            }
            if (dm.get("bootstrap") instanceof Map<?, ?> b && !b.isEmpty()) {
                LOG.warn("paper-plugin.yml 의 dependencies.bootstrap 은 읽지 않는다(Furnace 호환 어댑터 1차 범위 밖): {}", b.keySet());
            }
        }
        for (String key : List.of("bootstrapper", "loader")) {
            if (paper.get(key) != null) {
                LOG.warn("paper-plugin.yml 의 '{}' 는 Furnace 호환 어댑터가 실행하지 않는다 — 이 플러그인은 그것에 기대면 제대로 안 돈다", key);
            }
        }
        out.remove("dependencies");
        out.remove("bootstrapper");
        out.remove("loader");
        out.put("depend", depend);
        out.put("softdepend", soft);
        out.put("loadbefore", before);
        return out;
    }

    /** 이름이 같은 jar 가 둘일 때(부트스트랩이 하나를 버린다) 버려진 쪽 설명이 덮어쓰지 않게 main 클래스까지 키로 쓴다. */
    private static String key(String name, String main) { return name.toLowerCase(Locale.ROOT) + "|" + main; }

    /** NMS·CraftBukkit 이름 변환(H6): CraftBukkit 버전 접미사 풀기와 리플렉션 이름 다리. 서버는 plugins.compat.nms.enabled=false 면 묻지 않는다. */
    @Override
    public org.domi.furnace.api.plugin.ClassTransformer transformer(PluginDescription description, java.nio.file.Path jar) {
        return transformer(description, jar, Map.of());
    }

    /**
     * H7a: 위에 더해 jar 를 한 번 훑어 NMS 호환 보고를 남기고, shim 에 없는 심볼 호출을 그 자리의 UnsupportedNmsCall 로 바꾼다.
     * {@code plugins.compat.nms.strict=true} 이고 미지원 심볼이 있으면 던진다(플러그인을 불러오지 않는다).
     */
    @Override
    public org.domi.furnace.api.plugin.ClassTransformer transformer(PluginDescription description, java.nio.file.Path jar, Map<String, String> settings) {
        return transformer(description, jar, settings, null);
    }

    /**
     * 위와 같고, Spigot 이름 대응표 파일은 서버가 미리 주지 않는다: 이 jar 가 대응표를 필요로 한다고(Spigot 이름 jar 이고 NMS 이름을 담음) jar 를
     * 훑으면서 확인된 때만 {@code spigotMappingFiles} 로 서버에 물어 세 파일 경로(아래 {@link #SPIGOT_MAPPING_FILES} 형식)를 받는다 — 서버가 필요 여부를
     * 알아보려고 jar 를 또 읽지 않고, 필요 없는 플러그인에는 대응표를 받지도 않는다. null·빈 값이면 바꾸지 않는다.
     */
    @Override
    public org.domi.furnace.api.plugin.ClassTransformer transformer(PluginDescription description, java.nio.file.Path jar, Map<String, String> settings,
                                                                     java.util.function.Supplier<String> spigotMappingFiles) {
        var options = new org.domi.furnace.compat.nms.NmsClassTransformer.Options(
                Boolean.parseBoolean(settings.getOrDefault("plugins.compat.nms.strict", "false")),
                "versioned".equalsIgnoreCase(settings.getOrDefault("plugins.compat.nms.version-package", "plain")),
                spigotMappings(description.name(), settings.get(SPIGOT_MAPPING_FILES)));
        return org.domi.furnace.compat.nms.NmsClassTransformer.forJar(description.name(), jar, options,
                spigotMappingFiles == null ? null : () -> spigotMappings(description.name(), spigotMappingFiles.get()));
    }

    /**
     * H7d: 서버가 이 jar 에 Spigot 이름 대응표를 쓰기로 했으면(plugins.compat.nms.spigot-mappings=download 이고 준비됨) 세 파일 경로를
     * 경로 구분자({@link java.io.File#pathSeparator})로 이어 이 키에 넣는다: Mojang server.txt, Spigot 클래스 csrg, Spigot 멤버 csrg.
     */
    public static final String SPIGOT_MAPPING_FILES = "plugins.compat.nms.spigot-mappings.files";

    private static org.domi.furnace.compat.nms.SpigotMappings spigotMappings(String plugin, String files) {
        if (files == null || files.isBlank()) return null;
        String[] p = files.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator));
        if (p.length != 3) {
            LOG.warn("플러그인 {}: {} 값은 파일 세 개(Mojang server.txt, Spigot 클래스 csrg, 멤버 csrg)여야 한다 — Spigot 이름을 바꾸지 않는다", plugin, SPIGOT_MAPPING_FILES);
            return null;
        }
        try {
            return org.domi.furnace.compat.nms.SpigotMappings.load(java.nio.file.Path.of(p[0].strip()), java.nio.file.Path.of(p[1].strip()), java.nio.file.Path.of(p[2].strip()));
        } catch (java.io.IOException | RuntimeException e) {
            LOG.warn("플러그인 {}: Spigot 이름 대응표를 읽지 못했다({}) — Spigot 이름을 바꾸지 않는다(그 NMS 호출은 실패한다)", plugin, e.toString());
            return null;
        }
    }

    @Override
    public FurnacePlugin create(PluginContext context, Class<?> main) throws ReflectiveOperationException {
        if (!JavaPlugin.class.isAssignableFrom(main)) {
            throw new IllegalArgumentException("main 클래스가 org.bukkit.plugin.java.JavaPlugin 을 상속하지 않는다: " + main.getName());
        }
        createdPlugin = true;
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
        PluginDescription d = context.description();
        PluginDescriptionFile pdf = descriptors.get(key(d.name(), d.main()));
        if (pdf == null) pdf = new PluginDescriptionFile(d.name(), d.version(), d.main());
        BukkitPluginAdapter adapter = new BukkitPluginAdapter(pdf);
        PluginLoaders.bind(context.classLoader(), adapter);
        PluginInit.expect(new PluginInit(adapter, pdf, context.logger(), context.dataFolder().toFile(), context.file().toFile(), context.classLoader()));
        try {
            Constructor<?> c = main.getDeclaredConstructor();
            c.setAccessible(true);
            adapter.attach((JavaPlugin) c.newInstance());
        } catch (ReflectiveOperationException | RuntimeException | Error e) {
            PluginLoaders.unbind(adapter);   // 생성자가 터진 플러그인은 서버에 올라가지 않는다 — 묶임을 남기지 않는다
            throw e;
        } finally {
            PluginInit.clear();
        }
        return adapter;
    }
}
