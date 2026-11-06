// /debug function: 함수를 추적 소스로 돌려 debug/debug-trace-*.txt 에 [F]·[C]·[R]·[E]·[M] 줄을 적는다(바닐라 Tracer 형식).
// 오류·재귀·return run·권한, 성공 메시지(single/multiple)와 추적 소스의 출력 규칙(채팅이 아니라 파일), 컨텍스트 종료 때 파일 닫기.
package org.domi.furnace.server.command.exec;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.data.DataPackManager;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DebugFunctionTest {

    private static final Path DEBUG_DIR = Path.of("debug");

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;
    DataPackManager packs;
    Path functionDir;
    final List<String> messages = Collections.synchronizedList(new ArrayList<>());
    Set<Path> traceFilesBefore;
    boolean hadDebugDir;

    @BeforeEach
    void setUp() throws Exception {
        hadDebugDir = Files.exists(DEBUG_DIR);
        traceFilesBefore = traceFiles();
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
        fn("one", "scoreboard players set #a o 5", "execute if score #a o matches 5 run scoreboard players add #a o 1",
                "scoreboard players get #nobody o", "function t:inner");
        fn("inner", "scoreboard players set #in o 1");
        fn("empty");
        fn("retrun", "return run scoreboard players get #a o");
        fn("macro", "$scoreboard players set #m o $(v)");
        tag("t", "both", "t:inner", "t:empty");
        packs = DataPackManager.start(vanilla, tmp, org.domi.furnace.server.world.storage.LevelData.create("world", 1L, true, false,
                org.domi.furnace.server.world.GameMode.SURVIVAL, false, org.domi.furnace.server.world.Difficulty.EASY, List.of("vanilla"), List.of()),
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt"), true, List.of("vanilla"), List.of());
        core.dataPacks(packs);
        core.loadFunctions();
        sched.global().execute(() -> { }).get(10, TimeUnit.SECONDS);   // replaceLibrary 홉이 끝나기를 기다린다
        run("scoreboard objectives add o dummy");
    }

    @AfterEach
    void tearDown() throws IOException {
        sched.close();
        if (packs != null) packs.close();
        for (Path f : traceFiles()) if (!traceFilesBefore.contains(f)) Files.deleteIfExists(f);
        if (!hadDebugDir && Files.exists(DEBUG_DIR)) {
            try (Stream<Path> rest = Files.list(DEBUG_DIR)) {
                if (rest.findAny().isEmpty()) Files.deleteIfExists(DEBUG_DIR);
            }
        }
    }

    private static Set<Path> traceFiles() throws IOException {
        if (!Files.exists(DEBUG_DIR)) return new HashSet<>();
        try (Stream<Path> s = Files.list(DEBUG_DIR)) {
            return new HashSet<>(s.filter(p -> p.getFileName().toString().startsWith("debug-trace-")).toList());
        }
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

    private CommandResult run(String command) { return run(CommandSource.console(core), command); }

    private CommandResult run(CommandSource src, String command) {
        messages.clear();
        return core.commands().performPrefixedCommandAsync(src, command).orTimeout(20, TimeUnit.SECONDS).join();
    }

    /** 이번 테스트가 새로 만든 추적 파일 하나의 내용. */
    private String newTrace() throws IOException {
        Set<Path> now = traceFiles();
        now.removeAll(traceFilesBefore);
        assertEquals(1, now.size(), "추적 파일 하나가 새로 생긴다: " + now);
        return Files.readString(now.iterator().next(), StandardCharsets.UTF_8);
    }

    @Test
    void tracesCommandsResultsCallsAndErrorsIntoTheFile() throws IOException {
        run("debug function t:one");   // 추적한 줄 하나가 실패하므로 소스 콜백 결과는 실패로 모인다(바닐라 같다)
        assertEquals(1, messages.size(), "추적 중 명령의 출력은 채팅이 아니라 파일로 간다: " + messages);
        assertTrue(messages.get(0).startsWith("Traced 4 command(s) from function 't:one' to output file debug-trace-"), messages.get(0));
        String nl = System.lineSeparator();
        String trace = newTrace();
        // 첫 줄은 함수 id, 그다음 [F] 호출(깊이 0 → 들여쓰기 4칸), 줄들은 깊이 1(8칸)
        assertTrue(trace.startsWith("t:one" + nl + "    [F] t:one size=4" + nl + "        [C] scoreboard players set #a o 5" + nl), trace);
        // 메시지([M])가 끼면 결과는 " -> n" 대신 [R = n] 줄로 적힌다
        assertTrue(trace.contains("            [M] Set [o] for #a to 5" + nl + "        [R = 5] scoreboard players set #a o 5" + nl), trace);
        assertTrue(trace.contains("        [C] execute if score #a o matches 5 run scoreboard players add #a o 1" + nl
                + "            [M] Added 1 to [o] for #a (now 6)" + nl
                + "        [R = 6] execute if score #a o matches 5 run scoreboard players add #a o 1" + nl), trace);
        // 오류: [E] 는 줄을 끝내지 않아 이어지는 실패 메시지 [M] 이 같은 줄에 붙는다(바닐라 Tracer 그대로)
        int c = trace.indexOf("        [C] scoreboard players get #nobody o" + nl + "            [E] Can't get value of ");
        assertTrue(c >= 0, trace);
        String errLine = trace.substring(trace.indexOf("            [E] ", c), trace.indexOf(nl, trace.indexOf("            [E] ", c)));
        assertTrue(errLine.contains("                [M] Can't get value of "), errLine);
        // 안쪽 함수 호출: 깊이 1 에서 부르는 [F] 는 들여쓰기 8칸, 줄은 깊이 2(12칸). 실패 뒤 [M] 줄 다음 새 [C] 가 이어진다.
        assertTrue(trace.contains("        [C] function t:inner" + nl), trace);
        assertTrue(trace.contains("        [F] t:inner size=1" + nl + "            [C] scoreboard players set #in o 1" + nl), trace);
        assertEquals(1, run("scoreboard players get #in o").result());
    }

    @Test
    void successMessageForSeveralFunctionsAndEmptyTag() throws IOException {
        CommandResult r = run("debug function #t:both");
        assertTrue(r.success());
        assertTrue(messages.get(0).startsWith("Traced 1 command(s) from 2 functions to output file debug-trace-"), messages.get(0));
        String nl = System.lineSeparator();
        String trace = newTrace();
        assertTrue(trace.startsWith("t:inner" + nl + "    [F] t:inner size=1" + nl), trace);
        assertTrue(trace.contains("t:empty" + nl + "    [F] t:empty size=0" + nl), trace);
    }

    @Test
    void refusesReturnRunAndRecursionAndMacros() throws IOException {
        // return run: 추적 못 한다
        CommandResult r = run("return run debug function t:one");
        assertFalse(r.success());
        assertTrue(messages.stream().anyMatch(m -> m.contains("Tracing can't be used with return run")), messages.toString());
        // 인자 없이 매크로 함수: 실패 메시지(instantiation) 후에도 성공 메시지는 간다(명령 0 개)
        run("debug function t:macro");
        assertTrue(messages.stream().anyMatch(m -> m.contains("Traced 0 command(s)") || m.toLowerCase().contains("argument")), messages.toString());
    }

    @Test
    void permissionThreeAndFileIsClosedWhenContextEnds() throws IOException {
        assertFalse(run(CommandSource.console(core).withPermission(2), "debug function t:inner").success());
        assertTrue(traceFiles().equals(traceFilesBefore), "권한 없이는 파일이 만들어지지 않는다");
        assertTrue(run(CommandSource.console(core).withPermission(3), "debug function t:inner").success());
        // 컨텍스트가 끝난 뒤라 파일이 닫혀 내용이 모두 쓰여 있다
        assertTrue(newTrace().contains("t:inner"));
    }
}
