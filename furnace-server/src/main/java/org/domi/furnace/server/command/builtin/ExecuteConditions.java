// 바닐라 ExecuteCommand(amt) 중 E2~E4 단계: execute if|unless score(E3)·data(E4: block|entity|storage 경로 일치 수)·entity·block·blocks·biome·loaded·dimension·predicate·items.
// 조건 하나는 포크(참이면 소스 유지, 아니면 사라짐)와 종단 실행(성공 1 / 개수 / 실패)의 두 얼굴을 가진다. 결과 의미는 바닐라 그대로:
//  - 불리언 조건: if 는 참이면 1(conditional.pass), 아니면 conditional.fail. unless 는 반대.
//  - 개수 조건(entity·items·blocks): if 는 개수가 있으면 그 개수(pass_count), 없으면 fail. unless 는 없으면 1, 있으면 fail_count(개수).
// region 상태(블록·바이옴·엔티티 칸·청크)는 소유 region 에서 읽고, 판정과 메시지는 전역 스레드에서 한다. 조회성 조건(block·blocks·biome·loaded·items block)은
// 주인 region 이 없으면 region 을 만들지 않고 즉시 "로드 안 됨"(loaded 는 거짓)으로 끝낸다.
// 차이: blocks 는 두 영역과 세 모서리가 시작 영역의 최소 모서리 청크를 가진 region 한 곳에 모두 로드돼 있어야 한다(/clone 과 같은 제약, 아니면 argument.pos.unloaded).
//       loaded 는 바닐라처럼 엔티티 틱 단계 + 엔티티 로드 완료 청크여야 참이다(Region.isEntityTickingLoaded — 플레이어 시뮬레이션 거리 안 또는 /forceload). biome 의 지터 이웃 청크가 안 불러져 있으면 바닐라처럼 생성기 바이옴으로 본다.
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.Collection;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.entity.BlockEntity;
import org.domi.furnace.server.block.entity.HopperBlockEntity;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.EntityOps;
import org.domi.furnace.server.command.ItemSlots;
import org.domi.furnace.server.command.SharedSuggestions;
import org.domi.furnace.server.command.arguments.BlockPosArgument;
import org.domi.furnace.server.command.arguments.BlockPredicateArgument;
import org.domi.furnace.server.command.arguments.DimensionArgument;
import org.domi.furnace.server.command.arguments.EntityArgument;
import org.domi.furnace.server.command.arguments.EntitySelector;
import org.domi.furnace.server.command.arguments.IntRangeArgument;
import org.domi.furnace.server.command.arguments.ItemPredicateArgument;
import org.domi.furnace.server.command.arguments.NbtPathArgument;
import org.domi.furnace.server.command.arguments.NbtPathArgument.NbtPath;
import org.domi.furnace.server.command.arguments.ScoreHolderArgument;
import org.domi.furnace.server.command.arguments.ScoreboardArguments;
import org.domi.furnace.server.scores.Objective;
import org.domi.furnace.server.scores.ScoreHolder;
import org.domi.furnace.server.command.arguments.ResourceOrIdArgument;
import org.domi.furnace.server.command.arguments.ResourceOrTagKeyArgument;
import org.domi.furnace.server.command.arguments.SlotsArgument;
import org.domi.furnace.server.command.brigadier.ArgumentBuilder;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.Dynamic2CommandExceptionType;
import org.domi.furnace.server.command.brigadier.DynamicCommandExceptionType;
import org.domi.furnace.server.command.brigadier.LiteralArgumentBuilder;
import org.domi.furnace.server.command.brigadier.LiteralCommandNode;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.LivingEntity;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Rider;
import org.domi.furnace.server.loot.LootCondition;
import org.domi.furnace.server.loot.LootEntity;
import org.domi.furnace.server.loot.LootParams;
import org.domi.furnace.server.net.io.nbt.NbtTags;
import org.domi.furnace.server.world.BiomeZoom;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.RegionLevel;
import org.domi.furnace.server.world.World;

final class ExecuteConditions {
    private ExecuteConditions() { }

    private static final int MAX_BLOCKS = 32768;
    private static final String BIOME_REGISTRY = "minecraft:worldgen/biome";

