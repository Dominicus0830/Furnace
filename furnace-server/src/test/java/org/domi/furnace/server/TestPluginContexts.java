// 시험용: jar 없이 플러그인 객체를 초기화한다. PluginContext 에 메서드가 늘어도 시험이 깨지지 않게 프록시로 만든다
// (모르는 메서드는 null). 설명은 설명 파일과 같은 경로(PluginDescription.read)로 읽는다.
package org.domi.furnace.server;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.config.ConfigFormat;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.plugin.PluginContext;
import org.domi.furnace.api.plugin.PluginDescription;

public final class TestPluginContexts {
    private TestPluginContexts() {}

    public static void init(FurnacePlugin plugin, Furnace server, String name, Path dir) {
        PluginDescription d = PluginDescription.read(ConfigFormat.YAML.parse(
                "name: " + name + "\nversion: '1'\nmain: " + plugin.getClass().getName().replace('$', '.') + "\n"));
        Path folder = dir.resolve(name);
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(name);
        PluginContext ctx = (PluginContext) Proxy.newProxyInstance(PluginContext.class.getClassLoader(), new Class<?>[]{PluginContext.class},
                (proxy, m, args) -> switch (m.getName()) {
                    case "description" -> d;
                    case "server" -> server;
                    case "logger" -> logger;
                    case "configFolder", "dataFolder" -> folder;
                    case "file" -> dir.resolve(name + ".jar");
                    case "classLoader" -> plugin.getClass().getClassLoader();
                    case "toString" -> "TestPluginContext(" + name + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
        plugin.init(ctx);
    }
}
