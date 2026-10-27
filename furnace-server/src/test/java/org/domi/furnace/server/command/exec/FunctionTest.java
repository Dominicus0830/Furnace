// 함수 시스템(S5): 데이터 팩 함수 로드·태그 합산, /function · /return(값·fail·run), execute if|unless function,
// 함수 안 줄 순서(앞 줄의 region 작업이 끝난 뒤 다음 줄), #minecraft:load·#minecraft:tick, /reload 뒤 라이브러리 교체, 체인 예산.
package org.domi.furnace.server.command.exec;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.data.DataPackManager;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FunctionTest {

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;
    DataPackManager packs;
    Path functionDir;
    final List<java.util.concurrent.CompletableFuture<Integer>> hangs = Collections.synchronizedList(new ArrayList<>());
    final List<String> messages = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        core.world().awaitChunk(new ChunkPos(0, 0));
        DeferredResultTest.spawnCows(core, 2);
        core.addConsoleListener(t -> messages.add(t.getString()));
        Path vanilla = tmp.resolve("vanilla");
        Files.createDirectories(vanilla.resolve("data/minecraft/tags/item"));
        Files.writeString(vanilla.resolve("data/minecraft/tags/item/x.json"), "{\"values\":[]}");
        Path pack = tmp.resolve("datapacks/mine");
        Files.createDirectories(pack);
        Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"description\":\"t\",\"pack_format\":48}}");
        functionDir = pack.resolve("data/t/function");
        Files.createDirectories(functionDir);
        fn("order", "tag @e[type=cow] add a", "execute if entity @e[tag=a] run scoreboard players set #n o 1", "tag @e[type=cow] remove a");
        fn("block", "setblock 0 70 0 minecraft:stone", "execute if block 0 70 0 minecraft:stone run scoreboard players set #b o 1",
                "setblock 0 70 0 minecraft:air");
        fn("ret7", "scoreboard players set #a o 1", "return 7", "scoreboard players set #a o 2");
        fn("storeret", "execute store success score #x o run return 5");
        fn("fail", "return fail");
        fn("retrun", "return run execute if entity @e[type=zombie]");
        fn("three", "return 3");
        fn("noreturn", "scoreboard players add #nr o 1");
        fn("ret1", "return 1");
        fn("marker", "scoreboard players set #marker o 1");
        fn("four", "scoreboard players add #c o 1", "scoreboard players add #c o 1", "scoreboard players add #c o 1", "scoreboard players add #c o 1");
        fn("comments", "# 주석", "", "scoreboard players \\", "  add #cont o 4");
        fn("bad", "scoreboard players set");
        fn("slash", "/say hi");
        fn("useplugin", "plugincmd");
        fn("macro", "$scoreboard players set #m o $(v)");
        fn("callsmacro", "function t:macro {v:1}");
        fn("hangfn", "hang");
        tag("t", "withmacro", "t:macro", "t:ret1");
        fn("onload", "scoreboard players add #load o 1");
        fn("ontick", "scoreboard players add #tick o 1", "execute if score #load o matches 1.. run scoreboard players add #after o 1");
        tag("t", "mixed", "t:three", "t:fail");
        tag("t", "failonly", "t:fail", "t:noreturn");
        tag("t", "first", "t:ret1", "t:marker");
        tag("t", "empty");
        tag("t", "broken", "t:bad");
        tag("minecraft", "load", "t:onload");
        tag("minecraft", "tick", "t:ontick");
        packs = DataPackManager.start(vanilla, tmp, org.domi.furnace.server.world.storage.LevelData.create("world", 1L, true, false,
                org.domi.furnace.server.world.GameMode.SURVIVAL, false, org.domi.furnace.server.world.Difficulty.EASY, List.of("vanilla"), List.of()),
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt"), true, List.of("vanilla"), List.of());
        // 플러그인 명령 흉내: 함수 로드(loadFunctions) 전에 등록돼 있으면 그 명령을 쓰는 함수가 시작 때부터 실린다
        var d = core.commands().getDispatcher();
        d.register(org.domi.furnace.server.command.brigadier.LiteralArgumentBuilder.<CommandSource>literal("plugincmd").executes(c -> 9));
        d.register(org.domi.furnace.server.command.brigadier.LiteralArgumentBuilder.<CommandSource>literal("hang").executes(c -> {
            var f = new java.util.concurrent.CompletableFuture<Integer>();
            hangs.add(f);
            c.getSource().defer(f);
            return 1;
        }));
        core.dataPacks(packs);
        core.loadFunctions();
        onGlobal(() -> { });   // replaceLibrary 홉이 끝나기를 기다린다(전역 실행기는 FIFO)
        run("scoreboard objectives add o dummy");
    }

    @AfterEach
    void tearDown() {
        sched.close();
        if (packs != null) packs.close();
    }

    private void fn(String name, String... lines) throws IOException {
        Files.writeString(functionDir.resolve(name + ".mcfunction"), String.join("\n", lines) + "\n");
    }

    private void tag(String ns, String name, String... values) throws IOException {
        Path dir = tmp.resolve("datapacks/mine/data/" + ns + "/tags/function");
        Files.createDirectories(dir);
        StringBuilder sb = new StringBuilder("{\"values\":[");
        for (int i = 0; i < values.length; i++) sb.append(i > 0 ? "," : "").append('"').append(values[i]).append('"');
        Files.writeString(dir.resolve(name + ".json"), sb.append("]}").toString());
    }

    private void onGlobal(Runnable r) throws Exception { sched.global().execute(r).get(10, TimeUnit.SECONDS); }

    private CommandResult run(String command) {
        messages.clear();
        return core.commands().performPrefixedCommandAsync(CommandSource.console(core), command).orTimeout(20, TimeUnit.SECONDS).join();
    }

    /** 점수(없으면 null) — scoreboard players get 의 결과값. */
    private Integer score(String holder) {
        CommandResult r = run("scoreboard players get " + holder + " o");
        return r.success() ? r.result() : null;
    }

    // ---------------------------------------------------------------- 로드

    @Test
    void loadsFunctionsAndTagsAndSkipsOnlyBrokenOnes() {
        var lib = core.functions().library();
        assertTrue(lib.function(ResourceId.parse("t:ret7")).isPresent());
        assertTrue(lib.function(ResourceId.parse("t:bad")).isEmpty(), "파싱 실패 함수는 빠진다");
        assertTrue(lib.function(ResourceId.parse("t:slash")).isEmpty(), "'/' 로 시작하는 줄은 로드 실패");
        assertEquals(List.of("t:three", "t:fail"), lib.tag(ResourceId.parse("t:mixed")).stream().map(f -> f.id().toString()).toList());
        assertTrue(lib.tag(ResourceId.parse("t:broken")).isEmpty(), "빠진 함수를 필수로 참조한 태그는 실리지 않는다");
        assertEquals(3, lib.function(ResourceId.parse("t:ret7")).orElseThrow().entries().size());
        assertEquals(1, lib.function(ResourceId.parse("t:comments")).orElseThrow().entries().size(), "주석·빈 줄 무시, \\ 이어 붙이기");
        CommandResult r = run("function t:bad");
        assertEquals(new CommandResult(false, 0, 1), r, "인자 해석 오류는 handleError 뒤 소스 콜백 onFailure(바닐라 hp.b)");
        assertTrue(messages.stream().anyMatch(m -> m.contains("Unknown function t:bad")), messages.toString());
        run("function t:comments");
        assertEquals(4, score("#cont"));
    }

    // ---------------------------------------------------------------- 줄 순서

    @Test
    void linesRunInOrderAcrossRegionRoundTrips() {
        run("function t:order");
        assertEquals(1, score("#n"), "앞 줄(tag @e — region 왕복)이 끝난 뒤 다음 줄이 태그를 본다");
        run("function t:block");
        assertEquals(1, score("#b"), "setblock 이 끝난 뒤 if block 이 새 블록을 본다");
    }

    // ---------------------------------------------------------------- return

    @Test
    void returnValueStopsFunctionAndReportsResult() {
        CommandResult r = run("function t:ret7");
        assertEquals(new CommandResult(true, 7, 1), r);
        assertEquals(List.of("Running function t:ret7", "Function t:ret7 returned 7"), messages);
        assertEquals(1, score("#a"), "return 뒤 줄은 실행되지 않는다");
        run("execute store result score #r o run function t:ret7");
        assertEquals(7, score("#r"), "store 가 함수 반환값을 받는다");
    }

    @Test
    void returnCallsSourceCallbackBeforeFrameCallback() {
        // 안쪽 store success(소스 콜백)가 1 을, 바깥 store result(프레임 콜백)가 5 를 같은 점수에 쓴다 — 순서가 소스→프레임이면 5 가 남는다
        run("execute store result score #x o run function t:storeret");
        assertEquals(5, score("#x"));
    }

    @Test
    void returnFailAndReturnRunWithoutSourcesFail() {
        run("scoreboard players set #h o 5");
        run("execute store success score #h o run function t:fail");
        assertEquals(0, score("#h"), "return fail → success 0");
        run("scoreboard players set #z o 5");
        run("execute store success score #z o run function t:retrun");
        assertEquals(0, score("#z"), "return run 의 명령이 실패하면 함수도 실패");
        CommandResult r = run("function t:fail");
        assertEquals(new CommandResult(false, 0, 1), r);
    }

    @Test
    void topLevelReturnClearsTheContext() {
        CommandResult r = run("return 4");
        assertEquals(new CommandResult(true, 4, 1), r, "최상위 return 은 소스 콜백에 값을 보낸다(프레임 콜백은 빈 콜백)");
    }

    // ---------------------------------------------------------------- 태그 합산

    @Test
    void tagResultsAreSummedIgnoringSuccess() {
        run("execute store result score #sum o run function #t:mixed");
        assertEquals(3, score("#sum"), "return 3 + return fail(0) = 3");
        run("scoreboard players set #fo o 0");
        run("execute store success score #fo o run function #t:failonly");
        assertEquals(1, score("#fo"), "return fail 만 있어도 결과가 왔으면 onSuccess(0) — OR 가 아니다");
        CommandResult r = run("function #t:empty");
        assertEquals(new CommandResult(false, 0, 1), r);
        assertTrue(messages.stream().anyMatch(m -> m.contains("Can't find any functions for name t:empty")), messages.toString());
    }

    // ---------------------------------------------------------------- execute if function

    @Test
    void executeIfFunctionUsesReturnValue() {
        run("execute if function t:noreturn run scoreboard players set #if1 o 1");
        assertNull(score("#if1"), "return 없는 함수는 0 → if 탈락");
        assertEquals(1, score("#nr"), "함수 자체는 실행된다");
        run("execute unless function t:noreturn run scoreboard players set #if2 o 1");
        assertEquals(1, score("#if2"));
        run("execute if function t:ret1 run scoreboard players set #if3 o 1");
        assertEquals(1, score("#if3"));
        run("execute if function #t:first run scoreboard players set #if4 o 1");
        assertEquals(1, score("#if4"));
        assertNull(score("#marker"), "태그 안 첫 return 이 나머지 함수를 버린다");
        run("execute if function #t:empty run scoreboard players set #if5 o 1");
        run("execute unless function #t:empty run scoreboard players set #if6 o 1");
        assertNull(score("#if5"));
        assertNull(score("#if6"), "빈 함수 목록이면 다음 단계를 큐에 넣지 않는다(unless 도)");
        CommandResult r = run("execute as @e[type=cow] if function t:ret1");
        assertEquals(CommandResult.NONE, r, "실행 노드 없는 if function 은 파싱 오류");
    }

    // ---------------------------------------------------------------- 예산

    @Test
    void chainLimitStopsFunction() {
        core.gameRules().set("maxCommandChainLength", 3);
        try {
            run("function t:four");
        } finally {
            core.gameRules().set("maxCommandChainLength", 65536);
        }
        assertEquals(2, score("#c"), "함수 호출 1 + 줄 2 = 3 에서 멈춘다");
    }

    // ---------------------------------------------------------------- #load · #tick · reload

    @Test
    void loadRunsOnceBeforeTickAndReloadSwapsLibrary() throws Exception {
        for (int i = 0; i < 3; i++) onGlobal(core::tick);
        assertEquals(1, score("#load"), "#load 는 첫 틱에 한 번");
        assertEquals(3, score("#tick"), "#tick 은 매 틱");
        assertEquals(3, score("#after"), "첫 틱에서도 #load 가 #tick 보다 먼저");
        fn("ontick", "scoreboard players add #tick o 10");
        packs.reload(packs.repository().selectedIds(), ForkJoinPool.commonPool()).get(20, TimeUnit.SECONDS);
        onGlobal(() -> { });   // 리로드 리스너가 넘긴 replaceLibrary(전역)를 기다린다 — 다른 스레드에서 부르면 assert 에 걸린다
        onGlobal(core::tick);
        assertEquals(2, score("#load"), "리로드 뒤 다음 틱에 #load 다시");
        assertEquals(13, score("#tick"), "새 tick 함수로 바뀐다");
        assertEquals(1, core.functions().tickingCount());
    }

    @Test
    void functionUsingPluginCommandLoadsAtStartup() {
        assertTrue(core.functions().library().function(ResourceId.parse("t:useplugin")).isPresent());
    }

    @Test
    void macroFunctionsLoadAndTagsThatRequireThemStay() {
        // S7: 매크로 함수·그것을 부르는 함수·그것을 필수 참조하는 태그가 모두 실린다(S5 의 연쇄 탈락이 사라진다)
        var lib = core.functions().library();
        assertTrue(lib.function(ResourceId.parse("t:macro")).isPresent());
        assertTrue(lib.function(ResourceId.parse("t:callsmacro")).isPresent());
        assertEquals(List.of("t:macro", "t:ret1"), lib.tag(ResourceId.parse("t:withmacro")).stream().map(f -> f.id().toString()).toList());
        run("function t:callsmacro");
        assertEquals(1, score("#m"));
    }

    @Test
    void frozenTicksSkipFunctionsAndStepRunsThem() throws Exception {
        run("tick freeze");
        onGlobal(core::scheduledTick);
        assertNull(score("#tick"), "freeze 중에는 함수 틱 없음");
        assertNull(score("#load"));
        run("tick step 1");
        onGlobal(core::scheduledTick);
        assertEquals(1, score("#load"));
        assertEquals(1, score("#tick"), "step 중에는 함수 틱이 돈다");
        run("tick unfreeze");
    }

    @Test
    void waitingTickFunctionIsSkippedAndReloadDuringSuspendIsSafe() throws Exception {
        tag("minecraft", "tick", "t:hangfn");
        packs.reload(packs.repository().selectedIds(), ForkJoinPool.commonPool()).get(20, TimeUnit.SECONDS);
        onGlobal(() -> { });
        for (int i = 0; i < 3; i++) onGlobal(core::tick);
        assertEquals(1, hangs.size(), "첫 실행이 멈춰 있다");
        int[] pending = new int[1];
        onGlobal(() -> pending[0] = core.commands().queue().pendingCount());
        assertEquals(1, pending[0], "둘째 틱 것은 대기, 셋째 틱 것은 같은 함수가 대기 중이라 건너뜀");
        // 멈춘 컨텍스트가 있는 채로 리로드: 옛 함수 객체로 끝까지 돈다
        fn("hangfn", "scoreboard players add #h2 o 1");
        packs.reload(packs.repository().selectedIds(), ForkJoinPool.commonPool()).get(20, TimeUnit.SECONDS);
        onGlobal(() -> { });
        hangs.get(0).complete(1);
        for (int i = 0; i < 200 && hangs.size() < 2; i++) Thread.sleep(10);
        assertEquals(2, hangs.size(), "대기하던 실행은 옛 함수(hang)로 시작한다");
        hangs.get(1).complete(1);
        boolean[] busy = {true};
        for (int i = 0; i < 200 && busy[0]; i++) {
            onGlobal(() -> busy[0] = core.commands().queue().isBusy());
            if (busy[0]) Thread.sleep(10);
        }
        assertFalse(busy[0]);
        onGlobal(core::tick);
        assertEquals(1, score("#h2"), "리로드 뒤 새 tick 함수");
    }

    @Test
    void returnFromManySourcesWithAsyncCallbackKeepsOrder() throws Exception {
        // 소스 콜백이 미완료 future 를 돌려주면(store block/entity 와 같은 꼴) 소스마다 차례로 기다린다 — 두 번째 소스가 다시 멈추려다 터지지 않는다
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        ResultCallback async = (ok, v) -> {
            order.add("src " + v);
            var f = new java.util.concurrent.CompletableFuture<Void>();
            java.util.concurrent.CompletableFuture.delayedExecutor(30, TimeUnit.MILLISECONDS).execute(() -> sched.global().execute(() -> {
                order.add("done");
                f.complete(null);
            }));
            return f;
        };
        CommandResult r = core.commands().performPrefixedCommandAsync(CommandSource.console(core).withCallback(async),
                "execute as @e[type=cow] run return 5").orTimeout(20, TimeUnit.SECONDS).join();
        assertEquals(2, r.callbacks());
        assertEquals(List.of("src 5", "done", "src 5", "done"), order);
    }
}
