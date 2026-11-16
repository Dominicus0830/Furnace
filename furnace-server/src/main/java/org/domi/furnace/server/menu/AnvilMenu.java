// 바닐라 AnvilMenu(모루, anvil) — 칸: 0 왼쪽, 1 오른쪽, 2 결과, 3..29 주 인벤토리, 30..38 핫바. 데이터 0 = 레벨 비용.
package org.domi.furnace.server.menu;

import java.util.LinkedHashMap;
import java.util.Map;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.EnchantTools;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.item.RepairMaterials;

public class AnvilMenu extends ItemCombinerMenu {

    public static final int INPUT_SLOT = 0, ADDITIONAL_SLOT = 1, RESULT_SLOT = 2, MAX_NAME_LENGTH = 50;

    private final DataSlot cost = DataSlot.standalone();
    private int repairItemCountCost;
    private String itemName;

    public AnvilMenu(int containerId, Container playerInventory, MenuPlayer player, int[] blockPos, BlockAccess access) {
        super("minecraft:anvil", containerId, playerInventory, player, blockPos, access, 2);
        addDataSlot(cost);
    }

    public int cost() { return cost.get(); }

    @Override protected boolean mayPlaceInput(int index, ItemStack stack) { return true; }

    @Override protected boolean isValidBlock() { return access.stillBlock(); }

    @Override protected boolean mayPickup(MenuPlayer p, boolean hasStack) { return (p.isCreative() || p.experienceLevel() >= cost.get()) && cost.get() >= 0 && hasStack; }   // CraftBukkit: 비용 0 도 꺼낼 수 있다(무료 아이템)

    @Override
    protected void onTake(MenuPlayer p, ItemStack stack) {
        if (!p.isCreative()) p.giveExperienceLevels(-cost.get());
        inputSlots.set(0, ItemStack.EMPTY);
        if (repairItemCountCost > 0) {
            ItemStack add = inputSlots.get(1);
            if (!add.isEmpty() && add.count() > repairItemCountCost) inputSlots.set(1, add.withCount(add.count() - repairItemCountCost));
            else inputSlots.set(1, ItemStack.EMPTY);
        } else {
            inputSlots.set(1, ItemStack.EMPTY);
        }
        cost.set(0);
        access.anvilUsed(p.isCreative());
    }

    private static int repairCost(ItemStack s) { return ItemTools.component(s, "minecraft:repair_cost") instanceof Number n ? n.intValue() : 0; }

    /** AnvilMenu.calculateIncreasedRepairCost. */
    public static int calculateIncreasedRepairCost(int c) { return (int) Math.min(c * 2L + 1L, Integer.MAX_VALUE); }

    private static String customNameText(ItemStack s) {
        Object v = ItemTools.component(s, "minecraft:custom_name");
        if (v == null) return null;
        if (v instanceof String str) {   // 텍스트 컴포넌트의 정규 값은 JSON 문자열
            if (str.startsWith("\"")) {
                try { return com.google.gson.JsonParser.parseString(str).getAsString(); } catch (RuntimeException e) { return str; }
            }
            return str;
        }
        if (v instanceof Map<?, ?> m && m.get("text") != null) return String.valueOf(m.get("text"));
        return v.toString();
    }

    /** 결과를 가져올 수 있는 비용의 위쪽 한계(Paper AnvilMenu.maximumRepairCost, 기본 40). 이 값 이상이면 생존 모드에서 "너무 비쌈". 창마다 따로다. */
    private int maximumRepairCost = 40;

    public int maximumRepairCost() { return maximumRepairCost; }

    public void setMaximumRepairCost(int levels) { this.maximumRepairCost = levels; }

    private void empty() {
        resultSlots.set(0, ItemStack.EMPTY);
        cost.set(0);
        if (firePrepare()) broadcastChanges();
    }

    /** 플러그인 PrepareAnvilEvent — 바뀐 값이 있으면 결과 칸·비용에 적용하고 true. */
    private boolean firePrepare() {
        var r = player.prepareAnvil(this, resultSlots.get(0), cost.get(), maximumRepairCost, itemName);
        if (r == null) return false;
        resultSlots.set(0, r.result());
        cost.set(r.repairCost());
        maximumRepairCost = r.maximumRepairCost();   // 이번 결과는 이미 정해졌다 — 새 한계는 다음 계산부터(Paper 와 같다)
        return true;
    }