    private static final SimpleCommandExceptionType ERROR_FAIL = new SimpleCommandExceptionType(Text.translatable("commands.execute.conditional.fail"));
    private static final DynamicCommandExceptionType ERROR_FAIL_COUNT = new DynamicCommandExceptionType(n -> Text.translatable("commands.execute.conditional.fail_count", n));
    private static final Dynamic2CommandExceptionType ERROR_AREA_TOO_LARGE =
            new Dynamic2CommandExceptionType((max, given) -> Text.translatable("commands.execute.blocks.toobig", max, given));
    private static final Dynamic2CommandExceptionType ERROR_UNKNOWN_RESOURCE =
            new Dynamic2CommandExceptionType((id, reg) -> Text.translatable("argument.resource.not_found", id, reg));
    private static final Dynamic2CommandExceptionType ERROR_UNKNOWN_TAG =
            new Dynamic2CommandExceptionType((id, reg) -> Text.translatable("argument.resource_tag.not_found", id, reg));

    /** 불리언 조건. 인자 오류는 던지고, region 에서 읽는 것은 future 로 돌려준다. */
    @FunctionalInterface
    private interface BoolTest {
        CompletableFuture<Boolean> test(CommandContext<CommandSource> c) throws CommandSyntaxException;
    }

    /** 개수 조건. 없음은 empty(entity·items 는 0 도 없음, blocks 는 일치하면 0 이어도 있음). */
    @FunctionalInterface
    private interface CountTest {
        CompletableFuture<OptionalInt> test(CommandContext<CommandSource> c) throws CommandSyntaxException;
    }

    /** 칸 하나가 속한 region 에서 레벨을 읽는 일. */
    @FunctionalInterface
    private interface LevelRead<T> {
        T read(RegionLevel level) throws CommandSyntaxException;
    }

    // ---------------------------------------------------------------- 트리

    /** if(expected=true) 또는 unless(expected=false) 노드. */
    static LiteralArgumentBuilder<CommandSource> build(LiteralCommandNode<CommandSource> execute, LiteralArgumentBuilder<CommandSource> root, boolean expected) {
        // 바닐라: SOURCE_PROVIDERS(source·sourcePos) 마다 같은 data 리터럴에 block|entity|storage 를 붙인다. 조건은 경로 일치 수 > 0, 종단은 일치 수.
        LiteralArgumentBuilder<CommandSource> data = literal("data");
        for (DataCommands.DataProvider provider : DataCommands.SOURCE_PROVIDERS) {
            provider.wrap(data, b -> b.then(count(execute, argument("path", NbtPathArgument.nbtPath()), expected, c -> data(c, provider))));
        }
        root.then(data);
        return root
                .then(literal("block").then(argument("pos", BlockPosArgument.blockPos())
                        .then(bool(execute, argument("block", BlockPredicateArgument.blockPredicate()), expected, ExecuteConditions::block))))
                .then(literal("biome").then(argument("pos", BlockPosArgument.blockPos())
                        .then(bool(execute, argument("biome", ResourceOrTagKeyArgument.resourceOrTag(BIOME_REGISTRY)), expected, ExecuteConditions::biome))))
                .then(literal("loaded").then(bool(execute, argument("pos", BlockPosArgument.blockPos()), expected, ExecuteConditions::loaded)))
                .then(literal("dimension").then(bool(execute, argument("dimension", DimensionArgument.dimension()), expected,
                        c -> CompletableFuture.completedFuture(DimensionArgument.getDimension(c, "dimension") == c.getSource().level()))))
                .then(literal("score").then(argument("target", ScoreHolderArgument.scoreHolder()).suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                        .then(scoreCompare(execute, expected))))
                .then(literal("entity").then(count(execute, argument("entities", EntityArgument.entities()), expected, ExecuteConditions::entity)))
                .then(literal("predicate").then(bool(execute, argument("predicate", ResourceOrIdArgument.lootPredicate()).suggests((c, b) -> {
                    var dp = c.getSource().getServer().dataPacks();
                    return SharedSuggestions.suggest(dp == null || dp.loot() == null ? List.of() : List.copyOf(dp.loot().predicateIds()), b);
                }), expected, ExecuteConditions::predicate)))
                .then(literal("blocks").then(argument("start", BlockPosArgument.blockPos()).then(argument("end", BlockPosArgument.blockPos())
                        .then(argument("destination", BlockPosArgument.blockPos())
                                .then(count(execute, literal("all"), expected, c -> blocks(c, false)))
                                .then(count(execute, literal("masked"), expected, c -> blocks(c, true)))))))
                .then(literal("items")
                        .then(literal("entity").then(argument("entities", EntityArgument.entities()).then(argument("slots", SlotsArgument.slots())
                                .then(count(execute, argument("item_predicate", ItemPredicateArgument.itemPredicate()), expected, ExecuteConditions::itemsEntity)))))
                        .then(literal("block").then(argument("pos", BlockPosArgument.blockPos()).then(argument("slots", SlotsArgument.slots())
                                .then(count(execute, argument("item_predicate", ItemPredicateArgument.itemPredicate()), expected, ExecuteConditions::itemsBlock))))));
    }

