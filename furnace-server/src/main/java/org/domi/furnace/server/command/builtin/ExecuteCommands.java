// 바닐라 ExecuteCommand(amt) 중 E1 단계: run·as·at·positioned(as, over)·rotated(as)·facing(entity)·align·anchored·in·summon·on.
// 수정자는 소스를 바꿀 뿐 부수효과가 없다. region 에서 읽어야 하는 것(over·summon·on)과 선택자 해석(as·at·…)은 비동기 수정자로 만들어,
// 실행 코어(BuildContexts)가 미완료면 큐를 멈췄다가 전역 스레드에서 이어 가게 한다. 소스 변형(엔티티 위치·회전·차원 읽기)은 전역 스레드에서 한다.
// if/unless 의 entity·block·blocks·biome·loaded·dimension·predicate·items(E2)·score(E3)·data(E4) 는 ExecuteConditions. store 는 score·bossbar(E3) 와
// block·entity·storage(E4, 콜백이 주인 스레드에서 쓰고 컨텍스트가 완료를 기다린다). function(E5) 만 아직 없다 — VanillaCommandTreeTest.NOT_YET 가 그만큼만 빼 준다.
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Function;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.EntityOps;
import org.domi.furnace.server.command.arguments.BlockPosArgument;
import org.domi.furnace.server.command.arguments.DimensionArgument;
import org.domi.furnace.server.command.arguments.EntityAnchorArgument;
import org.domi.furnace.server.command.arguments.EntityArgument;
import org.domi.furnace.server.command.arguments.EntitySelector;
import org.domi.furnace.server.command.arguments.HeightmapTypeArgument;
import org.domi.furnace.server.command.arguments.NbtPathArgument;
import org.domi.furnace.server.command.arguments.NbtPathArgument.NbtPath;
import org.domi.furnace.server.command.arguments.ResourceArgument;
import org.domi.furnace.server.command.arguments.ResourceLocationArgument;
import org.domi.furnace.server.command.arguments.RotationArgument;
import org.domi.furnace.server.command.arguments.ScoreHolderArgument;
import org.domi.furnace.server.command.arguments.ScoreboardArguments;
import org.domi.furnace.server.command.exec.ResultCallback;
import org.domi.furnace.server.bossbar.CustomBossEvent;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.scores.Objective;
import org.domi.furnace.server.scores.ScoreHolder;
import org.domi.furnace.server.scores.ServerScoreboard;
import org.domi.furnace.server.command.arguments.SwizzleArgument;
import org.domi.furnace.server.command.arguments.Vec3Argument;
import org.domi.furnace.server.command.brigadier.Arguments;
import org.domi.furnace.server.command.brigadier.AsyncRedirectModifier;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.LiteralArgumentBuilder;
import org.domi.furnace.server.command.brigadier.LiteralCommandNode;
import org.domi.furnace.server.entity.AbstractHorse;
import org.domi.furnace.server.entity.AreaEffectCloud;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.EvokerFangs;
import org.domi.furnace.server.entity.ItemEntity;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.PrimedTnt;
import org.domi.furnace.server.entity.Projectile;
import org.domi.furnace.server.entity.Rider;
import org.domi.furnace.server.entity.TamableAnimal;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.Vex;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.World;

public final class ExecuteCommands {
    private ExecuteCommands() { }

