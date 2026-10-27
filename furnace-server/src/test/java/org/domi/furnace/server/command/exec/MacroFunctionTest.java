// 함수 매크로(S7): '$' 줄과 $(name) 치환, /function <name> <arguments> · with block|entity|storage, 수 포맷, 캐시 8, 오류 3종,
// 매크로를 포함한 태그(#tick 에서 인자 없이 → 실행만 실패), 평범한 줄과 매크로 줄의 순서.
package org.domi.furnace.server.command.exec;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
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
import org.domi.furnace.server.function.CommandFunction;
import org.domi.furnace.server.function.FunctionInstantiationException;
import org.domi.furnace.server.function.MacroFunction;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MacroFunctionTest {

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
        DeferredResultTest.spawnCows(core, 1);
        core.addConsoleListener(t -> messages.add(t.getString()));
        Path vanilla = tmp.resolve("vanilla");
        Files.createDirectories(vanilla.resolve("data/minecraft/tags/item"));
        Files.writeString(vanilla.resolve("data/minecraft/tags/item/x.json"), "{\"values\":[]}");
        Path pack = tmp.resolve("datapacks/mine");
        Files.createDirectories(pack);
        Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"description\":\"t\",\"pack_format\":48}}");
        functionDir = pack.resolve("data/t/function");
        Files.createDirectories(functionDir);
        fn("set", "$scoreboard players set #$(name) o $(v)");
        fn("m", "$scoreboard players set #m o $(v)");
        fn("order", "scoreboard players set #o o 1", "$scoreboard players add #o o $(v)", "scoreboard players operation #o o *= #two o");
        fn("list", "$data modify storage t:out l set value $(l)");
        fn("cpd", "$data modify storage t:out c set value $(c)");
        fn("str", "$data modify storage t:out s set value \"$(s)\"");
        fn("hp", "$scoreboard players set #hp o $(Health)");
        fn("bx", "$scoreboard players set #bx o $(x)");
        fn("nested", "$function t:m {v:$(v)}");
        fn("novars", "$say hi");
        fn("unterminated", "$say $(v");
        fn("badname", "$say $(a-b)");
        fn("seq", "$data modify storage t:out seq append value \"$(Health)\"", "data modify storage t:out seq append value \"x\"");
        fn("plain", "scoreboard players add #plain o 1");
        fn("ontick", "scoreboard players add #tick o 1");
        tag("t", "withmacro", "t:m", "t:plain");
        tag("minecraft", "tick", "t:m", "t:ontick");
        packs = DataPackManager.start(vanilla, tmp, org.domi.furnace.server.world.storage.LevelData.create("world", 1L, true, false,
                org.domi.furnace.server.world.GameMode.SURVIVAL, false, org.domi.furnace.server.world.Difficulty.EASY, List.of("vanilla"), List.of()),
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt"), true, List.of("vanilla"), List.of());
        core.dataPacks(packs);
        core.loadFunctions();
        onGlobal(() -> { });
        run("scoreboard objectives add o dummy");
        run("scoreboard players set #two o 2");
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

    private Integer score(String holder) {
        CommandResult r = run("scoreboard players get " + holder + " o");
        return r.success() ? r.result() : null;
    }

    private MacroFunction macro(String id) {
        return (MacroFunction) core.functions().library().function(ResourceId.parse(id)).orElseThrow();
    }

    // ---------------------------------------------------------------- 로드

    @Test
    void macroFunctionsLoadAndBadTemplatesAreExcluded() {
        var lib = core.functions().library();
        assertInstanceOf(MacroFunction.class, lib.function(ResourceId.parse("t:m")).orElseThrow());
        assertInstanceOf(CommandFunction.PlainTextFunction.class, lib.function(ResourceId.parse("t:plain")).orElseThrow());
        assertEquals(List.of("name", "v"), macro("t:set").parameters());
        assertTrue(lib.function(ResourceId.parse("t:novars")).isEmpty(), "Macro without variables 는 로드 실패");
        assertTrue(lib.function(ResourceId.parse("t:unterminated")).isEmpty());
        assertTrue(lib.function(ResourceId.parse("t:badname")).isEmpty());
        assertEquals(List.of("t:m", "t:plain"), lib.tag(ResourceId.parse("t:withmacro")).stream().map(f -> f.id().toString()).toList());
    }

    // ---------------------------------------------------------------- 치환·수 포맷

    @Test
    void numberFormatFollowsVanilla() {
        assertEquals(".100000001490116", MacroFunction.stringify(0.1f), "float 은 double 로 넓혀 소수 15자리, DecimalFormat(\"#\") 라 앞 0 이 없다");
        assertEquals("1.5", MacroFunction.stringify(1.5d));
        assertEquals("3", MacroFunction.stringify(3.0d), "소수부 0 이면 정수꼴");
        assertEquals(".333333333333333", MacroFunction.stringify(1.0 / 3));
        assertEquals("12345678912345680000", MacroFunction.stringify(1.234567891234568e19), "지수 표기 없음");
        assertEquals("-0", MacroFunction.stringify(-0.0d));
        assertEquals("1", MacroFunction.stringify((byte) 1), "byte(불리언) 는 접미사 없이");
        assertEquals("2", MacroFunction.stringify((short) 2));
        assertEquals("3", MacroFunction.stringify(3L));
        assertEquals("4", MacroFunction.stringify(4));
        assertEquals("a b\"c", MacroFunction.stringify("a b\"c"), "문자열은 따옴표 없이 원문");
        NbtList list = new NbtList(org.domi.furnace.server.net.io.nbt.NbtIo.TAG_INT);
        list.add(1);
        list.add(2);
        assertEquals("[1,2]", MacroFunction.stringify(list));
        NbtCompound c = new NbtCompound();
        c.putString("b", "x y");
        c.putByte("a", (byte) 1);
        assertEquals("{a:1b,b:\"x y\"}", MacroFunction.stringify(c), "compound 는 키 정렬 SNBT");
        assertEquals("[I;1,2]", MacroFunction.stringify(new int[]{1, 2}));
    }

    @Test
    void substitutesArgumentsEndToEnd() {
        run("function t:set {name:\"q\",v:5}");
        assertEquals(5, score("#q"));
        run("function t:m {v:true}");
        assertEquals(1, score("#m"), "true 는 byte 1 → \"1\"");
        run("function t:list {l:[1,2,3]}");
        assertEquals(3, run("data get storage t:out l").result());
        run("function t:cpd {c:{a:1,b:\"x\"}}");
        assertEquals(1, run("data get storage t:out c.a").result());
        run("function t:str {s:\"hello\"}");
        assertEquals(5, run("data get storage t:out s").result());
        run("function t:nested {v:7}");
        assertEquals(7, score("#m"), "매크로 줄 안의 function 호출");
    }

    @Test
    void plainAndMacroLinesKeepOrder() {
        run("function t:order {v:3}");
        assertEquals(8, score("#o"), "(1 + 3) * 2 — 줄 순서가 바뀌면 다른 값");
    }

    // ---------------------------------------------------------------- 캐시

    @Test
    void instantiationIsCachedPerArgumentsUpToEight() throws Exception {
        MacroFunction m = macro("t:m");
        var d = core.commands().getDispatcher();
        NbtCompound a = new NbtCompound();
        a.putInt("v", 1);
        var first = m.instantiate(a, d);
        assertSame(first, m.instantiate(a.copy(), d), "같은 인자 문자열이면 같은 인스턴스");
        NbtCompound b = new NbtCompound();
        b.putLong("v", 1L);
        assertSame(first, m.instantiate(b, d), "키는 치환된 문자열(1 과 1L 은 같다)");
        assertTrue(first.id().path().startsWith("m/"), first.id().toString());
        for (int i = 2; i <= 9; i++) {
            NbtCompound x = new NbtCompound();
            x.putInt("v", i);
            m.instantiate(x, d);
        }
        assertEquals(8, m.cachedInstances());
        assertNotSame(first, m.instantiate(a, d), "가장 오래 안 쓴 것(1)이 밀려났다");
        // LRU: 최근에 쓴 것은 남는다
        NbtCompound nine = new NbtCompound();
        nine.putInt("v", 9);
        var ninth = m.instantiate(nine, d);
        assertSame(ninth, m.instantiate(nine, d));
    }

    // ---------------------------------------------------------------- 오류

    @Test
    void threeInstantiationErrors() {
        CommandResult r = run("function t:m");
        assertEquals(new CommandResult(false, 0, 1), r);
        assertTrue(messages.contains("Failed to instantiate function t:m: Missing arguments to function t:m"), messages.toString());
        r = run("function t:m {x:1}");
        assertEquals(new CommandResult(false, 0, 1), r);
        assertTrue(messages.contains("Failed to instantiate function t:m: Missing argument v to function t:m"), messages.toString());
        r = run("function t:m {v:abc}");
        assertEquals(new CommandResult(false, 0, 1), r);
        assertTrue(messages.stream().anyMatch(s -> s.startsWith(
                "Failed to instantiate function t:m: While instantiating macro t:m: Command 'scoreboard players set #m o abc' caused error: ")),
                messages.toString());
        assertNull(score("#m"));
        assertThrows(FunctionInstantiationException.class, () -> macro("t:m").instantiate(null, core.commands().getDispatcher()));
    }

    @Test
    void tagWithMacroFailsOnlyAtExecution() throws Exception {
        CommandResult r = run("function #t:withmacro");
        assertFalse(r.success());
        assertTrue(messages.stream().anyMatch(s -> s.contains("Missing arguments to function t:m")), messages.toString());
        assertNull(score("#plain"), "바닐라: 인스턴스화 오류가 던져지면 그 뒤 함수는 큐에 들어가지 않는다");
        run("function #t:withmacro {v:4}");
        assertEquals(4, score("#m"));
        assertEquals(1, score("#plain"));
        // #minecraft:tick 안의 매크로 함수: 조용히 건너뛰고 나머지는 돈다
        for (int i = 0; i < 2; i++) onGlobal(core::tick);
        assertEquals(2, score("#tick"));
        assertEquals(4, score("#m"));
        // if function 은 fork 노드라 BuildContexts(바닐라 hy.java:33-35)가 수정자 실행기 호출 전에 forked 를 켠다 → 오류 메시지를 숨긴다
        run("execute if function t:m run scoreboard players set #if o 1");
        assertNull(score("#if"));
    }

    // ---------------------------------------------------------------- with

    @Test
    void withStorageAndPath() {
        run("data modify storage t:src args set value {v:11,inner:{v:12},num:3}");
        run("function t:m with storage t:src args");
        assertEquals(11, score("#m"));
        run("function t:m with storage t:src args.inner");
        assertEquals(12, score("#m"));
        CommandResult r = run("function t:m with storage t:src args.num");
        assertEquals(new CommandResult(false, 0, 1), r);
        assertTrue(messages.contains("Invalid argument type: INT, expected Compound"), messages.toString());
        r = run("function t:m with storage t:src args.none");
        assertFalse(r.success());
        run("data modify storage t:whole v set value 13");
        run("function t:m with storage t:whole");
        assertEquals(13, score("#m"));
    }

    @Test
    void withEntityAndBlockGoThroughRegion() {
        run("data merge entity @e[type=cow,limit=1] {Health:7.0f}");
        run("function t:hp with entity @e[type=cow,limit=1]");
        CommandResult r;
        assertEquals(7, score("#hp"), "float 7.0 → \"7\"");
        r = run("function t:hp with entity @e[type=zombie,limit=1]");
        assertEquals(new CommandResult(false, 0, 1), r, "대상 없음은 hp.b 규칙: 오류 + onFailure");
        run("setblock 3 70 0 minecraft:chest");
        run("function t:bx with block 3 70 0");
        assertEquals(3, score("#bx"));
        r = run("function t:bx with block 4 70 0");
        assertEquals(new CommandResult(false, 0, 1), r);
        assertTrue(messages.stream().anyMatch(s -> s.contains("not a block entity")), messages.toString());
        r = run("function t:m with entity @e[type=cow,limit=1] Pos");
        assertFalse(r.success());
        assertTrue(messages.contains("Invalid argument type: LIST, expected Compound"), messages.toString());
    }

    @Test
    void withEntityKeepsSourceOrderAcrossRegions() throws Exception {
        // 소 3마리(둘은 0 근처, 하나는 다른 region) — 소스마다 그 소의 값으로 치환되고, 함수 전체가 소스 순서대로 돈다
        core.world().awaitChunk(new ChunkPos(40, 0));
        var done = new java.util.concurrent.CountDownLatch(1);
        core.world().runAt(640.5, 0.5, region -> {
            var lvl = region.level();
            var cow = new org.domi.furnace.server.entity.Cow(lvl, lvl.nextEntityId());
            cow.moveTo(640.5, -60, 0.5, 0, 0);
            lvl.addFreshEntity(cow);
            done.countDown();
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        DeferredResultTest.spawnCows(core, 1);
        run("execute as @e[type=cow] store result entity @s Health float 1 run scoreboard players add #k o 1");   // 1,2,3 서로 다르게
        assertEquals(3, run("execute if entity @e[type=cow]").result());
        run("execute as @e[type=cow] run data modify storage t:out ref append from entity @s Health");
        run("data remove storage t:out seq");
        run("execute as @e[type=cow] run function t:seq with entity @s");
        // ref: [h1,h2,h3], seq: ["h1","x","h2","x","h3","x"] 이어야 한다(역순·끼어들기면 다르다)
        List<String> ref = new ArrayList<>(), seq = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            run("data get storage t:out ref[" + i + "]");
            ref.add(String.join("", messages));
        }
        for (int i = 0; i < 6; i++) {
            run("data get storage t:out seq[" + i + "]");
            seq.add(String.join("", messages));
        }
        for (int i = 0; i < 3; i++) {
            String h = ref.get(i).substring(ref.get(i).lastIndexOf(' ') + 1).replace(".0f", "");
            assertTrue(seq.get(2 * i).endsWith("\"" + h + "\""), "소스 " + i + " 의 값 — ref=" + ref + " seq=" + seq);
            assertTrue(seq.get(2 * i + 1).endsWith("\"x\""), seq.toString());
        }
    }
}
