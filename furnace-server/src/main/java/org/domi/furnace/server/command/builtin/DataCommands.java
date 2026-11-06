// 바닐라 DataCommands(1.21.1) — /data get|merge|modify|remove 의 entity·block·storage 대상.
// 스레드: 엔티티는 그 엔티티를 가진 region 스레드, 블록 엔티티는 그 칸의 region 스레드, 플레이어·storage 는 명령 스레드에서 읽고 쓴다.
// 대상 해석과 읽기·쓰기는 future 로 이어지고, 모두 바로 끝났으면 결과를 그대로 돌려준다(아니면 1, 오류는 실패 메시지).
// 경로 연산 동안 배열은 NbtArray 꼴(NbtTags.thaw), 저장할 때 되돌린다.
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.entity.BlockEntity;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.PostMessages;
import org.domi.furnace.server.command.EntityOps;
import org.domi.furnace.server.command.NbtText;
import org.domi.furnace.server.command.SharedSuggestions;
import org.domi.furnace.server.command.arguments.BlockPosArgument;
import org.domi.furnace.server.command.arguments.CompoundTagArgument;
import org.domi.furnace.server.command.arguments.EntityArgument;
import org.domi.furnace.server.command.arguments.NbtPathArgument;
import org.domi.furnace.server.command.arguments.NbtPathArgument.NbtPath;
import org.domi.furnace.server.command.arguments.NbtTagArgument;
import org.domi.furnace.server.command.arguments.ResourceLocationArgument;
import org.domi.furnace.server.command.brigadier.ArgumentBuilder;
import org.domi.furnace.server.command.brigadier.Arguments;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.Dynamic2CommandExceptionType;
import org.domi.furnace.server.command.brigadier.DynamicCommandExceptionType;
import org.domi.furnace.server.command.brigadier.LiteralArgumentBuilder;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.PlayerData;
import org.domi.furnace.server.entity.Rider;
import org.domi.furnace.server.item.Snbt;
import org.domi.furnace.server.net.io.nbt.NbtArray;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.io.nbt.NbtTags;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.World;

public final class DataCommands {
    private DataCommands() {}

    private static final SimpleCommandExceptionType ERROR_MERGE_UNCHANGED = new SimpleCommandExceptionType(Text.translatable("commands.data.merge.failed"));
    private static final DynamicCommandExceptionType ERROR_GET_NOT_NUMBER = new DynamicCommandExceptionType(p -> Text.translatable("commands.data.get.invalid", p));
    private static final DynamicCommandExceptionType ERROR_GET_NON_EXISTENT = new DynamicCommandExceptionType(p -> Text.translatable("commands.data.get.unknown", p));
    private static final SimpleCommandExceptionType ERROR_MULTIPLE_TAGS = new SimpleCommandExceptionType(Text.translatable("commands.data.get.multiple"));
    private static final DynamicCommandExceptionType ERROR_EXPECTED_OBJECT = new DynamicCommandExceptionType(t -> Text.translatable("commands.data.modify.expected_object", t));
    private static final DynamicCommandExceptionType ERROR_EXPECTED_VALUE = new DynamicCommandExceptionType(t -> Text.translatable("commands.data.modify.expected_value", t));
    private static final Dynamic2CommandExceptionType ERROR_INVALID_SUBSTRING =
            new Dynamic2CommandExceptionType((a, b) -> Text.translatable("commands.data.modify.invalid_substring", a, b));
    static final SimpleCommandExceptionType ERROR_NO_PLAYERS = new SimpleCommandExceptionType(Text.translatable("commands.data.entity.invalid"));
    static final SimpleCommandExceptionType ERROR_NOT_A_BLOCK_ENTITY = new SimpleCommandExceptionType(Text.translatable("commands.data.block.invalid"));

    // ------------------------------------------------------------------ 접근자(바닐라 DataAccessor)

    /** 주인 스레드에서 쓰는 접근자. getData 는 배열이 풀린(thaw) 새 트리를 준다. */
    public interface DataAccessor {
        NbtCompound getData() throws CommandSyntaxException;

