// /execute E3 단계: if|unless score(비교 연산자·matches·점수 없음=거짓)와 store result|success score(명령 실제 반환값 저장, 포크마다 콜백 한 번).
package org.domi.furnace.server.command.exec;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.scores.ScoreHolder;
import org.domi.furnace.server.world.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExecuteStoreScoreTest {

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;
    Cow[] cows;

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        core.world().awaitChunk(new ChunkPos(0, 0));
        cows = DeferredResultTest.spawnCows(core, 2);
        run("scoreboard objectives add o dummy");
        run("scoreboard objectives add p dummy");
    }

    @AfterEach
    void tearDown() { sched.close(); }

    private CommandResult run(String command) {
        return core.commands().performPrefixedCommandAsync(CommandSource.console(core), command).orTimeout(20, TimeUnit.SECONDS).join();
    }

    private Integer score(String holder, String objective) {
        var board = core.scoreboard();
        var s = board.getPlayerScoreInfo(ScoreHolder.forNameOnly(holder), board.getObjective(objective));
        return s == null ? null : s.value();
    }

    @Test
    void comparisonOperators() {
        run("scoreboard players set #a o 3");
        run("scoreboard players set #b p 5");
        // 3 vs 5
        assertFalse(run("execute if score #a o = #b p").success());
        assertTrue(run("execute if score #a o < #b p").success());
        assertTrue(run("execute if score #a o <= #b p").success());
        assertFalse(run("execute if score #a o > #b p").success());
        assertFalse(run("execute if score #a o >= #b p").success());
        assertTrue(run("execute unless score #a o = #b p").success());
        assertFalse(run("execute unless score #a o < #b p").success());
        run("scoreboard players set #b p 3");
        assertTrue(run("execute if score #a o = #b p").success());
        assertTrue(run("execute if score #a o <= #b p").success());
        assertTrue(run("execute if score #a o >= #b p").success());
        assertFalse(run("execute if score #a o < #b p").success());
        assertFalse(run("execute if score #a o > #b p").success());
        assertEquals(new CommandResult(true, 1, 1), run("execute if score #a o = #b p"), "성공 시 1");
        assertEquals(new CommandResult(false, 0, 1), run("execute unless score #a o = #b p"), "실패 시 0");
    }

    @Test
    void missingScoreIsFalseOnEitherSide() {
        run("scoreboard players set #a o 3");
        assertFalse(run("execute if score #a o = #none p").success(), "원본 점수 없음");
        assertFalse(run("execute if score #none o = #a o").success(), "대상 점수 없음");
        assertTrue(run("execute unless score #none o = #a o").success(), "unless 는 반대");
        assertFalse(run("execute if score #none o matches 0..").success());
        assertTrue(run("execute unless score #none o matches 0..").success());
    }

    @Test
    void matchesRanges() {
        run("scoreboard players set #a o 5");
        assertTrue(run("execute if score #a o matches 5").success());
        assertTrue(run("execute if score #a o matches 1..5").success());
        assertTrue(run("execute if score #a o matches 5..").success());
        assertTrue(run("execute if score #a o matches ..5").success());
        assertFalse(run("execute if score #a o matches 6..").success());
        assertFalse(run("execute if score #a o matches ..4").success());
        assertTrue(run("execute unless score #a o matches 6..").success());
        run("scoreboard players set #n o -7");
        assertTrue(run("execute if score #n o matches -10..-5").success());
    }

    @Test
    void scoreConditionFiltersTheFork() {
        run("scoreboard players set #a o 1");
        assertEquals(1, run("execute if score #a o matches 1 store result score #r o run scoreboard players get #a o").callbacks());
        assertEquals(1, score("#r", "o"));
        assertEquals(CommandResult.NONE, run("execute if score #a o matches 2 store result score #r o run scoreboard players set #r o 99"));
        assertEquals(1, score("#r", "o"), "탈락하면 run 이 실행되지 않는다");
    }

    @Test
    void storeSavesActualReturnValue() {
        run("scoreboard players set #src o 42");
        run("execute store result score #r o run scoreboard players get #src o");
        assertEquals(42, score("#r", "o"));
        run("execute store success score #s o run scoreboard players get #src o");
        assertEquals(1, score("#s", "o"), "success 는 1");
    }

    @Test
    void failedCommandStoresZeroForBoth() {
        run("scoreboard players set #r o 9");
        run("scoreboard players set #s o 9");
        run("execute store result score #r o run scoreboard players get #none o");   // 점수 없음 -> 오류
        run("execute store success score #s o run scoreboard players get #none o");
        assertEquals(0, score("#r", "o"), "실패한 명령의 result 는 0");
        assertEquals(0, score("#s", "o"), "실패한 명령의 success 는 0");
    }

    @Test
    void storeSavesAsyncEntityDataValue() {
        var busy = new java.util.concurrent.CompletableFuture<Boolean>();
        sched.global().execute(() -> {
            core.commands().performPrefixedCommand(CommandSource.console(core), "execute store result score #h o run data get entity @e[type=cow,limit=1] Health");
            busy.complete(core.commands().queue().isBusy());   // 지연 결과를 기다리는 중이어야 한다
        });
        assertTrue(busy.orTimeout(10, TimeUnit.SECONDS).join(), "지연 경로를 탔다");
        run("scoreboard players get #h o");   // 앞 컨텍스트가 끝난 뒤(대기열)
        Integer h = score("#h", "o");
        assertNotNull(h, "지연 결과가 콜백으로 저장돼야 한다");
        assertEquals(10, h, "소의 실제 체력");
        run("execute store success score #hs o run data get entity @e[type=cow,limit=1] Health");
        assertEquals(1, score("#hs", "o"));
    }

    @Test
    void storeTargetsAsyncSelectorHolders() {
        run("scoreboard players set #src o 7");
        run("execute store result score @e[type=cow] o run scoreboard players get #src o");
        assertEquals(7, score(cows[0].uuid().toString(), "o"));
        assertEquals(7, score(cows[1].uuid().toString(), "o"));
    }

    @Test
    void entityHolderScoresAreComparable() {
        run("scoreboard players set #src o 7");
        run("execute store result score @e[type=cow] o run scoreboard players get #src o");
        run("scoreboard players set #t p 7");
        assertTrue(run("execute if score @e[type=cow,limit=1] o = #t p").success(), "엔티티 대상은 UUID 점수");
        assertFalse(run("execute if score @e[type=cow,limit=1] o < #t p").success());
    }

    @Test
    void forkedSourcesEachStoreOnceLastWins() {
        // store 가 as 앞에 있어도 포크마다 콜백 한 번 -> 합계(3)가 아니라 마지막 값(2)
        run("execute store result score #c o as @e[type=cow] run scoreboard players add #cnt o 1");
        assertEquals(2, score("#c", "o"));
        run("scoreboard players set #cnt o 0");
        run("execute as @e[type=cow] store result score #d o run scoreboard players add #cnt o 1");
        assertEquals(2, score("#d", "o"));
        run("scoreboard players set #cnt o 0");
        run("execute as @e[type=cow] store success score #e o run scoreboard players add #cnt o 1");
        assertEquals(1, score("#e", "o"));
        assertEquals(2, run("execute as @e[type=cow] store success score #e o run scoreboard players add #cnt o 1").callbacks());
    }

    @Test
    void multipleStoresChain() {
        run("scoreboard players set #src o 5");
        run("execute store result score #x o store success score #y o run scoreboard players get #src o");
        assertEquals(5, score("#x", "o"));
        assertEquals(1, score("#y", "o"));
    }

    @Test
    void storeErrorsDoNotRun() {
        assertEquals(CommandResult.NONE, run("execute store result score #r nosuch run say hi"), "없는 기준");
        assertEquals(CommandResult.NONE, run("execute store result score @e[type=creeper] o run say hi"), "대상 없음 오류");
        assertNull(score("#r", "o"));
    }

    // ---------------------------------------------------------------- 검토 반영

    @Test
    void chainOrderLaterStoreWritesLast() {
        run("scoreboard players set #src o 5");
        run("execute store result score #x o store success score #x o run scoreboard players get #src o");
        assertEquals(1, score("#x", "o"), "앞 store 가 먼저, 뒤가 최종값");
        run("execute store success score #x o store result score #x o run scoreboard players get #src o");
        assertEquals(5, score("#x", "o"));
    }

    @Test
    void wildcardStoreTargetsTrackedHolders() {
        run("scoreboard players set #a o 1");
        run("scoreboard players set #b o 2");
        run("scoreboard players set #src o 9");
        run("execute store result score * o run scoreboard players get #src o");
        assertEquals(9, score("#a", "o"));
        assertEquals(9, score("#b", "o"));
    }

    private java.util.List<String> capture() {
        var msgs = java.util.Collections.synchronizedList(new java.util.ArrayList<String>());
        core.addConsoleListener(t -> msgs.add(t.getString()));
        return msgs;
    }

    @Test
    void storeToReadOnlyObjectiveFailsCleanlySync() {
        var msgs = capture();
        run("scoreboard objectives add t health");
        run("scoreboard players set #src o 5");
        msgs.clear();
        run("execute store result score #x t run scoreboard players get #src o");
        assertTrue(msgs.stream().anyMatch(m -> m.contains("unexpected error")), msgs.toString());
        assertNotEquals(5, score("#x", "t"), "읽기 전용 기준에는 값이 쓰이지 않는다(점수 항목만 0 으로 생긴다, 바닐라와 같다)");
        msgs.clear();
        assertTrue(run("scoreboard players get #src o").success(), "다음 명령은 정상");
    }

    @Test
    void storeToReadOnlyObjectiveFailsCleanlyDeferred() {
        var msgs = capture();
        run("scoreboard objectives add t health");
        msgs.clear();
        run("execute store result score #x t run data get entity @e[type=cow,limit=1] Health");
        assertTrue(msgs.stream().anyMatch(m -> m.contains("unexpected error")), "지연 경로의 콜백 예외도 사용자에게: " + msgs);
        msgs.clear();
        assertTrue(run("scoreboard players get #src2 o").callbacks() == 1, "큐가 막히지 않고 다음 명령이 돈다");
    }

    @Test
    void storeExceptionAbortsWholeContextAcrossForks() {
        run("scoreboard objectives add t health");
        run("execute store result score #x t as @e[type=cow] run scoreboard players add #cnt o 1");
        assertEquals(1, score("#cnt", "o"), "첫 포크의 콜백 예외로 둘째 포크는 돌지 않는다");
    }

    @Test
    void errorOrderFollowsVanilla() {
        run("scoreboard players set #a o 1");
        // targetObjective 오류가 source 선택자 오류보다 먼저
        var msgs = capture();
        msgs.clear();
        run("execute if score #a nosuch = @e[type=creeper,limit=1] o");
        assertEquals(1, msgs.size(), msgs.toString());
        assertTrue(msgs.get(0).contains("nosuch"), msgs.get(0));
        msgs.clear();
        // 정상 기준이면 source 선택자 오류
        run("execute if score #a o = @e[type=creeper,limit=1] o");
        assertEquals(1, msgs.size(), msgs.toString());
        assertTrue(msgs.get(0).contains("No entity was found"), msgs.get(0));
        msgs.clear();
        // source 선택자 오류가 sourceObjective 오류보다 먼저
        run("execute if score #a o = @e[type=creeper,limit=1] nosuch");
        assertTrue(msgs.get(0).contains("No entity was found") || msgs.get(0).contains("nosuch"), msgs.get(0));
    }

    @Test
    void storeBossbarValueAndMax() {
        run("bossbar add x \"X\"");
        run("scoreboard players set #src o 30");
        run("execute store result bossbar minecraft:x max run scoreboard players get #src o");
        run("scoreboard players set #src o 12");
        run("execute store result bossbar minecraft:x value run scoreboard players get #src o");
        var bar = core.customBossEvents().get(org.domi.furnace.server.data.ResourceId.parse("minecraft:x"));
        assertEquals(30, bar.max());
        assertEquals(12, bar.value());
        run("execute store success bossbar minecraft:x value run scoreboard players get #none o");
        assertEquals(0, bar.value());
        assertEquals(CommandResult.NONE, run("execute store result bossbar minecraft:nope value run say hi"), "없는 보스바");
    }
}