    /** 바닐라 if score: targetObjective 뒤에 비교 연산자 5개와 matches. */
    private static ArgumentBuilder<CommandSource, ?> scoreCompare(LiteralCommandNode<CommandSource> execute, boolean expected) {
        var targetObjective = argument("targetObjective", ScoreboardArguments.objective());
        String[] ops = {"=", "<", "<=", ">", ">="};
        IntBinaryPredicate[] tests = {(a, b) -> a == b, (a, b) -> a < b, (a, b) -> a <= b, (a, b) -> a > b, (a, b) -> a >= b};
        for (int i = 0; i < ops.length; i++) {
            IntBinaryPredicate t = tests[i];
            targetObjective.then(literal(ops[i]).then(argument("source", ScoreHolderArgument.scoreHolder()).suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                    .then(bool(execute, argument("sourceObjective", ScoreboardArguments.objective()), expected, c -> scoreCompare(c, t)))));
        }
        targetObjective.then(literal("matches").then(bool(execute, argument("range", IntRangeArgument.intRange()), expected, c -> {
            CommandSource s = c.getSource();
            var range = IntRangeArgument.getRange(c, "range");
            CompletableFuture<Collection<ScoreHolder>> target = ScoreHolderArgument.getName(c, "target");
            if (failedNow(target)) return target.thenApply(x -> false);
            Objective o = ScoreboardArguments.getObjective(c, "targetObjective");
            return ExecuteCommands.<Collection<ScoreHolder>, Boolean>onGlobal(s, target, list -> {
                Integer v = scoreOf(s, list, o);
                return v != null && range.matches(v);
            });
        })));
        return targetObjective;
    }

    @FunctionalInterface
    private interface IntBinaryPredicate {
        boolean test(int a, int b);
    }

    private static boolean failedNow(CompletableFuture<?> f) { return f.isDone() && f.isCompletedExceptionally(); }

    /**
     * 바닐라 순서(target → targetObjective → source → sourceObjective)로 오류를 낸다. 기준 조회는 동기라 선택자 future 를 기다리지 않고 바로 하되,
     * 이미 실패로 끝난 선택자 future 는 그 다음 기준 조회보다 먼저 돌려준다. 아직 안 끝난 선택자(region 왕복)의 실패는 뒤따르는 기준 오류보다 늦을 수 있다(context-notes).
     */
    private static CompletableFuture<Boolean> scoreCompare(CommandContext<CommandSource> c, IntBinaryPredicate test) throws CommandSyntaxException {
        CommandSource s = c.getSource();
        CompletableFuture<Collection<ScoreHolder>> target = ScoreHolderArgument.getName(c, "target");
        if (failedNow(target)) return target.thenApply(x -> false);
        Objective to = ScoreboardArguments.getObjective(c, "targetObjective");
        CompletableFuture<Collection<ScoreHolder>> source = ScoreHolderArgument.getName(c, "source");
        if (failedNow(source)) return source.thenApply(x -> false);
        Objective so = ScoreboardArguments.getObjective(c, "sourceObjective");
        CompletableFuture<List<Collection<ScoreHolder>>> both = target.thenCombine(source, List::of);
        return ExecuteCommands.<List<Collection<ScoreHolder>>, Boolean>onGlobal(s, both, l -> {
            Integer a = scoreOf(s, l.get(0), to);
            Integer b = scoreOf(s, l.get(1), so);
            return a != null && b != null && test.test(a, b);   // 점수가 없으면 거짓
        });
    }

