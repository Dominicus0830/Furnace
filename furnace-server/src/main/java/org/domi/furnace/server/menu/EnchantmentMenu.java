// 바닐라 EnchantmentMenu(마법 부여대, enchantment) — 칸: 0 대상, 1 청금석, 2..28 주 인벤토리, 29..37 핫바.
// 데이터 순서: 0..2 비용, 3 시드, 4..6 인챈트 단서(레지스트리 id), 7..9 레벨 단서.
package org.domi.furnace.server.menu;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.block.LegacyRandomSource;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.EnchantTools;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.loot.Enchantments;
import org.domi.furnace.server.loot.LegacyNoiseRandom;

public class EnchantmentMenu extends AbstractContainerMenu {

    public static final int ITEM_SLOT = 0, LAPIS_SLOT = 1;

    private final int[] blockPos;
    private final BlockAccess access;
    private final MenuPlayer owner;
    private final Container enchantSlots;
    private final LegacyRandomSource random = new LegacyRandomSource(0);
    private final DataSlot enchantmentSeed = DataSlot.standalone();
    public final int[] costs = new int[3];
    public final int[] enchantClue = {-1, -1, -1};
    public final int[] levelClue = {-1, -1, -1};
    private final String[] clueName = new String[3];

    /** 제안 칸 i 가 단서로 보이는 마법 부여 id(단서가 없으면 null) — 플러그인 EnchantItemEvent#enchantmentHint. */
    public String clueName(int i) { return enchantClue[i] < 0 ? null : clueName[i]; }

