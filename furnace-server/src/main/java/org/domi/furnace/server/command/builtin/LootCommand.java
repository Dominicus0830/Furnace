// 바닐라 LootCommand(1.21.1) — /loot <give|insert|replace|spawn> <fish|loot|kill|mine>.
// 원천: loot(CHEST 문맥: origin·this_entity), fish(FISHING: origin·tool·this_entity), kill(ENTITY: 대상의 표, magic 피해, 실행자가 공격자),
//       mine(BLOCK: 그 칸 상태의 표, tool).
// 대상: give(플레이어 인벤토리), insert(용기에 나눠 넣기), replace block|entity(칸 덮어쓰기), spawn(아이템 엔티티).
// 스레드: kill 은 대상 엔티티 주인, mine·insert·replace block·spawn 은 그 칸 region, 플레이어는 menuLock, 생물 칸은 그 엔티티 주인.
// 차이: 실행 주체 엔티티는 플레이어뿐이라 mine 의 this_entity·block_entity 문맥은 비어 있다. 콘솔에서 mainhand/offhand 는 바닐라처럼 실패(command.failed).
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.block.BlockBehaviors;
import org.domi.furnace.server.block.BlockInfo;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.entity.HopperBlockEntity;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.EntityOps;
import org.domi.furnace.server.command.ItemSlots;
import org.domi.furnace.server.command.SharedSuggestions;
import org.domi.furnace.server.command.arguments.BlockPosArgument;
import org.domi.furnace.server.command.arguments.EntityArgument;
import org.domi.furnace.server.command.arguments.ItemArgument;
import org.domi.furnace.server.command.arguments.ResourceOrIdArgument;
import org.domi.furnace.server.command.arguments.SlotArgument;
import org.domi.furnace.server.command.arguments.Vec3Argument;
import org.domi.furnace.server.command.brigadier.ArgumentBuilder;
import org.domi.furnace.server.command.brigadier.Arguments;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.DynamicCommandExceptionType;
import org.domi.furnace.server.command.brigadier.LiteralArgumentBuilder;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.ItemEntity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.LivingEntity;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Rider;
import org.domi.furnace.server.loot.LootDamageSource;
import org.domi.furnace.server.loot.LootEntity;
import org.domi.furnace.server.loot.LootParams;
import org.domi.furnace.server.loot.LootTable;
import org.domi.furnace.server.menu.Stacks;
import org.domi.furnace.server.world.RegionLevel;

public final class LootCommand {
    private LootCommand() {}

    private static final DynamicCommandExceptionType ERROR_NO_HELD_ITEMS = new DynamicCommandExceptionType(n -> Text.translatable("commands.drop.no_held_items", n));
    private static final DynamicCommandExceptionType ERROR_NO_LOOT_TABLE = new DynamicCommandExceptionType(n -> Text.translatable("commands.drop.no_loot_table", n));

    /** 결과 알림(바닐라 Callback). */
    @FunctionalInterface
    interface Callback {
        void accept(List<ItemStack> items) throws CommandSyntaxException;
    }

    /** 대상(바닐라 DropConsumer): 아이템을 넣고 넣은 수를 future 로. */
    @FunctionalInterface
    interface DropConsumer {
        CompletableFuture<Integer> accept(CommandContext<CommandSource> c, List<ItemStack> items, Callback cb) throws CommandSyntaxException;
    }

    /** 원천 쪽 트리를 대상마다 붙인다(바닐라 TailProvider). */
    @FunctionalInterface
    interface TailProvider {
        ArgumentBuilder<CommandSource, ?> construct(ArgumentBuilder<CommandSource, ?> node, DropConsumer target);
    }