    /** 점수판 읽기: 조회와 값 읽기를 같은 락 안에서 한다. 점수가 없으면 null. */
    private static Integer scoreOf(CommandSource s, Collection<ScoreHolder> holders, Objective objective) {
        var board = s.getServer().scoreboard();
        synchronized (board) {
            var score = board.getPlayerScoreInfo(holders.iterator().next(), objective);
            return score == null ? null : score.value();
        }
    }

    /** 바닐라 addConditional(boolean): 포크는 참/거짓에 따라 소스를 남기고, 종단은 pass(1) 또는 fail. */
    private static <B extends ArgumentBuilder<CommandSource, B>> B bool(LiteralCommandNode<CommandSource> execute, B builder, boolean expected, BoolTest test) {
        return builder.forkAsync(execute, c -> {
            CommandSource s = c.getSource();
            return ExecuteCommands.<Boolean, Collection<CommandSource>>onGlobal(s, test.test(c), r -> r == expected ? List.of(s) : List.<CommandSource>of());
        }).executes(c -> {
            CommandSource s = c.getSource();
            return EntityOps.result(s, ExecuteCommands.<Boolean, Integer>onGlobal(s, test.test(c), r -> {
                if (r != expected) throw ERROR_FAIL.create();
                s.sendSuccess(() -> Text.translatable("commands.execute.conditional.pass"), false);
                return 1;
            }));
        });
    }

    /** 바닐라 addConditional(count)/ blocks 의 a(…, masked): 포크는 있음/없음, 종단은 개수. */
    private static <B extends ArgumentBuilder<CommandSource, B>> B count(LiteralCommandNode<CommandSource> execute, B builder, boolean expected, CountTest test) {
        return builder.forkAsync(execute, c -> {
            CommandSource s = c.getSource();
            return ExecuteCommands.<OptionalInt, Collection<CommandSource>>onGlobal(s, test.test(c), n -> n.isPresent() == expected ? List.of(s) : List.<CommandSource>of());
        }).executes(c -> {
            CommandSource s = c.getSource();
            return EntityOps.result(s, ExecuteCommands.<OptionalInt, Integer>onGlobal(s, test.test(c), n -> {
                if (expected) {
                    if (n.isEmpty()) throw ERROR_FAIL.create();
                    int v = n.getAsInt();
                    s.sendSuccess(() -> Text.translatable("commands.execute.conditional.pass_count", v), false);
                    return v;
                }
                if (n.isPresent()) throw ERROR_FAIL_COUNT.create(n.getAsInt());
                s.sendSuccess(() -> Text.translatable("commands.execute.conditional.pass"), false);
                return 1;
            }));
        });
    }

    // ---------------------------------------------------------------- region 읽기 도우미

    private static Pos pos(CommandContext<CommandSource> c, String name) {
        int[] p = BlockPosArgument.getBlockPos(c, name);
        return new Pos(p[0], p[1], p[2]);
    }

    /**
     * 칸 p 의 소유 region 에서 읽는다. 주인 region 이 없거나 청크가 안 불러졌으면 argument.pos.unloaded, 월드 밖이면 outofworld
     * (바닐라 BlockPosArgument.getLoadedBlockPos). region 을 만들지 않는다.
     */
    private static <T> CompletableFuture<T> atLoaded(CommandSource source, Pos p, LevelRead<T> read) throws CommandSyntaxException {
        World w = source.level();
        ChunkPos cp = new ChunkPos(p.x() >> 4, p.z() >> 4);
        CompletableFuture<T> f = w.regionizer().ownerOf(cp.x(), cp.z()) == null ? null : w.submitAtOwnedChunk(cp, r -> {
            try {
                RegionLevel level = r.level();
                checkLoaded(level, p);
                return read.read(level);
            } catch (CommandSyntaxException e) {
                throw new CompletionException(e);
            }
        });
        if (f == null) throw BlockPosArgument.ERROR_NOT_LOADED.create();
        return f;
    }

