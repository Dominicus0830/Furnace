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
            enabled = true;
            onEnable();
        } else {
            try {
                onDisable();
            } finally {
                enabled = false;
                tasks.cancelAll();
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

    /** plugins/<이름>/ (없으면 만든다). */
    public final Path dataFolder() {
        Path p = context.dataFolder();
        try {
            Files.createDirectories(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return p;
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
        if (resource(name) != null) saveResource(name, false);
    }

    public final synchronized void reloadConfig() {
        String name = configFileName();
        Path file = dataFolder().resolve(name);
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
            if (Files.exists(context.dataFolder().resolve(n)) || resource(n) != null) return n;
        }
        return "config.yml";
    }

    @Override
    public String toString() { return context == null ? getClass().getName() : description().fullName(); }
}
