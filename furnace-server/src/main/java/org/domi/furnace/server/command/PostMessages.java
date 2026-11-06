// region 스레드에서 일하는 동안 만든 결과 메시지를 모았다가, 일이 끝난 뒤 전역 스레드에서 보낸다(CommandSource 의 send* 는 전역에서만 부른다).
package org.domi.furnace.server.command;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;

public final class PostMessages {
    private final List<Runnable> queue = new ArrayList<>();

    /** 성공 메시지를 모은다(받을 사람이 없으면 본문을 만들지 않는다). region 에서 불러도 된다. */
    public synchronized void success(CommandSource source, Supplier<Text> message, boolean allowLogging) {
        if (!source.wouldSendSuccess(allowLogging)) return;
        Text t = message.get();
        queue.add(() -> source.sendSuccess(() -> t, allowLogging));
    }

    private synchronized List<Runnable> drain() {
        List<Runnable> out = new ArrayList<>(queue);
        queue.clear();
        return out;
    }

    /** f 가 끝난 뒤 전역 스레드에서 모은 메시지를 보내고 f 의 값을 돌려주는 future(f 가 실패하면 보내지 않는다). */
    public CompletableFuture<Integer> after(CommandSource source, CompletableFuture<Integer> f) {
        return EntityOps.awaitAsync(source, f, n -> {
            for (Runnable r : drain()) r.run();
            return CompletableFuture.completedFuture(n);
        });
    }

    /** 껍데기: after 를 EntityOps.result 로 마무리한다. */
    public int finish(CommandSource source, CompletableFuture<Integer> f) throws CommandSyntaxException {
        return EntityOps.result(source, after(source, f));
    }
}