    private static void checkLoaded(RegionLevel level, Pos p) throws CommandSyntaxException {
        if (level == null || !level.hasChunkAt(p)) throw BlockPosArgument.ERROR_NOT_LOADED.create();
        if (!level.isInWorldBounds(p)) throw BlockPosArgument.ERROR_OUT_OF_WORLD.create();
    }

    // ---------------------------------------------------------------- 조건

    private static CompletableFuture<Boolean> block(CommandContext<CommandSource> c) throws CommandSyntaxException {
        Pos p = pos(c, "pos");
        BlockPredicateArgument.Predicate predicate = BlockPredicateArgument.getBlockPredicate(c, "block");
        return atLoaded(c.getSource(), p, level -> predicate.test(level, p));
    }

    /** 바닐라는 위치 확인 없이 "그 청크가 로드돼 있나"만 본다(월드 밖 높이도 상관없음). */
    private static CompletableFuture<Boolean> loaded(CommandContext<CommandSource> c) {
        CommandSource s = c.getSource();
        Pos p = pos(c, "pos");
        ChunkPos cp = new ChunkPos(p.x() >> 4, p.z() >> 4);
        World w = s.level();
        if (w.regionizer().ownerOf(cp.x(), cp.z()) == null) return CompletableFuture.completedFuture(false);
        CompletableFuture<Boolean> f = w.submitAtOwnedChunk(cp, r -> r.isEntityTickingLoaded(cp.x(), cp.z()));
        return f == null ? CompletableFuture.completedFuture(false) : f;
    }

    private static CompletableFuture<Boolean> biome(CommandContext<CommandSource> c) throws CommandSyntaxException {
        CommandSource s = c.getSource();
        Pos p = pos(c, "pos");
        IntPredicate filter = biomeFilter(s, ResourceOrTagKeyArgument.get(c, "biome"));
        List<String> names = s.getServer().dataPacks().loot().registries().entries(BIOME_REGISTRY);
        World w = s.level();
        int[] q = BiomeZoom.quart(s.getServer().biomeZoomSeed(w), p.x(), p.y(), p.z());
        CompletableFuture<Boolean> loaded = atLoaded(s, p, level -> true);
        // 지터로 고른 quart 의 청크가 다른 region 일 수 있으니 그 주인에게 따로 묻는다(없으면 일치 없음)
        return ExecuteCommands.<Boolean, CompletableFuture<Integer>>onGlobal(s, loaded, ok -> {
            ChunkPos qc = new ChunkPos(q[0] >> 2, q[2] >> 2);
            if (w.regionizer().ownerOf(qc.x(), qc.z()) == null) return CompletableFuture.completedFuture(-1);
            CompletableFuture<Integer> id = w.submitAtOwnedChunk(qc, r -> r.biomeIdAtQuart(q[0], q[1], q[2]));
            return id == null ? CompletableFuture.completedFuture(-1) : id;
        }).thenCompose(f -> f).thenApply(id -> {
            if (id < 0) {
                // 바닐라 LevelReader.getNoiseBiome: 그 quart 의 청크가 없으면 생성기 바이옴(getUncachedNoiseBiome)
                var src = org.domi.furnace.server.world.gen.NoiseWorldSource.of(w.worldSource());
                String gen = src == null ? null : src.biomeAt(q[0] << 2, q[1] << 2, q[2] << 2);
                id = gen == null ? -1 : names.indexOf(gen);
            }
            return id >= 0 && filter.test(id);
        });
    }

    /** 바이옴 이름 목록의 인덱스가 바이옴 id. 모르는 id/태그는 실행 때 바닐라와 같은 오류. */
    private static IntPredicate biomeFilter(CommandSource s, ResourceOrTagKeyArgument.Result arg) throws CommandSyntaxException {
        String name = arg.id().namespace() + ":" + arg.id().path();
        var dp = s.getServer().dataPacks();
        if (dp == null || dp.loot() == null) throw (arg.tag() ? ERROR_UNKNOWN_TAG : ERROR_UNKNOWN_RESOURCE).create(name, BIOME_REGISTRY);
        var regs = dp.loot().registries();
        List<String> names = regs.entries(BIOME_REGISTRY);
        if (arg.tag()) {
            if (!regs.hasTag(BIOME_REGISTRY, name)) throw ERROR_UNKNOWN_TAG.create(name, BIOME_REGISTRY);
            List<String> members = regs.tag(BIOME_REGISTRY, name);
            return id -> id >= 0 && id < names.size() && members.contains(names.get(id));
        }
        int want = names.indexOf(name);
        if (want < 0) throw ERROR_UNKNOWN_RESOURCE.create(name, BIOME_REGISTRY);
        return id -> id == want;
    }

