// O11 P-O11-01: 명령 큐도 region 급한 작업을 곧바로 돌린다(commands.region-drain). 리전 접근 한 줄이 한 틱씩 걸려 #tick 함수가 초당 6.7 번만 돌던 문제와,
// 켜고 꺼도 함수 결과(줄 순서·점수·블록·엔티티)가 바닐라 기대값과 같은지, 예산을 넘기면 예전처럼 다음 틱으로 물러나는지 본다.
package org.domi.furnace.server.command.exec;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.config.OperatorOptions;
import org.domi.furnace.server.data.DataPackManager;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.CommandDrainGate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandRegionDrainTest {

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;
    DataPackManager packs;
    Path functionDir;
    Path vanilla;

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 4);
        core = TestServers.core(tmp, sched);
        core.world().awaitChunk(new ChunkPos(0, 0));
        DeferredResultTest.spawnCows(core, 2);
        vanilla = tmp.resolve("vanilla");
        Files.createDirectories(vanilla.resolve("data/minecraft/tags/item"));
        Files.writeString(vanilla.resolve("data/minecraft/tags/item/x.json"), "{\"values\":[]}");
        Path pack = tmp.resolve("datapacks/mine");
        Files.createDirectories(pack);
        Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"description\":\"t\",\"pack_format\":48}}");
        functionDir = pack.resolve("data/t/function");
        Files.createDirectories(functionDir);
        run("scoreboard objectives add o dummy");
    }

    @AfterEach
    void tearDown() {
        OperatorOptions.install(null);
        sched.close();
        if (packs != null) packs.close();
    }

    private void fn(String name, String... lines) throws IOException {
        Files.writeString(functionDir.resolve(name + ".mcfunction"), String.join("\n", lines) + "\n");
    }

    private void tickTag(String... values) throws IOException {
        Path dir = tmp.resolve("datapacks/mine/data/minecraft/tags/function");
        Files.createDirectories(dir);
        StringBuilder sb = new StringBuilder("{\"values\":[");
        for (int i = 0; i < values.length; i++) sb.append(i > 0 ? "," : "").append('"').append(values[i]).append('"');
        Files.writeString(dir.resolve("tick.json"), sb.append("]}").toString());
    }

    private void load() throws Exception {
        if (packs != null) packs.close();
        packs = DataPackManager.start(vanilla, tmp, org.domi.furnace.server.world.storage.LevelData.create("world", 1L, true, false,
                org.domi.furnace.server.world.GameMode.SURVIVAL, false, org.domi.furnace.server.world.Difficulty.EASY, List.of("vanilla"), List.of()),
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt"), true, List.of("vanilla"), List.of());
        core.dataPacks(packs);
        core.loadFunctions();
        sched.global().execute(() -> { }).get(10, TimeUnit.SECONDS);   // replaceLibrary 홉이 끝나기를 기다린다
    }

    private static void drain(boolean on) {
        var d = OperatorOptions.DEFAULT;
        OperatorOptions.install(new OperatorOptions(d.strictChatOrder(), d.unsignedChatAsSystem(), d.serverBrand(), d.announceSleepStatus(),
                d.confirmInterrupt(), d.interruptConfirmSeconds(), d.motdFormat(), d.gravityBlockPortalDupe(), d.sendNamespacedCommands(), on));
    }

    private CommandResult run(String command) {
        return core.commands().performPrefixedCommandAsync(CommandSource.console(core), command).orTimeout(20, TimeUnit.SECONDS).join();
    }

    private Integer score(String holder) {
        CommandResult r = run("scoreboard players get " + holder + " o");
        return r.success() ? r.result() : null;
    }

    // ---------------------------------------------------------------- #tick 함수 실행률

    /** 50ms 간격으로 전역 틱을 secs 초 돌리고, 지난 틱 수를 돌려준다. */
    private int driveTicks(double secs) throws Exception {
        ScheduledExecutorService timer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        int[] ticks = {0};
        timer.scheduleAtFixedRate(() -> sched.global().execute(() -> { ticks[0]++; core.tick(); }), 0, 50, TimeUnit.MILLISECONDS);
        Thread.sleep((long) (secs * 1000));
        timer.shutdownNow();
        timer.awaitTermination(2, TimeUnit.SECONDS);
        sched.global().execute(() -> { }).get(10, TimeUnit.SECONDS);
        return ticks[0];
    }

    private double tickFunctionRatio(String... hopLines) throws Exception {
        fn("tick", prepend("scoreboard players add #runs o 1", hopLines));
        tickTag("t:tick");
        load();
        int ticks = driveTicks(3.0);
        // 마지막 실행(아직 도는 중)이 끝나기를 기다린 뒤 센다
        Thread.sleep(300);
        int runs = score("#runs");
        System.out.printf("TICKFN hops=%d drain=%s: %d runs / %d ticks%n", hopLines.length, OperatorOptions.current().commandRegionDrain(), runs, ticks);
        assertTrue(runs <= ticks + 1, "바닐라처럼 한 틱에 한 번보다 많이 돌 수 없다: " + runs + " / " + ticks);
        return (double) runs / ticks;
    }

    private static String[] prepend(String first, String[] rest) {
        List<String> all = new ArrayList<>();
        all.add(first);
        all.addAll(List.of(rest));
        return all.toArray(String[]::new);
    }

    private static final String HOP = "execute if block 0 -61 0 minecraft:stone";

    @Test
    void tickFunctionWithThreeRegionAccessesRunsEveryTick() throws Exception {
        double ratio = tickFunctionRatio(HOP, HOP, HOP);
        assertTrue(ratio >= 0.9, "리전 접근 3번이어도 #tick 함수가 매 틱 돌아야 한다(바닐라 20/s): " + ratio);
    }

    @Test
    void tickFunctionWithSetblockAndReadRunsEveryTick() throws Exception {
        double ratio = tickFunctionRatio("setblock 0 -61 0 minecraft:stone", HOP, "setblock 0 -61 0 minecraft:air", HOP);
        assertTrue(ratio >= 0.9, "쓰기·읽기 섞인 #tick 함수도 매 틱 돌아야 한다: " + ratio);
    }

    @Test
    void optionOffKeepsTheOldOneTickPerRegionAccessBehaviour() throws Exception {
        drain(false);
        double ratio = tickFunctionRatio(HOP, HOP, HOP);
        assertTrue(ratio < 0.6, "commands.region-drain=false 면 예전처럼 접근 한 줄이 한 틱씩 걸린다(약 1/3): " + ratio);
    }

    // ---------------------------------------------------------------- 결과 동일성

    private static final String[] ORDER_SENSITIVE = {
            "setblock 0 70 0 minecraft:stone",
            "execute store result score #a o if block 0 70 0 minecraft:stone",
            "setblock 0 70 0 minecraft:air",
            "execute store result score #b o if block 0 70 0 minecraft:stone",
            "execute store result score #c o if entity @e[type=cow]",
            "tag @e[type=cow] add a",
            "execute as @e[tag=a] run scoreboard players add #n o 1",
            "execute as @e[type=cow] at @s run scoreboard players add #m o 2",
            "tag @e[type=cow] remove a",
            "execute store result score #d o if entity @e[tag=a]",
            "execute if block 0 70 0 minecraft:air run scoreboard players set #e o 9",
    };

    private void assertOrderSensitiveResult() {
        assertEquals(1, score("#a"), "setblock 뒤 if block 은 새 블록을 본다");
        assertEquals(0, score("#b"), "다시 공기로 바꾼 뒤 if block stone 은 실패");
        assertEquals(2, score("#c"), "if entity 의 결과값은 일치한 수");
        assertEquals(2, score("#n"), "tag 가 끝난 뒤 as @e[tag=a] 는 소 두 마리를 돈다");
        assertEquals(4, score("#m"));
        assertEquals(0, score("#d"), "tag remove 가 끝난 뒤 보면 없다");
        assertEquals(9, score("#e"));
    }

    @Test
    void functionResultIsTheSameWithTheGateOpenAndClosed() throws Exception {
        fn("order", ORDER_SENSITIVE);
        load();
        for (boolean on : new boolean[]{true, false}) {
            drain(on);
            for (String h : new String[]{"#a", "#b", "#c", "#n", "#m", "#d", "#e"}) run("scoreboard players reset " + h + " o");
            run("function t:order");
            assertOrderSensitiveResult();
        }
    }

    @Test
    void nestedFunctionsAndReturnKeepTheirOrderWithTheGateOpen() throws Exception {
        fn("inner", "execute store result score #i o if block 0 70 0 minecraft:stone", "return 7", "scoreboard players set #never o 1");
        fn("outer", "setblock 0 70 0 minecraft:stone", "execute store result score #r o run function t:inner", "setblock 0 70 0 minecraft:air",
                "scoreboard players add #s o 1");
        load();
        drain(true);
        run("function t:outer");
        assertEquals(1, score("#i"), "안쪽 함수가 바깥의 setblock 결과를 본다");
        assertEquals(7, score("#r"), "return 값이 store 로 간다");
        assertEquals(1, score("#s"));
        assertNull(score("#never"), "return 뒤 줄은 돌지 않는다");
    }

    // ---------------------------------------------------------------- 시간·예산

    private long timedFunctionMillis(String... lines) throws Exception {
        fn("many", lines);
        load();
        long t0 = System.nanoTime();
        run("function t:many");
        return (System.nanoTime() - t0) / 1_000_000L;
    }

    @Test
    void hundredRegionAccessesFinishWellInsideOneSecondWithTheGateOpen() throws Exception {
        String[] lines = new String[40];
        java.util.Arrays.fill(lines, HOP);
        long ms = timedFunctionMillis(lines);
        System.out.println("DRAIN 40 hops took " + ms + " ms");
        assertTrue(ms < 1000, "40줄이 줄당 1틱(2000ms)이 아니라 몇 틱 안에 끝나야 한다: " + ms + " ms");
    }

    @Test
    void exhaustedBudgetFallsBackToNextTickButStillGivesTheSameResult() throws Exception {
        fn("order", ORDER_SENSITIVE);
        load();
        drain(true);
        // 시험은 전역 틱을 돌리지 않으므로 이번 틱 예산이 되살아나지 않는다 — 다 쓴 상태로 함수를 돌린다
        CommandDrainGate.charge(Long.MAX_VALUE / 4);
        try {
            run("function t:order");
            assertOrderSensitiveResult();   // 느려질 뿐 결과는 같다
        } finally {
            CommandDrainGate.newGlobalTick();
        }
    }
}
