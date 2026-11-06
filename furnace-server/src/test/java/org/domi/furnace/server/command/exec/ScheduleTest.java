// /schedule(S6): 지연 틱 수, append·replace, clear 개수, 같은 이름·시각 중복, 같은 시각 순서, 함수 태그, 없어진 함수, tick freeze,
// level.dat ScheduledEvents 왕복.
package org.domi.furnace.server.command.exec;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.data.DataPackManager;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.function.ScheduledEvents;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScheduleTest {

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;
    DataPackManager packs;
    Path functionDir;
    final List<String> messages = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        core.world().awaitChunk(new ChunkPos(0, 0));
        core.addConsoleListener(t -> messages.add(t.getString()));
        Path vanilla = tmp.resolve("vanilla");
        Files.createDirectories(vanilla.resolve("data/minecraft/tags/item"));
        Files.writeString(vanilla.resolve("data/minecraft/tags/item/x.json"), "{\"values\":[]}");
        Path pack = tmp.resolve("datapacks/mine");
        Files.createDirectories(pack);
        Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"description\":\"t\",\"pack_format\":48}}");
        functionDir = pack.resolve("data/t/function");
        Files.createDirectories(functionDir);
        fn("inc", "scoreboard players add #n o 1");
        fn("a", "scoreboard players set #seq o 1");
        fn("b", "scoreboard players set #seq o 2");
        fn("m1", "scoreboard players add #m o 1");
        fn("m2", "scoreboard players add #m o 10");
        fn("probefn", "probe");
        fn("settime", "time set 0");
        Path tags = tmp.resolve("datapacks/mine/data/t/tags/function");
        Files.createDirectories(tags);
        Files.writeString(tags.resolve("both.json"), "{\"values\":[\"t:m1\",\"t:m2\"]}");
        packs = DataPackManager.start(vanilla, tmp, org.domi.furnace.server.world.storage.LevelData.create("world", 1L, true, false,
                org.domi.furnace.server.world.GameMode.SURVIVAL, false, org.domi.furnace.server.world.Difficulty.EASY, List.of("vanilla"), List.of()),
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt"), true, List.of("vanilla"), List.of());
        // 예약 함수가 도는 동안 다른 스레드가 clock getter 를 부를 수 있는지 재는 명령(함수 로드 전에 등록)
        core.commands().getDispatcher().register(org.domi.furnace.server.command.brigadier.LiteralArgumentBuilder.<CommandSource>literal("probe").executes(c -> {
            Thread t = new Thread(() -> core.clock().rainLevel());
            t.start();
            try {
                t.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            probeBlocked = t.isAlive();
            return 1;
        }));
        core.dataPacks(packs);
        core.loadFunctions();
        onGlobal(() -> { });
        run("scoreboard objectives add o dummy");
    }

    volatile boolean probeBlocked;

    @AfterEach
    void tearDown() {
        sched.close();
        if (packs != null) packs.close();
    }

    private void fn(String name, String... lines) throws Exception {
        Files.writeString(functionDir.resolve(name + ".mcfunction"), String.join("\n", lines) + "\n");
    }

    private void onGlobal(Runnable r) throws Exception { sched.global().execute(r).get(10, TimeUnit.SECONDS); }

    private void tick(int n) throws Exception {
        for (int i = 0; i < n; i++) onGlobal(core::tick);
    }

    private CommandResult run(String command) {
        messages.clear();
        return core.commands().performPrefixedCommandAsync(CommandSource.console(core), command).orTimeout(20, TimeUnit.SECONDS).join();
    }

    private int score(String holder) {
        CommandResult r = run("scoreboard players get " + holder + " o");
        return r.success() ? r.result() : 0;
    }

    @Test
    void sameTickIsRejected() {
        CommandResult r = run("schedule function t:inc 0");
        assertFalse(r.success());
        assertTrue(messages.stream().anyMatch(m -> m.contains("Can't schedule for current tick")), messages.toString());
        assertEquals(0, core.scheduledEvents().size());
    }

    @Test
    void runsAfterExactlyNTicksAndReportsTriggerTime() throws Exception {
        long t0 = core.clock().gameTime();
        CommandResult r = run("schedule function t:inc 3t");
        assertEquals(new CommandResult(true, (int) (t0 + 3), 1), r, "반환값은 triggerTime 을 Integer.MAX 로 나눈 나머지");
        assertEquals(List.of("Scheduled function 't:inc' in 3 tick(s) at gametime " + (t0 + 3)), messages);
        tick(2);
        assertEquals(0, score("#n"), "2틱 뒤에는 아직");
        tick(1);
        assertEquals(1, score("#n"), "3틱째에 실행");
        tick(3);
        assertEquals(1, score("#n"), "한 번만");
        assertEquals(0, core.scheduledEvents().size());
    }

    @Test
    void timeUnitsAreTicksSecondsDays() {
        long t0 = core.clock().gameTime();
        assertEquals((int) (t0 + 40), run("schedule function t:inc 2s").result());
        assertEquals((int) (t0 + 48000), run("schedule function t:inc 2d append").result());
        assertEquals((int) (t0 + 7), run("schedule function t:inc 7 append").result());
    }

    @Test
    void replaceDropsAllSameNameAppendKeeps() throws Exception {
        run("schedule function t:inc 5");
        run("schedule function t:inc 3 replace");
        assertEquals(1, core.scheduledEvents().size(), "replace 는 같은 이름 전부를 지우고 새로 넣는다");
        tick(6);
        assertEquals(1, score("#n"));
        run("schedule function t:inc 2 append");
        run("schedule function t:inc 4 append");
        assertEquals(2, core.scheduledEvents().size(), "append 는 둘 다 남긴다");
        tick(5);
        assertEquals(3, score("#n"));
    }

    @Test
    void sameNameAndSameTimeIsNotAddedTwice() throws Exception {
        run("schedule function t:inc 4 append");
        run("schedule function t:inc 4 append");
        assertEquals(1, core.scheduledEvents().size());
        tick(4);
        assertEquals(1, score("#n"));
    }

    @Test
    void sameTimeRunsInRegistrationOrder() throws Exception {
        run("schedule function t:a 2");
        run("schedule function t:b 2");
        tick(2);
        assertEquals(2, score("#seq"), "나중에 등록한 b 가 마지막에 돈다");
        run("schedule function t:b 2");
        run("schedule function t:a 2");
        tick(2);
        assertEquals(1, score("#seq"), "등록 순서가 바뀌면 결과도 바뀐다");
    }

    @Test
    void clearReturnsRemovedCountAndFailsWhenNone() {
        run("schedule function t:inc 5 append");
        run("schedule function t:inc 9 append");
        run("schedule function t:a 5");
        CommandResult r = run("schedule clear t:inc");
        assertEquals(new CommandResult(true, 2, 1), r);
        assertEquals(List.of("Removed 2 schedule(s) with id t:inc"), messages);
        assertEquals(1, core.scheduledEvents().size());
        CommandResult none = run("schedule clear t:inc");
        assertFalse(none.success());
        assertTrue(messages.stream().anyMatch(m -> m.contains("No schedules with id t:inc")), messages.toString());
    }

    @Test
    void functionTagsRunEachFunctionAndAreNamedWithHash() throws Exception {
        long t0 = core.clock().gameTime();
        CommandResult r = run("schedule function #t:both 2");
        assertEquals((int) (t0 + 2), r.result());
        assertEquals(List.of("Scheduled tag 't:both' in 2 ticks at gametime " + (t0 + 2)), messages);
        assertTrue(core.scheduledEvents().names().contains("#t:both"));
        assertFalse(run("schedule clear t:both").success(), "함수 이름으로는 지워지지 않는다");
        tick(2);
        assertEquals(11, score("#m"), "태그의 함수가 모두 돈다");
        run("schedule function #t:both 5");
        assertEquals(1, run("schedule clear #t:both").result());
    }

    @Test
    void unknownFunctionIsRejectedAtCommandTime() {
        CommandResult r = run("schedule function t:nope 5");
        assertFalse(r.success());
        assertEquals(0, core.scheduledEvents().size());
    }

    @Test
    void functionMissingAtRunTimeIsSilentlyIgnored() throws Exception {
        long t = core.clock().gameTime() + 1;
        onGlobal(() -> core.scheduledEvents().schedule("x:gone", t, new ScheduledEvents.Callback(false, ResourceId.parse("x:gone"))));
        onGlobal(() -> core.scheduledEvents().schedule("#x:gone", t, new ScheduledEvents.Callback(true, ResourceId.parse("x:gone"))));
        tick(1);
        assertEquals(0, core.scheduledEvents().size(), "이벤트는 소비되고 아무 일도 없다");
    }

    @Test
    void frozenClockPausesScheduleAndStepAdvancesIt() throws Exception {
        run("schedule function t:inc 2");
        run("tick freeze");
        onGlobal(core::scheduledTick);
        onGlobal(core::scheduledTick);
        onGlobal(core::scheduledTick);
        assertEquals(0, score("#n"), "freeze 중에는 gameTime 이 안 가므로 예약도 멈춘다");
        run("tick step 2");
        onGlobal(core::scheduledTick);
        assertEquals(0, score("#n"), "step 1 개로는 아직");
        onGlobal(core::scheduledTick);
        assertEquals(1, score("#n"), "step 으로 gameTime 이 2 가면 실행");
        run("tick unfreeze");
        long g = core.clock().gameTime();
        onGlobal(core::scheduledTick);
        assertEquals(g + 1, core.clock().gameTime(), "unfreeze 뒤에는 틱마다 gameTime 이 다시 간다");
        run("schedule function t:inc 1");
        onGlobal(core::scheduledTick);
        assertEquals(2, score("#n"), "unfreeze 뒤 새 예약도 정상 실행");
    }

    @Test
    void scheduledFunctionRunsOutsideClockLock() throws Exception {
        run("schedule function t:probefn 1");
        tick(1);
        assertEquals(0, core.scheduledEvents().size());
        assertFalse(probeBlocked, "예약 함수가 도는 동안 다른 스레드의 clock getter 가 막히면 안 된다");
    }

    @Test
    void timeSetInScheduledFunctionThenDayTimeAdvancesLikeVanilla() throws Exception {
        core.clock().setDayTime(5000);
        run("schedule function t:settime 1");
        tick(1);
        assertEquals(1, core.clock().dayTime(), "gameTime+1 → 예약 함수(time set 0) → dayTime+1 = 1 (바닐라 순서)");
    }

    @Test
    void restartRestoresEventsFromLevelDatFileAndRunsThem(@TempDir Path dir) throws Exception {
        run("schedule function t:inc 3");
        run("schedule function #t:both 5 append");
        tick(1);
        core.writeLevelData();
        long savedTime = core.clock().gameTime();
        try (var storage = org.domi.furnace.server.world.storage.LevelStorage.open(dir)) {
            storage.saveLevelData(core.levelData());
        }
        // "재시작": 파일에서 다시 읽은 LevelData 로 새 서버를 띄우고 같은 함수 라이브러리를 올린다
        org.domi.furnace.server.world.storage.LevelData loaded;
        try (var storage = org.domi.furnace.server.world.storage.LevelStorage.open(dir)) {
            loaded = storage.loadLevelData();
        }
        Path tmp2 = dir.resolve("srv2");
        Files.createDirectories(tmp2);
        ServerCore core2 = TestServers.core(tmp2, sched, reg -> new org.domi.furnace.server.world.gen.SuperflatWorldSource(reg, 0), loaded);
        core2.dataPacks(packs);
        core2.loadFunctions();
        onGlobal(() -> { });
        assertEquals(savedTime, core2.clock().gameTime());
        assertEquals(java.util.Set.of("t:inc", "#t:both"), core2.scheduledEvents().names());
        core2.commands().performPrefixedCommandAsync(CommandSource.console(core2), "scoreboard objectives add o dummy").join();
        for (int i = 0; i < 4; i++) sched.global().execute(core2::tick).get(10, TimeUnit.SECONDS);
        assertEquals(0, core2.scheduledEvents().size(), "남은 시간(2틱, 4틱) 뒤 둘 다 실행됐다");
        int n = core2.commands().performPrefixedCommandAsync(CommandSource.console(core2), "scoreboard players get #n o").join().result();
        int m = core2.commands().performPrefixedCommandAsync(CommandSource.console(core2), "scoreboard players get #m o").join().result();
        assertEquals(1, n);
        assertEquals(11, m);
    }

    @Test
    void closedCommandQueueKeepsEventsPending() throws Exception {
        run("schedule function t:inc 1");
        onGlobal(() -> core.commands().queue().close());
        tick(2);
        assertEquals(1, core.scheduledEvents().size(), "큐가 닫혔으면 이벤트를 꺼내지 않아 저장에 남는다");
    }

    @Test
    void levelDataRoundTripKeepsEventsAndOrder() throws Exception {
        long t0 = core.clock().gameTime();
        run("schedule function t:b 9");
        run("schedule function #t:both 4 append");
        run("schedule function t:a 4 append");
        core.writeLevelData();
        NbtCompound data = core.levelData().toNbt(0L);
        NbtList saved = data.getList("ScheduledEvents");
        assertEquals(3, saved.size());
        NbtCompound first = (NbtCompound) saved.get(0);
        assertEquals("#t:both", first.getString("Name"));
        assertEquals(t0 + 4, first.getLong("TriggerTime"), "TriggerTime 은 절대 gameTime");
        assertEquals("minecraft:function_tag", first.getCompound("Callback").getString("Type"));
        assertEquals("t:both", first.getCompound("Callback").getString("Name"));
        assertEquals("t:a", ((NbtCompound) saved.get(1)).getString("Name"), "같은 시각은 등록 순서");
        assertEquals("minecraft:function", ((NbtCompound) saved.get(1)).getCompound("Callback").getString("Type"));
        assertEquals("t:b", ((NbtCompound) saved.get(2)).getString("Name"));
        // 다시 읽어 실행한다(서버 재시작과 같다)
        ScheduledEvents reloaded = new ScheduledEvents();
        reloaded.load(saved);
        assertEquals(3, reloaded.size());
        List<String> ran = new ArrayList<>();
        reloaded.tick(t0 + 9, cb -> ran.add((cb.tag() ? "#" : "") + cb.id()));
        assertEquals(List.of("#t:both", "t:a", "t:b"), ran);
        assertEquals(0, reloaded.size());
    }

    @Test
    void loadSkipsUnknownCallbackTypesAndKeepsLateEventsPending() {
        NbtList in = new NbtList(org.domi.furnace.server.net.io.nbt.NbtIo.TAG_COMPOUND);
        in.add(entry("t:inc", 50, "minecraft:function", "t:inc"));
        in.add(entry("weird", 10, "minecraft:unknown_type", "t:inc"));
        in.add(entry("t:old", 3, "minecraft:function", "t:old"));
        ScheduledEvents ev = new ScheduledEvents();
        ev.load(in);
        assertEquals(2, ev.size(), "모르는 Type 은 건너뛴다");
        List<String> ran = new ArrayList<>();
        ev.tick(5, cb -> ran.add(cb.id().toString()));
        assertEquals(List.of("t:old"), ran, "이미 지난 시각의 이벤트는 로드 직후 첫 틱에 돈다");
        assertEquals(1, ev.size());
    }

    private static NbtCompound entry(String name, long time, String type, String fn) {
        NbtCompound t = new NbtCompound();
        t.putString("Name", name);
        t.putLong("TriggerTime", time);
        NbtCompound cb = new NbtCompound();
        cb.putString("Type", type);
        cb.putString("Name", fn);
        t.putCompound("Callback", cb);
        return t;
    }
}
