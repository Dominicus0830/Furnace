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
        String[] p = v.split("\\.");
        try {
            int major = Integer.parseInt(p[0]);
            int minor = p.length > 1 ? Integer.parseInt(p[1]) : 0;
            int patch = p.length > 2 ? Integer.parseInt(p[2]) : 0;
            boolean tooNew = major > 1 || (major == 1 && (minor > SUPPORTED_MINOR || (minor == SUPPORTED_MINOR && patch > SUPPORTED_PATCH)));
            if (tooNew) throw new IllegalArgumentException("Unsupported API version " + v + " (이 서버는 Bukkit API 1." + SUPPORTED_MINOR + "." + SUPPORTED_PATCH + " 까지 받는다)");
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
        return new org.domi.furnace.compat.nms.NmsClassTransformer(description.name());
    }

    @Override
    public FurnacePlugin create(PluginContext context, Class<?> main) throws ReflectiveOperationException {
        if (!JavaPlugin.class.isAssignableFrom(main)) {
            throw new IllegalArgumentException("main 클래스가 org.bukkit.plugin.java.JavaPlugin 을 상속하지 않는다: " + main.getName());
        }
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
