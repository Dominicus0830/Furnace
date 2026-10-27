// 바닐라 GiveCommand·ClearInventoryCommands·EnchantCommand.
// 인벤토리 조작은 메뉴 잠금 안에서(다른 경로와 같다), 떨굼·소리는 플레이어가 선 region 스레드에서 바닐라 순서대로.
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.domi.furnace.api.text.HoverEvent;
import org.domi.furnace.api.text.NamedTextColor;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.text.TextColor;
import org.domi.furnace.api.text.TextDecoration;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.arguments.EntityArgument;
import org.domi.furnace.server.command.arguments.ItemArgument;
import org.domi.furnace.server.command.arguments.ItemPredicateArgument;
import org.domi.furnace.server.command.arguments.ResourceArgument;
import org.domi.furnace.server.command.arguments.ResourceLocationArgument;
import org.domi.furnace.server.command.brigadier.Arguments;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.Dynamic2CommandExceptionType;
import org.domi.furnace.server.command.brigadier.DynamicCommandExceptionType;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.item.EnchantTools;
import org.domi.furnace.server.item.FlatText;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.loot.Enchantment;
import org.domi.furnace.server.loot.LootRegistries;

public final class ItemCommands {
    private ItemCommands() { }

    // ------------------------------------------------------------------ give

    public static final int MAX_ALLOWED_ITEMSTACKS = 100;

    public static void registerGive(CommandDispatcher<CommandSource> d) {
        d.register(literal("give").requires(s -> s.hasPermission(2))
                .then(argument("targets", EntityArgument.players())
                        .then(argument("item", ItemArgument.item())
                                .executes(c -> give(c.getSource(), ItemArgument.getItem(c, "item"), EntityArgument.getPlayers(c, "targets"), 1))
                                .then(argument("count", Arguments.integer(1))
                                        .executes(c -> give(c.getSource(), ItemArgument.getItem(c, "item"), EntityArgument.getPlayers(c, "targets"),
                                                c.getArgument("count", Integer.class)))))));
    }

    /** 떨굼 쪽 효과: 다 넣었으면 가짜 아이템(1개) + 줍는 소리, 남았으면 그 나머지를 주인 지정·즉시 줍기로. */
    public interface GiveEffects {
        void fakeDrop(ItemStack one);

        void drop(ItemStack rest);
    }

    static int give(CommandSource source, ItemArgument.ItemInput item, Collection<Player> targets, int count) throws CommandSyntaxException {
        ItemStack proto = item.createItemStack(1, false);
        int max = ItemArgument.maxStackSize(proto);
        int limit = max * MAX_ALLOWED_ITEMSTACKS;
        if (count > limit) {
            source.sendFailure(Text.translatable("commands.give.failed.toomanyitems", limit, displayName(proto)));
            return 0;
        }
        for (Player p : targets) {
            List<java.util.function.Consumer<org.domi.furnace.server.world.Region>> ops = new ArrayList<>();
            giveTo(p, item, count, new GiveEffects() {
                public void fakeDrop(ItemStack one) { ops.add(r -> pending(r, p, one, true)); }

                public void drop(ItemStack rest) { ops.add(r -> pending(r, p, rest, false)); }
            });
            if (!ops.isEmpty()) source.level().runAt(p.x(), p.z(), region -> { for (var op : ops) op.accept(region); });
        }
        source.sendSuccess(() -> giveMessage(count, proto, targets), true);
        return targets.size();
    }

    /** 성공 메시지. 바닐라 1.21.1 은 여러 명이어도 .single 키에 인원 수를 넣는다(그대로 따른다). */
    static Text giveMessage(int count, ItemStack proto, Collection<Player> targets) {
        if (targets.size() == 1) return Text.translatable("commands.give.success.single", count, displayName(proto), targets.iterator().next().displayName());
        return Text.translatable("commands.give.success.single", count, displayName(proto), targets.size());
    }

    /** region 스레드에서: Player.drop(stack, false) 뒤 가짜 아이템·소리, 또는 즉시 줍기·주인 지정. */
    private static void pending(org.domi.furnace.server.world.Region region, Player p, ItemStack stack, boolean fake) {
        var ie = region.playerDrop(p, stack, false, false);
        if (fake) {
            if (ie != null) ie.makeFakeItem();
            region.level().playSound(null, p.x(), p.y(), p.z(), "minecraft:entity.item.pickup", "players", 0.2F, pickupPitch(p.random()));
        } else if (ie != null) {
            ie.setNoPickUpDelay();
            ie.setTarget(p.uuid());
        }
    }