    private static CompletableFuture<OptionalInt> entity(CommandContext<CommandSource> c) throws CommandSyntaxException {
        // getOptionalEntities: 없어도 오류가 아니다
        return c.getArgument("entities", EntitySelector.class).findRiders(c.getSource())
                .thenApply(l -> l.isEmpty() ? OptionalInt.empty() : OptionalInt.of(l.size()));
    }

    /**
     * 바닐라 COMMAND 문맥: origin = 소스 위치, 소스 엔티티가 있으면 this_entity.
     * this_entity 는 그 엔티티의 소유 region 에서 스냅샷으로 읽고, 조건 평가는 origin 청크의 소유 region 에서 한다
     * (위치 조건 biome·block 이 그 region 의 청크만 읽는다). 엔티티와 origin 이 다른 region 이면 엔티티 값은 스냅샷 시점 것이다.
     * origin 청크에 주인이 없으면 region 을 만들지 않고 전역에서 평가한다 — 위치 조건은 읽을 곳이 없어 거짓이다.
     */
    private static CompletableFuture<Boolean> predicate(CommandContext<CommandSource> c) throws CommandSyntaxException {
        CommandSource s = c.getSource();
        LootCondition cond = resolveCondition(s, ResourceOrIdArgument.get(c, "predicate"));
        double[] origin = {s.x(), s.y(), s.z()};
        Rider self = s.entity();
        CompletableFuture<LootEntity> snap = self == null ? CompletableFuture.completedFuture(null)
                : EntityOps.onOwner(s, self, r -> snapshot(lootEntity(r)));
        return ExecuteCommands.<LootEntity, CompletableFuture<Boolean>>onGlobal(s, snap, ent -> {
            var params = new LootParams().with(LootParams.ORIGIN, origin);
            if (ent != null) params.with(LootParams.THIS_ENTITY, ent);
            World w = s.level();
            ChunkPos cp = ChunkPos.ofBlock(origin[0], origin[2]);
            CompletableFuture<Boolean> f = w.regionizer().ownerOf(cp.x(), cp.z()) == null ? null
                    : w.submitAtOwnedChunk(cp, r -> s.getServer().commandCondition(w, cond, params));
            return f != null ? f : CompletableFuture.completedFuture(s.getServer().commandCondition(w, cond, params));
        }).thenCompose(f -> f);
    }

    private static final String[] EQUIPMENT_SLOTS = {"mainhand", "offhand", "head", "chest", "legs", "feet", "body"};

    /** 소유 스레드에서 한 번에 읽은 전리품 엔티티 값(typeSpecific 만 살아 있는 보기로 넘긴다). */
    private static LootEntity snapshot(LootEntity live) {
        String type = live.type();
        boolean fire = live.isOnFire(), crouch = live.isCrouching(), sprint = live.isSprinting(), swim = live.isSwimming(), baby = live.isBaby(), ground = live.onGround();
        double[] pos = live.position() == null ? null : live.position().clone();
        float luck = live.luck();
        java.util.Map<String, org.domi.furnace.server.loot.LootStack> eq = new java.util.HashMap<>();
        for (String slot : EQUIPMENT_SLOTS) eq.put(slot, live.equipment(slot));
        boolean flying = live.isFlying();
        // 시선 끝의 엔티티(looking_at)는 레이캐스트라 조건이 쓰일 때만, 소유 스레드에서 떠 둔다
        Object lookedAt = org.domi.furnace.server.loot.EntityPredicate.lookingAtUsed && live.isPlayer() ? live.typeSpecific("looking_at") : null;
        return new LootEntity() {
            public String type() { return type; }
            public boolean isOnFire() { return fire; }
            public boolean isCrouching() { return crouch; }
            public boolean isSprinting() { return sprint; }
            public boolean isSwimming() { return swim; }
            public boolean isBaby() { return baby; }
            public boolean isFlying() { return flying; }
            public boolean onGround() { return ground; }
            public double[] position() { return pos == null ? null : pos.clone(); }
            public float luck() { return luck; }
            public org.domi.furnace.server.loot.LootStack equipment(String slot) { return eq.containsKey(slot) ? eq.get(slot) : live.equipment(slot); }
            public Object typeSpecific(String key) { return key.equals("looking_at") ? lookedAt : live.typeSpecific(key); }
        };
    }

