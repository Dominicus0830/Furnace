// T-141 E4a: /gamerule 의 GameRuleChangeEvent — 값 바꾸기, 취소(값 그대로·명령 성공·지금 값 알림), 플러그인 규칙도 거침, 구독자 없을 때 무영향.
package org.domi.furnace.server.command.builtin;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.event.world.GameRuleChangeEvent;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.exec.CommandResult;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GameRuleEventTest {

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;
    final FurnacePlugin plugin = new FurnacePlugin() {};
    final List<GameRuleChangeEvent> seen = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUp() {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        plugin.setEnabled(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        core.plugins().eventsImpl().unregisterAll(plugin);
        core.plugins().close();
        sched.close();
        TestServers.drainIoPools();
    }

    private CommandResult run(String command) {
        return core.commands().performPrefixedCommandAsync(CommandSource.console(core), command).orTimeout(20, TimeUnit.SECONDS).join();
    }

    private void on(java.util.function.Consumer<GameRuleChangeEvent> h) {
        core.plugins().eventsImpl().subscribe(plugin, GameRuleChangeEvent.class, e -> {
            seen.add(e);
            h.accept(e);
        });
    }

    @Test
    void noSubscriberKeepsVanillaBehaviour() {
        CommandResult r = run("gamerule randomTickSpeed 5");
        assertTrue(r.success());
        assertEquals(5, r.result());
        assertEquals(5, core.gameRules().getInt("randomTickSpeed"));
    }

    @Test
    void handlerRewritesValue() {
        on(e -> e.value("8"));
        CommandResult r = run("gamerule randomTickSpeed 5");
        assertEquals(8, core.gameRules().getInt("randomTickSpeed"));
        assertEquals(8, r.result());
        var e = seen.get(0);
        assertEquals("randomTickSpeed", e.rule());
        assertEquals("minecraft:overworld", e.world());
        assertNotNull(e.sender());
    }

    @Test
    void cancelledGameruleKeepsValueAndStillSucceeds() {
        core.gameRules().set("keepInventory", 0);
        on(e -> e.setCancelled(true));
        CommandResult r = run("gamerule keepInventory true");
        assertTrue(r.success(), "Paper: 취소돼도 명령은 성공");
        assertFalse(core.gameRules().getBoolean("keepInventory"));
        assertEquals(0, r.result(), "지금 값");
    }

    @Test
    void nonNumericValueFailsTheCommand() {
        core.gameRules().set("randomTickSpeed", 3);
        on(e -> e.value(null));
        assertFalse(run("gamerule randomTickSpeed 5").success(), "Paper: Integer.parseInt 예외");
        assertEquals(3, core.gameRules().getInt("randomTickSpeed"));
    }
}