    public static void register(CommandDispatcher<CommandSource> d) {
        LiteralCommandNode<CommandSource> execute = d.register(literal("execute").requires(s -> s.hasPermission(2)));
        d.register(literal("execute").requires(s -> s.hasPermission(2))
                .then(literal("run").redirect(d.getRoot()))
                .then(literal("as").then(argument("targets", EntityArgument.entities())
                        .forkAsync(execute, c -> entities(c, "targets", (s, e) -> s.withEntity(e.entity())))))
                .then(literal("at").then(argument("targets", EntityArgument.entities())
                        .forkAsync(execute, c -> entities(c, "targets", (s, e) -> {
                            World w = s.getServer().world(e.dim());
                            return (w == null ? s : s.withLevel(w)).withPosition(e.x(), e.y(), e.z()).withRotation(e.yRot(), e.xRot());
                        }))))
                .then(literal("positioned")
                        .then(argument("pos", Vec3Argument.vec3()).redirect(execute, c -> {
                            double[] p = Vec3Argument.getVec3(c, "pos");
                            return c.getSource().withPosition(p[0], p[1], p[2]).withAnchor(EntityAnchorArgument.Anchor.FEET);
                        }))
                        .then(literal("as").then(argument("targets", EntityArgument.entities())
                                .forkAsync(execute, c -> entities(c, "targets", (s, e) -> s.withPosition(e.x(), e.y(), e.z())))))
                        .then(literal("over").then(argument("heightmap", HeightmapTypeArgument.heightmap())
                                .redirectAsync(execute, ExecuteCommands::over))))
                .then(literal("rotated")
                        .then(argument("rot", RotationArgument.rotation()).redirect(execute, c -> {
                            var v = RotationArgument.getRotation(c, "rot").get(c.getSource());   // x=pitch, y=yaw
                            return c.getSource().withRotation(v.y(), v.x());
                        }))
                        .then(literal("as").then(argument("targets", EntityArgument.entities())
                                .forkAsync(execute, c -> entities(c, "targets", (s, e) -> s.withRotation(e.yRot(), e.xRot()))))))
                .then(literal("facing")
                        .then(literal("entity").then(argument("targets", EntityArgument.entities())
                                .then(argument("anchor", EntityAnchorArgument.anchor()).forkAsync(execute, c -> {
                                    EntityAnchorArgument.Anchor anchor = EntityAnchorArgument.getAnchor(c, "anchor");
                                    return entities(c, "targets", (s, e) -> {
                                        double[] p = e.anchor(anchor);
                                        return s.facing(p[0], p[1], p[2]);
                                    });
                                }))))
                        .then(argument("pos", Vec3Argument.vec3()).redirect(execute, c -> {
                            double[] p = Vec3Argument.getVec3(c, "pos");
                            return c.getSource().facing(p[0], p[1], p[2]);
                        })))
                .then(literal("align").then(argument("axes", SwizzleArgument.swizzle()).redirect(execute, c -> {
                    CommandSource s = c.getSource();
                    var axes = SwizzleArgument.getSwizzle(c, "axes");
                    return s.withPosition(axes.contains(SwizzleArgument.Axis.X) ? Math.floor(s.x()) : s.x(),
                            axes.contains(SwizzleArgument.Axis.Y) ? Math.floor(s.y()) : s.y(),
                            axes.contains(SwizzleArgument.Axis.Z) ? Math.floor(s.z()) : s.z());
                })))
                .then(literal("anchored").then(argument("anchor", EntityAnchorArgument.anchor())
                        .redirect(execute, c -> c.getSource().withAnchor(EntityAnchorArgument.getAnchor(c, "anchor")))))
                .then(literal("in").then(argument("dimension", DimensionArgument.dimension())
                        .redirect(execute, c -> c.getSource().withLevel(DimensionArgument.getDimension(c, "dimension")))))
                .then(literal("summon").then(argument("entity", ResourceArgument.resource("minecraft:entity_type")).suggests(SummonCommand.SUMMONABLE_ENTITIES)
                        .redirectAsync(execute, ExecuteCommands::summon)))
                .then(on(execute, literal("on")))
                .then(literal("store").then(store(execute, literal("result"), true)).then(store(execute, literal("success"), false)))
                .then(ExecuteConditions.build(execute, literal("if"), true))
                .then(ExecuteConditions.build(execute, literal("unless"), false)));
    }

    // ---------------------------------------------------------------- store (E3: score·bossbar, E4: block·entity·storage)

    /** 바닐라 amt 의 IntFunction<Tag>: 결과 × scale 을 그 타입으로 캐스팅한 NBT 값(바닐라 캐스팅 순서 그대로: double 곱 뒤 캐스팅). */
    @FunctionalInterface
    private interface TagMaker {
        Object make(int value, double scale);
    }

    private static final String[] STORE_TYPES = {"int", "float", "short", "long", "double", "byte"};
    private static final TagMaker[] STORE_MAKERS = {
            (v, s) -> (int) (v * s), (v, s) -> (float) (v * s), (v, s) -> (short) (v * s),
            (v, s) -> (long) (v * s), (v, s) -> v * s, (v, s) -> (byte) (v * s)};