    private static LootCondition resolveCondition(CommandSource s, ResourceOrIdArgument.Ref ref) throws CommandSyntaxException {
        var dp = s.getServer().dataPacks();
        if (dp == null || dp.loot() == null) throw ResourceOrIdArgument.missing("minecraft:predicate", String.valueOf(ref.id()));
        if (ref.inline() != null) {
            try {
                return LootCondition.parse(ref.inline(), dp.loot().registries());
            } catch (RuntimeException e) {
                throw ResourceOrIdArgument.ERROR_FAILED_TO_PARSE.create(String.valueOf(e.getMessage()));
            }
        }
        LootCondition cond = dp.loot().predicate(ref.id());
        if (cond == null) throw ResourceOrIdArgument.missing("minecraft:predicate", ref.id());
        return cond;
    }

    private static LootEntity lootEntity(Rider r) {
        if (r instanceof Player p) return ServerCore.playerLootEntity(p);
        if (r instanceof LivingEntity l) return l.lootEntity();
        String type = r.typeName();
        return () -> type;
    }

    // ---------------------------------------------------------------- data

    /** 바닐라 amt.a(DataAccessor, NbtPath) = path.countMatching(getData()). 대상 해석 오류(엔티티 없음·블록 엔티티 아님·로드 안 됨)는 명령 오류. */
    private static CompletableFuture<OptionalInt> data(CommandContext<CommandSource> c, DataCommands.DataProvider provider) throws CommandSyntaxException {
        NbtPath path = NbtPathArgument.getPath(c, "path");
        return provider.with(c, a -> path.countMatching(a.getData())).thenApply(n -> n > 0 ? OptionalInt.of(n) : OptionalInt.empty());
    }

    // ---------------------------------------------------------------- items

    private static int countItems(Rider r, List<Integer> slots, Predicate<ItemStack> predicate) {
        int total = 0;
        for (int slot : slots) {
            var access = ItemSlots.of(r, slot);
            if (access == null) continue;
            ItemStack st = access.get();
            if (st != null && predicate.test(st)) total += st.count();
        }
        return total;
    }

    private static CompletableFuture<OptionalInt> itemsEntity(CommandContext<CommandSource> c) throws CommandSyntaxException {
        CommandSource s = c.getSource();
        List<Integer> slots = SlotsArgument.getSlots(c, "slots").slots();
        Predicate<ItemStack> predicate = ItemPredicateArgument.getItemPredicate(c, "item_predicate");
        // getEntities: 대상이 없으면 오류
        return EntityOps.awaitAsync(s, EntityArgument.findRiders(c, "entities", false), list -> EntityOps.forEachAsync(s, list, r -> {
            if (r instanceof Player p) {
                synchronized (p.menuLock()) {
                    return countItems(r, slots, predicate);
                }
            }
            return countItems(r, slots, predicate);
        }, results -> results.stream().mapToInt(Integer::intValue).sum())).thenApply(n -> n > 0 ? OptionalInt.of(n) : OptionalInt.empty());
    }

    private static CompletableFuture<OptionalInt> itemsBlock(CommandContext<CommandSource> c) throws CommandSyntaxException {
        Pos p = pos(c, "pos");
        List<Integer> slots = SlotsArgument.getSlots(c, "slots").slots();
        Predicate<ItemStack> predicate = ItemPredicateArgument.getItemPredicate(c, "item_predicate");
        return atLoaded(c.getSource(), p, level -> {
            var container = HopperBlockEntity.containerOf(level, level.getBlockEntity(p));
            if (container == null) throw LootCommand.ItemCommandsExt.ERROR_SOURCE_NOT_A_CONTAINER.create(p.x(), p.y(), p.z());
            int total = 0;
            for (int slot : slots) {
                if (slot < 0 || slot >= container.size()) continue;
                ItemStack st = container.get(slot);
                if (st != null && predicate.test(st)) total += st.count();
            }
            return total;
        }).thenApply(n -> n > 0 ? OptionalInt.of(n) : OptionalInt.empty());
    }