    /** ((random.nextFloat() - random.nextFloat()) * 0.7 + 1) * 2 — 플레이어 난수, 왼쪽부터. */
    public static float pickupPitch(org.domi.furnace.server.block.RandomSource r) {
        float a = r.nextFloat();
        float b = r.nextFloat();
        return ((a - b) * 0.7F + 1.0F) * 2.0F;
    }

    /** 인벤토리 쪽: 최대 스택 단위로 나눠 넣고 효과를 순서대로 알린다. */
    public static void giveTo(Player p, ItemArgument.ItemInput item, int count, GiveEffects fx) throws CommandSyntaxException {
        ItemStack proto = item.createItemStack(1, false);
        int max = ItemArgument.maxStackSize(proto);
        boolean creative = p.vitals().isCreative();
        synchronized (p.menuLock()) {
            int left = count;
            while (left > 0) {
                int n = Math.min(max, left);
                left -= n;
                ItemStack stack = item.createItemStack(n, false);
                boolean damaged = ItemTools.isDamageable(stack) && ItemTools.damage(stack) > 0;
                ItemStack rest = p.inventory().add(stack, creative, max, damaged);
                if (rest.isEmpty()) {
                    fx.fakeDrop(proto);
                } else {
                    fx.drop(rest);
                }
            }
            var menu = p.containerMenu();
            if (menu != null) menu.broadcastChanges();
        }
    }

    // ------------------------------------------------------------------ clear

    private static final DynamicCommandExceptionType ERROR_SINGLE = new DynamicCommandExceptionType(n -> Text.translatable("clear.failed.single", n));
    private static final DynamicCommandExceptionType ERROR_MULTIPLE = new DynamicCommandExceptionType(n -> Text.translatable("clear.failed.multiple", n));

    public static void registerClear(CommandDispatcher<CommandSource> d) {
        d.register(literal("clear").requires(s -> s.hasPermission(2))
                .executes(c -> clear(c.getSource(), List.of(c.getSource().getPlayerOrException()), s -> true, -1))
                .then(argument("targets", EntityArgument.players())
                        .executes(c -> clear(c.getSource(), EntityArgument.getPlayers(c, "targets"), s -> true, -1))
                        .then(argument("item", ItemPredicateArgument.itemPredicate())
                                .executes(c -> clear(c.getSource(), EntityArgument.getPlayers(c, "targets"), ItemPredicateArgument.getItemPredicate(c, "item"), -1))
                                .then(argument("maxCount", Arguments.integer(0))
                                        .executes(c -> clear(c.getSource(), EntityArgument.getPlayers(c, "targets"), ItemPredicateArgument.getItemPredicate(c, "item"),
                                                c.getArgument("maxCount", Integer.class)))))));
    }

    static int clear(CommandSource source, Collection<Player> targets, Predicate<ItemStack> pred, int maxCount) throws CommandSyntaxException {
        int total = 0;
        for (Player p : targets) total += clearOrCount(p, pred, maxCount);
        if (total == 0) {
            if (targets.size() == 1) throw ERROR_SINGLE.create(Text.text(targets.iterator().next().name()));
            throw ERROR_MULTIPLE.create(targets.size());
        }
        int n = total;
        source.sendSuccess(() -> clearMessage(n, targets, maxCount), true);
        return total;
    }

    static Text clearMessage(int n, Collection<Player> targets, int maxCount) {
        String mode = maxCount == 0 ? "commands.clear.test." : "commands.clear.success.";
        if (targets.size() == 1) return Text.translatable(mode + "single", n, targets.iterator().next().displayName());
        return Text.translatable(mode + "multiple", n, targets.size());
    }

    /**
     * Inventory.clearOrCountMatchingItems: 인벤토리(0..40) → 2x2 제작 칸 → 커서 순. maxCount 0 이면 세기만, 음수면 전부.
     * 끝나면 broadcastChanges 와 제작 결과 다시 계산(inventoryMenu.slotsChanged).
     */
    public static int clearOrCount(Player p, Predicate<ItemStack> pred, int maxCount) {
        boolean dry = maxCount == 0;
        int n = 0;
        synchronized (p.menuLock()) {
            PlayerInventory inv = p.inventory();
            for (int i = 0; i < 41; i++) n += clearSlot(inv, PlayerInventory.windowSlot(i), pred, maxCount - n, dry);
            for (int i = 0; i < 4; i++) n += clearSlot(inv, 1 + i, pred, maxCount - n, dry);
            var menu = p.containerMenu();
            if (menu != null) {
                ItemStack carried = menu.getCarried();
                int k = count(carried, pred, maxCount - n, dry);
                if (k > 0 && !dry) menu.setCarried(carried.withCount(carried.count() - k));
                n += k;
                menu.broadcastChanges();
            }
            var im = p.inventoryMenu();
            if (im != null) im.slotsChanged(im.craftSlots());
        }
        return n;
    }

