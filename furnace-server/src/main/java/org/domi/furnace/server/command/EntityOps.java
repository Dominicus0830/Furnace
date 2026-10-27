// 명령이 엔티티를 건드릴 때의 스레드 규칙: 일반 엔티티는 그 엔티티를 가진 region 스레드에서만 바꾼다(플레이어는 연결이 스레드를 알아서 처리).
// 주인에게 보낸 일의 결과는 future 로 모으고, 모두 끝났으면(플레이어만 건드렸다면 즉시) 명령 스레드에서 바로 이어 간다.
package org.domi.furnace.server.command;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Rider;

public final class EntityOps {

    private EntityOps() {}

    /** 모인 결과를 받아 명령 결과(성공 개수)를 돌려주는 마무리. */
    @FunctionalInterface
    public interface Finisher<T> {
        int finish(List<T> results) throws CommandSyntaxException;
    }

    /** r 의 주인 스레드에서 fn 을 돌린다. 플레이어는 지금 스레드에서 바로 돌린다. */
    public static <T> CompletableFuture<T> onOwner(CommandSource source, Rider r, Function<Rider, T> fn) {
        if (!(r instanceof Entity e)) return CompletableFuture.completedFuture(fn.apply(r));
        CompletableFuture<T> f = new CompletableFuture<>();
        var world = source.getServer().world(e.dimensionKey());
        if (world == null) world = source.level();
        world.runAt(e.x(), e.z(), region -> {
            try {
                f.complete(fn.apply(e));
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        return f;
    }

    /** 대상마다 onOwner 로 보낸 결과를 모아 마무리를 부른다. 모두 끝나 있으면 마무리 결과를 돌려주고(오류도 그대로 던진다), 아니면 1 을 돌려주고 끝난 뒤 부른다. */
    public static <T> int forEach(CommandSource source, List<Rider> targets, Function<Rider, T> fn, Finisher<T> finisher) throws CommandSyntaxException {
        List<CompletableFuture<T>> fs = new ArrayList<>(targets.size());
        for (Rider r : targets) fs.add(onOwner(source, r, fn));
        return await(source, fs, finisher);
    }

    public static <T> int await(CommandSource source, List<CompletableFuture<T>> fs, Finisher<T> finisher) throws CommandSyntaxException {
        boolean done = true;
        for (var f : fs) if (!f.isDone()) done = false;
        if (done) return finisher.finish(collect(fs));
        CompletableFuture.allOf(fs.toArray(new CompletableFuture[0])).whenComplete((v, ex) -> {
            try {
                if (ex != null) throw new RuntimeException(ex);
                finisher.finish(collect(fs));
            } catch (CommandSyntaxException e) {
                source.sendFailure(e.getRawMessage());
            }
        });
        return 1;
    }

    private static <T> List<T> collect(List<CompletableFuture<T>> fs) {
        List<T> out = new ArrayList<>(fs.size());
        for (var f : fs) out.add(f.join());
        return out;
    }

    /** 플레이어면 접속 이름, 아니면 종류 이름(로그용). */
    public static String describe(Rider r) { return r instanceof Player p ? p.name() : r.typeName(); }
}
