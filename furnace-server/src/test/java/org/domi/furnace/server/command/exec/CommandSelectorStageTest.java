// T-150/T-151: 엔티티를 고르는 명령(tag @e, effect give @e, execute as @e at @s …, data get entity @s …)이 소스·단계마다 한 틱(50ms)씩
// 밀리지 않는지 본다. F2(O11 P-O11-01) 뒤에도 후속 단계의 전역 실행기가 명령 범위 밖이라 #tick 함수가 초당 1번만 돌았는데, 그 회귀를 CI 에서 잡는다.
// 실행률 단언은 문 예산과 무관하다(기준은 1.0/s, 고친 뒤 20/s) — 시험 JVM 예산(500ms)에서도 회귀를 잡는다. 결과는 문을 켜고 끈 두 변형이 같아야 한다.
package org.domi.furnace.server.command.exec;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
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

class CommandSelectorStageTest {

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;
    DataPackManager packs;
    Path functionDir;
    Path vanilla;
    ScheduledExecutorService timer;
    final int[] ticks = {0};

    static final int COWS = 20;

    @BeforeEach
    void setUp() throws Exception {
        CommandDrainGate.newGlobalTick();
        sched = new FurnaceSchedulerImpl(50_000_000L, 4);
        core = TestServers.core(tmp, sched);
        for (int cx = -2; cx <= 1; cx++) for (int cz = -1; cz <= 0; cz++) core.world().awaitChunk(new ChunkPos(cx, cz));
        vanilla = tmp.resolve("vanilla");
        Files.createDirectories(vanilla.resolve("data/minecraft/tags/item"));
        Files.writeString(vanilla.resolve("data/minecraft/tags/item/x.json"), "{\"values\":[]}");
        Path pack = tmp.resolve("datapacks/mine");
        Files.createDirectories(pack);
        Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"description\":\"t\",\"pack_format\":48}}");
        functionDir = pack.resolve("data/t/function");
        Files.createDirectories(functionDir);
        run("scoreboard objectives add o dummy");
        run("scoreboard objectives add px dummy");
        // 소 20마리를 청크 여러 장(x −30..27, z −6·6)에 흩는다 — 움직이지 않게 NoAI. 짝수 번은 돌, 홀수 번은 흙 위
        for (int i = 0; i < COWS; i++) {
            int x = cowX(i), z = cowZ(i);
            run("setblock " + x + " -61 " + z + " minecraft:" + (i % 2 == 0 ? "stone" : "dirt"));
            run("summon minecraft:cow " + (x + 0.5) + " -60 " + (z + 0.5) + " {NoAI:1b,Tags:[\"c" + i + "\"]}");
        }
    }

    static int cowX(int i) { return i * 3 - 30; }

    static int cowZ(int i) { return i % 2 == 0 ? -6 : 6; }

    @AfterEach
    void tearDown() throws Exception {
        stopTicks();
        OperatorOptions.install(null);
        sched.close();
        if (packs != null) packs.close();
    }

    private void fn(String name, String... lines) throws IOException {
        Files.writeString(functionDir.resolve(name + ".mcfunction"), String.join("\n", lines) + "\n");
    }

    private void tickTag(String value) throws IOException {
        Path dir = tmp.resolve("datapacks/mine/data/minecraft/tags/function");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("tick.json"), "{\"values\":[\"" + value + "\"]}");
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
        return core.commands().performPrefixedCommandAsync(CommandSource.console(core), command).orTimeout(30, TimeUnit.SECONDS).join();
    }

    private Integer score(String holder, String objective) {
        CommandResult r = run("scoreboard players get " + holder + " " + objective);
        return r.success() ? r.result() : null;
    }

    /** 50ms 간격 전역 틱(예산 되살리기·#tick 함수)을 돌린다 — 운영 서버의 전역 틱 흉내. */
    private void startTicks() {
        timer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        timer.scheduleAtFixedRate(() -> sched.global().execute(() -> { ticks[0]++; core.tick(); }), 0, 50, TimeUnit.MILLISECONDS);
    }

    private void stopTicks() throws Exception {
        if (timer == null) return;
        timer.shutdownNow();
        timer.awaitTermination(2, TimeUnit.SECONDS);
        timer = null;
        sched.global().execute(() -> { }).get(10, TimeUnit.SECONDS);
    }

    // ---------------------------------------------------------------- #tick 함수 실행률

    /** 소스마다 엔티티·region 을 건드리는 데이터 팩 문형. 줄마다 소스 20개가 각자 홉을 한다. */
    private static final String[] PER_ENTITY_LINES = {
            "execute as @e[type=cow] at @s run particle minecraft:flame ~ ~ ~ 0 0 0 0 1",
            "tag @e[type=cow] add x",
            "effect give @e[type=cow] minecraft:speed 1 0 true",
            "tag @e[type=cow] remove x",
            "execute as @e[type=cow] store result score @s px run data get entity @s Pos[0] 10",
            "execute as @e[type=cow] at @s if block ~ ~-1 ~ minecraft:stone run tag @s add z",
            "execute as @e[type=cow] run data modify entity @s Air set value 300s",
    };

    @Test
    void tickFunctionWithPerEntityStepsRunsEveryTick() throws Exception {
        String[] lines = Arrays.copyOf(PER_ENTITY_LINES, PER_ENTITY_LINES.length + 1);
        lines[lines.length - 1] = "scoreboard players add #runs o 1";   // 마지막 줄 — 함수가 끝까지 돈 횟수
        fn("tick", lines);
        tickTag("t:tick");
        load();
        startTicks();
        Thread.sleep(2000);
        stopTicks();
        Thread.sleep(200);   // 마지막 실행(아직 도는 중)이 끝나기를 기다린다
        int runs = score("#runs", "o");
        double ratio = (double) runs / ticks[0];
        System.out.printf("TICKFN per-entity: %d runs / %d ticks%n", runs, ticks[0]);
        assertTrue(runs <= ticks[0] + 1, "바닐라처럼 한 틱에 한 번보다 많이 돌 수 없다: " + runs + " / " + ticks[0]);
        // 회귀 전(소스·단계마다 1틱)은 약 0.05(초당 1번). 바닐라는 1.0
        assertTrue(ratio >= 0.8, "소 20마리 per-entity 줄 7개의 #tick 함수가 매 틱 돌아야 한다(바닐라 20/s): " + runs + " / " + ticks[0]);
    }

    // ---------------------------------------------------------------- 명령 한 줄 지연

    /** 고치기 전 기준(4코어, 운영 예산 20ms): 아래 줄 모두 50ms(한 단계) ~ 3초(소스 20 × 단계). */
    private static final String[] ONE_LINE = {
            "tag @e[type=cow] add y",
            "effect give @e[type=cow] minecraft:speed 1 0 true",
            "tp @e[type=cow] ~ ~ ~",
            "data get entity @e[type=cow,limit=1] Pos[0]",
            "data merge entity @e[type=cow,limit=1] {Air:300s}",
            "execute store result score #x o run data get entity @e[type=cow,limit=1] Pos[0]",
            "attribute @e[type=cow,limit=1] minecraft:generic.max_health get",
            "execute as @e[type=cow] run tag @s add y",
            "execute as @e[type=cow] at @s if block ~ ~-1 ~ minecraft:stone run tag @s add z",
            "execute as @e[type=cow] store result score @s px run data get entity @s Pos[0] 10",
            "execute as @e[type=cow] if data entity @s Pos",
            "execute as @e[type=cow] store result entity @s Air short 1 run scoreboard players get #x o",
            "execute as @e[type=cow] at @s rotated as @s run tp @s ~ ~ ~ ~ ~",
            "execute as @e[type=cow] at @s facing entity @s eyes run tag @s add f",
            "execute as @e[type=cow] run attribute @s minecraft:generic.max_health get",
    };

    @Test
    void selectorCommandsFinishWithinATickEach() throws Exception {
        startTicks();
        List<String> slow = new ArrayList<>();
        for (String c : ONE_LINE) {
            long[] ms = new long[5];
            for (int i = 0; i < ms.length; i++) {
                long t0 = System.nanoTime();
                run(c);   // 같은 줄을 거듭 돌리면 바닐라처럼 실패하는 줄도 있다(이미 붙은 태그) — 시간만 본다
                ms[i] = (System.nanoTime() - t0) / 1_000_000L;
            }
            Arrays.sort(ms);
            System.out.printf("STAGE %-90s median %d ms%n", c, ms[2]);
            // 한 단계가 한 틱(50ms)씩 밀리던 회귀를 잡는다. 고친 뒤 이 컨테이너 중앙값 1~15ms
            if (ms[2] >= 30) slow.add(c + " = " + ms[2] + "ms");
        }
        assertTrue(slow.isEmpty(), "엔티티 선택 명령 한 줄이 한 틱 가까이 걸린다(단계마다 1틱 회귀): " + slow);
    }

    @Test
    void spreadplayersPerSourceDoesNotWaitATickPerChunkRead() throws Exception {
        // spreadplayers 는 땅을 가상 스레드에서 읽는다 — 그 스레드도 명령 범위여야 청크 읽기 홉이 틱마다 밀리지 않는다(검토 R1).
        // 기준 3050ms, 가상 스레드만 범위 밖이면 약 1000ms, 고친 뒤 약 115ms
        startTicks();
        long[] ms = new long[3];
        for (int i = 0; i < ms.length; i++) {
            long t0 = System.nanoTime();
            run("execute as @e[type=cow] at @s run spreadplayers ~ ~ 0 1 false @s");
            ms[i] = (System.nanoTime() - t0) / 1_000_000L;
        }
        Arrays.sort(ms);
        System.out.println("STAGE spreadplayers per source median " + ms[1] + " ms");
        assertTrue(ms[1] < 500, "소 20마리 spreadplayers 가 소스마다 1틱 이상 걸린다: " + ms[1] + " ms");
    }

    // ---------------------------------------------------------------- 결과 동일성

    /** 문을 켜고 끈 두 변형에서 같은 결과(소스 순서·점수·태그·NBT)가 나와야 한다. */
    private String effects() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < COWS; i++) {
            String self = "@e[tag=c" + i + ",limit=1]";
            sb.append(i).append(':')
                    .append(score(self, "px")).append(',')
                    .append(score(self, "o")).append(',')
                    .append(run("execute if entity @e[tag=c" + i + ",tag=z]").result()).append(',')
                    .append(run("execute if entity @e[tag=c" + i + ",tag=x]").result()).append(',')
                    .append(run("data get entity " + self + " Air").result()).append(',')
                    .append(run("data get entity " + self + " Pos[1] 100").result()).append(';');
        }
        return sb.toString();
    }

    private static final String[] ORDER_SENSITIVE = {
            "execute as @e[type=cow] at @s store result score @s o run scoreboard players add #seq o 1",   // 소스 적용 순서를 점수로 새긴다
            "execute as @e[type=cow] store result score @s px run data get entity @s Pos[0] 10",
            "execute as @e[type=cow] at @s if block ~ ~-1 ~ minecraft:stone run tag @s add z",
            "execute as @e[type=cow,tag=z] run data modify entity @s Air set value 123s",
            "execute as @e[type=cow,tag=!z] at @s run tp @s ~ ~0.5 ~",
            "tag @e[type=cow,tag=z] add x",
            "execute as @e[type=cow] at @s if entity @e[type=cow,tag=x,distance=..1] run scoreboard players add #near o 1",
    };

    @Test
    void perEntityResultsAreTheSameWithTheGateOpenAndClosed() throws Exception {
        fn("order", ORDER_SENSITIVE);
        load();
        startTicks();
        String[] out = new String[2];
        int[] near = new int[2];
        boolean[] modes = {true, false};
        for (int m = 0; m < 2; m++) {
            drain(modes[m]);
            run("scoreboard players reset * o");
            run("scoreboard players reset * px");
            run("tag @e[type=cow] remove z");
            run("tag @e[type=cow] remove x");
            for (int i = 0; i < COWS; i++) run("tp @e[tag=c" + i + "] " + (cowX(i) + 0.5) + " -60 " + (cowZ(i) + 0.5));
            run("data modify entity @e[type=cow,limit=1] Air set value 300s");
            run("execute as @e[type=cow] run data modify entity @s Air set value 300s");
            CommandResult fr = run("function t:order");
            System.out.println("FN " + modes[m] + " " + fr.success() + " " + fr.result());
            near[m] = score("#near", "o") == null ? -1 : score("#near", "o");
            out[m] = effects();
            System.out.println("RESULT " + modes[m] + " " + out[m]);
        }
        assertEquals(out[1], out[0], "명령 큐 즉시 처리 문을 켜도(T-150 후속 단계 포함) 결과가 같아야 한다");
        assertEquals(near[1], near[0]);
        // 바닐라 기대값 몇 가지: 짝수 번(돌 위)만 z, 그 소의 Air 는 123, 나머지는 반 칸 올라감, x 좌표 ×10
        for (int i = 0; i < COWS; i++) {
            String self = "@e[tag=c" + i + ",limit=1]";
            assertEquals(cowX(i) * 10 + 5, score(self, "px"), "data get entity @s Pos[0] 10 → 소 " + i);
            assertEquals(i % 2 == 0 ? 1 : 0, run("execute if entity @e[tag=c" + i + ",tag=z]").result(), "if block 돌 → 소 " + i);
            assertEquals(i % 2 == 0 ? 123 : 300, run("data get entity " + self + " Air").result(), "Air → 소 " + i);
            assertEquals(i % 2 == 0 ? -6000 : -5950, run("data get entity " + self + " Pos[1] 100").result(), "tp ~0.5 → 소 " + i);
        }
        java.util.Set<Integer> order = new java.util.TreeSet<>();
        for (int i = 0; i < COWS; i++) order.add(score("@e[tag=c" + i + ",limit=1]", "o"));
        assertEquals(COWS, order.size(), "as @e 의 소스마다 한 번씩, 차례대로 적용된다(1.." + COWS + "): " + order);
        assertEquals(COWS / 2, near[0], "x 태그 소 옆(..1)에 있는 소는 자기 자신뿐 — 짝수 번 10마리");
    }
}
