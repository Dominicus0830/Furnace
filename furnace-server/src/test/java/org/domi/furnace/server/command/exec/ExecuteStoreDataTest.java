// /execute E4 단계: store result|success block|entity|storage <path> <type> <scale>(콜백이 주인 스레드에서 쓰고 컨텍스트가 완료를 기다린다)와 if|unless data.
package org.domi.furnace.server.command.exec;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.block.entity.ContainerBlockEntity;
import org.domi.furnace.server.block.entity.SpawnerBlockEntity;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExecuteStoreDataTest {

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;
    ChunkColumn column;
    Cow[] cows;
    final List<String> messages = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        column = core.world().awaitChunk(new ChunkPos(0, 0));
        cows = DeferredResultTest.spawnCows(core, 2);
        core.addConsoleListener(t -> messages.add(t.getString()));
        run("scoreboard objectives add o dummy");
        run("scoreboard players set #src o 7");
    }

    @AfterEach
    void tearDown() { sched.close(); }

    private CommandResult run(String command) {
        messages.clear();
        return core.commands().performPrefixedCommandAsync(CommandSource.console(core), command).orTimeout(20, TimeUnit.SECONDS).join();
    }

    private <T> T inRegion(java.util.function.Supplier<T> task) {
        return core.world().submitAtChunk(new ChunkPos(0, 0), r -> task.get()).orTimeout(10, TimeUnit.SECONDS).join();
    }

    private Object storage(String id, String key) { return core.world().commandStorage().get(id).tags().get(key); }

    private NbtCompound cowNbt(Cow cow) { return inRegion(() -> cow.saveWithoutId(new NbtCompound())); }

    private int chestCount(int x, int y, int z, int slot) {
        return inRegion(() -> ((ContainerBlockEntity) column.blockEntityAt(x, y, z)).items[slot].count());
    }

    // ---------------------------------------------------------------- storage: 타입 × scale

    @Test
    void storageEachTypeCastsAfterScaling() {
        run("execute store result storage t:v i int 1 run scoreboard players get #src o");
        assertEquals(7, storage("t:v", "i"));
        run("execute store result storage t:v i2 int 0.5 run scoreboard players get #src o");
        assertEquals(3, storage("t:v", "i2"), "(int)(7*0.5) 는 내림이 아니라 절삭");
        run("execute store result storage t:v f float 0.5 run scoreboard players get #src o");
        assertEquals(3.5f, storage("t:v", "f"));
        run("execute store result storage t:v s short 2 run scoreboard players get #src o");
        assertEquals((short) 14, storage("t:v", "s"));
        run("execute store result storage t:v l long 1000000000 run scoreboard players get #src o");
        assertEquals(7_000_000_000L, storage("t:v", "l"));
        run("execute store result storage t:v d double 0.25 run scoreboard players get #src o");
        assertEquals(1.75, storage("t:v", "d"));
        run("execute store result storage t:v b byte 100 run scoreboard players get #src o");
        assertEquals((byte) 700, storage("t:v", "b"), "byte 는 (byte)(700) = -68 로 넘친다(바닐라 캐스팅)");
        run("scoreboard players set #neg o -7");
        run("execute store result storage t:v n int 0.5 run scoreboard players get #neg o");
        assertEquals(-3, storage("t:v", "n"), "음수도 0 쪽으로 절삭");
    }

    @Test
    void storageSuccessStoresOneOrZeroAndFailureStoresZeroResult() {
        run("execute store success storage t:v ok byte 1 run scoreboard players get #src o");
        assertEquals((byte) 1, storage("t:v", "ok"));
        run("data modify storage t:v r set value 99");
        run("data modify storage t:v s set value 99");
        run("execute store result storage t:v r int 1 run scoreboard players get #none o");   // 점수 없음 -> 실패
        run("execute store success storage t:v s int 1 run scoreboard players get #none o");
        assertEquals(0, storage("t:v", "r"), "실패한 명령의 result 는 0");
        assertEquals(0, storage("t:v", "s"), "실패한 명령의 success 는 0");
        assertTrue(messages.stream().anyMatch(m -> m.contains("Can't get value")), "원 명령의 실패 메시지는 그대로: " + messages);
    }

    @Test
    void storageWritesNestedPathCreatingParents() {
        run("execute store result storage t:v a.b.c int 1 run scoreboard players get #src o");
        var a = core.world().commandStorage().get("t:v").getCompound("a");
        assertEquals(7, a.getCompound("b").getInt("c"));
        // 빈 리스트의 [0] 은 만들어지지 않는다(nothing_found) → 바닐라처럼 조용히 삼키고 저장소는 그대로(setData 전에 실패)
        CommandResult r = run("execute store result storage t:v x.l[0].c int 1 run scoreboard players get #src o");
        assertEquals(new CommandResult(true, 7, 1), r);
        assertNull(storage("t:v", "x"), "실패한 쓰기는 부분 결과를 남기지 않는다");
        assertTrue(messages.stream().noneMatch(m -> m.contains("Found no elements") || m.contains("unexpected")), messages.toString());
    }

    // ---------------------------------------------------------------- 지연 결과 → store

    @Test
    void deferredResultIsStoredBeforeTheNextCommandStarts() {
        // data get entity @e 는 region 왕복(지연 결과). 콜백 저장이 끝난 뒤에야 다음 최상위 명령이 시작해야 한다.
        var busy = new java.util.concurrent.CompletableFuture<Boolean>();
        sched.global().execute(() -> {
            core.commands().performPrefixedCommand(CommandSource.console(core), "execute store result storage t:h v int 1 run data get entity @e[type=cow,limit=1] Health");
            busy.complete(core.commands().queue().isBusy());
        });
        assertTrue(busy.orTimeout(10, TimeUnit.SECONDS).join(), "지연 경로를 탔다");
        CommandResult r = run("data get storage t:h v");
        assertEquals(new CommandResult(true, 10, 1), r, "바로 다음 명령이 저장된 값을 본다");
        assertEquals(10, storage("t:h", "v"), "소의 실제 체력");
    }

    @Test
    void deferredResultIntoBlockWaitsForTheRegionWrite() {
        run("setblock 5 -60 5 minecraft:chest");
        run("item replace block 5 -60 5 container.0 with minecraft:stone 3");
        // 지연 결과(엔티티 region) → 콜백(블록 region) 두 번 왕복. 컨텍스트가 두 번째도 기다려야 다음 줄이 새 값을 본다.
        run("execute store result block 5 -60 5 Items[0].count int 1 run data get entity @e[type=cow,limit=1] Health");
        assertEquals(new CommandResult(true, 10, 1), run("data get block 5 -60 5 Items[0].count"));
        assertEquals(10, chestCount(5, -60, 5, 0));
    }

    // ---------------------------------------------------------------- block

    @Test
    void blockStoresIntoBlockEntityNbt() {
        run("setblock 5 -60 5 minecraft:chest");
        run("item replace block 5 -60 5 container.0 with minecraft:stone 3");
        assertEquals(1, run("execute store result block 5 -60 5 Items[0].count int 1 run scoreboard players get #src o").callbacks());
        assertEquals(7, chestCount(5, -60, 5, 0));
        run("execute store success block 5 -60 5 Items[0].count int 5 run scoreboard players get #src o");
        assertEquals(5, chestCount(5, -60, 5, 0), "success(1) × 5");
        // short 타입: 스포너 Delay
        run("setblock 7 -60 7 minecraft:spawner");
        run("execute store result block 7 -60 7 Delay short 3 run scoreboard players get #src o");
        assertEquals(21, inRegion(() -> ((SpawnerBlockEntity) column.blockEntityAt(7, -60, 7)).spawner().spawnDelay()));
    }

    @Test
    void blockTargetIsCheckedWhenTheModifierRuns() {
        run("setblock 5 -60 5 minecraft:stone");
        assertEquals(CommandResult.NONE, run("execute store result block 5 -60 5 x int 1 run scoreboard players set #ran o 1"), "블록 엔티티가 아니면 run 이 돌지 않는다");
        assertEquals(List.of("The target block is not a block entity"), messages);
        int before = core.world().regionCount();
        assertEquals(CommandResult.NONE, run("execute store result block 100000 0 100000 x int 1 run scoreboard players set #ran o 1"));
        assertEquals(List.of("That position is not loaded"), messages);
        assertEquals(before, core.world().regionCount(), "조회가 region 을 만들지 않는다");
        assertFalse(run("scoreboard players get #ran o").success(), "run 이 한 번도 돌지 않았다");
    }

    @Test
    void blockEntityGoneBeforeTheCallbackIsSwallowedSilently() {
        run("setblock 5 -60 5 minecraft:chest");
        // run 이 상자를 없앤다 → 콜백은 블록 엔티티를 못 찾고(commands.data.block.invalid) 바닐라처럼 조용히 삼킨다
        CommandResult r = run("execute store result block 5 -60 5 Items[0].count int 1 run setblock 5 -60 5 minecraft:stone");
        assertEquals(1, r.callbacks());
        assertTrue(r.success());
        assertTrue(messages.stream().noneMatch(m -> m.contains("block entity") || m.contains("unexpected")), messages.toString());
        assertEquals(1, run("scoreboard players get #src o").callbacks(), "큐는 이어진다");
    }

    // ---------------------------------------------------------------- entity

    @Test
    void entityStoresIntoAsyncTargetNbt() {
        run("execute store result entity @e[type=cow,limit=1,sort=nearest] Health float 0.5 run scoreboard players get #src o");
        // nearest 기준 콘솔 (0,0,0): cows[0] 이 (0.5,-60,0.5)
        assertEquals(3.5f, inRegion(() -> cows[0].health()), "콜백이 소유 region 에서 load 로 반영한 체력");
        assertEquals(3.5f, cowNbt(cows[0]).getFloat("Health"));
        run("execute store success entity @e[type=cow,limit=1,sort=nearest] Health float 8 run scoreboard players get #src o");
        assertEquals(8f, inRegion(() -> cows[0].health()));
        run("execute store result entity @e[type=cow,limit=1,sort=nearest] Age int 1 run scoreboard players get #src o");
        assertEquals(7, cowNbt(cows[0]).getInt("Age"));
    }

    @Test
    void entityKeepsUuidAndMissingTargetIsAnError() {
        UUID id = cows[0].uuid();
        run("execute store result entity @e[type=cow,limit=1,sort=nearest] Age int 1 run scoreboard players get #src o");
        assertEquals(id, inRegion(() -> cows[0].uuid()));
        assertEquals(CommandResult.NONE, run("execute store result entity @e[type=creeper,limit=1] Age int 1 run scoreboard players set #ran o 1"));
        assertEquals(List.of("No entity was found"), messages);
    }

    @Test
    void entityRemovedBeforeTheCallbackDoesNotBlockTheQueue() {
        // run 이 대상을 없앤다(/kill 은 아직 플레이어 전용이라 테스트 명령으로 소유 region 에서 discard) → 콜백의 runOnOwner 가 주인을 못 찾는다
        // → 바닐라(죽은 엔티티에 조용히 load)처럼 쓰기를 건너뛰고 조용히 끝난다. 두 region 작업은 같은 인박스에 순서대로 들어가 discard 가 먼저다.
        core.commands().getDispatcher().register(org.domi.furnace.server.command.brigadier.LiteralArgumentBuilder.<CommandSource>literal("discardcow").executes(c -> {
            org.domi.furnace.server.command.EntityOps.runOnOwner(c.getSource(), cows[0], org.domi.furnace.server.entity.Entity::discard, () -> { });
            return 1;
        }));
        CommandResult r = run("execute store result entity @e[type=cow,limit=1,sort=nearest] Age int 1 run discardcow");
        assertEquals(new CommandResult(true, 1, 1), r);
        assertTrue(inRegion(() -> cows[0].isRemoved()));
        assertEquals(List.of(), messages, "오류·실패 메시지가 없다");
        assertEquals(new CommandResult(true, 7, 1), run("scoreboard players get #src o"), "큐는 이어진다");
        assertEquals(0, cowNbt(cows[1]).getInt("Age"), "남은 소는 바뀌지 않았다");
    }

    @Test
    void mismatchedItemsTagIsIgnoredLikeVanillaAndBothForksRun() {
        // 바닐라 CompoundTag.getList 는 리스트가 아니면 빈 리스트라 load 는 예외 없이 Items 를 비운다: 오류 없이 두 포크가 모두 돈다.
        run("setblock 5 -60 5 minecraft:chest");
        run("item replace block 5 -60 5 container.0 with minecraft:stone 3");
        run("scoreboard players set #cnt o 0");
        run("execute store result block 5 -60 5 Items int 1 as @e[type=cow] run scoreboard players add #cnt o 1");
        assertTrue(messages.stream().noneMatch(m -> m.contains("unexpected error")), "오류 메시지가 없다: " + messages);
        var board = core.scoreboard();
        assertEquals(2, board.getPlayerScoreInfo(org.domi.furnace.server.scores.ScoreHolder.forNameOnly("#cnt"), board.getObjective("o")).value());
        assertEquals(0, chestCount(5, -60, 5, 0), "Items 가 비었다");
    }

    /** load 에서 RuntimeException 을 던지는 시험용 블록 엔티티. */
    private static final class ThrowingBlockEntity extends org.domi.furnace.server.block.entity.BlockEntity {
        ThrowingBlockEntity(int x, int y, int z) { super("minecraft:chest", new org.domi.furnace.server.block.Pos(x, y, z), 0); }
        @Override public void load(NbtCompound tag) { throw new IllegalStateException("시험용 load 실패"); }
    }

    @Test
    void nonSyntaxCallbackFailureReportsCommandFailedAndAbortsTheContext() {
        // 콜백이 region 에서 던진 비-CommandSyntaxException 은 동기 콜백 예외와 같은 길:
        // CommandQueue.drive 의 catch 가 command.failed 를 알리고 컨텍스트를 끝낸다(남은 포크는 돌지 않는다). 다음 최상위 명령은 정상.
        inRegion(() -> { column.putBlockEntity(new ThrowingBlockEntity(6, -60, 6)); return null; });
        run("scoreboard players set #cnt o 0");
        run("execute store result block 6 -60 6 Items int 1 as @e[type=cow] run scoreboard players add #cnt o 1");
        assertTrue(messages.stream().anyMatch(m -> m.contains("unexpected error")), "비-문법 콜백 실패는 사용자에게 알린다: " + messages);
        var board = core.scoreboard();
        assertEquals(1, board.getPlayerScoreInfo(org.domi.furnace.server.scores.ScoreHolder.forNameOnly("#cnt"), board.getObjective("o")).value(),
                "첫 포크의 콜백 실패로 둘째 포크는 돌지 않는다");
        assertEquals(new CommandResult(true, 7, 1), run("scoreboard players get #src o"), "큐는 이어진다");
    }

    @Test
    void playerTargetIsRejectedSilentlyAndTheChainContinues() throws Exception {
        var p = new Player(5, UUID.randomUUID(), "Steve", new org.domi.furnace.server.ConcurrentEmbeddedChannel());
        CommandSource src = CommandSource.player(core, p).withPermission(2);
        List<String> sent = Collections.synchronizedList(new ArrayList<>());
        core.addConsoleListener(t -> sent.add(t.getString()));
        CommandResult r = core.commands().performPrefixedCommandAsync(src, "execute store result entity @s foodLevel int 1 store result storage t:p v int 1 run scoreboard players get #src o")
                .orTimeout(20, TimeUnit.SECONDS).join();
        assertEquals(new CommandResult(true, 7, 1), r, "원 명령은 정상");
        assertEquals(7, storage("t:p", "v"), "뒤 store 콜백은 그대로 이어진다(chain)");
        assertTrue(messages.stream().noneMatch(m -> m.contains("Unable to modify player data") || m.contains("unexpected")),
                "플레이어 쓰기 거부(commands.data.entity.invalid)는 바닐라처럼 삼킨다: " + messages);
    }

    // ---------------------------------------------------------------- 포크

    @Test
    void forkedSourcesEachStoreOnceLastWins() {
        run("execute as @e[type=cow] store result storage t:f v int 1 run scoreboard players add #cnt o 1");
        assertEquals(2, storage("t:f", "v"), "포크마다 저장, 마지막 값(합 3 이 아니다)");
        run("scoreboard players set #cnt o 0");
        assertEquals(2, run("execute store result storage t:f w int 1 as @e[type=cow] run scoreboard players add #cnt o 1").callbacks());
        assertEquals(2, storage("t:f", "w"));
        // 포크 소스마다 다른 엔티티에 저장: 두 소 모두 바뀐다
        run("execute as @e[type=cow] store result entity @s Age int 1 run scoreboard players get #src o");
        for (Cow c : cows) assertEquals(7, cowNbt(c).getInt("Age"));
    }

    @Test
    void storeDataChainsWithScoreStoreInOrder() {
        run("execute store result storage t:c v int 1 store result score #r o run scoreboard players get #src o");
        assertEquals(7, storage("t:c", "v"));
        var board = core.scoreboard();
        assertEquals(7, board.getPlayerScoreInfo(org.domi.furnace.server.scores.ScoreHolder.forNameOnly("#r"), board.getObjective("o")).value());
    }

    // ---------------------------------------------------------------- if data

    @Test
    void ifDataStorageCountsMatchesAndUnlessInverts() {
        run("data merge storage t:a {x:1,l:[1,2,3]}");
        assertEquals(new CommandResult(true, 1, 1), run("execute if data storage t:a x"));
        assertEquals(List.of("Test passed, count: 1"), messages);
        assertEquals(new CommandResult(true, 3, 1), run("execute if data storage t:a l[]"), "경로 일치 수");
        assertEquals(new CommandResult(false, 0, 1), run("execute if data storage t:a nope"));
        assertEquals(List.of("Test failed"), messages);
        assertEquals(new CommandResult(true, 1, 1), run("execute unless data storage t:a nope"));
        assertEquals(List.of("Test passed"), messages);
        assertEquals(new CommandResult(false, 0, 1), run("execute unless data storage t:a l[]"));
        assertEquals(List.of("Test failed, count: 3"), messages);
        // 포크: 참이면 소스 유지(개수와 무관하게 1개), 거짓이면 사라진다
        assertEquals(1, run("execute if data storage t:a l[] run scoreboard players get #src o").callbacks());
        assertEquals(CommandResult.NONE, run("execute if data storage t:a nope run scoreboard players get #src o"));
        assertEquals(1, run("execute unless data storage t:a nope run scoreboard players get #src o").callbacks());
    }

    @Test
    void ifDataEntityAndBlockReadOnTheOwnerRegion() {
        assertEquals(new CommandResult(true, 1, 1), run("execute if data entity @e[type=cow,limit=1] Health"));
        assertEquals(new CommandResult(false, 0, 1), run("execute if data entity @e[type=cow,limit=1] NoSuchKey"));
        assertEquals(new CommandResult(false, 0, 1), run("execute if data entity @e[type=creeper,limit=1] Health"));
        assertEquals(List.of("No entity was found"), messages);
        run("setblock 5 -60 5 minecraft:chest");
        run("item replace block 5 -60 5 container.0 with minecraft:stone 3");
        run("item replace block 5 -60 5 container.4 with minecraft:stone 4");
        assertEquals(new CommandResult(true, 2, 1), run("execute if data block 5 -60 5 Items[]"));
        assertEquals(new CommandResult(true, 1, 1), run("execute if data block 5 -60 5 Items[{Slot:4b}]"));
        assertEquals(new CommandResult(false, 0, 1), run("execute if data block 5 -60 5 Items[{Slot:1b}]"));
        run("setblock 6 -60 5 minecraft:stone");
        assertEquals(new CommandResult(false, 0, 1), run("execute if data block 6 -60 5 Items"));
        assertEquals(List.of("The target block is not a block entity"), messages);
        int before = core.world().regionCount();
        assertEquals(new CommandResult(false, 0, 1), run("execute if data block 100000 0 100000 Items"));
        assertEquals(List.of("That position is not loaded"), messages);
        assertEquals(before, core.world().regionCount(), "조회가 region 을 만들지 않는다");
    }
}