    private static int clearSlot(PlayerInventory inv, int ws, Predicate<ItemStack> pred, int max, boolean dry) {
        ItemStack s = inv.get(ws);
        int k = count(s, pred, max, dry);
        if (k > 0 && !dry) inv.set(ws, s.withCount(s.count() - k));
        return k;
    }

    /** ContainerHelper.clearOrCountMatchingItems(stack, …): 지울(셀) 개수. */
    private static int count(ItemStack s, Predicate<ItemStack> pred, int max, boolean dry) {
        if (s.isEmpty() || !pred.test(s)) return 0;
        if (dry) return s.count();
        return max < 0 ? s.count() : Math.min(max, s.count());
    }

    // ------------------------------------------------------------------ enchant

    private static final DynamicCommandExceptionType ERROR_NOT_LIVING_ENTITY = new DynamicCommandExceptionType(n -> Text.translatable("commands.enchant.failed.entity", n));
    private static final DynamicCommandExceptionType ERROR_NO_ITEM = new DynamicCommandExceptionType(n -> Text.translatable("commands.enchant.failed.itemless", n));
    private static final DynamicCommandExceptionType ERROR_INCOMPATIBLE = new DynamicCommandExceptionType(n -> Text.translatable("commands.enchant.failed.incompatible", n));
    private static final Dynamic2CommandExceptionType ERROR_LEVEL_TOO_HIGH =
            new Dynamic2CommandExceptionType((a, b) -> Text.translatable("commands.enchant.failed.level", a, b));
    private static final SimpleCommandExceptionType ERROR_NOTHING_HAPPENED = new SimpleCommandExceptionType(Text.translatable("commands.enchant.failed"));
    private static final Dynamic2CommandExceptionType ERROR_UNKNOWN_RESOURCE =
            new Dynamic2CommandExceptionType((id, reg) -> Text.translatable("argument.resource.not_found", id, reg));

    public static void registerEnchant(CommandDispatcher<CommandSource> d) {
        d.register(literal("enchant").requires(s -> s.hasPermission(2))
                .then(argument("targets", EntityArgument.entities())
                        .then(argument("enchantment", ResourceArgument.resource("minecraft:enchantment"))
                                .executes(c -> enchant(c.getSource(), EntityArgument.getEntities(c, "targets"), enchantmentId(c), 1))
                                .then(argument("level", Arguments.integer(0))
                                        .executes(c -> enchant(c.getSource(), EntityArgument.getEntities(c, "targets"), enchantmentId(c),
                                                c.getArgument("level", Integer.class)))))));
    }

    private static String enchantmentId(CommandContext<CommandSource> c) { return ResourceLocationArgument.getId(c, "enchantment").toString(); }

    static int enchant(CommandSource source, Collection<Player> targets, String id, int level) throws CommandSyntaxException {
        LootRegistries regs = source.getServer().dataPacks().loot().registries();
        Enchantment e = regs.enchantment(id);
        if (e == null) throw ERROR_UNKNOWN_RESOURCE.create(id, "minecraft:enchantment");
        int n = enchantAll(targets, e, level, regs);
        if (targets.size() == 1) {
            Player p = targets.iterator().next();
            source.sendSuccess(() -> Text.translatable("commands.enchant.success.single", fullName(e, level), p.displayName()), true);
        } else {
            source.sendSuccess(() -> Text.translatable("commands.enchant.success.multiple", fullName(e, level), targets.size()), true);
        }
        return n;
    }

    /** 대상마다 주손 아이템에 건다. 걸 수 없으면 대상이 하나일 때만 오류. 하나도 못 걸면 commands.enchant.failed. */
    public static int enchantAll(Collection<Player> targets, Enchantment e, int level, LootRegistries regs) throws CommandSyntaxException {
        if (level > e.maxLevel()) throw ERROR_LEVEL_TOO_HIGH.create(level, e.maxLevel());
        int n = 0;
        for (Player p : targets) {
            synchronized (p.menuLock()) {
                PlayerInventory inv = p.inventory();
                int ws = inv.heldWindowSlot();
                ItemStack s = inv.get(ws);
                if (s.isEmpty()) {
                    if (targets.size() == 1) throw ERROR_NO_ITEM.create(p.name());
                    continue;
                }
                if (e.supports(ItemTools.itemName(s), regs) && compatible(EnchantTools.forCrafting(s), e, regs)) {
                    inv.set(ws, EnchantTools.update(s, m -> {
                        if (level > 0) m.merge(e.id(), Math.min(level, 255), Math::max);   // ItemEnchantments.Mutable.upgrade
                    }));
                    var menu = p.containerMenu();
                    if (menu != null) menu.broadcastChanges();
                    n++;
                } else if (targets.size() == 1) {
                    throw ERROR_INCOMPATIBLE.create(plain(hoverName(s)));
                }
            }
        }
        if (n == 0) throw ERROR_NOTHING_HAPPENED.create();
        return n;
    }