        void setData(NbtCompound tag) throws CommandSyntaxException;

        Text getModifiedSuccess();

        Text getPrintSuccess(Object tag);

        Text getPrintSuccess(NbtPath path, double scale, int value);
    }

    @FunctionalInterface
    public interface Op<T> {
        T apply(DataAccessor a) throws CommandSyntaxException;
    }

    /**
     * 바닐라 DataProvider.access(ctx) 가 돌려주는 접근자에 해당하는 손잡이: 대상은 이미 해석돼 있고, 나중에(execute store 콜백) 주인 스레드에서 op 를 돌린다.
     * 블록은 칸 좌표만 붙들고 쓸 때 블록 엔티티를 다시 찾는다(그새 바뀌었으면 commands.data.block.invalid).
     */
    @FunctionalInterface
    public interface Handle {
        <T> CompletableFuture<T> with(CommandSource source, Op<T> op);
    }

    /** 바닐라 DataCommands.DataProvider: 대상 해석(+ 주인 스레드에서 일하기)과 트리 꾸미기. */
    public interface DataProvider {
        <T> CompletableFuture<T> with(CommandContext<CommandSource> ctx, Op<T> op) throws CommandSyntaxException;

        /** 바닐라 access(ctx): 대상을 지금 해석한다(없는 엔티티·블록 엔티티 아님·로드 안 됨 오류는 여기서). 플레이어 쓰기 거부는 setData 때. */
        CompletableFuture<Handle> resolve(CommandContext<CommandSource> ctx) throws CommandSyntaxException;

        ArgumentBuilder<CommandSource, ?> wrap(ArgumentBuilder<CommandSource, ?> parent,
                                               Function<ArgumentBuilder<CommandSource, ?>, ArgumentBuilder<CommandSource, ?>> fn);
    }

    private static <T> CompletableFuture<T> runNow(DataAccessor a, Op<T> op) {
        try {
            return CompletableFuture.completedFuture(op.apply(a));
        } catch (Throwable t) {
            return CompletableFuture.failedFuture(t);
        }
    }

    // ---- entity

    static DataProvider entityProvider(String name) {
        return new DataProvider() {
            public <T> CompletableFuture<T> with(CommandContext<CommandSource> ctx, Op<T> op) throws CommandSyntaxException {
                CommandSource source = ctx.getSource();
                return EntityArgument.findRiders(ctx, name, true).thenCompose(list -> onEntity(source, list.get(0), op));
            }

            public CompletableFuture<Handle> resolve(CommandContext<CommandSource> ctx) throws CommandSyntaxException {
                // 바닐라 EntityDataAccessor 는 해석 시점의 엔티티를 붙든다(순수 변환이라 완성 스레드와 무관)
                return EntityArgument.findRiders(ctx, name, true).thenApply(list -> {
                    Rider r = list.get(0);
                    return new Handle() {
                        public <T> CompletableFuture<T> with(CommandSource source, Op<T> op) {
                            // 바닐라는 붙든 엔티티가 그새 죽었어도(execute store … entity @s … run kill @s) 조용히 load 한다 → 주인을 못 찾으면 쓰기를 건너뛰고 조용히 끝낸다
                            return onEntity(source, r, op).exceptionally(t -> {
                                if (EntityOps.unwrap(t) instanceof IllegalStateException) return null;
                                throw t instanceof CompletionException ce ? ce : new CompletionException(t);
                            });
                        }
                    };
                });
            }

            public ArgumentBuilder<CommandSource, ?> wrap(ArgumentBuilder<CommandSource, ?> parent,
                                                          Function<ArgumentBuilder<CommandSource, ?>, ArgumentBuilder<CommandSource, ?>> fn) {
                return parent.then(literal("entity").then(fn.apply(argument(name, EntityArgument.entity()))));
            }
        };
    }

