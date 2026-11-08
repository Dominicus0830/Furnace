// 바닐라 Entity.getSlot(int) → SlotAccess(/item·/loot replace entity). 번호는 SlotRanges 와 같다.
// 플레이어(Player.getSlot): 499 커서, 500..503 2x2 제작 칸, 0..35 인벤토리, 200..226 엔더 상자, 그 밖은 장비(LivingEntity.getSlot).
// 생물(LivingEntity.getSlot): 98 주손, 99 보조손, 100..103 방어구, 105 몸. 마을 주민·방랑 상인(AbstractVillager.getSlot): 300..307 가방.
// 용기 엔티티(상자 배·상자/깔때기 수레, ContainerEntity.getChestVehicleSlot): 0..칸 수-1 — 읽고 쓸 때 열지 않은 전리품 표를 먼저 푼다.
// 말(AbstractHorse.getSlot): 400 안장(빈 것·안장만), 500.. 인벤토리 1.., 상자 단 말(AbstractChestedHorse): 499 상자. 그 밖은 장비.
// 약탈자 가방 300..304, 아이템 엔티티·액자·디스플레이·화살·던진 아이템은 contents(0) 한 칸.
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
        if (r instanceof org.domi.furnace.server.entity.Villager || r instanceof org.domi.furnace.server.entity.WanderingTrader) {
            var inv = ((org.domi.furnace.server.entity.InventoryCarrier) r).getInventory();
            int i = slot - 300;
            if (i >= 0 && i < inv.size()) {
                return new SlotAccess() {
                    public ItemStack get() { return inv.get(i); }

                    public boolean set(ItemStack s) {
                        inv.set(i, s);
                        return true;
                    }
                };
            }
        }
        if (r instanceof org.domi.furnace.server.entity.Pillager pl) {
            int i = slot - 300;
            if (i >= 0 && i < pl.inventorySize()) {   // SlotAccess.forContainer(inventory, i)
                return new SlotAccess() {
                    public ItemStack get() { return pl.inventoryItem(i); }

                    public boolean set(ItemStack s) {
                        pl.setInventoryItem(i, s);
                        return true;
                    }
                };
            }
        }
        // 아이템 엔티티·아이템 액자·아이템 디스플레이·화살·던진 아이템: 칸 0(contents)만 SlotAccess.of(getItem, setItem)
        if (slot == 0) {
            if (r instanceof org.domi.furnace.server.entity.ItemEntity e) return item(e::getItem, e::setItem);
            if (r instanceof org.domi.furnace.server.entity.ItemFrame f) return item(f::getItem, f::setItem);
            if (r instanceof org.domi.furnace.server.entity.Display.ItemDisplay d) return item(d::getItem, d::setItem);
            if (r instanceof org.domi.furnace.server.entity.AbstractArrow a) return item(a::pickupItemStack, a::setPickupItemStack);
            if (r instanceof org.domi.furnace.server.entity.ThrowableProjectile t) return item(t::getItem, t::setItem);
        }
        if (r instanceof org.domi.furnace.server.entity.ContainerEntity ce) {
            var c = new org.domi.furnace.server.world.EntityContainer(ce);
            if (slot < 0 || slot >= ce.chestVehicleItems().length) return null;
            return new SlotAccess() {
                public ItemStack get() { return c.get(slot); }

                public boolean set(ItemStack s) {
                    c.set(slot, s);   // setChestVehicleItem: 표를 풀고 넣은 뒤 칸 최대 개수로 자른다
                    return true;
                }
            };
        }
        if (r instanceof org.domi.furnace.server.entity.AbstractHorse h) {
            SlotAccess a = horse(h, slot);
            if (a != null) return a;
        }
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

    /** SlotAccess.of(getter, setter). */
    private static SlotAccess item(java.util.function.Supplier<ItemStack> g, java.util.function.Consumer<ItemStack> st) {
        return new SlotAccess() {
            public ItemStack get() { return g.get(); }

            public boolean set(ItemStack s) {
                st.accept(s);
                return true;
            }
        };
    }

    /** AbstractHorse·AbstractChestedHorse.getSlot: 400 안장, 499 상자(상자 단 말만), 500+i 인벤토리 i+1. 아니면 null(장비로 넘어간다). */
    private static SlotAccess horse(org.domi.furnace.server.entity.AbstractHorse h, int slot) {
        if (slot == 400) {
            return new SlotAccess() {
                public ItemStack get() { return h.inventoryContainer().get(0); }

                public boolean set(ItemStack s) {
                    if (!s.isEmpty() && !h.isSaddleItem(s)) return false;
                    h.inventoryContainer().set(0, s);   // setItem → containerChanged(안장 상태·소리)
                    return true;
                }
            };
        }
        if (slot == 499 && h instanceof org.domi.furnace.server.entity.AbstractChestedHorse ch) {
            return new SlotAccess() {
                public ItemStack get() {
                    return ch.hasChest() ? new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", "chest"), 1) : ItemStack.EMPTY;
                }

                public boolean set(ItemStack s) { return ch.setChestFromSlot(s); }
            };
        }
        int i = slot - 500 + 1;
        if (i >= 1 && i < h.getInventorySize()) {
            return new SlotAccess() {   // SlotAccess.forContainer(inventory, i) — 지금 인벤토리 배열을 그때그때 본다
                public ItemStack get() { return h.inventoryContainer().get(i); }

                public boolean set(ItemStack s) {
                    var c = h.inventoryContainer();
                    int max = Math.min(c.maxStackSize(), org.domi.furnace.server.menu.Stacks.maxStack(s));
                    c.set(i, !s.isEmpty() && s.count() > max ? s.withCount(max) : s);   // SimpleContainer.setItem: limitSize 뒤 setChanged
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