    private static LiteralArgumentBuilder<CommandSource> store(LiteralCommandNode<CommandSource> execute, LiteralArgumentBuilder<CommandSource> b, boolean result) {
        for (DataCommands.DataProvider provider : DataCommands.TARGET_PROVIDERS) {
            provider.wrap(b, node -> {
                var path = argument("path", NbtPathArgument.nbtPath());
                for (int i = 0; i < STORE_TYPES.length; i++) {
                    TagMaker maker = STORE_MAKERS[i];
                    path.then(literal(STORE_TYPES[i]).then(argument("scale", Arguments.doubleArg(-Double.MAX_VALUE, Double.MAX_VALUE))
                            .redirectAsync(execute, c -> storeData(c, provider, maker, result))));
                }
                return node.then(path);
            });
        }
        return b
                .then(literal("bossbar").then(argument("id", ResourceLocationArgument.id())
                        .then(literal("value").redirect(execute, c -> storeBossbar(c, true, result)))
                        .then(literal("max").redirect(execute, c -> storeBossbar(c, false, result)))))
                .then(literal("score").then(argument("targets", ScoreHolderArgument.scoreHolders()).suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                .then(argument("objective", ScoreboardArguments.objective()).redirectAsync(execute, c -> {
                    CommandSource source = c.getSource();
                    // 바닐라는 redirect 수정자에서 대상 목록을 한 번 풀고(빈 목록·기준 없음은 여기서 오류), 콜백은 명령 결과마다 그 목록에 쓴다
                    CompletableFuture<Collection<ScoreHolder>> holders = ScoreHolderArgument.getNamesWithDefaultWildcard(c, "targets");
                    Objective objective = ScoreboardArguments.getObjective(c, "objective");
                    return ExecuteCommands.<Collection<ScoreHolder>, CommandSource>onGlobal(source, holders, list -> storeScore(source, list, objective, result));
                }))));
    }

    /** 바닐라 amt.a(et, als, value, result): 보스바 값/최대를 쓴다(CustomBossEvent 는 synchronized). 막대 조회는 redirect 때 한다. */
    private static CommandSource storeBossbar(CommandContext<CommandSource> c, boolean value, boolean result) throws CommandSyntaxException {
        ResourceId id = ResourceLocationArgument.getId(c, "id");
        CustomBossEvent bar = c.getSource().getServer().customBossEvents().get(id);
        if (bar == null) throw BossBarCommands.ERROR_DOESNT_EXIST.create(id.toString());
        return c.getSource().withCallback((success, v) -> {
            int n = result ? v : (success ? 1 : 0);
            if (value) bar.setValue(n);
            else bar.setMax(n);
            return ResultCallback.DONE;
        }, ResultCallback::chain);
    }

    /** 바닐라 amt.a(et, Collection, objective, result): 소스 콜백에 점수 쓰기를 chain 한다. 점수판은 synchronized 라 어느 스레드에서 불려도 안전하다. */
    private static CommandSource storeScore(CommandSource source, Collection<ScoreHolder> holders, Objective objective, boolean result) {
        ServerScoreboard board = source.getServer().scoreboard();
        return source.withCallback((success, value) -> {
            int v = result ? value : (success ? 1 : 0);
            for (ScoreHolder h : holders) board.getOrCreatePlayerScore(h, objective).set(v);
            return ResultCallback.DONE;
        }, ResultCallback::chain);
    }

