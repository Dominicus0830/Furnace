package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.compat.paper.fixtures.WiringProbe;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * T-260: Furnace 에 모양만 있는 Paper 이벤트(전체 껍데기, 클래스 초기화 덫)를 듣는 처리기는 그것만 건너뛰고, 같은 리스너의 진짜 이벤트 처리기는
 * 등록된다 — 껍데기가 없던 때(T-040a: 없는 이벤트 처리기만 건너뜀)와 같은 결과. 껍데기는 시험 소스의 {@code io.papermc.furnacetest.ShellEvent}.
 */
class ShellEventListenerTest {

    private static final String YML = """
            name: ShellEv
            version: '1'
            main: sev.SevPlugin
            api-version: '1.21'
            """;

    private static final String PLUGIN = """
            package sev;
            import org.bukkit.event.*;
            import org.bukkit.event.player.PlayerJoinEvent;
            import org.bukkit.plugin.RegisteredListener;
            import org.bukkit.plugin.java.JavaPlugin;
            import org.domi.furnace.compat.paper.fixtures.WiringProbe;
            public final class SevPlugin extends JavaPlugin {
                public static final class L implements Listener {
                    @EventHandler public void shell(io.papermc.furnacetest.ShellEvent e) {}
                    @EventHandler public void join(PlayerJoinEvent e) {}
                }
                @Override public void onEnable() {
                    WiringProbe.plugin = this;
                    try {
                        L l = new L();
                        getServer().getPluginManager().registerEvents(l, this);
                        for (RegisteredListener r : PlayerJoinEvent.getHandlerList().getRegisteredListeners()) {
                            if (r.getListener() == l) WiringProbe.LOG.add("join-registered");
                        }
                    } catch (Throwable t) { WiringProbe.failure = t; }
                }
            }
            """;

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() { WiringProbe.reset(); }

    @Test
    void shellEventHandlerIsSkippedAndTheRealOneIsRegistered() throws Exception {
        Path jar = JarCompiler.jar(tmp.resolve("sev.jar"), YML, Map.of("sev.SevPlugin", PLUGIN));
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(jar).start()) {
            assertNull(WiringProbe.failure, () -> "registerEvents 가 던졌다: " + WiringProbe.failure);
            assertTrue(WiringProbe.LOG.contains("join-registered"), WiringProbe.LOG::toString);
            assertTrue(WiringProbe.plugin.isEnabled());
        }
    }
}