    /** EnchantmentHelper.isEnchantmentCompatible: 이미 있는 모든 마법과 함께 걸 수 있다(같은 마법도 안 된다). */
    private static boolean compatible(Map<String, Integer> existing, Enchantment e, LootRegistries regs) {
        for (String other : existing.keySet()) {
            Enchantment o = regs.enchantment(other);
            if (o == null ? other.equals(e.id()) : !Enchantment.areCompatible(o, e, regs)) return false;
        }
        return true;
    }

    /** Enchantment.getFullname: 설명(저주는 빨강, 아니면 회색) + 레벨(최대 레벨이 1 인 레벨 1 은 생략). */
    public static Text fullName(Enchantment e, int level) {
        String[] ns = e.id().split(":", 2);
        Text t = Text.translatable("enchantment." + ns[0] + "." + ns[1]);   // 가정: 데이터 팩 description 이 바닐라 기본 번역 키
        t = t.colorIfAbsent(EnchantTools.isCurse(e.id()) ? NamedTextColor.RED : NamedTextColor.GRAY);
        if (level != 1 || e.maxLevel() != 1) t = t.append(Text.space()).append(Text.translatable("enchantment.level." + level));
        return t;
    }

    // ------------------------------------------------------------------ 아이템 이름

    /** ItemStack.getHoverName: custom_name, 없으면 item_name(아이템 기본값). */
    public static Text hoverName(ItemStack s) {
        Object custom = ItemArgument.componentValue(s, "minecraft:custom_name");
        if (custom instanceof String str) return FlatText.parse(str);
        Object name = ItemArgument.componentValue(s, "minecraft:item_name");
        if (name instanceof String str) return FlatText.parse(str);
        String id = ItemTools.itemName(s);
        String[] ns = id.split(":", 2);
        return Text.translatable("item." + ns[0] + "." + ns[1]);
    }

    /** ItemStack.getDisplayName: [이름] — 이름이 붙었으면 기울임, 희귀도 색, show_item 호버. */
    public static Text displayName(ItemStack s) {
        Text inner = Text.empty().append(hoverName(s));
        if (ItemArgument.componentValue(s, "minecraft:custom_name") != null) inner = inner.decorate(TextDecoration.ITALIC);
        Text out = Text.translatable("chat.square_brackets", inner);
        if (!s.isEmpty()) {
            String comps = s.patch().isEmpty() ? null : org.domi.furnace.server.item.Snbt.write(s.patch().toNbt());
            out = out.color(rarityColor(s)).hoverEvent(new HoverEvent.ShowItem(ItemTools.itemName(s), s.count(), comps));
        }
        return out;
    }

    /** ItemStack.getRarity: 인챈트가 있으면 common·uncommon → rare, rare → epic. */
    static TextColor rarityColor(ItemStack s) {
        String r = ItemArgument.componentValue(s, "minecraft:rarity") instanceof String str ? str : "common";
        if (!EnchantTools.levels(s, EnchantTools.ENCHANTMENTS).isEmpty()) {
            r = switch (r) {
                case "common", "uncommon" -> "rare";
                case "rare" -> "epic";
                default -> r;
            };
        }
        return switch (r) {
            case "uncommon" -> NamedTextColor.YELLOW;
            case "rare" -> NamedTextColor.AQUA;
            case "epic" -> NamedTextColor.LIGHT_PURPLE;
            default -> NamedTextColor.WHITE;
        };
    }

    /** Component.getString 근사: 번역 키는 서버 언어 표(en_us)로, 리터럴은 그대로. */
    private static String plain(Text t) {
        StringBuilder sb = new StringBuilder();
        if (t.translationKey() != null) sb.append(org.domi.furnace.server.net.protocol.Language.getOrDefault(t.translationKey()));
        else if (t.literal() != null) sb.append(t.literal());
        for (Text c : t.children()) sb.append(plain(c));
        return sb.toString();
    }
}