    private static <T> CompletableFuture<T> onEntity(CommandSource source, Rider r, Op<T> op) {
        if (r instanceof Player p) return runNow(new EntityAccessor(r), op);
        CompletableFuture<T> f = new CompletableFuture<>();
        EntityOps.runOnOwner(source, (Entity) r, e -> {
            try {
                f.complete(op.apply(new EntityAccessor(e)));
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        }, () -> f.completeExceptionally(new IllegalStateException("엔티티를 가진 region 을 찾지 못했다: " + r.typeName())));
        return f;
    }

    // ---- 플러그인 API(Entity.readNbt/editNbt, World.blockEntityNbt/editBlockEntityNbt)가 /data 와 같은 경로를 쓰도록 — 모두 주인 스레드에서

    /** /data get entity 의 내용(배열은 NbtArray 로 풀린 새 트리). */
    public static NbtCompound entityData(Rider entity) { return new EntityAccessor(entity).getData(); }

    /** /data 쓰기: 바닐라 Entity.load + UUID 유지. 플레이어는 거부(바닐라 commands.data.entity.invalid) — UnsupportedOperationException. */
    public static void setEntityData(Rider entity, NbtCompound tag) {
        if (entity instanceof Player) throw new UnsupportedOperationException("플레이어 NBT 는 쓸 수 없다(바닐라 /data 와 같다)");
        try {
            new EntityAccessor(entity).setData(tag);
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /** /data get block 의 내용. */
    public static NbtCompound blockEntityData(org.domi.furnace.server.world.RegionLevel level, BlockEntity be, Pos pos) {
        return new BlockAccessor(level, be, pos).getData();
    }

    /** /data 쓰기: loadWithComponents → setChanged → sendBlockUpdated. */
    public static void setBlockEntityData(org.domi.furnace.server.world.RegionLevel level, BlockEntity be, Pos pos, NbtCompound tag) {
        new BlockAccessor(level, be, pos).setData(tag);
    }

    /** 바닐라 EntityDataAccessor. 플레이어는 읽기만(쓰면 commands.data.entity.invalid). */
    record EntityAccessor(Rider entity) implements DataAccessor {
        public NbtCompound getData() {
            NbtCompound tag;
            if (entity instanceof Player p) {
                synchronized (p.menuLock()) { tag = PlayerData.snapshotNbt(p); }   // 인벤토리·커서를 바꾸는 쪽과 같은 잠금
            } else {
                tag = ((Entity) entity).saveWithoutId(new NbtCompound());
            }
            return (NbtCompound) NbtTags.thaw(NbtTags.copy(tag));   // thaw 는 제자리라 살아 있는 상태(어깨 몹 등)를 망가뜨리지 않게 복사본에
        }

        public void setData(NbtCompound tag) throws CommandSyntaxException {
            if (entity instanceof Player) throw ERROR_NO_PLAYERS.create();
            Entity e = (Entity) entity;
            UUID id = e.uuid();
            e.load((NbtCompound) NbtTags.freeze(NbtTags.copy(tag)));
            e.setUuid(id);
        }

        public Text getModifiedSuccess() { return Text.translatable("commands.data.entity.modified", entity.displayName()); }

        public Text getPrintSuccess(Object tag) { return Text.translatable("commands.data.entity.query", entity.displayName(), NbtText.pretty(tag)); }

        public Text getPrintSuccess(NbtPath path, double scale, int value) {
            return Text.translatable("commands.data.entity.get", path.asString(), entity.displayName(), String.format(Locale.ROOT, "%.2f", scale), value);
        }
    }

    // ---- block

    static DataProvider blockProvider(String name) {
        return new DataProvider() {
            public <T> CompletableFuture<T> with(CommandContext<CommandSource> ctx, Op<T> op) {
                return atBlockEntity(ctx.getSource().level(), blockPos(ctx), op);
            }

            public CompletableFuture<Handle> resolve(CommandContext<CommandSource> ctx) {
                World level = ctx.getSource().level();
                Pos pos = blockPos(ctx);
                // 바닐라 BlockDataAccessor 는 해석 시점에 블록 엔티티가 있어야 한다. 손잡이는 칸 좌표를 붙들고 쓸 때 다시 찾는다.
                return atBlockEntity(level, pos, a -> new Handle() {
                    public <T> CompletableFuture<T> with(CommandSource source, Op<T> op) { return atBlockEntity(level, pos, op); }
                });
            }

            private Pos blockPos(CommandContext<CommandSource> ctx) {
                int[] p = BlockPosArgument.getBlockPos(ctx, name + "Pos");
                return new Pos(p[0], p[1], p[2]);
            }

            public ArgumentBuilder<CommandSource, ?> wrap(ArgumentBuilder<CommandSource, ?> parent,
                                                          Function<ArgumentBuilder<CommandSource, ?>, ArgumentBuilder<CommandSource, ?>> fn) {
                return parent.then(literal("block").then(fn.apply(argument(name + "Pos", BlockPosArgument.blockPos()))));
            }
        };
    }

    /**
     * 칸 pos 의 블록 엔티티 접근자로 op 를 그 칸의 주인 region 에서 돌린다(바닐라 BlockPosArgument.getLoadedBlockPos + getBlockEntity).
     * 조회성이라 주인 region 이 없으면 region 을 만들지 않고 argument.pos.unloaded 로 끝낸다(설계서 §3 I7).
     */
    static <T> CompletableFuture<T> atBlockEntity(World world, Pos pos, Op<T> op) {
        ChunkPos cp = ChunkPos.ofBlock(pos.x(), pos.z());
        if (world.regionizer().ownerOf(cp.x(), cp.z()) == null) return CompletableFuture.failedFuture(BlockPosArgument.ERROR_NOT_LOADED.create());
        CompletableFuture<T> f = world.submitAtOwnedChunk(cp, region -> {
            try {
                var level = region.level();
                if (level == null || !level.hasChunkAt(pos)) throw BlockPosArgument.ERROR_NOT_LOADED.create();
                if (!level.isInWorldBounds(pos)) throw BlockPosArgument.ERROR_OUT_OF_WORLD.create();
                if (!(level.getBlockEntity(pos) instanceof BlockEntity be)) throw ERROR_NOT_A_BLOCK_ENTITY.create();
                return op.apply(new BlockAccessor(level, be, pos));
            } catch (CommandSyntaxException e) {
                throw new CompletionException(e);
            }
        });
        return f == null ? CompletableFuture.failedFuture(BlockPosArgument.ERROR_NOT_LOADED.create()) : f;
    }

    /**
     * 바닐라 handleBlockEntityTagQuery 의 읽기: 블록 엔티티의 saveWithoutMetadata. 로드되지 않았거나 블록 엔티티가 아니면 null.
     * 그 칸의 주인 region 에서 돌고 future 로 돌려준다(오류는 null).
     */
    public static CompletableFuture<NbtCompound> blockEntityTag(World world, Pos pos) {
        return atBlockEntity(world, pos, a -> {
            BlockAccessor b = (BlockAccessor) a;
            return (NbtCompound) NbtTags.thaw(NbtTags.copy(b.entity().saveWithoutMetadata()));
        }).exceptionally(t -> null);
    }

    /** 바닐라 BlockDataAccessor. 쓰면 loadWithComponents → setChanged → sendBlockUpdated. */
    record BlockAccessor(org.domi.furnace.server.world.RegionLevel level, BlockEntity entity, Pos pos) implements DataAccessor {
        public NbtCompound getData() { return (NbtCompound) NbtTags.thaw(NbtTags.copy(entity.saveWithFullMetadata())); }

        public void setData(NbtCompound tag) {
            NbtCompound t = (NbtCompound) NbtTags.freeze(NbtTags.copy(tag));
            entity.load(t);
            entity.loadComponents(t);   // loadWithComponents
            entity.setChanged(level);
            level.sendBlockUpdated(pos);
        }

        public Text getModifiedSuccess() { return Text.translatable("commands.data.block.modified", pos.x(), pos.y(), pos.z()); }

        public Text getPrintSuccess(Object tag) { return Text.translatable("commands.data.block.query", pos.x(), pos.y(), pos.z(), NbtText.pretty(tag)); }

        public Text getPrintSuccess(NbtPath path, double scale, int value) {
            return Text.translatable("commands.data.block.get", path.asString(), pos.x(), pos.y(), pos.z(), String.format(Locale.ROOT, "%.2f", scale), value);
        }
    }

    // ---- storage

    static DataProvider storageProvider(String name) {
        return new DataProvider() {
            public <T> CompletableFuture<T> with(CommandContext<CommandSource> ctx, Op<T> op) {
                var storage = ctx.getSource().getServer().world().commandStorage();
                // 읽기-수정-쓰기 전체를 저장소 잠금 하나로 묶는다(여러 region 스레드의 명령이 같은 id 를 동시에 바꿔도 갱신이 사라지지 않게)
                synchronized (storage) {
                    return runNow(new StorageAccessor(storage, ResourceLocationArgument.getId(ctx, name).toString()), op);
                }
            }

            public CompletableFuture<Handle> resolve(CommandContext<CommandSource> ctx) {
                var storage = ctx.getSource().getServer().world().commandStorage();
                String id = ResourceLocationArgument.getId(ctx, name).toString();
                return CompletableFuture.completedFuture(new Handle() {
                    public <T> CompletableFuture<T> with(CommandSource source, Op<T> op) {
                        synchronized (storage) {
                            return runNow(new StorageAccessor(storage, id), op);
                        }
                    }
                });
            }

            public ArgumentBuilder<CommandSource, ?> wrap(ArgumentBuilder<CommandSource, ?> parent,
                                                          Function<ArgumentBuilder<CommandSource, ?>, ArgumentBuilder<CommandSource, ?>> fn) {
                return parent.then(literal("storage").then(fn.apply(argument(name, ResourceLocationArgument.id())
                        .suggests((c, b) -> SharedSuggestions.suggest(c.getSource().getServer().world().commandStorage().keys(), b)))));
            }
        };
    }

    /** 바닐라 StorageDataAccessor. */
    record StorageAccessor(org.domi.furnace.server.world.CommandStorage storage, String id) implements DataAccessor {
        public NbtCompound getData() { return (NbtCompound) NbtTags.thaw(NbtTags.copy(storage.get(id))); }

        public void setData(NbtCompound tag) { storage.set(id, (NbtCompound) NbtTags.freeze(NbtTags.copy(tag))); }

        public Text getModifiedSuccess() { return Text.translatable("commands.data.storage.modified", id); }

        public Text getPrintSuccess(Object tag) { return Text.translatable("commands.data.storage.query", id, NbtText.pretty(tag)); }

        public Text getPrintSuccess(NbtPath path, double scale, int value) {
            return Text.translatable("commands.data.storage.get", path.asString(), id, String.format(Locale.ROOT, "%.2f", scale), value);
        }
    }

    public static final List<Function<String, DataProvider>> ALL_PROVIDERS = List.of(DataCommands::entityProvider, DataCommands::blockProvider, DataCommands::storageProvider);
    public static final List<DataProvider> TARGET_PROVIDERS = ALL_PROVIDERS.stream().map(f -> f.apply("target")).toList();
    public static final List<DataProvider> SOURCE_PROVIDERS = ALL_PROVIDERS.stream().map(f -> f.apply("source")).toList();

    // ------------------------------------------------------------------ 트리

    @FunctionalInterface
    interface DataManipulator {
        int modify(CommandContext<CommandSource> ctx, NbtCompound root, NbtPath path, List<Object> source) throws CommandSyntaxException;
    }

    @FunctionalInterface
    interface DataManipulatorDecorator {
        ArgumentBuilder<CommandSource, ?> create(DataManipulator m);
    }

    @FunctionalInterface
    interface StringProcessor {
        String process(String s) throws CommandSyntaxException;
    }

    /** 원천 값(같은 명령의 다른 대상에서 읽은 것)을 future 로 받는 단계. */
    @FunctionalInterface
    interface SourceGetter {
        CompletableFuture<List<Object>> get(CommandContext<CommandSource> ctx) throws CommandSyntaxException;
    }

    public static void register(CommandDispatcher<CommandSource> d) {
        LiteralArgumentBuilder<CommandSource> root = literal("data").requires(s -> s.hasPermission(2));
        for (DataProvider target : TARGET_PROVIDERS) {
            root.then(target.wrap(literal("merge"), b -> b.then(argument("nbt", CompoundTagArgument.compoundTag())
                            .executes(c -> mergeData(c, target, CompoundTagArgument.getCompoundTag(c, "nbt"))))))
                    .then(target.wrap(literal("get"), b -> b.executes(c -> getData(c, target))
                            .then(argument("path", NbtPathArgument.nbtPath()).executes(c -> getData(c, target, NbtPathArgument.getPath(c, "path")))
                                    .then(argument("scale", Arguments.doubleArg(-Double.MAX_VALUE, Double.MAX_VALUE)).executes(c -> getNumeric(c, target, NbtPathArgument.getPath(c, "path"),
                                            c.getArgument("scale", Double.class)))))))
                    .then(target.wrap(literal("remove"), b -> b.then(argument("path", NbtPathArgument.nbtPath())
                            .executes(c -> removeData(c, target, NbtPathArgument.getPath(c, "path"))))))
                    .then(decorateModification((parent, dec) -> parent
                            .then(literal("insert").then(argument("index", Arguments.integer())
                                    .then(dec.create((c, r, path, src) -> path.insert(c.getArgument("index", Integer.class), r, src)))))
                            .then(literal("prepend").then(dec.create((c, r, path, src) -> path.insert(0, r, src))))
                            .then(literal("append").then(dec.create((c, r, path, src) -> path.insert(-1, r, src))))
                            .then(literal("set").then(dec.create((c, r, path, src) -> path.set(r, src.get(src.size() - 1)))))
                            .then(literal("merge").then(dec.create((c, r, path, src) -> {
                                NbtCompound merged = new NbtCompound();
                                for (Object t : src) {
                                    if (NbtPath.isTooDeep(t, 0)) throw NbtPathArgument.ERROR_DATA_TOO_DEEP.create();
                                    if (!(t instanceof NbtCompound tc)) throw ERROR_EXPECTED_OBJECT.create(snbt(t));
                                    NbtTags.merge(merged, tc);
                                }
                                Collection<Object> targets = path.getOrCreate(r, NbtCompound::new);
                                int n = 0;
                                for (Object t : targets) {
                                    if (!(t instanceof NbtCompound tc)) throw ERROR_EXPECTED_OBJECT.create(snbt(t));
                                    NbtCompound before = tc.copy();
                                    NbtTags.merge(tc, merged);
                                    n += NbtTags.equal(before, tc) ? 0 : 1;
                                }
                                return n;
                            })))));
        }
        d.register(root);
    }

    private static String snbt(Object tag) { return Snbt.write(NbtTags.freeze(NbtTags.copy(tag))); }

    /** DataCommands.getAsText: 값 태그(수·문자열)의 getAsString. */
    private static String getAsText(Object tag) throws CommandSyntaxException {
        if (tag instanceof String s) return s;
        if (tag instanceof Number) return Snbt.write(tag);
        throw ERROR_EXPECTED_VALUE.create(snbt(tag));
    }

    private static List<Object> stringifyTagList(List<Object> source, StringProcessor proc) throws CommandSyntaxException {
        List<Object> out = new ArrayList<>(source.size());
        for (Object t : source) out.add(proc.process(getAsText(t)));
        return out;
    }

    private static ArgumentBuilder<CommandSource, ?> decorateModification(BiConsumer<ArgumentBuilder<CommandSource, ?>, DataManipulatorDecorator> nodes) {
        LiteralArgumentBuilder<CommandSource> modify = literal("modify");
        for (DataProvider target : TARGET_PROVIDERS) {
            target.wrap(modify, b -> {
                ArgumentBuilder<CommandSource, ?> targetPath = argument("targetPath", NbtPathArgument.nbtPath());
                for (DataProvider source : SOURCE_PROVIDERS) {
                    nodes.accept(targetPath, m -> source.wrap(literal("from"), s -> s
                            .executes(c -> manipulate(c, target, m, wholeData(source)))
                            .then(argument("sourcePath", NbtPathArgument.nbtPath()).executes(c -> manipulate(c, target, m, pathData(source))))));
                    nodes.accept(targetPath, m -> source.wrap(literal("string"), s -> s
                            .executes(c -> manipulate(c, target, m, strings(wholeData(source), v -> v)))
                            .then(argument("sourcePath", NbtPathArgument.nbtPath())
                                    .executes(c -> manipulate(c, target, m, strings(pathData(source), v -> v)))
                                    .then(argument("start", Arguments.integer())
                                            .executes(c -> manipulate(c, target, m, strings(pathData(source),
                                                    v -> substring(v, c.getArgument("start", Integer.class)))))
                                            .then(argument("end", Arguments.integer())
                                                    .executes(c -> manipulate(c, target, m, strings(pathData(source),
                                                            v -> substring(v, c.getArgument("start", Integer.class), c.getArgument("end", Integer.class))))))))));
                }
                nodes.accept(targetPath, m -> literal("value").then(argument("value", NbtTagArgument.nbtTag()).executes(c -> {
                    Object v = NbtTags.thaw(NbtTags.copy(NbtTagArgument.getNbtTag(c, "value")));
                    return manipulate(c, target, m, ctx -> CompletableFuture.completedFuture(Collections.singletonList(v)));
                })));
                return b.then(targetPath);
            });
        }
        return modify;
    }

    private static String validatedSubstring(String s, int start, int end) throws CommandSyntaxException {
        if (start >= 0 && end <= s.length() && start <= end) return s.substring(start, end);
        throw ERROR_INVALID_SUBSTRING.create(start, end);
    }

    private static String substring(String s, int start, int end) throws CommandSyntaxException {
        int len = s.length();
        return validatedSubstring(s, getOffset(start, len), getOffset(end, len));
    }

    private static String substring(String s, int start) throws CommandSyntaxException {
        int len = s.length();
        return validatedSubstring(s, getOffset(start, len), len);
    }

    private static int getOffset(int i, int len) { return i >= 0 ? i : len + i; }

    private static SourceGetter wholeData(DataProvider source) {
        return c -> source.with(c, a -> Collections.singletonList(a.getData()));
    }

    private static SourceGetter pathData(DataProvider source) {
        return c -> {
            NbtPath path = NbtPathArgument.getPath(c, "sourcePath");
            return source.with(c, a -> path.get(a.getData()));
        };
    }

    private static SourceGetter strings(SourceGetter in, StringProcessor proc) {
        return c -> in.get(c).thenApply(l -> {
            try {
                return stringifyTagList(l, proc);
            } catch (CommandSyntaxException e) {
                throw new CompletionException(e);
            }
        });
    }

    private static int manipulate(CommandContext<CommandSource> c, DataProvider target, DataManipulator m, SourceGetter source) throws CommandSyntaxException {
        NbtPath path = NbtPathArgument.getPath(c, "targetPath");
        CommandSource src = c.getSource();
        PostMessages post = new PostMessages();
        CompletableFuture<Integer> f = source.get(c).thenCompose(values -> {
            try {
                return target.with(c, a -> {
                    NbtCompound data = a.getData();
                    int n = m.modify(c, data, path, values);
                    if (n == 0) throw ERROR_MERGE_UNCHANGED.create();
                    a.setData(data);
                    post.success(src, a::getModifiedSuccess, true);
                    return n;
                });
            } catch (CommandSyntaxException e) {
                throw new CompletionException(e);
            }
        });
        return post.finish(src, f);
    }

    private static int removeData(CommandContext<CommandSource> c, DataProvider target, NbtPath path) throws CommandSyntaxException {
        CommandSource src = c.getSource();
        PostMessages post = new PostMessages();
        return post.finish(src, target.with(c, a -> {
            NbtCompound data = a.getData();
            int n = path.remove(data);
            if (n == 0) throw ERROR_MERGE_UNCHANGED.create();
            a.setData(data);
            post.success(src, a::getModifiedSuccess, true);
            return n;
        }));
    }

    /** getSingleTag: 하나가 아니면 commands.data.get.multiple. */
    public static Object getSingleTag(NbtPath path, DataAccessor a) throws CommandSyntaxException {
        Iterator<Object> it = path.get(a.getData()).iterator();
        Object t = it.next();
        if (it.hasNext()) throw ERROR_MULTIPLE_TAGS.create();
        return t;
    }

    private static int getData(CommandContext<CommandSource> c, DataProvider target, NbtPath path) throws CommandSyntaxException {
        CommandSource src = c.getSource();
        PostMessages post = new PostMessages();
        return post.finish(src, target.with(c, a -> {
            Object t = getSingleTag(path, a);
            int n;
            if (t instanceof Number num) n = NbtTags.floor(num.doubleValue());
            else if (t instanceof NbtList l) n = l.size();
            else if (t instanceof NbtArray arr) n = arr.size();
            else if (t instanceof NbtCompound tc) n = tc.tags().size();
            else if (t instanceof String s) n = s.length();
            else throw ERROR_GET_NON_EXISTENT.create(path.toString());
            post.success(src, () -> a.getPrintSuccess(t), false);
            return n;
        }));
    }

    private static int getNumeric(CommandContext<CommandSource> c, DataProvider target, NbtPath path, double scale) throws CommandSyntaxException {
        CommandSource src = c.getSource();
        PostMessages post = new PostMessages();
        return post.finish(src, target.with(c, a -> {
            Object t = getSingleTag(path, a);
            if (!(t instanceof Number num)) throw ERROR_GET_NOT_NUMBER.create(path.toString());
            int n = NbtTags.floor(num.doubleValue() * scale);
            post.success(src, () -> a.getPrintSuccess(path, scale, n), false);
            return n;
        }));
    }

    private static int getData(CommandContext<CommandSource> c, DataProvider target) throws CommandSyntaxException {
        CommandSource src = c.getSource();
        PostMessages post = new PostMessages();
        return post.finish(src, target.with(c, a -> {
            NbtCompound data = a.getData();
            post.success(src, () -> a.getPrintSuccess(data), false);
            return 1;
        }));
    }

    private static int mergeData(CommandContext<CommandSource> c, DataProvider target, NbtCompound nbt) throws CommandSyntaxException {
        CommandSource src = c.getSource();
        Object patch = NbtTags.thaw(nbt.copy());
        PostMessages post = new PostMessages();
        return post.finish(src, target.with(c, a -> {
            NbtCompound data = a.getData();
            if (NbtPath.isTooDeep(patch, 0)) throw NbtPathArgument.ERROR_DATA_TOO_DEEP.create();
            NbtCompound merged = NbtTags.merge(data.copy(), (NbtCompound) patch);
            if (NbtTags.equal(data, merged)) throw ERROR_MERGE_UNCHANGED.create();
            a.setData(merged);
            post.success(src, a::getModifiedSuccess, true);
            return 1;
        }));
    }

    /** 껍데기: 끝났으면 결과(오류는 그대로 던짐), 아니면 실행 컨텍스트에 맡기고(defer) 1(무시됨) — 오류 메시지는 컨텍스트가 전역에서 보낸다. */
    static int finish(CommandSource source, CompletableFuture<Integer> f) throws CommandSyntaxException {
        return EntityOps.result(source, finishAsync(source, f));
    }

    /** finish 의 future 판: 완료를 전역 스레드로 되돌린다(이미 끝났으면 그대로). */
    static CompletableFuture<Integer> finishAsync(CommandSource source, CompletableFuture<Integer> f) {
        return EntityOps.awaitAsync(source, f, CompletableFuture::completedFuture);
    }
}
