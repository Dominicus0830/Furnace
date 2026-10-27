// 명령이 엔티티를 건드릴 때의 스레드 규칙: 일반 엔티티는 그 엔티티를 가진 region 스레드에서만 바꾼다(플레이어는 연결이 스레드를 알아서 처리).
// 주인에게 보낸 일의 결과는 future 로 모으고, 모두 끝났으면(플레이어만 건드렸다면 즉시) 그 자리에서, 아니면 전역 스레드로 돌아와 이어 간다.
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
    /** 플레이어 주인 찾기 재시도 상한(틱 — 한 번에 50ms 뒤). 차원 이동은 목적 청크 준비로 몇 틱 걸린다. */
    private static final int PLAYER_OWNER_RETRY_TICKS = 40;

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

    /**
     * 플레이어를 가진 region 스레드에서 task 를 돌린다. 플레이어의 지금 차원 월드에서 위치로 보내고, 도착한 region 의 players 에
     * 있는지 **먼저** 확인한 뒤에만 task 를 부른다(아니면 한 틱 뒤 지금 위치·차원으로 다시, 최대 40틱). 끝내 못 찾으면(접속 끊김·차원 이동 중) onFail.
     */
    public static void runOnPlayerOwner(CommandSource source, Player p, java.util.function.Consumer<org.domi.furnace.server.world.Region> task, Runnable onFail) {
        runOnPlayerOwner(source, p, task, onFail, PLAYER_OWNER_RETRY_TICKS);
    }

    private static void runOnPlayerOwner(CommandSource source, Player p, java.util.function.Consumer<org.domi.furnace.server.world.Region> task,
                                         Runnable onFail, int tries) {
        var world = source.getServer().world(p.dimension());
        if (world == null) world = source.getServer().world();
        world.runAt(p.x(), p.z(), region -> {
            if (region.players().get(p.entityId()) == p) {
                task.accept(region);
            } else if (!p.isDisconnected() && tries > 0) {
                // 넘기기 대기·차원 이동 중일 수 있다 — 한 틱(50ms) 뒤 지금 위치·차원으로 다시
                CompletableFuture.delayedExecutor(50, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .execute(() -> runOnPlayerOwner(source, p, task, onFail, tries - 1));
            } else {
                onFail.run();
            }
        });
    }

    /** 주인을 끝내 못 찾았을 때의 실패(명령 오류로 보고된다). */
    public static IllegalStateException ownerNotFound(Rider r) {
        return new IllegalStateException("대상을 가진 region 을 찾지 못했다: " + describe(r));
    }

    /** region 작업: 오류를 던질 수 있다. */
    @FunctionalInterface
    public interface RegionTask<T> {
        T run(org.domi.furnace.server.world.Region region) throws Exception;
    }

    /** 그 좌표 청크의 주인 region 에서 task 를 돌리고 결과(오류 포함)를 future 로 돌려준다. 메시지는 이 안에서 보내지 말고 PostMessages 에 모은다. */
    public static <T> CompletableFuture<T> atRegion(org.domi.furnace.server.world.World world, double blockX, double blockZ, RegionTask<T> task) {
        CompletableFuture<T> f = new CompletableFuture<>();
        world.runAt(blockX, blockZ, region -> {
            try {
                f.complete(task.run(region));
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        return f;
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

    /** 값을 받아 future 결과를 내는 다음 단계(헬퍼 합성용). */
    @FunctionalInterface
    public interface AsyncAction<T> {
        CompletableFuture<Integer> run(T value) throws CommandSyntaxException;
    }

    /** 대상마다 onOwner 로 보낸 결과를 모아 마무리를 부른다(껍데기 — 명령 본문 맨 바깥에서만). 끝나 있으면 그 결과, 아니면 defer 후 1(무시됨). */
    public static <T> int forEach(CommandSource source, List<Rider> targets, Function<Rider, T> fn, Finisher<T> finisher) throws CommandSyntaxException {
        return result(source, forEachAsync(source, targets, fn, finisher));
    }

    /** forEach 의 future 판(중첩 헬퍼 안쪽에서). 마무리는 전역 스레드에서 돈다. */
    public static <T> CompletableFuture<Integer> forEachAsync(CommandSource source, List<Rider> targets, Function<Rider, T> fn, Finisher<T> finisher) {
        List<CompletableFuture<T>> fs = new ArrayList<>(targets.size());
        for (Rider r : targets) fs.add(onOwner(source, r, fn));
        return awaitAllAsync(source, fs, finisher);
    }

    public static <T> int await(CommandSource source, List<CompletableFuture<T>> fs, Finisher<T> finisher) throws CommandSyntaxException {
        return result(source, awaitAllAsync(source, fs, finisher));
    }

    public static <T> CompletableFuture<Integer> awaitAllAsync(CommandSource source, List<CompletableFuture<T>> fs, Finisher<T> finisher) {
        CompletableFuture<Void> all = CompletableFuture.allOf(fs.toArray(new CompletableFuture[0]));
        return awaitAsync(source, all, v -> CompletableFuture.completedFuture(finisher.finish(collect(fs))));
    }

    /**
     * 헬퍼 공통 본체: future 가 끝나 있으면 action 을 그 자리에서(지름길), 아니면 끝난 뒤 **전역 스레드에서** 부른다
     * (region 에서 완성되는 future 에 thenCompose 를 그냥 붙이면 action 이 region 에서 돈다 — 설계 §1.4).
     * action 의 오류(CommandSyntaxException 포함)는 실패한 future 로 돌려준다.
     */
    public static <T> CompletableFuture<Integer> awaitAsync(CommandSource source, CompletableFuture<T> future, AsyncAction<T> action) {
        if (future.isDone()) {
            T v;
            try {
                v = future.join();
            } catch (java.util.concurrent.CompletionException | java.util.concurrent.CancellationException e) {
                return CompletableFuture.failedFuture(unwrap(e));
            }
            return invoke(action, v);
        }
        java.util.concurrent.Executor global = r -> source.getServer().scheduler().global().execute(r);
        return future.thenComposeAsync(v -> invoke(action, v), global);
    }

    private static <T> CompletableFuture<Integer> invoke(AsyncAction<T> action, T v) {
        try {
            CompletableFuture<Integer> f = action.run(v);
            return f == null ? CompletableFuture.completedFuture(0) : f;
        } catch (Throwable t) {
            return CompletableFuture.failedFuture(t);
        }
    }

    /**
     * 껍데기: 끝난 future 면 그 값(오류는 벗겨서 그대로 던진다), 아니면 실행 컨텍스트에 맡기고(source.defer) 1 을 돌려준다(무시됨).
     * 결과·오류 메시지는 컨텍스트가 전역 스레드에서 처리한다(forked 규칙 포함).
     */
    public static int result(CommandSource source, CompletableFuture<Integer> f) throws CommandSyntaxException {
        if (f.isDone()) {
            try {
                Integer v = f.join();
                return v == null ? 0 : v;
            } catch (java.util.concurrent.CompletionException | java.util.concurrent.CancellationException e) {
                Throwable t = unwrap(e);
                if (t instanceof CommandSyntaxException cse) throw cse;
                if (t instanceof RuntimeException re) throw re;
                if (t instanceof Error err) throw err;
                throw new RuntimeException(t);
            }
        }
        source.defer(f);
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