    @Override
    public void createResult() {
        ItemStack input = inputSlots.get(0);
        cost.set(1);
        int levelCost = 0;
        long baseCost = 0L;
        int renameCost = 0;
        if (input.isEmpty()) { empty(); return; }
        ItemStack result = input;
        ItemStack addition = inputSlots.get(1);
        Map<String, Integer> mutable = new LinkedHashMap<>(EnchantTools.forCrafting(result));
        baseCost += repairCost(input) + repairCost(addition);
        repairItemCountCost = 0;
        if (!addition.isEmpty()) {
            boolean bookLike = ItemTools.component(addition, EnchantTools.STORED) != null
                    || "minecraft:enchanted_book".equals(ItemTools.itemName(addition));
            if (ItemTools.isDamageable(result) && RepairMaterials.isValidRepairItem(input, addition)) {
                int d = Math.min(ItemTools.damage(result), ItemTools.maxDamage(result) / 4);
                if (d <= 0) { empty(); return; }
                int n;
                for (n = 0; d > 0 && n < addition.count(); n++) {
                    result = ItemTools.withDamage(result, ItemTools.damage(result) - d);
                    levelCost++;
                    d = Math.min(ItemTools.damage(result), ItemTools.maxDamage(result) / 4);
                }
                repairItemCountCost = n;
            } else {
                if (!bookLike && (result.itemId() != addition.itemId() || !ItemTools.isDamageable(result))) { empty(); return; }
                if (ItemTools.isDamageable(result) && !bookLike) {
                    int a = ItemTools.maxDamage(input) - ItemTools.damage(input);
                    int b = ItemTools.maxDamage(addition) - ItemTools.damage(addition);
                    int c = b + ItemTools.maxDamage(result) * 12 / 100;
                    int newDamage = Math.max(ItemTools.maxDamage(result) - (a + c), 0);
                    if (newDamage < ItemTools.damage(result)) {
                        result = ItemTools.withDamage(result, newDamage);
                        levelCost += 2;
                    }
                }
                boolean applied = false, rejected = false;
                for (var e : EnchantTools.forCrafting(addition).entrySet()) {
                    String ench = e.getKey();
                    int existing = mutable.getOrDefault(ench, 0);
                    int lvl = e.getValue();
                    lvl = existing == lvl ? lvl + 1 : Math.max(lvl, existing);
                    var def = player.enchantment(ench);
                    boolean canApply = player.enchantmentSupports(ench, input);
                    if (player.isCreative() || "minecraft:enchanted_book".equals(ItemTools.itemName(input))) canApply = true;
                    for (String other : mutable.keySet()) {
                        if (!other.equals(ench) && !player.enchantmentsCompatible(ench, other)) {
                            canApply = false;
                            levelCost++;
                        }
                    }
                    if (!canApply) {
                        rejected = true;
                    } else {
                        applied = true;
                        int max = def != null ? def.maxLevel() : 255;
                        if (lvl > max) lvl = max;
                        mutable.put(ench, lvl);
                        int fee = def != null ? def.anvilCost() : 0;
                        if (bookLike) fee = Math.max(1, fee / 2);
                        levelCost += fee * lvl;
                        if (input.count() > 1) levelCost = 40;
                    }
                }
                if (rejected && !applied) { empty(); return; }
            }
        }
        if (itemName != null && !isBlank(itemName)) {
            String current = org.domi.furnace.server.command.builtin.ItemCommands.hoverName(input).getString();   // 이름 없는 아이템은 기본 이름(서버 언어 en_us)과 비교
            if (!itemName.equals(current)) {
                renameCost = 1;
                levelCost += 1;
                result = ItemTools.with(result, "minecraft:custom_name", new com.google.gson.JsonPrimitive(itemName).toString());
            }
        } else if (customNameText(input) != null) {
            renameCost = 1;
            levelCost += 1;
            result = ItemTools.with(result, "minecraft:custom_name", null);
        }
        int total = (int) Math.max(0L, Math.min(baseCost + levelCost, Integer.MAX_VALUE));
        cost.set(total);
        if (levelCost <= 0) result = ItemStack.EMPTY;
        if (renameCost == levelCost && renameCost > 0 && cost.get() >= maximumRepairCost) cost.set(maximumRepairCost - 1);
        if (cost.get() >= maximumRepairCost && !player.isCreative()) result = ItemStack.EMPTY;
        if (!result.isEmpty()) {
            int rc = repairCost(result);
            if (rc < repairCost(addition)) rc = repairCost(addition);
            if (renameCost != levelCost || renameCost == 0) rc = calculateIncreasedRepairCost(rc);
            result = ItemTools.with(result, "minecraft:repair_cost", rc);
            final Map<String, Integer> fin = mutable;
            result = EnchantTools.update(result, m -> { m.clear(); m.putAll(fin); });
        }
        resultSlots.set(0, result);
        firePrepare();
        broadcastChanges();
    }

    private static boolean isBlank(String s) { return s.chars().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c)); }

    /** StringUtil.filterText: § 와 제어 문자를 지운다. */
    private static String filterText(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) if (c != 167 && c >= ' ' && c != 127) b.append(c);
        return b.toString();
    }

    /** rename_item: 이름을 바꾸고 결과를 다시 구한다. 50자 넘으면 무시. */
    public boolean setItemName(String name) {
        String n = filterText(name);
        if (n.length() > MAX_NAME_LENGTH) return false;
        if (!n.equals(itemName)) {
            itemName = n;
            createResult();
            return true;
        }
        return false;
    }
}