    private static CompletableFuture<Integer> run(java.util.concurrent.Callable<CompletableFuture<Integer>> f) {
        try {
            return f.call();
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static CompletionException wrap(Throwable t) { return t instanceof CompletionException ce ? ce : new CompletionException(t); }

    // ------------------------------------------------------------------ 트리

    public static void register(CommandDispatcher<CommandSource> d) {
        d.register(addTargets(literal("loot").requires(s -> s.hasPermission(2)), (node, target) -> node
                .then(literal("fish").then(argument("loot_table", ResourceOrIdArgument.lootTable()).suggests(LootCommand::suggestTables)
                        .then(argument("pos", BlockPosArgument.blockPos())
                                .executes(c -> dropFishingLoot(c, ResourceOrIdArgument.get(c, "loot_table"), pos(c, "pos"), ItemStack.EMPTY, target))
                                .then(argument("tool", ItemArgument.item())
                                        .executes(c -> dropFishingLoot(c, ResourceOrIdArgument.get(c, "loot_table"), pos(c, "pos"),
                                                ItemArgument.getItem(c, "tool").createItemStack(1, false), target)))
                                .then(literal("mainhand").executes(c -> dropFishingLoot(c, ResourceOrIdArgument.get(c, "loot_table"), pos(c, "pos"),
                                        sourceHandItem(c.getSource(), true), target)))
                                .then(literal("offhand").executes(c -> dropFishingLoot(c, ResourceOrIdArgument.get(c, "loot_table"), pos(c, "pos"),
                                        sourceHandItem(c.getSource(), false), target))))))
                .then(literal("loot").then(argument("loot_table", ResourceOrIdArgument.lootTable()).suggests(LootCommand::suggestTables)
                        .executes(c -> dropChestLoot(c, ResourceOrIdArgument.get(c, "loot_table"), target))))
                .then(literal("kill").then(argument("target", EntityArgument.entity()).executes(c -> dropKillLoot(c, target))))
                .then(literal("mine").then(argument("pos", BlockPosArgument.blockPos())
                        .executes(c -> dropBlockLoot(c, pos(c, "pos"), ItemStack.EMPTY, target))
                        .then(argument("tool", ItemArgument.item())
                                .executes(c -> dropBlockLoot(c, pos(c, "pos"), ItemArgument.getItem(c, "tool").createItemStack(1, false), target)))
                        .then(literal("mainhand").executes(c -> dropBlockLoot(c, pos(c, "pos"), sourceHandItem(c.getSource(), true), target)))
                        .then(literal("offhand").executes(c -> dropBlockLoot(c, pos(c, "pos"), sourceHandItem(c.getSource(), false), target)))))));
    }

    private static Pos pos(CommandContext<CommandSource> c, String name) {
        int[] p = BlockPosArgument.getBlockPos(c, name);
        return new Pos(p[0], p[1], p[2]);
    }

    private static CompletableFuture<org.domi.furnace.server.command.brigadier.Suggestions> suggestTables(
            CommandContext<CommandSource> c, org.domi.furnace.server.command.brigadier.SuggestionsBuilder b) {
        var dp = c.getSource().getServer().dataPacks();
        List<String> ids = dp == null || dp.loot() == null ? List.of() : new ArrayList<>(dp.loot().tables().keySet());
        return SharedSuggestions.suggest(ids, b);
    }

    private static LiteralArgumentBuilder<CommandSource> addTargets(LiteralArgumentBuilder<CommandSource> root, TailProvider tail) {
        return root.then(literal("replace")
                        .then(literal("entity").then(argument("entities", EntityArgument.entities())
                                .then(tail.construct(argument("slot", SlotArgument.slot()),
                                                (c, items, cb) -> entityReplace(c, SlotArgument.getSlot(c, "slot"), items.size(), items, cb))
                                        .then(tail.construct(argument("count", Arguments.integer(0)),
                                                (c, items, cb) -> entityReplace(c, SlotArgument.getSlot(c, "slot"), c.getArgument("count", Integer.class), items, cb))))))
                        .then(literal("block").then(argument("targetPos", BlockPosArgument.blockPos())
                                .then(tail.construct(argument("slot", SlotArgument.slot()),
                                                (c, items, cb) -> blockReplace(c, pos(c, "targetPos"), SlotArgument.getSlot(c, "slot"), items.size(), items, cb))
                                        .then(tail.construct(argument("count", Arguments.integer(0)),
                                                (c, items, cb) -> blockReplace(c, pos(c, "targetPos"), SlotArgument.getSlot(c, "slot"),
                                                        c.getArgument("count", Integer.class), items, cb)))))))
                .then(literal("insert").then(tail.construct(argument("targetPos", BlockPosArgument.blockPos()),
                        (c, items, cb) -> blockDistribute(c, pos(c, "targetPos"), items, cb))))
                .then(literal("give").then(tail.construct(argument("players", EntityArgument.players()),
                        (c, items, cb) -> playerGive(EntityArgument.getPlayers(c, "players"), items, cb))))
                .then(literal("spawn").then(tail.construct(argument("targetPos", Vec3Argument.vec3()),
                        (c, items, cb) -> dropInWorld(c, Vec3Argument.getVec3(c, "targetPos"), items, cb))));
    }

    // ------------------------------------------------------------------ 대상

    /** getLoadedBlockPos 검사 뒤 그 칸 region 에서 일한다. */
    interface RegionWork {
        int run(RegionLevel level) throws CommandSyntaxException;
    }

    private static CompletableFuture<Integer> atPos(CommandSource source, Pos pos, RegionWork work) {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        source.level().runAt(pos.x(), pos.z(), region -> {
            try {
                var level = region.level();
                checkLoaded(level, pos);
                f.complete(work.run(level));
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        return f;
    }

    private static void checkLoaded(RegionLevel level, Pos pos) throws CommandSyntaxException {
        if (!level.hasChunkAt(pos)) throw BlockPosArgument.ERROR_NOT_LOADED.create();
        if (!level.isInWorldBounds(pos)) throw BlockPosArgument.ERROR_OUT_OF_WORLD.create();
    }

    /** getContainer: 용기가 아니면 commands.item.target.not_a_container. */
    static HopperBlockEntity.Inv container(RegionLevel level, Pos pos) throws CommandSyntaxException {
        var c = HopperBlockEntity.containerOf(level, level.getBlockEntity(pos));
        if (c == null) throw ItemCommandsExt.ERROR_TARGET_NOT_A_CONTAINER.create(pos.x(), pos.y(), pos.z());
        return c;
    }

    static CompletableFuture<Integer> blockDistribute(CommandContext<CommandSource> c, Pos pos, List<ItemStack> items, Callback cb) {
        return atPos(c.getSource(), pos, level -> {
            var container = container(level, pos);
            List<ItemStack> added = new ArrayList<>(items.size());
            for (ItemStack s : items) {
                ItemStack shown = distributeToContainer(container, s);
                if (shown != null) {
                    container.setChanged();
                    added.add(shown);
                }
            }
            cb.accept(added);
            return added.size();
        });
    }

    /** distributeToContainer: 넣었으면 메시지에 보일 스택(바닐라는 원본을 줄여 가며 넣어 그 남은 수가 보인다), 못 넣었으면 null. */
    private static ItemStack distributeToContainer(HopperBlockEntity.Inv c, ItemStack stack) {
        boolean changed = false;
        ItemStack rest = stack;
        for (int i = 0; i < c.size() && !rest.isEmpty(); i++) {
            ItemStack cur = c.get(i);
            if (c.canPlaceItem(i, rest)) {
                if (cur.isEmpty()) {
                    c.setItem(i, rest);
                    rest = c.get(i);   // limitSize 뒤의 같은 스택
                    changed = true;
                    break;
                }
                int max = ItemArgument.maxStackSize(rest);
                if (cur.count() <= ItemArgument.maxStackSize(cur) && Stacks.isSameItemSameComponents(cur, rest)) {
                    int space = max - cur.count();
                    int n = Math.min(rest.count(), space);
                    rest = rest.withCount(rest.count() - n);
                    c.put(i, cur.withCount(cur.count() + n));
                    changed = true;
                }
            }
        }
        return changed ? rest : null;
    }

    private static CompletableFuture<Integer> blockReplace(CommandContext<CommandSource> c, Pos pos, int slot, int count, List<ItemStack> items, Callback cb) {
        return atPos(c.getSource(), pos, level -> {
            var container = container(level, pos);
            if (slot < 0 || slot >= container.size()) throw ItemCommandsExt.ERROR_TARGET_INAPPLICABLE_SLOT.create(slot);
            List<ItemStack> added = new ArrayList<>(items.size());
            for (int j = 0; j < count; j++) {
                int idx = slot + j;
                ItemStack s = j < items.size() ? items.get(j) : ItemStack.EMPTY;
                if (container.canPlaceItem(idx, s)) {
                    container.setItem(idx, s);
                    added.add(s);
                }
            }
            cb.accept(added);
            return added.size();
        });
    }

    static CompletableFuture<Integer> playerGive(Collection<Player> players, List<ItemStack> items, Callback cb) throws CommandSyntaxException {
        List<ItemStack> added = new ArrayList<>(items.size());
        for (ItemStack s : items) {
            for (Player p : players) {
                synchronized (p.menuLock()) {
                    int max = ItemArgument.maxStackSize(s);
                    boolean damaged = org.domi.furnace.server.item.ItemTools.isDamageable(s) && org.domi.furnace.server.item.ItemTools.damage(s) > 0;
                    ItemStack rest = p.inventory().add(s, p.vitals().isCreative(), max, damaged);
                    if (rest.count() < s.count() || rest.isEmpty()) added.add(s);
                    ItemSlots.afterPlayerChange(p);
                }
            }
        }
        cb.accept(added);
        return CompletableFuture.completedFuture(added.size());
    }

    /** setSlots: 한 엔티티의 slot..slot+count 칸. */
    private static List<ItemStack> setSlots(Rider r, List<ItemStack> items, int slot, int count) {
        List<ItemStack> added = new ArrayList<>();
        for (int j = 0; j < count; j++) {
            ItemStack s = j < items.size() ? items.get(j) : ItemStack.EMPTY;
            var access = ItemSlots.of(r, slot + j);
            if (access != null && access.set(s)) added.add(s);
        }
        return added;
    }

    private static CompletableFuture<Integer> entityReplace(CommandContext<CommandSource> c, int slot, int count, List<ItemStack> items, Callback cb)
            throws CommandSyntaxException {
        CommandSource source = c.getSource();
        return EntityArgument.findRiders(c, "entities", false).thenCompose(targets -> {
            List<CompletableFuture<List<ItemStack>>> fs = new ArrayList<>();
            for (Rider r : targets) {
                if (r instanceof Player p) {
                    synchronized (p.menuLock()) {
                        fs.add(CompletableFuture.completedFuture(setSlots(p, items, slot, count)));
                        ItemSlots.afterPlayerChange(p);
                    }
                } else {
                    fs.add(EntityOps.onOwner(source, r, e -> setSlots(e, items, slot, count)));
                }
            }
            return CompletableFuture.allOf(fs.toArray(new CompletableFuture[0])).thenApply(v -> {
                List<ItemStack> added = new ArrayList<>();
                for (var f : fs) added.addAll(f.join());
                try {
                    cb.accept(added);
                } catch (CommandSyntaxException e) {
                    throw wrap(e);
                }
                return added.size();
            });
        });
    }

    private static CompletableFuture<Integer> dropInWorld(CommandContext<CommandSource> c, double[] pos, List<ItemStack> items, Callback cb) {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        c.getSource().level().runAt(pos[0], pos[2], region -> {
            try {
                var level = region.level();
                for (ItemStack s : items) {
                    ItemEntity e = new ItemEntity(level, level.nextEntityId(), pos[0], pos[1], pos[2], s);
                    e.setDefaultPickUpDelay();
                    level.addFreshEntity(e);
                }
                cb.accept(items);
                f.complete(items.size());
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        return f;
    }

    // ------------------------------------------------------------------ 메시지

    private static void callback(CommandSource source, List<ItemStack> items) {
        if (items.size() == 1) {
            ItemStack s = items.get(0);
            source.sendSuccess(() -> Text.translatable("commands.drop.success.single", s.count(), ItemCommands.displayName(s)), false);
        } else {
            source.sendSuccess(() -> Text.translatable("commands.drop.success.multiple", items.size()), false);
        }
    }

    private static void callback(CommandSource source, List<ItemStack> items, String table) {
        if (items.size() == 1) {
            ItemStack s = items.get(0);
            source.sendSuccess(() -> Text.translatable("commands.drop.success.single_with_table", s.count(), ItemCommands.displayName(s), table), false);
        } else {
            source.sendSuccess(() -> Text.translatable("commands.drop.success.multiple_with_table", items.size(), table), false);
        }
    }

    // ------------------------------------------------------------------ 원천

    /** getSourceHandItem: 실행 주체의 손 아이템. */
    private static ItemStack sourceHandItem(CommandSource source, boolean mainhand) throws CommandSyntaxException {
        Player p = source.getPlayer();
        // 바닐라: 실행 주체가 생물이 아니면 commands.drop.no_held_items(콘솔이면 엔티티가 없어 바닐라에서도 예외 → command.failed)
        if (p == null) throw new IllegalStateException("실행 주체 엔티티 없음");
        synchronized (p.menuLock()) {
            return p.inventory().get(mainhand ? p.inventory().heldWindowSlot() : 45);
        }
    }

    private static LootEntity sourceLootEntity(CommandSource source) {
        Player p = source.getPlayer();
        return p == null ? null : ServerCore.playerLootEntity(p);
    }

    /** 표 찾기: id 면 데이터 팩의 것(없으면 failed_to_parse), 인라인이면 그 자리에서 읽은 것. */
    static LootTable resolveTable(CommandSource source, ResourceOrIdArgument.Ref ref) throws CommandSyntaxException {
        var dp = source.getServer().dataPacks();
        if (dp == null || dp.loot() == null) throw ResourceOrIdArgument.missing("minecraft:loot_table", String.valueOf(ref.id()));
        if (ref.inline() != null) {
            try {
                return dp.loot().parseTable(ref.inline());
            } catch (RuntimeException e) {
                throw ResourceOrIdArgument.ERROR_FAILED_TO_PARSE.create(String.valueOf(e.getMessage()));
            }
        }
        if (!dp.loot().hasTable(ref.id())) throw ResourceOrIdArgument.missing("minecraft:loot_table", ref.id());
        return dp.loot().table(ref.id());
    }

    private static int finish(CommandSource source, CompletableFuture<Integer> f) throws CommandSyntaxException { return DataCommands.finish(source, f); }

    /** dropChestLoot: CHEST 문맥(this_entity 선택, origin). */
    private static int dropChestLoot(CommandContext<CommandSource> c, ResourceOrIdArgument.Ref ref, DropConsumer target) throws CommandSyntaxException {
        CommandSource source = c.getSource();
        var params = new LootParams().with(LootParams.ORIGIN, new double[] {source.x(), source.y(), source.z()});
        LootEntity self = sourceLootEntity(source);
        if (self != null) params.with(LootParams.THIS_ENTITY, self);
        return drop(c, resolveTable(source, ref), params, target);
    }

    /** dropFishingLoot: FISHING 문맥(origin = 칸 중심, tool, this_entity 선택). 칸은 로드돼 있어야 한다. */
    private static int dropFishingLoot(CommandContext<CommandSource> c, ResourceOrIdArgument.Ref ref, Pos pos, ItemStack tool, DropConsumer target)
            throws CommandSyntaxException {
        CommandSource source = c.getSource();
        LootTable table = resolveTable(source, ref);
        var params = new LootParams().with(LootParams.ORIGIN, new double[] {pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5})
                .with(LootParams.TOOL, ServerCore.toLootStack(tool));
        LootEntity self = sourceLootEntity(source);
        if (self != null) params.with(LootParams.THIS_ENTITY, self);
        CompletableFuture<List<ItemStack>> items = new CompletableFuture<>();
        source.level().runAt(pos.x(), pos.z(), region -> {
            try {
                checkLoaded(region.level(), pos);
                items.complete(source.getServer().commandLoot(table, params));
            } catch (Throwable t) {
                items.completeExceptionally(t);
            }
        });
        return finish(source, items.thenCompose(l -> run(() -> target.accept(c, l, list -> callback(source, list)))));
    }

    private static int drop(CommandContext<CommandSource> c, LootTable table, LootParams params, DropConsumer target) throws CommandSyntaxException {
        CommandSource source = c.getSource();
        List<ItemStack> items = source.getServer().commandLoot(table, params);
        return finish(source, run(() -> target.accept(c, items, list -> callback(source, list))));
    }

    /** dropKillLoot: 대상의 전리품 표를 ENTITY 문맥으로(magic 피해, 실행 주체가 공격자·마지막 공격 플레이어). */
    private static int dropKillLoot(CommandContext<CommandSource> c, DropConsumer target) throws CommandSyntaxException {
        CommandSource source = c.getSource();
        double[] origin = {source.x(), source.y(), source.z()};
        LootEntity attacker = sourceLootEntity(source);
        CompletableFuture<Object[]> f = EntityArgument.findRiders(c, "target", true).thenCompose(list -> {
            Rider r = list.get(0);
            if (r instanceof Player p) return CompletableFuture.completedFuture(evaluateKill(source, "minecraft:entities/player",
                    ServerCore.playerLootEntity(p), origin, attacker));
            if (!(r instanceof LivingEntity)) return CompletableFuture.failedFuture(ERROR_NO_LOOT_TABLE.create(r.displayName()));
            return EntityOps.onOwner(source, r, e -> {
                LivingEntity le = (LivingEntity) e;
                String table = le.lootTable();
                return evaluateKill(source, table == null ? "minecraft:empty" : table, le.lootEntity(), origin, attacker);
            });
        });
        return finish(source, f.thenCompose(res -> run(() -> {
            @SuppressWarnings("unchecked") List<ItemStack> items = (List<ItemStack>) res[0];
            return target.accept(c, items, l -> callback(source, l, (String) res[1]));
        })));
    }

    /** {표 결과, 표 id}. */
    private static Object[] evaluateKill(CommandSource source, String tableId, LootEntity self, double[] origin, LootEntity attacker) {
        var dp = source.getServer().dataPacks();
        LootTable table = dp == null || dp.loot() == null ? LootTable.EMPTY : dp.loot().table(tableId);
        var params = new LootParams().with(LootParams.THIS_ENTITY, self).with(LootParams.ORIGIN, origin)
                .with(LootParams.DAMAGE_SOURCE, new LootDamageSource() {
                    public String type() { return "minecraft:magic"; }

                    public boolean is(String tag) { return false; }   // TODO(피해 종류 태그): magic 의 태그

                    public LootEntity entity() { return null; }

                    public LootEntity directEntity() { return null; }
                });
        if (attacker != null) {
            params.with(LootParams.LAST_DAMAGE_PLAYER, attacker).with(LootParams.ATTACKING_ENTITY, attacker).with(LootParams.DIRECT_ATTACKING_ENTITY, attacker);
        }
        return new Object[] {source.getServer().commandLoot(table, params), tableId};
    }

    /** dropBlockLoot: 그 칸 상태의 getDrops(BLOCK 문맥: origin 칸 중심·block_state·tool). */
    private static int dropBlockLoot(CommandContext<CommandSource> c, Pos pos, ItemStack tool, DropConsumer target) throws CommandSyntaxException {
        CommandSource source = c.getSource();
        CompletableFuture<Object[]> f = new CompletableFuture<>();
        source.level().runAt(pos.x(), pos.z(), region -> {
            try {
                var level = region.level();
                checkLoaded(level, pos);
                int state = level.getBlockState(pos);
                Object be = level.getBlockEntity(pos);
                List<ItemStack> drops = BlockBehaviors.of(state).getDrops(state, level, pos, be, null, tool);
                String table = BlockInfo.get().lootTable(Blocks.get().blockOf(state));
                f.complete(new Object[] {drops, table == null ? "minecraft:empty" : table});
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        return finish(source, f.thenCompose(res -> run(() -> {
            @SuppressWarnings("unchecked") List<ItemStack> items = (List<ItemStack>) res[0];
            return target.accept(c, items, l -> callback(source, l, (String) res[1]));
        })));
    }

    /** /item 과 함께 쓰는 오류(바닐라 ItemCommands 의 것). */
    static final class ItemCommandsExt {
        static final org.domi.furnace.server.command.brigadier.Dynamic3CommandExceptionType ERROR_TARGET_NOT_A_CONTAINER =
                new org.domi.furnace.server.command.brigadier.Dynamic3CommandExceptionType((x, y, z) -> Text.translatable("commands.item.target.not_a_container", x, y, z));
        static final org.domi.furnace.server.command.brigadier.Dynamic3CommandExceptionType ERROR_SOURCE_NOT_A_CONTAINER =
                new org.domi.furnace.server.command.brigadier.Dynamic3CommandExceptionType((x, y, z) -> Text.translatable("commands.item.source.not_a_container", x, y, z));
        static final DynamicCommandExceptionType ERROR_TARGET_INAPPLICABLE_SLOT = new DynamicCommandExceptionType(s -> Text.translatable("commands.item.target.no_such_slot", s));
        static final DynamicCommandExceptionType ERROR_SOURCE_INAPPLICABLE_SLOT = new DynamicCommandExceptionType(s -> Text.translatable("commands.item.source.no_such_slot", s));
        static final DynamicCommandExceptionType ERROR_TARGET_NOT_CHANGED = new DynamicCommandExceptionType(s -> Text.translatable("commands.item.target.no_changes", s));
        static final org.domi.furnace.server.command.brigadier.Dynamic2CommandExceptionType ERROR_TARGET_NOT_CHANGED_KNOWN_ITEM =
                new org.domi.furnace.server.command.brigadier.Dynamic2CommandExceptionType((a, b) -> Text.translatable("commands.item.target.no_changed.known_item", a, b));
    }
}
