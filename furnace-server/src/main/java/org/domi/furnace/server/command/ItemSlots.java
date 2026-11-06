// 바닐라 Entity.getSlot(int) → SlotAccess(/item·/loot replace entity). 번호는 SlotRanges 와 같다.
// 플레이어(Player.getSlot): 499 커서, 500..503 2x2 제작 칸, 0..35 인벤토리, 200..226 엔더 상자, 그 밖은 장비(LivingEntity.getSlot).
// 생물(LivingEntity.getSlot): 98 주손, 99 보조손, 100..103 방어구, 105 몸.
// 차이: 말·마을 주민·알레이·상자 보트·광산 수레·아이템 액자·아이템 디스플레이의 전용 칸은 아직 없다(SlotAccess.NULL 취급).
// 스레드: 플레이어 칸은 menuLock 안에서, 생물 칸은 그 엔티티를 가진 region 스레드에서 쓴다.
package org.domi.furnace.server.command;

import org.domi.furnace.server.entity.EquipmentSlot;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.LivingEntity;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.PlayerData;
import org.domi.furnace.server.entity.Rider;

public final class ItemSlots {
    private ItemSlots() {}

    /** 바닐라 SlotAccess. */
    public interface SlotAccess {
        ItemStack get();

        boolean set(ItemStack stack);
    }

    /** 칸이 없으면 null(SlotAccess.NULL). */
    public static SlotAccess of(Rider r, int slot) {
        if (r instanceof Player p) return player(p, slot);
        if (r instanceof LivingEntity le) {
            EquipmentSlot es = equipmentSlot(slot);
            if (es == null) return null;
            return new SlotAccess() {
                public ItemStack get() { return le.getItemBySlot(es); }

                public boolean set(ItemStack s) {
                    le.setItemSlot(es, s);
                    return true;
                }
            };
        }
        return null;
    }

    /** LivingEntity.getEquipmentSlot(int). */
    static EquipmentSlot equipmentSlot(int slot) {
        return switch (slot) {
            case 98 -> EquipmentSlot.MAINHAND;
            case 99 -> EquipmentSlot.OFFHAND;
            case 100 -> EquipmentSlot.FEET;
            case 101 -> EquipmentSlot.LEGS;
            case 102 -> EquipmentSlot.CHEST;
            case 103 -> EquipmentSlot.HEAD;
            case 105 -> EquipmentSlot.BODY;
            default -> null;
        };
    }

    private static SlotAccess window(Player p, int ws) {
        return new SlotAccess() {
            public ItemStack get() { return p.inventory().get(ws); }

            public boolean set(ItemStack s) {
                p.inventory().set(ws, s);
                return true;
            }
        };
    }

    private static SlotAccess player(Player p, int slot) {
        if (slot == 499) {
            return new SlotAccess() {
                private org.domi.furnace.server.menu.AbstractContainerMenu menu() {
                    var m = p.containerMenu();
                    return m != null ? m : p.inventoryMenu();
                }

                public ItemStack get() {
                    var m = menu();
                    return m == null ? ItemStack.EMPTY : m.getCarried();
                }

                public boolean set(ItemStack s) {
                    var m = menu();
                    if (m != null) m.setCarried(s);
                    return true;
                }
            };
        }
        int craft = slot - 500;
        if (craft >= 0 && craft < 4) {
            return new SlotAccess() {
                public ItemStack get() { return p.inventory().get(1 + craft); }

                public boolean set(ItemStack s) {
                    p.inventory().set(1 + craft, s);
                    var im = p.inventoryMenu();
                    if (im != null) im.slotsChanged(im.craftSlots());
                    return true;
                }
            };
        }
        if (slot >= 0 && slot < 36) return window(p, PlayerData.vanillaToWindowSlot(slot));
        int ender = slot - 200;
        if (ender >= 0 && ender < org.domi.furnace.server.entity.EnderChestInventory.SIZE) {
            return new SlotAccess() {
                public ItemStack get() { return p.enderChest().get(ender); }

                public boolean set(ItemStack s) {
                    p.enderChest().set(ender, s);
                    return true;
                }
            };
        }
        EquipmentSlot es = equipmentSlot(slot);
        if (es == null) return null;
        return switch (es) {
            case MAINHAND -> new SlotAccess() {
                public ItemStack get() { return p.inventory().get(p.inventory().heldWindowSlot()); }

                public boolean set(ItemStack s) {
                    p.inventory().set(p.inventory().heldWindowSlot(), s);
                    return true;
                }
            };
            case OFFHAND -> window(p, 45);
            case BODY -> new SlotAccess() {   // Player.getItemBySlot(BODY) 는 빈 칸, setItemSlot 은 아무것도 하지 않는다
                public ItemStack get() { return ItemStack.EMPTY; }

                public boolean set(ItemStack s) { return true; }
            };
            default -> window(p, PlayerData.vanillaToWindowSlot(slot));
        };
    }

    /** 플레이어 칸을 바꾼 뒤(바닐라 containerMenu.broadcastChanges). */
    public static void afterPlayerChange(Player p) {
        var menu = p.containerMenu();
        if (menu != null) menu.broadcastChanges();
    }
}
