// 바닐라 ItemCommands(1.21.1) — /item replace|modify (block <pos> | entity <targets>) <slot> ….
// 원천: with <item> [count], from block|entity <source> <sourceSlot> [modifier]. 수정자는 COMMAND 문맥(origin·this_entity)으로 적용.
// 스레드: 블록 용기는 그 칸 region, 생물 칸은 그 엔티티 주인, 플레이어 칸은 menuLock 안에서.
// 차이: 엔티티 칸은 플레이어·생물 장비만(ItemSlots 참고). 블록 용기는 캠프파이어·주크박스·독서대를 빼고 용기 블록 엔티티 전부.
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.entity.HopperBlockEntity;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.PostMessages;
import org.domi.furnace.server.command.EntityOps;
import org.domi.furnace.server.command.ItemSlots;
import org.domi.furnace.server.command.SharedSuggestions;
import org.domi.furnace.server.command.arguments.BlockPosArgument;
import org.domi.furnace.server.command.arguments.EntityArgument;
import org.domi.furnace.server.command.arguments.ItemArgument;
import org.domi.furnace.server.command.arguments.ResourceOrIdArgument;
import org.domi.furnace.server.command.arguments.SlotArgument;
import org.domi.furnace.server.command.brigadier.Arguments;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.SuggestionProvider;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Rider;
import org.domi.furnace.server.loot.LootFunction;
import org.domi.furnace.server.loot.LootParams;
import org.domi.furnace.server.world.RegionLevel;

public final class ItemSlotCommands {
    private ItemSlotCommands() {}

    private static final SuggestionProvider<CommandSource> SUGGEST_MODIFIER = (c, b) -> {
        var dp = c.getSource().getServer().dataPacks();
        return SharedSuggestions.suggest(dp == null || dp.loot() == null ? List.of() : new ArrayList<>(dp.loot().modifierIds()), b);
    };

    /** 원천 아이템을 future 로(블록이면 그 region, 엔티티면 그 주인에서 읽는다). */
    @FunctionalInterface
    interface ItemSource {
        CompletableFuture<ItemStack> get(CommandContext<CommandSource> c) throws CommandSyntaxException;
    }

    public static void register(CommandDispatcher<CommandSource> d) {
        d.register(literal("item").requires(s -> s.hasPermission(2))
                .then(literal("replace")
                        .then(literal("block").then(argument("pos", BlockPosArgument.blockPos()).then(argument("slot", SlotArgument.slot())
                                .then(literal("with").then(argument("item", ItemArgument.item())
                                        .executes(c -> setBlockItem(c, withItem(c, 1, false)))
                                        .then(argument("count", Arguments.integer(1, 99))
                                                .executes(c -> setBlockItem(c, withItem(c, c.getArgument("count", Integer.class), true))))))
                                .then(fromTree(true)))))
                        .then(literal("entity").then(argument("targets", EntityArgument.entities()).then(argument("slot", SlotArgument.slot())
                                .then(literal("with").then(argument("item", ItemArgument.item())
                                        .executes(c -> setEntityItem(c, withItem(c, 1, false)))
                                        .then(argument("count", Arguments.integer(1, 99))
                                                .executes(c -> setEntityItem(c, withItem(c, c.getArgument("count", Integer.class), true))))))
                                .then(fromTree(false))))))
                .then(literal("modify")
                        .then(literal("block").then(argument("pos", BlockPosArgument.blockPos()).then(argument("slot", SlotArgument.slot())
                                .then(argument("modifier", ResourceOrIdArgument.lootModifier()).suggests(SUGGEST_MODIFIER)
                                        .executes(ItemSlotCommands::modifyBlockItem)))))
                        .then(literal("entity").then(argument("targets", EntityArgument.entities()).then(argument("slot", SlotArgument.slot())
                                .then(argument("modifier", ResourceOrIdArgument.lootModifier()).suggests(SUGGEST_MODIFIER)
                                        .executes(ItemSlotCommands::modifyEntityItem)))))));
    }