    // ---------------------------------------------------------------- blocks

    private static CompletableFuture<OptionalInt> blocks(CommandContext<CommandSource> c, boolean masked) throws CommandSyntaxException {
        CommandSource s = c.getSource();
        Pos start = pos(c, "start"), end = pos(c, "end"), dest = pos(c, "destination");
        int minX = Math.min(start.x(), end.x()), minY = Math.min(start.y(), end.y()), minZ = Math.min(start.z(), end.z());
        int maxX = Math.max(start.x(), end.x()), maxY = Math.max(start.y(), end.y()), maxZ = Math.max(start.z(), end.z());
        World w = s.level();
        ChunkPos cp = new ChunkPos(minX >> 4, minZ >> 4);
        CompletableFuture<OptionalInt> f = w.regionizer().ownerOf(cp.x(), cp.z()) == null ? null : w.submitAtOwnedChunk(cp, r -> {
            try {
                return compareAreas(r.level(), start, end, dest, minX, minY, minZ, maxX, maxY, maxZ, masked);
            } catch (CommandSyntaxException e) {
                throw new CompletionException(e);
            }
        });
        if (f == null) throw BlockPosArgument.ERROR_NOT_LOADED.create();
        return f;
    }

    private static OptionalInt compareAreas(RegionLevel level, Pos start, Pos end, Pos dest, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                            boolean masked) throws CommandSyntaxException {
        // 바닐라는 인자를 start, end, destination 순으로 getLoadedBlockPos 한 뒤 크기를 본다
        checkLoaded(level, start);
        checkLoaded(level, end);
        checkLoaded(level, dest);
        int dx = dest.x() - minX, dy = dest.y() - minY, dz = dest.z() - minZ;
        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume > MAX_BLOCKS) throw ERROR_AREA_TOO_LARGE.create(MAX_BLOCKS, (int) volume);
        // region 모델: 두 영역이 모두 이 region 에 로드돼 있어야 한다(바닐라는 사이 청크를 불러온다)
        if (!CloneCommand.chunksLoaded(level, minX, minY, minZ, maxX, maxY, maxZ)
                || !CloneCommand.chunksLoaded(level, minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz)) {
            throw BlockPosArgument.ERROR_NOT_LOADED.create();
        }
        int air = B.block(B.defaultState("minecraft:air")), voidAir = B.defaultState("minecraft:void_air");
        int count = 0;
        for (int z = minZ; z <= maxZ; z++) for (int y = minY; y <= maxY; y++) for (int x = minX; x <= maxX; x++) {   // 바닐라 순서: z, y, x
            Pos from = new Pos(x, y, z), to = new Pos(x + dx, y + dy, z + dz);
            int a = stateAt(level, from, voidAir);
            if (masked && B.block(a) == air) continue;
            if (a != stateAt(level, to, voidAir)) return OptionalInt.empty();
            BlockEntity be1 = blockEntityAt(level, from);
            if (be1 != null) {
                BlockEntity be2 = blockEntityAt(level, to);
                if (be2 == null || !be1.type().equals(be2.type())) return OptionalInt.empty();
                // 바닐라 saveWithoutMetadata(컴포넌트 포함) 동등성
                if (!NbtTags.equal(be1.saveWithoutMetadata(), be2.saveWithoutMetadata())) return OptionalInt.empty();
            }
            count++;
        }
        return OptionalInt.of(count);
    }

    private static BlockEntity blockEntityAt(RegionLevel level, Pos p) {
        return level.isOutsideBuildHeight(p.y()) ? null : level.getBlockEntity(p) instanceof BlockEntity be ? be : null;
    }

    /** 월드 높이 밖은 void_air(바닐라 getBlockState). */
    private static int stateAt(RegionLevel level, Pos p, int voidAir) {
        return level.isOutsideBuildHeight(p.y()) ? voidAir : level.getBlockState(p);
    }
}