    /**
     * 바닐라 amt.a(et, DataAccessor, NbtPath, IntFunction<Tag>, result): 대상은 redirect 때 해석하고(오류는 여기서 명령 오류),
     * 콜백은 결과마다 주인 스레드에서 getData → path.set → setData 를 하고 완료 future 를 돌려준다(컨텍스트가 완료까지 기다린다).
     * 콜백 안 CommandSyntaxException(플레이어 대상 commands.data.entity.invalid, 블록 엔티티 사라짐, 경로 너무 깊음)은 바닐라처럼 삼킨다 — 메시지 없음.
     */
    private static CompletableFuture<CommandSource> storeData(CommandContext<CommandSource> c, DataCommands.DataProvider provider, TagMaker maker, boolean result)
            throws CommandSyntaxException {
        CommandSource source = c.getSource();
        NbtPath path = NbtPathArgument.getPath(c, "path");
        double scale = c.getArgument("scale", Double.class);
        CompletableFuture<DataCommands.Handle> handle = provider.resolve(c);
        return ExecuteCommands.<DataCommands.Handle, CommandSource>onGlobal(source, handle, h -> source.withCallback((success, value) -> {
            int v = result ? value : (success ? 1 : 0);
            Object tag = maker.make(v, scale);
            CompletableFuture<Void> write = h.with(source, a -> {
                NbtCompound data = a.getData();
                path.set(data, tag);
                a.setData(data);
                return null;
            });
            return swallowSyntaxErrors(source, write);
        }, ResultCallback::chain));
    }

    /** 콜백 쓰기의 CommandSyntaxException 은 삼키고(바닐라), 그 밖 오류는 그대로 실패. 미완료면 완료를 전역 스레드로 되돌린다(ResultCallback 규약). */
    private static CompletableFuture<Void> swallowSyntaxErrors(CommandSource source, CompletableFuture<Void> write) {
        if (write.isDone()) {
            if (!write.isCompletedExceptionally()) return ResultCallback.DONE;
            try {
                write.join();
            } catch (CompletionException | java.util.concurrent.CancellationException e) {
                Throwable t = EntityOps.unwrap(e);
                if (t instanceof CommandSyntaxException) return ResultCallback.DONE;
                if (t instanceof RuntimeException re) throw re;   // 바닐라 performCommand catch(= CommandQueue.drive) 로
                if (t instanceof Error err) throw err;
                throw new CompletionException(t);
            }
            return ResultCallback.DONE;
        }
        Executor global = r -> source.getServer().scheduler().global().execute(r);
        return write.<Void>handleAsync((v, ex) -> {
            if (ex == null || EntityOps.unwrap(ex) instanceof CommandSyntaxException) return null;
            throw ex instanceof CompletionException ce ? ce : new CompletionException(ex);
        }, global);
    }

    // ---------------------------------------------------------------- 선택자 포크

    @FunctionalInterface
    private interface SourceMapper {
        CommandSource map(CommandSource source, Snap entity);
    }

    /** 엔티티의 (차원, 위치, 회전, 눈높이) 불변 스냅샷 — 소유 region 스레드에서 한 번에 읽어 전역으로 넘긴다(한 틱의 일관된 값). */
    private record Snap(Rider entity, String dim, double x, double y, double z, float yRot, float xRot, double eyeY) {
        static Snap of(Rider r) {
            return new Snap(r, r.dimensionKey(), r.x(), r.y(), r.z(), r.riderYRot(), r.riderXRot(), r.eyeY());
        }

        double[] anchor(EntityAnchorArgument.Anchor a) { return a == EntityAnchorArgument.Anchor.FEET ? new double[] {x, y, z} : new double[] {x, eyeY, z}; }
    }