    /** from block|entity … — 대상이 블록(toBlock)이냐 엔티티냐에 따라 마무리가 다르다. */
    private static org.domi.furnace.server.command.brigadier.LiteralArgumentBuilder<CommandSource> fromTree(boolean toBlock) {
        return literal("from")
                .then(literal("block").then(argument("source", BlockPosArgument.blockPos()).then(argument("sourceSlot", SlotArgument.slot())
                        .executes(c -> finishFrom(c, toBlock, blockSource(), false))
                        .then(argument("modifier", ResourceOrIdArgument.lootModifier()).suggests(SUGGEST_MODIFIER)
                                .executes(c -> finishFrom(c, toBlock, blockSource(), true))))))
                .then(literal("entity").then(argument("source", EntityArgument.entity()).then(argument("sourceSlot", SlotArgument.slot())
                        .executes(c -> finishFrom(c, toBlock, entitySource(), false))
                        .then(argument("modifier", ResourceOrIdArgument.lootModifier()).suggests(SUGGEST_MODIFIER)
                                .executes(c -> finishFrom(c, toBlock, entitySource(), true))))));
    }

    private static Pos pos(CommandContext<CommandSource> c, String name) {
        int[] p = BlockPosArgument.getBlockPos(c, name);
        return new Pos(p[0], p[1], p[2]);
    }

    private static ItemSource withItem(CommandContext<CommandSource> c, int count, boolean check) throws CommandSyntaxException {
        ItemStack s = ItemArgument.getItem(c, "item").createItemStack(count, check);
        return ctx -> CompletableFuture.completedFuture(s);
    }

    private static int finishFrom(CommandContext<CommandSource> c, boolean toBlock, ItemSource source, boolean modified) throws CommandSyntaxException {
        ItemSource src = source;
        if (modified) {
            LootFunction m = resolveModifier(c.getSource(), ResourceOrIdArgument.get(c, "modifier"));
            src = ctx -> source.get(ctx).thenApply(s -> applyModifier(ctx.getSource(), m, s));
        }
        return toBlock ? setBlockItem(c, src) : setEntityItem(c, src);
    }

    // ------------------------------------------------------------------ 원천

    private static ItemSource blockSource() {
        return c -> {
            Pos p = pos(c, "source");
            int slot = SlotArgument.getSlot(c, "sourceSlot");
            return atPos(c.getSource(), p, level -> {
                var container = container(level, p, false);
                if (slot < 0 || slot >= container.size()) throw LootCommand.ItemCommandsExt.ERROR_SOURCE_INAPPLICABLE_SLOT.create(slot);
                return container.get(slot);
            });
        };
    }

    private static ItemSource entitySource() {
        return c -> {
            CommandSource source = c.getSource();
            int slot = SlotArgument.getSlot(c, "sourceSlot");
            return EntityArgument.findRiders(c, "source", true).thenCompose(list -> {
                Rider r = list.get(0);
                if (r instanceof Player p) {
                    synchronized (p.menuLock()) {
                        return getEntityItem(r, slot);
                    }
                }
                return EntityOps.onOwner(source, r, e -> getEntityItem(e, slot).join());
            });
        };
    }

    private static CompletableFuture<ItemStack> getEntityItem(Rider r, int slot) {
        var access = ItemSlots.of(r, slot);
        if (access == null) return CompletableFuture.failedFuture(LootCommand.ItemCommandsExt.ERROR_SOURCE_INAPPLICABLE_SLOT.create(slot));
        return CompletableFuture.completedFuture(access.get());
    }

    // ------------------------------------------------------------------ 블록 대상

    interface RegionWork<T> {
        T run(RegionLevel level) throws CommandSyntaxException;
    }

