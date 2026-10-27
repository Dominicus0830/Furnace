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

    private static final int MAX_OWNER_RETRIES = 5;

    /**
     * e 를 가진 region 스레드에서 task 를 돌린다. 위치를 읽고 보내는 사이 엔티티가 다른 region 으로 넘어갔을 수 있으므로, 도착한 region 이
     * 실제로 e 를 갖고 있는지 확인하고 아니면 엔티티의 지금 위치로 다시 보낸다(최대 5번). 끝내 못 찾으면(사라진 엔티티 등) onFail.
     */
    public static void runOnOwner(CommandSource source, Entity e, java.util.function.Consumer<Entity> task, Runnable onFail) {
        runOnOwner(source, e, task, onFail, MAX_OWNER_RETRIES);
    }

    private static void runOnOwner(CommandSource source, Entity e, java.util.function.Consumer<Entity> task, Runnable onFail, int tries) {
        var world = source.getServer().world(e.dimensionKey());
        if (world == null) world = source.level();
        world.runAt(e.x(), e.z(), region -> {
            var lvl = region.level();
            if (lvl != null && lvl.entities().contains(e)) {
                task.accept(e);
            } else if (!e.isRemoved() && tries > 0) {
                runOnOwner(source, e, task, onFail, tries - 1);
            } else {
                onFail.run();
            }
        });
    }

    /** r 의 주인 스레드에서 fn 을 돌린다. 플레이어는 지금 스레드에서 바로 돌린다. */
    public static <T> CompletableFuture<T> onOwner(CommandSource source, Rider r, Function<Rider, T> fn) {
        if (!(r instanceof Entity e)) return CompletableFuture.completedFuture(fn.apply(r));
        CompletableFuture<T> f = new CompletableFuture<>();
        runOnOwner(source, e, en -> {
            try {
                f.complete(fn.apply(en));
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        }, () -> f.completeExceptionally(new IllegalStateException("엔티티를 가진 region 을 찾지 못했다: " + e.typeName())));
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
                if (ex != null) throw unwrap(ex);
                finisher.finish(collect(fs));
            } catch (CommandSyntaxException e) {
                source.sendFailure(e.getRawMessage());
            } catch (Throwable t) {
                reportFailure(source, t);
            }
        });
        return 1;
    }

    /** CompletionException·ExecutionException 껍질을 벗긴 원인. */
    public static Throwable unwrap(Throwable t) {
        while ((t instanceof java.util.concurrent.CompletionException || t instanceof java.util.concurrent.ExecutionException) && t.getCause() != null) t = t.getCause();
        return t;
    }

    /** 명령 도중 예상 못한 실패: 로그를 남기고 사용자에게 알린다. */
    public static void reportFailure(CommandSource source, Throwable t) {
        org.slf4j.LoggerFactory.getLogger(EntityOps.class).warn("명령 처리 실패", t);
        source.sendFailure(org.domi.furnace.api.text.Text.translatable("command.failed"));
    }

    private static <T> List<T> collect(List<CompletableFuture<T>> fs) {
        List<T> out = new ArrayList<>(fs.size());
        for (var f : fs) out.add(f.join());
        return out;
    }

    /** 플레이어면 접속 이름, 아니면 종류 이름(로그용). */
    public static String describe(Rider r) { return r instanceof Player p ? p.name() : r.typeName(); }
}
