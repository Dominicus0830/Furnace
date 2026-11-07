// C2(불만 표 67번): 데이터 팩 함수·함수 줄 실행 훅(FunctionExecuteEvent·FunctionCommandEvent) — 호출 경로 구분, 취소, 구독자 없을 때 무영향.
package org.domi.furnace.server.command.exec;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.event.server.FunctionCommandEvent;
import org.domi.furnace.api.event.server.FunctionExecuteEvent;
import org.domi.furnace.api.event.server.FunctionExecuteEvent.Trigger;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.data.DataPackManager;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FunctionHookTest {

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;
    DataPackManager packs;
    Path functionDir;
    final FurnacePlugin plugin = new FurnacePlugin() {};
    final List<String> log = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        plugin.setEnabled(true);
        Path vanilla = tmp.resolve("vanilla");
        Files.createDirectories(vanilla.resolve("data/minecraft/tags/item"));
        Files.writeString(vanilla.resolve("data/minecraft/tags/item/x.json"), "{\"values\":[]}");
        Path pack = tmp.resolve("datapacks/mine");
        Files.createDirectories(pack);
        Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"description\":\"t\",\"pack_format\":48}}");
        functionDir = pack.resolve("data/t/function");
        Files.createDirectories(functionDir);
        fn("outer", "scoreboard players add #a o 1", "function t:inner", "scoreboard players add #a o 10");
        fn("inner", "scoreboard players add #i o 1");
        fn("ticker", "scoreboard players add #tick o 1");
        fn("loader", "scoreboard players add #load o 1");
        fn("macro", "$scoreboard players add #m o $(v)");
        Path tags = tmp.resolve("datapacks/mine/data/minecraft/tags/function");
        Files.createDirectories(tags);
        Files.writeString(tags.resolve("tick.json"), "{\"values\":[\"t:ticker\"]}");
        Files.writeString(tags.resolve("load.json"), "{\"values\":[\"t:loader\"]}");
        packs = DataPackManager.start(vanilla, tmp, org.domi.furnace.server.world.storage.LevelData.create("world", 1L, true, false,
                org.domi.furnace.server.world.GameMode.SURVIVAL, false, org.domi.furnace.server.world.Difficulty.EASY, List.of("vanilla"), List.of()),
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt"), true, List.of("vanilla"), List.of());
        core.dataPacks(packs);
        core.loadFunctions();
        onGlobal(() -> { });
        run("scoreboard objectives add o dummy");
    }

    @AfterEach
    void tearDown() throws Exception {
        core.plugins().eventsImpl().unregisterAll(plugin);
        core.plugins().close();
        sched.close();
        if (packs != null) packs.close();
        TestServers.drainIoPools();
    }

    private void fn(String name, String... lines) throws Exception {
        Files.writeString(functionDir.resolve(name + ".mcfunction"), String.join("\n", lines) + "\n");
    }

    private void onGlobal(Runnable r) throws Exception { sched.global().execute(r).get(10, TimeUnit.SECONDS); }

    private CommandResult run(String command) {
        return core.commands().performPrefixedCommandAsync(CommandSource.console(core), command).orTimeout(20, TimeUnit.SECONDS).join();
    }

    private Integer score(String holder) {
        CommandResult r = run("scoreboard players get " + holder + " o");
        return r.success() ? r.result() : null;
    }

    private <E extends org.domi.furnace.api.event.Event> void on(Class<E> type, java.util.function.Consumer<? super E> h) {
        core.plugins().eventsImpl().subscribe(plugin, type, h);
    }

    @Test
    void executeEventSeesCommandAndNestedCalls() {
        on(FunctionExecuteEvent.class, e -> log.add(e.function() + ":" + e.trigger() + ":" + e.depth()));
        run("function t:outer");
        assertEquals(List.of("t:outer:COMMAND:0", "t:inner:NESTED:1"), log);
        assertEquals(11, score("#a"));
        assertEquals(1, score("#i"));
    }

    @Test
    void cancellingExecuteSkipsWholeFunctionBodyOnly() {
        on(FunctionExecuteEvent.class, e -> { if (e.function().equals("t:inner")) e.setCancelled(true); });
        run("function t:outer");
        assertNull(score("#i"), "취소한 함수 본문은 돌지 않는다");
        assertEquals(11, score("#a"), "바깥 함수의 나머지 줄은 그대로");
    }

    @Test
    void tickLoadAndScheduleTriggersAreDistinguished() throws Exception {
        on(FunctionExecuteEvent.class, e -> log.add(e.function() + ":" + e.trigger()));
        onGlobal(core::tick);
        assertEquals(List.of("t:loader:LOAD", "t:ticker:TICK"), log);
        log.clear();
        run("schedule function t:inner 1t");
        onGlobal(core::tick);
        onGlobal(core::tick);
        assertTrue(log.contains("t:inner:" + Trigger.SCHEDULED), log.toString());
        assertEquals(1, score("#i"));
    }

    @Test
    void cancellingTickFunctionStopsIt() throws Exception {
        on(FunctionExecuteEvent.class, e -> { if (e.trigger() == Trigger.TICK) e.setCancelled(true); });
        onGlobal(core::tick);
        onGlobal(core::tick);
        assertNull(score("#tick"));
        assertEquals(1, score("#load"), "load 는 취소하지 않았으니 돈다");
    }

    @Test
    void commandEventSeesEachLineAndCanCancelOne() {
        on(FunctionCommandEvent.class, e -> {
            log.add(e.depth() + ":" + e.command());
            if (e.command().endsWith("#a o 10")) e.setCancelled(true);
        });
        run("function t:outer");
        assertEquals(List.of("1:scoreboard players add #a o 1", "1:function t:inner", "2:scoreboard players add #i o 1", "1:scoreboard players add #a o 10"), log);
        assertEquals(1, score("#a"), "취소한 줄(+10)만 빠진다");
        assertEquals(1, score("#i"));
    }

    @Test
    void macroLinesAreReportedWithValuesFilledIn() {
        on(FunctionCommandEvent.class, e -> log.add(e.command()));
        run("function t:macro {v:5}");
        assertEquals(List.of("scoreboard players add #m o 5"), log);
    }

    @Test
    void noSubscribersChangesNothing() {
        assertFalse(core.gameEvents().wantsFunctionCommands());
        run("function t:outer");
        assertEquals(11, score("#a"));
        assertEquals(1, score("#i"));
    }
}