    public EnchantmentMenu(int containerId, Container playerInventory, MenuPlayer owner, int[] blockPos, BlockAccess access) {
        super("minecraft:enchantment", containerId);
        this.owner = owner;
        this.blockPos = blockPos;
        this.access = access;
        this.enchantSlots = new SimpleContainer(2) {
            @Override public void setChanged() {
                super.setChanged();
                slotsChanged(this);
            }
        };
        addSlot(new Slot(enchantSlots, 0) {
            @Override public int getMaxStackSize() { return 1; }
        });
        addSlot(new Slot(enchantSlots, 1) {
            @Override public boolean mayPlace(ItemStack stack) { return "minecraft:lapis_lazuli".equals(ItemTools.itemName(stack)); }
        });
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(playerInventory, c + r * 9 + 9));
        for (int c = 0; c < 9; c++) addSlot(new Slot(playerInventory, c));
        for (int i = 0; i < 3; i++) { final int k = i; addDataSlot(DataSlot.of(() -> costs[k], v -> costs[k] = v)); }
        addDataSlot(enchantmentSeed).set(owner.enchantmentSeed());
        for (int i = 0; i < 3; i++) { final int k = i; addDataSlot(DataSlot.of(() -> enchantClue[k], v -> enchantClue[k] = v)); }
        for (int i = 0; i < 3; i++) { final int k = i; addDataSlot(DataSlot.of(() -> levelClue[k], v -> levelClue[k] = v)); }
    }

    @Override public int[] containerBlockPos() { return blockPos; }

    @Override public boolean stillValid(MenuPlayer player) {
        return access.stillBlock() && player.distanceToSqr(blockPos[0] + 0.5, blockPos[1] + 0.5, blockPos[2] + 0.5) <= 64.0;
    }

    /** ItemStack.isEnchantable: 이미 인챈트가 있으면 안 되고, 책은 1개, 그 밖은 쌓이지 않는 내구도 아이템. */
    private static boolean isEnchantable(ItemStack s) {
        if (s.isEmpty() || !EnchantTools.levels(s, EnchantTools.ENCHANTMENTS).isEmpty()) return false;
        if ("minecraft:book".equals(ItemTools.itemName(s))) return s.count() == 1;
        return Stacks.maxStack(s) == 1 && ItemTools.isDamageable(s);
    }

    /** EnchantmentHelper.getEnchantmentCost(random, slot, power, stack). */
    private static int getEnchantmentCost(LegacyRandomSource r, int slot, int power, ItemStack stack) {
        int value = org.domi.furnace.server.loot.ItemData.vanilla().enchantmentValue(ItemTools.itemName(stack));
        if (value <= 0) return 0;
        if (power > 15) power = 15;
        int j = r.nextInt(8) + 1 + (power >> 1) + r.nextInt(power + 1);
        if (slot == 0) return Math.max(j / 3, 1);
        return slot == 1 ? j * 2 / 3 + 1 : Math.max(j, power * 2);
    }

    private List<Enchantments.TableEnchant> getEnchantmentList(ItemStack stack, int slot, int cost) {
        var regs = owner.lootRegistries();
        if (regs == null) return new ArrayList<>();
        random.setSeed((long) (enchantmentSeed.get() + slot));
        List<Enchantments.TableEnchant> list = Enchantments.selectForTable(new LegacyNoiseRandom(random), ItemTools.itemName(stack), cost, regs);
        if ("minecraft:book".equals(ItemTools.itemName(stack)) && list.size() > 1) list.remove(random.nextInt(list.size()));
        return list;
    }

    @Override
    public void slotsChanged(Container container) {
        if (container != enchantSlots) return;
        ItemStack item = enchantSlots.get(0);
        if (!item.isEmpty() && isEnchantable(item)) {
            int power = access.enchantingPower();
            random.setSeed(enchantmentSeed.get());
            for (int i = 0; i < 3; i++) {
                costs[i] = getEnchantmentCost(random, i, power, item);
                enchantClue[i] = -1;
                levelClue[i] = -1;
                clueName[i] = null;
                if (costs[i] < i + 1) costs[i] = 0;
            }
            for (int i = 0; i < 3; i++) {
                if (costs[i] > 0) {
                    List<Enchantments.TableEnchant> list = getEnchantmentList(item, i, costs[i]);
                    if (!list.isEmpty()) {
                        var pick = list.get(random.nextInt(list.size()));
                        Integer id = ItemContext.id("minecraft:enchantment", pick.id());
                        enchantClue[i] = id == null ? -1 : id;
                        levelClue[i] = pick.level();
                        clueName[i] = pick.id();
                    }
                }
            }
            if (!applyPlugin(item)) return;
            broadcastChanges();
        } else {
            for (int i = 0; i < 3; i++) { costs[i] = 0; enchantClue[i] = -1; levelClue[i] = -1; }
        }
    }

    /** 플러그인 PrepareItemEnchantEvent(CraftBukkit 처럼 단서가 있는 칸만 제안): 취소면 세 칸을 비우고 false. */
    private boolean applyPlugin(ItemStack item) {
        var offers = new org.domi.furnace.server.plugin.GameEvents.EnchantOffer[3];
        for (int i = 0; i < 3; i++) {
            if (clueName[i] != null) offers[i] = new org.domi.furnace.server.plugin.GameEvents.EnchantOffer(clueName[i], levelClue[i], costs[i]);
        }
        var now = owner.prepareEnchant(this, blockPos, item, access.enchantingPower(), offers);
        if (now == offers) {
            // 구독자가 없다: 예전 동작 그대로(단서 없는 칸의 비용도 남는다)
            return true;
        }
        for (int i = 0; i < 3; i++) {
            var o = now == null ? null : now[i];
            if (o == null) {
                costs[i] = 0;
                enchantClue[i] = -1;
                levelClue[i] = -1;
                clueName[i] = null;
            } else {
                costs[i] = o.cost();
                clueName[i] = o.enchantment();
                Integer id = o.enchantment() == null ? null : ItemContext.id("minecraft:enchantment", o.enchantment());
                enchantClue[i] = id == null ? -1 : id;
                levelClue[i] = id == null ? -1 : o.level();
            }
        }
        if (now == null) broadcastChanges();
        return now != null;
    }

    @Override
    public boolean clickMenuButton(MenuPlayer player, int id) {
        if (id < 0 || id >= costs.length) return false;
        ItemStack item = enchantSlots.get(0), lapis = enchantSlots.get(1);
        int cost = id + 1;
        if ((lapis.isEmpty() || lapis.count() < cost) && !player.isCreative()) return false;
        if (costs[id] <= 0 || item.isEmpty() || (player.experienceLevel() < cost || player.experienceLevel() < costs[id]) && !player.isCreative()) return false;
        List<Enchantments.TableEnchant> list = getEnchantmentList(item, id, costs[id]);
        if (!list.isEmpty()) {
            var chosen = new java.util.LinkedHashMap<String, Integer>();
            for (var e : list) chosen.merge(e.id(), e.level(), Math::max);
            var apply = player.enchantItem(this, blockPos, item, id, costs[id], chosen);   // 플러그인 EnchantItemEvent
            if (apply == null || apply.enchants().isEmpty() || apply.expLevelCost() > player.experienceLevel() && !player.isCreative()) return true;
            player.onEnchantmentPerformed(cost);
            ItemStack target = item;
            if ("minecraft:book".equals(ItemTools.itemName(item))) {
                Integer eb = ItemContext.id("minecraft:item", "minecraft:enchanted_book");
                if (eb != null) target = new ItemStack(eb, item.count(), item.patch().normalizedFor("minecraft:enchanted_book"));
            }
            for (var e : apply.enchants().entrySet()) {
                final var en = e;
                target = EnchantTools.update(target, m -> m.merge(en.getKey(), en.getValue(), Math::max));
            }
            enchantSlots.set(0, target);
            player.awardStat("minecraft:enchant_item", 1);
            if (!player.isCreative()) {
                ItemStack rest = lapis.withCount(lapis.count() - cost);
                enchantSlots.set(1, rest.isEmpty() ? ItemStack.EMPTY : rest);
            }
            var sp = player.entityPlayer();   // 바닐라: 통계 다음, setChanged 전에 enchanted_item(플레이어, 마법 부여한 스택, 비용)
            if (sp != null) {
                var enchanted = org.domi.furnace.server.ServerCore.lootStackOf(target);
                sp.triggerAdvancement(a -> a.triggerEnchanted(enchanted, cost));
            }
            enchantSlots.setChanged();
            enchantmentSeed.set(player.enchantmentSeed());
            slotsChanged(enchantSlots);
            access.playSound("minecraft:block.enchantment_table.use", 1.0F, access.nextFloat() * 0.1F + 0.9F);
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(MenuPlayer player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return result;
        ItemStack stack = slot.getItem();
        result = stack;
        Moved moved;
        if (index == 0 || index == 1) {
            moved = moveItemStackTo(stack, 2, 38, true);
            if (!moved.changed()) return ItemStack.EMPTY;
            stack = moved.remaining();
        } else if ("minecraft:lapis_lazuli".equals(ItemTools.itemName(stack))) {
            moved = moveItemStackTo(stack, 1, 2, true);
            if (!moved.changed()) return ItemStack.EMPTY;
            stack = moved.remaining();
        } else {
            if (slots.get(0).hasItem() || !slots.get(0).mayPlace(stack)) return ItemStack.EMPTY;
            ItemStack single = stack.withCount(1);
            stack = stack.withCount(stack.count() - 1);
            slots.get(0).setByPlayer(single);
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setCountInPlace(stack);
        slot.setChanged();
        if (stack.count() == result.count()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return result;
    }

    @Override
    public void removed(MenuPlayer player) {
        super.removed(player);
        clearContainer(player, enchantSlots);
    }
}