    /**
     * 선택자(0개 이상 허용, 바닐라 getOptionalEntities)를 풀어 엔티티마다 소스를 만든다.
     * 엔티티 상태는 소유 region 에서 스냅샷으로 읽고(플레이어는 그 자리에서), 전역 스레드에서는 소스만 조립한다.
     * 그새 사라져 주인을 못 찾은 엔티티는 뺀다.
     */
    private static CompletableFuture<Collection<CommandSource>> entities(CommandContext<CommandSource> c, String name, SourceMapper mapper)
            throws CommandSyntaxException {
        CommandSource source = c.getSource();
        CompletableFuture<List<Rider>> found = c.getArgument(name, EntitySelector.class).findRiders(source);
        CompletableFuture<List<CompletableFuture<Snap>>> reads = ExecuteCommands.<List<Rider>, List<CompletableFuture<Snap>>>onGlobal(source, found, list -> {
            List<CompletableFuture<Snap>> fs = new java.util.ArrayList<>(list.size());
            for (Rider r : list) fs.add(EntityOps.onOwner(source, r, Snap::of).exceptionally(t -> null));
            return fs;
        });
        CompletableFuture<List<Snap>> snaps = reads.thenCompose(fs -> CompletableFuture.allOf(fs.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<Snap> out = new java.util.ArrayList<>(fs.size());
                    for (var f : fs) if (f.join() != null) out.add(f.join());
                    return out;
                }));
        return ExecuteCommands.<List<Snap>, Collection<CommandSource>>onGlobal(source, snaps, list -> {
            java.util.ArrayList<CommandSource> out = new java.util.ArrayList<>(list.size());
            for (Snap s : list) out.add(mapper.map(source, s));
            return out;
        });
    }

    // ---------------------------------------------------------------- positioned over

    private static CompletableFuture<CommandSource> over(CommandContext<CommandSource> c) throws CommandSyntaxException {
        CommandSource source = c.getSource();
        World level = source.level();
        double x = source.x(), z = source.z();
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        // 조회성: 주인 region 이 없으면 region 을 만들지 말고 바로 "로드 안 됨"으로 끝낸다(runAtRequested 와 같은 원칙).
        if (level.regionizer().ownerOf(bx >> 4, bz >> 4) == null) throw BlockPosArgument.ERROR_NOT_LOADED.create();
        var type = HeightmapTypeArgument.getHeightmap(c, "heightmap");
        CompletableFuture<Integer> height = level.submitAtOwnedChunk(new ChunkPos(bx >> 4, bz >> 4), r -> r.heightAt(type, bx, bz));
        if (height == null) throw BlockPosArgument.ERROR_NOT_LOADED.create();
        return onGlobal(source, height, h -> {
            if (h == Integer.MIN_VALUE) throw BlockPosArgument.ERROR_NOT_LOADED.create();
            return source.withPosition(x, h, z);
        });
    }

    // ---------------------------------------------------------------- summon

    private static CompletableFuture<CommandSource> summon(CommandContext<CommandSource> c) throws CommandSyntaxException {
        CommandSource source = c.getSource();
        CompletableFuture<Entity> spawned = SummonCommand.summonChecked(source, ResourceLocationArgument.getId(c, "entity"),
                new double[] {source.x(), source.y(), source.z()}, new NbtCompound(), true);
        // 타임아웃 뒤 늦게 끝나면 엔티티는 이미 생겼고 결과(소스 변환)만 버려진다(세대 토큰, 설계서 §7 "늦은 부작용")
        return onGlobal(source, spawned, source::withEntity);
    }

    // ---------------------------------------------------------------- on

    private static LiteralArgumentBuilder<CommandSource> on(LiteralCommandNode<CommandSource> execute, LiteralArgumentBuilder<CommandSource> on) {
        return on
                .then(literal("owner").forkAsync(execute, relation(ExecuteCommands::owner)))
                .then(literal("leasher").forkAsync(execute, relation(r -> List.of())))   // 끈 체계가 아직 없다(isLeashed 는 항상 거짓)
                .then(literal("target").forkAsync(execute, relation(r -> r instanceof Mob m ? one(m.getTarget()) : List.of())))
                .then(literal("attacker").forkAsync(execute, relation(r -> one(lastHurtBy(r)))))
                .then(literal("vehicle").forkAsync(execute, relation(r -> one(r.vehicle()))))
                .then(literal("controller").forkAsync(execute, relation(r -> r instanceof Entity e ? one(e.getControllingPassenger()) : List.of())))
                .then(literal("origin").forkAsync(execute, relation(ExecuteCommands::origin)))
                .then(literal("passengers").forkAsync(execute, relation(r -> r instanceof Entity e ? List.copyOf(e.passengers()) : List.of())));
    }

    /** 소스 엔티티의 주인 region(플레이어는 그 자리)에서 관계 엔티티를 읽는다. 제거된 엔티티는 뺀다. */
    private static AsyncRedirectModifier<CommandSource> relation(Function<Rider, List<Rider>> fn) {
        return c -> {
            CommandSource source = c.getSource();
            Rider self = source.entity();
            if (self == null) return CompletableFuture.<Collection<CommandSource>>completedFuture(List.of());
            // 제거 여부는 관계를 읽는 순간 소유 region 안에서 거른다(바닐라 amt.java:693,701)
            Function<Rider, List<Rider>> live = r -> fn.apply(r).stream().filter(x -> !(x instanceof Entity e && e.isRemoved())).toList();
            CompletableFuture<List<Rider>> read = EntityOps.onOwner(source, self, live)
                    .exceptionally(t -> EntityOps.unwrap(t) instanceof IllegalStateException ? List.of() : rethrow(t));   // 그새 사라진 엔티티는 관계가 없다
            return ExecuteCommands.<List<Rider>, Collection<CommandSource>>onGlobal(source, read, list -> {
                java.util.ArrayList<CommandSource> out = new java.util.ArrayList<>(list.size());
                for (Rider r : list) if (!(r instanceof Player p && !source.getServer().players().contains(p))) out.add(source.withEntity(r));   // 접속이 끊긴 플레이어는 뺀다
                return out;
            });
        };
    }

    private static List<Rider> rethrow(Throwable t) {
        throw t instanceof CompletionException ce ? ce : new CompletionException(t);
    }

    private static List<Rider> one(Object o) { return o instanceof Rider r ? List.of(r) : List.of(); }

    private static Targetable lastHurtBy(Rider r) {
        return r instanceof Player p ? p.getLastHurtByMob() : r instanceof org.domi.furnace.server.entity.LivingEntity l ? l.getLastHurtByMob() : null;
    }

    /** OwnableEntity.getOwner — 길들인 동물·말(플레이어만 주인이 될 수 있다). */
    private static List<Rider> owner(Rider r) {
        if (r instanceof TamableAnimal t) return one(t.getOwner());
        if (r instanceof AbstractHorse h) {
            var uuid = h.getOwnerUUID();
            return uuid == null ? List.of() : h.level().getTargetableByUuid(uuid) instanceof Player p ? List.of(p) : List.of();
        }
        return List.of();
    }

    /** TraceableEntity.getOwner — 발사체·효과 구름·송곳니·점화된 TNT·벡스·떨어진 아이템. */
    private static List<Rider> origin(Rider r) {
        Optional<Object> o = Optional.empty();
        if (r instanceof Projectile p) o = Optional.ofNullable(p.getOwner());
        else if (r instanceof AreaEffectCloud a) o = Optional.ofNullable(a.getOwner());
        else if (r instanceof EvokerFangs f) o = Optional.ofNullable(f.getOwner());
        else if (r instanceof PrimedTnt t) o = Optional.ofNullable(t.getOwner());
        else if (r instanceof Vex v) o = Optional.ofNullable(v.getOwner());
        else if (r instanceof ItemEntity i) o = Optional.ofNullable(i.getOwnerEntity());
        return o.map(ExecuteCommands::one).orElse(List.of());
    }

    // ---------------------------------------------------------------- 공통

    @FunctionalInterface
    interface Mapper<T, R> {
        R apply(T value) throws CommandSyntaxException;
    }

    /**
     * future 를 값으로 바꾸는 단계를 **전역 스레드에서** 돌린다. 이미 끝나 있으면(우리는 전역 스레드) 그 자리에서,
     * 아니면 완료 뒤 전역으로 넘겨서 — region 스레드에서 완성되는 future 에 그냥 thenApply 를 붙이면 변환이 region 에서 돈다.
     */
    static <T, R> CompletableFuture<R> onGlobal(CommandSource source, CompletableFuture<T> f, Mapper<T, R> fn) {
        if (f.isDone()) {
            try {
                return CompletableFuture.completedFuture(fn.apply(f.join()));
            } catch (CompletionException e) {
                return CompletableFuture.failedFuture(EntityOps.unwrap(e));
            } catch (Throwable t) {
                return CompletableFuture.failedFuture(t);
            }
        }
        Executor global = r -> source.getServer().scheduler().global().execute(r);
        return f.thenApplyAsync(v -> {
            try {
                return fn.apply(v);
            } catch (CommandSyntaxException e) {
                throw new CompletionException(e);
            }
        }, global);
    }
}
