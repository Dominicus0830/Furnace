// 비동기 명령 실패의 처리: CompletionException 껍질을 벗기고, 원인이 명령 오류가 아니어도 사용자에게 실패를 알린다.
package org.domi.furnace.server.command;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EntityOpsFailureTest {

    @TempDir Path tmp;

    @Test
    void unwrapStripsCompletionWrappers() {
        var cause = new IllegalStateException("x");
        assertSame(cause, EntityOps.unwrap(new CompletionException(new CompletionException(cause))));
    }

    @Test
    void unexpectedFailureReachesTheUser() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        try {
            ServerCore core = TestServers.core(tmp, sched);
            var ch = new EmbeddedChannel();
            var p = new Player(5, UUID.randomUUID(), "Steve", ch);
            CommandSource src = CommandSource.player(core, p).withPermission(2);
            CompletableFuture<Integer> bad = new CompletableFuture<>();
            assertEquals(1, EntityOps.await(src, List.of(bad), r -> 1), "끝나지 않았으면 1");
            bad.completeExceptionally(new IllegalStateException("boom"));
            ch.flushOutbound();
            assertFalse(ch.outboundMessages().isEmpty(), "예상 못한 실패도 사용자에게 알린다");
        } finally {
            sched.close();
        }
    }
}
