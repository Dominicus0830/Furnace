// furnace.properties 의 플러그인 관련 키.
//   plugins.fail-fast=false        플러그인을 불러오거나 켜다가 하나라도 실패하면 서버 시작을 멈춘다
//   plugins.data-root=             비우면 데이터 폴더 = plugins/<이름>/, 쓰면 <data-root>/<이름>/
//   plugins.log-level.<이름>=INFO  플러그인별 로그 수준(JUL 수준 이름)
package org.domi.furnace.server.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record PluginSettings(boolean failFast, Path dataRoot, Map<String, Level> logLevels) {

    public static final PluginSettings DEFAULT = new PluginSettings(false, null, Map.of());

    public PluginSettings {
        Map<String, Level> m = new HashMap<>();
        logLevels.forEach((k, v) -> m.put(k.toLowerCase(Locale.ROOT), v));
        logLevels = Map.copyOf(m);
    }

    /** 이 플러그인에 지정된 로그 수준(없으면 null). */
    public Level levelFor(String plugin) { return logLevels.get(plugin.toLowerCase(Locale.ROOT)); }

    public static PluginSettings load(Path file) {
        if (!Files.exists(file)) return DEFAULT;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        } catch (IOException e) {
            return DEFAULT;
        }
        return from(p, LoggerFactory.getLogger("Furnace"));
    }

    static PluginSettings from(Properties p, Logger log) {
        boolean failFast = Boolean.parseBoolean(p.getProperty("plugins.fail-fast", "false").strip());
        String root = p.getProperty("plugins.data-root", "").strip();
        Map<String, Level> levels = new HashMap<>();
        String prefix = "plugins.log-level.";
        for (String k : p.stringPropertyNames()) {
            if (!k.startsWith(prefix)) continue;
            String v = p.getProperty(k).strip();
            try {
                levels.put(k.substring(prefix.length()), Level.parse(v.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                log.warn("furnace.properties 의 '{}' 값 '{}' 은 로그 수준이 아니다 — 무시한다", k, v);
            }
        }
        return new PluginSettings(failFast, root.isEmpty() ? null : Path.of(root), levels);
    }
}
