// P-O14-10: /save-all 이 전역 스레드를 막지 않고(저장 대기는 지연 결과), 메시지·순서·결과는 바닐라 SaveAllCommand 와 같으며 flush 인자를 넘긴다.
package org.domi.furnace.server.command;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.command.exec.CommandResult;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SaveAllCommandTest {

    private static final String SAVING = "Saving the game (this may take a moment!)";
    private static final String SAVED = "Saved the game";
    private static final String FAILED = "Unable to save the game (is there enough disk space?)";

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private final List<String> out = new CopyOnWriteArrayList<>();
    private final List<Boolean> flushes = Collections.synchronizedList(new ArrayList<>());
    private volatile CompletableFuture<Void> pending;

    @BeforeEach
    void setUp() {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = VanillaCommandTreeTest.core(tmp, sched);
        core.addConsoleListener(t -> out.add(t.getString()));
        core.onSaveAll(flush -> {
            flushes.add(flush);
            pending = new CompletableFuture<>();
            return pending;
        });
    }

    @AfterEach
    void tearDown() { sched.close(); }

    private CompletableFuture<CommandResult> start(String command) {
        return core.commands().performPrefixedCommandAsync(CommandSource.console(core), command);
    }

    private void awaitHook(int calls) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (flushes.size() < calls) {
            assertTrue(System.nanoTime() < deadline, "저장 훅이 불리지 않았다");
            Thread.sleep(5);
        }
    }

    /** 저장이 도는 동안 전역 스레드는 다른 일을 한다. 완료 메시지·결과는 저장이 끝난 뒤에 온다(바닐라 메시지 두 줄 그대로). */
    @Test
    void saveDoesNotBlockGlobalThread() throws Exception {
        CompletableFuture<CommandResult> cmd = start("save-all");
        awaitHook(1);
        assertFalse(flushes.get(0), "flush 없는 save-all");
        // 저장이 안 끝났는데도 전역 작업이 돈다(예전엔 저장 대기로 전역 스레드가 멈췄다)
        assertEquals(42, sched.global().submit(() -> 42).get(5, TimeUnit.SECONDS));
        assertEquals(List.of(SAVING), List.copyOf(out));
        assertFalse(cmd.isDone(), "저장이 끝나기 전에 명령이 끝나면 안 된다");
        Thread.sleep(100);
        assertFalse(cmd.isDone());
        pending.complete(null);
        CommandResult r = cmd.get(5, TimeUnit.SECONDS);
        assertEquals(List.of(SAVING, SAVED), List.copyOf(out));
        assertTrue(r.success());
        assertEquals(1, r.result());
    }

    @Test
    void flushArgumentIsPassed() throws Exception {
        CompletableFuture<CommandResult> cmd = start("save-all flush");
        awaitHook(1);
        assertTrue(flushes.get(0), "save-all flush 는 flush=true 로 저장한다");
        pending.complete(null);
        assertTrue(cmd.get(5, TimeUnit.SECONDS).success());
        assertEquals(List.of(SAVING, SAVED), List.copyOf(out));
    }

    /** 바닐라는 저장이 끝나야 다음 명령이 돈다 — 뒤 명령 순서는 그대로 지킨다(전역 틱만 풀어 준다). */
    @Test
    void laterCommandsRunAfterSave() throws Exception {
        CompletableFuture<CommandResult> save = start("save-all flush");
        awaitHook(1);
        CompletableFuture<CommandResult> off = start("save-off");
        Thread.sleep(200);
        assertFalse(off.isDone(), "save-all 뒤 명령은 저장이 끝난 뒤에 돈다");
        assertEquals(List.of(SAVING), List.copyOf(out));
        pending.complete(null);
        save.get(5, TimeUnit.SECONDS);
        off.get(5, TimeUnit.SECONDS);
        assertEquals(List.of(SAVING, SAVED, "Automatic saving is now disabled"), List.copyOf(out));
    }

    /** 기본 지연 결과 한도(5초, 여기선 50ms 로 줄임)보다 오래 걸리는 저장도 실패로 끊지 않는다. */
    @Test
    void longSaveIsNotCutByDeferredTimeout() throws Exception {
        core.commands().queue().deferredTimeout(Duration.ofMillis(50));
        CompletableFuture<CommandResult> cmd = start("save-all");
        awaitHook(1);
        Thread.sleep(400);
        assertFalse(cmd.isDone());
        pending.complete(null);
        assertTrue(cmd.get(5, TimeUnit.SECONDS).success());
        assertEquals(List.of(SAVING, SAVED), List.copyOf(out));
    }

    @Test
    void failedSaveReportsVanillaError() throws Exception {
        CompletableFuture<CommandResult> cmd = start("save-all");
        awaitHook(1);
        pending.completeExceptionally(new java.io.IOException("disk full"));
        CommandResult r = cmd.get(5, TimeUnit.SECONDS);
        assertFalse(r.success());
        assertEquals(List.of(SAVING, FAILED), List.copyOf(out));
    }

    /** 이미 끝난 저장(훅이 바로 끝냄)은 그 자리에서 두 메시지를 낸다. */
    @Test
    void immediateSave() throws Exception {
        core.onSaveAll(flush -> CompletableFuture.completedFuture(null));
        CommandResult r = start("save-all").get(5, TimeUnit.SECONDS);
        assertTrue(r.success());
        assertEquals(List.of(SAVING, SAVED), List.copyOf(out));
    }

    /** RCON(백업 스크립트의 save-all flush): 응답은 저장이 끝난 뒤 두 줄을 함께 돌려준다 — 저장 전에 돌아오면 스크립트가 덜 쓴 월드를 복사한다. */
    @Test
    void rconResponseWaitsForSave() throws Exception {
        CompletableFuture<String> resp = CompletableFuture.supplyAsync(() -> {
            try {
                return core.runRconCommand("save-all flush");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        awaitHook(1);
        assertTrue(flushes.get(0));
        Thread.sleep(150);
        assertFalse(resp.isDone(), "저장이 끝나기 전에 RCON 이 응답하면 안 된다");
        assertEquals(42, sched.global().submit(() -> 42).get(5, TimeUnit.SECONDS));
        pending.complete(null);
        String text = resp.get(10, TimeUnit.SECONDS);
        assertTrue(text.contains(SAVING) && text.contains(SAVED), text);
        assertTrue(text.indexOf(SAVING) < text.indexOf(SAVED), text);
    }
}
