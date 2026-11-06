// Furnace 플러그인의 바탕 클래스(Bukkit JavaPlugin 에 해당). 메인 클래스는 이것을 상속하고 인자 없는 생성자를 가진다.
// 수명: 생성 → onLoad(월드 전, 모든 플러그인 로드 직후) → onEnable(의존 순서대로) → … → onDisable(역순).
package org.domi.furnace.api.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.config.Config;
import org.domi.furnace.api.config.ConfigFormat;

public abstract class FurnacePlugin {

    private volatile PluginContext context;
    private volatile boolean enabled;
    private volatile PluginTasks tasks;
    private Config config;
    private final java.util.ArrayDeque<AutoCloseable> owned = new java.util.ArrayDeque<>();
    private boolean closing;

    /** 로더 전용 — 한 번만 부를 수 있다. */
    public final void init(PluginContext ctx) {
        if (context != null) throw new IllegalStateException("이미 초기화된 플러그인: " + ctx.description().name());
        this.context = ctx;
        this.tasks = new PluginTasks(this, ctx.server().scheduler(), ctx.server().asyncExecutor(), ctx.server().profiler());
    }

    /** 로더 전용 — 켜짐 상태를 바꾸고 수명 함수를 부른다. */
    public final void setEnabled(boolean v) {
        if (enabled == v) return;
        if (v) {
            synchronized (owned) { closing = false; }
            enabled = true;
            onEnable();
        } else {
            try {
                onDisable();
            } finally {
                enabled = false;
                tasks.cancelAll();
                synchronized (owned) { closing = true; }
                closeOwned();
            }
        }
    }

    public void onLoad() {}

    public void onEnable() {}

    public void onDisable() {}

    public final boolean isEnabled() { return enabled; }

    public final PluginDescription description() { return context.description(); }

    public final String name() { return context.description().name(); }

    public final Furnace server() { return context.server(); }

    public final Logger logger() { return context.logger(); }

    public final PluginTasks tasks() { return tasks; }

    /** 플러그인끼리 공유하는 정의 레지스트리(이 플러그인 소유로 기록된다 — 꺼지면 정리). */
    public final org.domi.furnace.api.registry.PluginRegistries registries() { return context.registries(); }

    /** 데이터 폴더(없으면 만든다). 기본은 plugins/<이름>/ — 서버 설정 plugins.data-root 가 있으면 거기 아래로 갈라진다. */
    public final Path dataFolder() { return ensure(context.dataFolder()); }

    /** 설정 폴더 plugins/<이름>/ (없으면 만든다). config.yml 같은 관리자용 파일이 있는 곳. */
    public final Path configFolder() { return ensure(context.configFolder()); }

    private static Path ensure(Path p) {
        try {
            Files.createDirectories(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return p;
    }

    /**
     * 이 플러그인이 꺼질 때 자동으로 닫을 자원(열린 메뉴·핸들 등)을 맡긴다. onDisable 뒤 등록 역순으로 닫히고,
     * 이미 꺼진 뒤에 맡기면 바로 닫는다. 닫다가 난 예외는 로그만 남기고 나머지를 계속 닫는다.
     */
    public final <T extends AutoCloseable> T own(T resource) {
        boolean closeNow;
        synchronized (owned) {
            closeNow = closing;
            if (!closeNow) owned.push(resource);
        }
        if (closeNow) closeQuiet(resource);
        return resource;
    }

    private void closeOwned() {
        java.util.List<AutoCloseable> list;
        synchronized (owned) {
            list = new java.util.ArrayList<>(owned);   // ArrayDeque.push 는 앞에 넣으므로 이미 등록 역순
            owned.clear();
        }
        for (AutoCloseable c : list) closeQuiet(c);
    }

    private void closeQuiet(AutoCloseable c) {
        try {
            c.close();
        } catch (Throwable t) {
            logger().log(java.util.logging.Level.WARNING, "자원을 닫다가 예외", t);
        }
    }

    /** jar 안의 리소스(없으면 null). */
    public final InputStream resource(String name) {
        return context.classLoader().getResourceAsStream(name);
    }

    /** jar 안의 리소스를 데이터 폴더로 복사(replace 가 false 면 이미 있을 때 그대로 둔다). */
    public final void saveResource(String name, boolean replace) {
        Path target = dataFolder().resolve(name);
        if (!replace && Files.exists(target)) return;
        try (InputStream in = resource(name)) {
            if (in == null) throw new IllegalArgumentException("jar 에 리소스가 없다: " + name);
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 설정(config.yml — 없으면 config.toml, config.json 순으로 찾는다). jar 안의 같은 이름 파일이 기본값이 된다.
     * 처음 부를 때 읽는다.
     */
    public final synchronized Config config() {
        if (config == null) reloadConfig();
        return config;
    }

    /** jar 안의 기본 설정을 데이터 폴더에 없을 때만 복사한다. */
    public final void saveDefaultConfig() {
        String name = configFileName();
        if (resource(name) == null) return;
        Path target = configFolder().resolve(name);
        if (Files.exists(target)) return;
        try (InputStream in = resource(name)) {
            Files.copy(in, target);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public final synchronized void reloadConfig() {
        String name = configFileName();
        Path file = configFolder().resolve(name);
        try {
            config = Config.loadOrEmpty(file);
            try (InputStream in = resource(name)) {
                if (in != null) {
                    ConfigFormat f = ConfigFormat.fromFileName(name).orElse(ConfigFormat.YAML);
                    config.setDefaults(f.parse(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public final synchronized void saveConfig() {
        try {
            config().save();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 데이터 폴더나 jar 에 있는 설정 파일 이름(yml → toml → json, 아무것도 없으면 config.yml). */
    private String configFileName() {
        for (String n : new String[]{"config.yml", "config.yaml", "config.toml", "config.json"}) {
            if (Files.exists(context.configFolder().resolve(n)) || resource(n) != null) return n;
        }
        return "config.yml";
    }

    @Override
    public String toString() { return context == null ? getClass().getName() : description().fullName(); }
}
