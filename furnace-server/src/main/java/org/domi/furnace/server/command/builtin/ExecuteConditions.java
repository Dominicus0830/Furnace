// 바닐라 ExecuteCommand(amt) 중 E2 단계: execute if|unless entity·block·blocks·biome·loaded·dimension·predicate·items.
// 조건 하나는 포크(참이면 소스 유지, 아니면 사라짐)와 종단 실행(성공 1 / 개수 / 실패)의 두 얼굴을 가진다. 결과 의미는 바닐라 그대로:
//  - 불리언 조건: if 는 참이면 1(conditional.pass), 아니면 conditional.fail. unless 는 반대.
//  - 개수 조건(entity·items·blocks): if 는 개수가 있으면 그 개수(pass_count), 없으면 fail. unless 는 없으면 1, 있으면 fail_count(개수).
// region 상태(블록·바이옴·엔티티 칸·청크)는 소유 region 에서 읽고, 판정과 메시지는 전역 스레드에서 한다. 조회성 조건(block·blocks·biome·loaded·items block)은
// 주인 region 이 없으면 region 을 만들지 않고 즉시 "로드 안 됨"(loaded 는 거짓)으로 끝낸다.
// 차이: blocks 는 두 영역과 세 모서리가 시작 영역의 최소 모서리 청크를 가진 region 한 곳에 모두 로드돼 있어야 한다(/clone 과 같은 제약, 아니면 argument.pos.unloaded).
//       loaded 는 바닐라의 "엔티티 틱 청크" 대신 region 에 로드된 청크로 본다. biome 의 지터 이웃 청크가 이 region 밖이거나 안 불러져 있으면 일치 없음으로 본다.
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
import org.domi.furnace.server.command.arguments.ItemPredicateArgument;
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
        return root
                .then(literal("block").then(argument("pos", BlockPosArgument.blockPos())
                        .then(bool(execute, argument("block", BlockPredicateArgument.blockPredicate()), expected, ExecuteConditions::block))))
                .then(literal("biome").then(argument("pos", BlockPosArgument.blockPos())
                        .then(bool(execute, argument("biome", ResourceOrTagKeyArgument.resourceOrTag(BIOME_REGISTRY)), expected, ExecuteConditions::biome))))
                .then(literal("loaded").then(bool(execute, argument("pos", BlockPosArgument.blockPos()), expected, ExecuteConditions::loaded)))
                .then(literal("dimension").then(bool(execute, argument("dimension", DimensionArgument.dimension()), expected,
                        c -> CompletableFuture.completedFuture(DimensionArgument.getDimension(c, "dimension") == c.getSource().level()))))
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
        CompletableFuture<Boolean> f = w.submitAtOwnedChunk(cp, r -> r.level() != null && r.level().hasChunkAt(p));
        return f == null ? CompletableFuture.completedFuture(false) : f;
    }

    private static CompletableFuture<Boolean> biome(CommandContext<CommandSource> c) throws CommandSyntaxException {
        CommandSource s = c.getSource();
        Pos p = pos(c, "pos");
        IntPredicate filter = biomeFilter(s, ResourceOrTagKeyArgument.get(c, "biome"));
        World w = s.level();
        int[] q = BiomeZoom.quart(s.getServer().biomeZoomSeed(), p.x(), p.y(), p.z());
        CompletableFuture<Boolean> loaded = atLoaded(s, p, level -> true);
        // 지터로 고른 quart 의 청크가 다른 region 일 수 있으니 그 주인에게 따로 묻는다(없으면 일치 없음)
        return ExecuteCommands.<Boolean, CompletableFuture<Integer>>onGlobal(s, loaded, ok -> {
            ChunkPos qc = new ChunkPos(q[0] >> 2, q[2] >> 2);
            if (w.regionizer().ownerOf(qc.x(), qc.z()) == null) return CompletableFuture.completedFuture(-1);
            CompletableFuture<Integer> id = w.submitAtOwnedChunk(qc, r -> r.biomeIdAtQuart(q[0], q[1], q[2]));
            return id == null ? CompletableFuture.completedFuture(-1) : id;
        }).thenCompose(f -> f).thenApply(id -> id >= 0 && filter.test(id));
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

    private static CompletableFuture<Boolean> predicate(CommandContext<CommandSource> c) throws CommandSyntaxException {
        CommandSource s = c.getSource();
        LootCondition cond = resolveCondition(s, ResourceOrIdArgument.get(c, "predicate"));
        // 바닐라 COMMAND 문맥: origin = 소스 위치, 소스 엔티티가 있으면 this_entity. 엔티티 필드는 전역에서 읽기만 한다.
        var params = new LootParams().with(LootParams.ORIGIN, new double[] {s.x(), s.y(), s.z()});
        Rider self = s.entity();
        if (self != null) params.with(LootParams.THIS_ENTITY, lootEntity(self));
        return CompletableFuture.completedFuture(s.getServer().commandCondition(s.level(), cond, params));
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
