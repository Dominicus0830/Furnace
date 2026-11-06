// 바닐라 LoomMenu(베틀, loom) — 칸: 0 깃발, 1 염료, 2 무늬 아이템, 3 결과, 4..30 주 인벤토리, 31..39 핫바. 데이터 0 = 고른 무늬 번호.
package org.domi.furnace.server.menu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.recipe.SpecialRecipes;

public class LoomMenu extends AbstractContainerMenu {

    public static final int BANNER_SLOT = 0, DYE_SLOT = 1, PATTERN_SLOT = 2, RESULT_SLOT = 3, INV_SLOT_START = 4, INV_SLOT_END = 31,
            USE_ROW_SLOT_START = 31, USE_ROW_SLOT_END = 40;

    private final int[] blockPos;
    private final BlockAccess access;
    private final DataSlot selectedBannerPatternIndex = DataSlot.standalone();
    private List<String> selectablePatterns = List.of();
    private long lastSoundTime;
    private final Container inputContainer;
    private final Container outputContainer = new SimpleContainer(1);
    private final Slot bannerSlot, dyeSlot, patternSlot, resultSlot;

    public LoomMenu(int containerId, Container playerInventory, MenuPlayer owner, int[] blockPos, BlockAccess access) {
        super("minecraft:loom", containerId);
        this.blockPos = blockPos;
        this.access = access;
        this.inputContainer = new SimpleContainer(3) {
            @Override public void setChanged() {
                super.setChanged();
                slotsChanged(this);
            }
        };
        bannerSlot = addSlot(new Slot(inputContainer, 0) {
            @Override public boolean mayPlace(ItemStack stack) { return SpecialRecipes.banner(stack) >= 0; }
        });
        dyeSlot = addSlot(new Slot(inputContainer, 1) {
            @Override public boolean mayPlace(ItemStack stack) { return SpecialRecipes.dye(stack) >= 0; }
        });
        patternSlot = addSlot(new Slot(inputContainer, 2) {
            @Override public boolean mayPlace(ItemStack stack) { return patternTag(stack) != null; }
        });
        resultSlot = addSlot(new Slot(outputContainer, 0) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }

            @Override public void onTake(MenuPlayer player, ItemStack stack) {
                bannerSlot.remove(1);
                dyeSlot.remove(1);
                if (!bannerSlot.hasItem() || !dyeSlot.hasItem()) selectedBannerPatternIndex.set(-1);
                long now = access.gameTime();
                if (lastSoundTime != now) {
                    access.playSound("minecraft:ui.loom.take_result");
                    lastSoundTime = now;
                }
                super.onTake(player, stack);
            }
        });
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(playerInventory, c + r * 9 + 9));
        for (int c = 0; c < 9; c++) addSlot(new Slot(playerInventory, c));
        addDataSlot(selectedBannerPatternIndex);
    }

    /** 무늬 아이템(BannerPatternItem)이면 그 무늬 태그(pattern_item/<이름>), 아니면 null. */
    private static String patternTag(ItemStack s) {
        String n = ItemTools.itemName(s);
        if (!n.endsWith("_banner_pattern")) return null;
        return "minecraft:pattern_item/" + n.substring("minecraft:".length(), n.length() - "_banner_pattern".length());
    }

    public List<String> selectablePatterns() { return selectablePatterns; }

    public int selectedBannerPatternIndex() { return selectedBannerPatternIndex.get(); }

    @Override public int[] containerBlockPos() { return blockPos; }

    @Override public boolean stillValid(MenuPlayer player) {
        return access.stillBlock() && player.distanceToSqr(blockPos[0] + 0.5, blockPos[1] + 0.5, blockPos[2] + 0.5) <= 64.0;
    }

    @Override
    public boolean clickMenuButton(MenuPlayer player, int id) {
        if (id >= 0 && id < selectablePatterns.size()) {
            selectedBannerPatternIndex.set(id);
            setupResultSlot(selectablePatterns.get(id));
            return true;
        }
        return false;
    }

    private List<String> getSelectablePatterns(ItemStack pattern) {
        String tag = pattern.isEmpty() ? "minecraft:no_item_required" : patternTag(pattern);
        List<String> out = new ArrayList<>();
        if (tag == null) return out;
        if (tag.startsWith("minecraft:pattern_item/") || tag.equals("minecraft:no_item_required")) {
            for (int id : ItemContext.tagEntries("minecraft:banner_pattern", tag)) out.add(ItemContext.name("minecraft:banner_pattern", id));
        }
        return out;
    }

    private boolean isValidPatternIndex(int i) { return i >= 0 && i < selectablePatterns.size(); }

    private static int layerCount(ItemStack banner) {
        return ItemTools.component(banner, "minecraft:banner_patterns") instanceof List<?> l ? l.size() : 0;
    }

    @Override
    public void slotsChanged(Container container) {
        if (container != inputContainer) return;   // 바닐라는 slotsChanged 를 이렇게 덮어써 broadcastChanges 를 직접 부른다
        ItemStack banner = bannerSlot.getItem(), dye = dyeSlot.getItem(), pattern = patternSlot.getItem();
        if (!banner.isEmpty() && !dye.isEmpty()) {
            int selected = selectedBannerPatternIndex.get();
            boolean valid = isValidPatternIndex(selected);
            List<String> previous = selectablePatterns;
            selectablePatterns = getSelectablePatterns(pattern);
            String sel;
            if (selectablePatterns.size() == 1) {
                selectedBannerPatternIndex.set(0);
                sel = selectablePatterns.get(0);
            } else if (!valid) {
                selectedBannerPatternIndex.set(-1);
                sel = null;
            } else {
                String prev = previous.get(selected);
                int idx = selectablePatterns.indexOf(prev);
                if (idx != -1) {
                    sel = prev;
                    selectedBannerPatternIndex.set(idx);
                } else {
                    sel = null;
                    selectedBannerPatternIndex.set(-1);
                }
            }
            if (sel != null) {
                if (layerCount(banner) >= 6) {
                    selectedBannerPatternIndex.set(-1);
                    resultSlot.set(ItemStack.EMPTY);
                } else {
                    setupResultSlot(sel);
                }
            } else {
                resultSlot.set(ItemStack.EMPTY);
            }
            broadcastChanges();
        } else {
            resultSlot.set(ItemStack.EMPTY);
            selectablePatterns = List.of();
            selectedBannerPatternIndex.set(-1);
        }
    }

    private void setupResultSlot(String pattern) {
        ItemStack banner = bannerSlot.getItem(), dye = dyeSlot.getItem();
        ItemStack out = ItemStack.EMPTY;
        if (!banner.isEmpty() && !dye.isEmpty()) {
            out = banner.withCount(1);
            List<Object> layers = new ArrayList<>();
            if (ItemTools.component(banner, "minecraft:banner_patterns") instanceof List<?> l) layers.addAll(l);
            Map<String, Object> layer = new LinkedHashMap<>();
            layer.put("pattern", pattern);
            layer.put("color", SpecialRecipes.DYES.get(SpecialRecipes.dye(dye)));
            layers.add(layer);
            out = ItemTools.with(out, "minecraft:banner_patterns", layers);
        }
        if (!out.equals(resultSlot.getItem())) resultSlot.set(out);
    }

    @Override
    public ItemStack quickMoveStack(MenuPlayer player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return result;
        ItemStack stack = slot.getItem();
        result = stack;
        Moved moved;
        if (index == RESULT_SLOT) {
            moved = moveItemStackTo(stack, INV_SLOT_START, USE_ROW_SLOT_END, true);
            if (!moved.changed()) return ItemStack.EMPTY;
            slot.onQuickCraft(moved.remaining(), result);
        } else if (index == DYE_SLOT || index == BANNER_SLOT || index == PATTERN_SLOT) {
            moved = moveItemStackTo(stack, INV_SLOT_START, USE_ROW_SLOT_END, false);
            if (!moved.changed()) return ItemStack.EMPTY;
        } else if (SpecialRecipes.banner(stack) >= 0) {
            moved = moveItemStackTo(stack, BANNER_SLOT, BANNER_SLOT + 1, false);
            if (!moved.changed()) return ItemStack.EMPTY;
        } else if (SpecialRecipes.dye(stack) >= 0) {
            moved = moveItemStackTo(stack, DYE_SLOT, DYE_SLOT + 1, false);
            if (!moved.changed()) return ItemStack.EMPTY;
        } else if (patternTag(stack) != null) {
            moved = moveItemStackTo(stack, PATTERN_SLOT, PATTERN_SLOT + 1, false);
            if (!moved.changed()) return ItemStack.EMPTY;
        } else if (index >= INV_SLOT_START && index < INV_SLOT_END) {
            moved = moveItemStackTo(stack, USE_ROW_SLOT_START, USE_ROW_SLOT_END, false);
            if (!moved.changed()) return ItemStack.EMPTY;
        } else {
            moved = moveItemStackTo(stack, INV_SLOT_START, INV_SLOT_END, false);
            if (!moved.changed()) return ItemStack.EMPTY;
        }
        stack = moved.remaining();
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setCountInPlace(stack);
        if (stack.count() == result.count()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return result;
    }

    @Override
    public void removed(MenuPlayer player) {
        super.removed(player);
        clearContainer(player, inputContainer);
    }
}