    private static <T> CompletableFuture<T> atPos(CommandSource source, Pos pos, RegionWork<T> work) {
        CompletableFuture<T> f = new CompletableFuture<>();
        source.level().runAt(pos.x(), pos.z(), region -> {
            try {
                var level = region.level();
                if (!level.hasChunkAt(pos)) throw BlockPosArgument.ERROR_NOT_LOADED.create();
                if (!level.isInWorldBounds(pos)) throw BlockPosArgument.ERROR_OUT_OF_WORLD.create();
                f.complete(work.run(level));
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        return f;
    }

    private static HopperBlockEntity.Inv container(RegionLevel level, Pos pos, boolean target) throws CommandSyntaxException {
        var c = HopperBlockEntity.containerOf(level, level.getBlockEntity(pos));
        if (c == null) {
            throw (target ? LootCommand.ItemCommandsExt.ERROR_TARGET_NOT_A_CONTAINER : LootCommand.ItemCommandsExt.ERROR_SOURCE_NOT_A_CONTAINER)
                    .create(pos.x(), pos.y(), pos.z());
        }
        return c;
    }

    private static int setBlockItem(CommandContext<CommandSource> c, ItemSource item) throws CommandSyntaxException {
        CommandSource source = c.getSource();
        Pos p = pos(c, "pos");
        int slot = SlotArgument.getSlot(c, "slot");
        PostMessages post = new PostMessages();
        CompletableFuture<Integer> f = item.get(c).thenCompose(stack -> atPos(source, p, level -> {
            var container = container(level, p, true);
            if (slot < 0 || slot >= container.size()) throw LootCommand.ItemCommandsExt.ERROR_TARGET_INAPPLICABLE_SLOT.create(slot);
            container.setItem(slot, stack);
            post.success(source, () -> Text.translatable("commands.item.block.set.success", p.x(), p.y(), p.z(), ItemCommands.displayName(stack)), true);
            return 1;
        }));
        return post.finish(source, f);
    }

    private static int modifyBlockItem(CommandContext<CommandSource> c) throws CommandSyntaxException {
        CommandSource source = c.getSource();
        Pos p = pos(c, "pos");
        int slot = SlotArgument.getSlot(c, "slot");
        LootFunction m = resolveModifier(source, ResourceOrIdArgument.get(c, "modifier"));
        PostMessages post = new PostMessages();
        return post.finish(source, atPos(source, p, level -> {
            var container = container(level, p, true);
            if (slot < 0 || slot >= container.size()) throw LootCommand.ItemCommandsExt.ERROR_TARGET_INAPPLICABLE_SLOT.create(slot);
            ItemStack result = applyModifier(source, m, container.get(slot));
            container.setItem(slot, result);
            post.success(source, () -> Text.translatable("commands.item.block.set.success", p.x(), p.y(), p.z(), ItemCommands.displayName(result)), true);
            return 1;
        }));
    }

    // ------------------------------------------------------------------ 엔티티 대상

    /** 대상마다 주인 스레드에서 fn(칸 접근) → 바뀐 결과(없으면 null)를 모은다. */
    @FunctionalInterface
    interface SlotWork {
        ItemStack apply(ItemSlots.SlotAccess access);
    }

    private static CompletableFuture<Map<Rider, ItemStack>> forTargets(CommandContext<CommandSource> c, int slot, SlotWork work) throws CommandSyntaxException {
        CommandSource source = c.getSource();
        return EntityArgument.findRiders(c, "targets", false).thenCompose(targets -> {
            List<CompletableFuture<ItemStack>> fs = new ArrayList<>(targets.size());
            for (Rider r : targets) {
                if (r instanceof Player p) {
                    synchronized (p.menuLock()) {
                        var access = ItemSlots.of(p, slot);
                        ItemStack res = access == null ? null : work.apply(access);
                        if (res != null) ItemSlots.afterPlayerChange(p);
                        fs.add(CompletableFuture.completedFuture(res));
                    }
                } else {
                    fs.add(EntityOps.onOwner(source, r, e -> {
                        var access = ItemSlots.of(e, slot);
                        return access == null ? null : work.apply(access);
                    }));
                }
            }
            return CompletableFuture.allOf(fs.toArray(new CompletableFuture[0])).thenApply(v -> {
                Map<Rider, ItemStack> out = new LinkedHashMap<>();
                for (int i = 0; i < targets.size(); i++) {
                    ItemStack res = fs.get(i).join();
                    if (res != null) out.put(targets.get(i), res);
                }
                return out;
            });
        });
    }

    private static int setEntityItem(CommandContext<CommandSource> c, ItemSource item) throws CommandSyntaxException {
        CommandSource source = c.getSource();
        int slot = SlotArgument.getSlot(c, "slot");
        CompletableFuture<Integer> f = item.get(c).thenCompose(stack -> {
            try {
                return EntityOps.awaitAsync(source, forTargets(c, slot, a -> a.set(stack) ? stack : null), changed -> {   // 메시지는 전역 스레드에서
                    if (changed.isEmpty()) throw LootCommand.ItemCommandsExt.ERROR_TARGET_NOT_CHANGED_KNOWN_ITEM.create(ItemCommands.displayName(stack), slot);
                    if (changed.size() == 1) {
                        Rider r = changed.keySet().iterator().next();
                        source.sendSuccess(() -> Text.translatable("commands.item.entity.set.success.single", r.displayName(), ItemCommands.displayName(stack)), true);
                    } else {
                        source.sendSuccess(() -> Text.translatable("commands.item.entity.set.success.multiple", changed.size(), ItemCommands.displayName(stack)), true);
                    }
                    return CompletableFuture.completedFuture(changed.size());
                });
            } catch (CommandSyntaxException e) {
                throw new CompletionException(e);
            }
        });
        return DataCommands.finish(source, f);
    }

    private static int modifyEntityItem(CommandContext<CommandSource> c) throws CommandSyntaxException {
        CommandSource source = c.getSource();
        int slot = SlotArgument.getSlot(c, "slot");
        LootFunction m = resolveModifier(source, ResourceOrIdArgument.get(c, "modifier"));
        CompletableFuture<Integer> f = EntityOps.awaitAsync(source, forTargets(c, slot, a -> {
            ItemStack result = applyModifier(source, m, a.get());
            return a.set(result) ? result : null;
        }), changed -> {   // 메시지는 전역 스레드에서
            if (changed.isEmpty()) throw LootCommand.ItemCommandsExt.ERROR_TARGET_NOT_CHANGED.create(slot);
            if (changed.size() == 1) {
                var e = changed.entrySet().iterator().next();
                source.sendSuccess(() -> Text.translatable("commands.item.entity.set.success.single", e.getKey().displayName(), ItemCommands.displayName(e.getValue())), true);
            } else {
                source.sendSuccess(() -> Text.translatable("commands.item.entity.set.success.multiple", changed.size()), true);
            }
            return CompletableFuture.completedFuture(changed.size());
        });
        return DataCommands.finish(source, f);
    }

    // ------------------------------------------------------------------ 수정자

    static LootFunction resolveModifier(CommandSource source, ResourceOrIdArgument.Ref ref) throws CommandSyntaxException {
        var dp = source.getServer().dataPacks();
        if (dp == null || dp.loot() == null) throw ResourceOrIdArgument.missing("minecraft:item_modifier", String.valueOf(ref.id()));
        if (ref.inline() != null) {
            try {
                return dp.loot().parseModifier(ref.inline());
            } catch (RuntimeException e) {
                throw ResourceOrIdArgument.ERROR_FAILED_TO_PARSE.create(String.valueOf(e.getMessage()));
            }
        }
        LootFunction m = dp.loot().modifier(ref.id());
        if (m == null) throw ResourceOrIdArgument.missing("minecraft:item_modifier", ref.id());
        return m;
    }

    /** applyModifier: COMMAND 문맥(origin = 실행 위치, this_entity = 실행 주체) → limitSize(최대 스택). */
    static ItemStack applyModifier(CommandSource source, LootFunction m, ItemStack stack) {
        var params = new LootParams().with(LootParams.ORIGIN, new double[] {source.x(), source.y(), source.z()});
        Player p = source.getPlayer();
        if (p != null) params.with(LootParams.THIS_ENTITY, org.domi.furnace.server.ServerCore.playerLootEntity(p));
        ItemStack out = source.getServer().applyModifier(source.level(), m, stack, params);
        int max = ItemArgument.maxStackSize(out);
        return out.count() > max ? out.withCount(max) : out;
    }
}
