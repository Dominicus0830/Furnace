// 플러그인이 이벤트를 취소했을 때 클라이언트를 서버 상태로 되돌리는 코어 도우미(docs/api/event-cancel-contract.md).
// 규칙: 취소하면 상태 변경이 전혀 없고, 클라이언트가 먼저 예측했을 수 있는 것(칸·블록·능력·위치)은 코어가 되돌려 보낸다. 플러그인은 아무것도 하지 않는다.
// 어느 스레드에서 불러도 된다(패킷만 보내고, 인벤토리 읽기는 칸마다 잠겨 있다).
package org.domi.furnace.server.plugin;

import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.entity.BlockEntity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.net.protocol.play.BlockEntityDataPacket;
import org.domi.furnace.server.net.protocol.play.BlockUpdate;
import org.domi.furnace.server.net.protocol.play.ContainerSetSlot;
import org.domi.furnace.server.net.protocol.play.SetHeldSlot;

public final class ClientResync {

    private ClientResync() {}

    /** 창 칸(0..45) 하나를 서버 값으로 다시 보낸다. 열린 창과 무관하게 플레이어 인벤토리 자체를 고치는 패킷(창 -2)이라 어느 창이 열려 있어도 된다. */
    public static void slot(Player p, int windowSlot) {
        int index = PlayerInventory.inventoryIndex(windowSlot);
        if (index < 0) {   // 제작 결과·제작 칸은 인벤토리 번호가 없다 — 인벤토리 창(0)의 칸으로
            p.send(new ContainerSetSlot(0, p.inventoryMenu() == null ? 0 : p.inventoryMenu().getStateId(), windowSlot, p.inventory().get(windowSlot)));
            return;
        }
        p.send(new ContainerSetSlot(-2, 0, index, p.inventory().get(windowSlot)));
    }

    /** 손(0 주 손, 1 보조 손)의 칸. */
    public static void hand(Player p, int hand) {
        slot(p, hand == 0 ? p.inventory().heldWindowSlot() : 45);
    }

    /** 두 손. */
    public static void hands(Player p) {
        hand(p, 0);
        hand(p, 1);
    }

    /** 열린 창(없으면 인벤토리 창)의 모든 칸과 커서를 다시 보낸다 — Bukkit updateInventory. */
    public static void inventory(Player p) {
        synchronized (p.menuLock()) {
            var menu = p.containerMenu() != null ? p.containerMenu() : p.inventoryMenu();
            if (menu != null) menu.sendAllDataToRemote();   // 연결이 없는 시험용 플레이어는 창이 없다
        }
    }

    /** 단축 칸 선택을 서버 값으로. */
    public static void heldSlot(Player p) {
        p.send(new SetHeldSlot(p.inventory().selected()));
    }

    /** 비행·무적·속도 같은 능력 상태를 서버 값으로. */
    public static void abilities(Player p) {
        p.send(p.vitals().abilitiesPacket());
    }

    /** 블록 하나를 서버 값으로 — 블록 상태와, 갱신 패킷이 있는 블록 엔티티(표지판 글·배너 무늬·머리 등)까지. */
    public static void block(Player p, BlockLevel level, Pos pos) {
        p.send(new BlockUpdate(pos.x(), pos.y(), pos.z(), level.getBlockState(pos)));
        if (level.getBlockEntity(pos) instanceof BlockEntity be && be.hasUpdatePacket()) {
            p.send(new BlockEntityDataPacket(pos.x(), pos.y(), pos.z(), be.typeId(), be.updateTag()));
        }
    }

    /**
     * 취소된 드롭을 원래 든 칸으로 돌려놓는다(CraftBukkit): 칸이 비었거나 같은 아이템이 합쳐질 만큼 비었으면 거기에, 아니면 못 넣고 그대로 돌려준다
     * (남은 것은 부르는 쪽이 첫 빈 칸에 넣는다).
     */
    public static ItemStack returnToSlot(Player p, int windowSlot, ItemStack stack) {
        PlayerInventory inv = p.inventory();
        synchronized (inv) {
            ItemStack cur = inv.get(windowSlot);
            if (cur.isEmpty()) {
                inv.set(windowSlot, stack);
                return ItemStack.EMPTY;
            }
            int max = org.domi.furnace.server.item.ItemStackCodecs.maxStackSize(cur);
            if (cur.sameItemSameComponents(stack) && cur.count() + stack.count() <= max) {
                inv.set(windowSlot, cur.withCount(cur.count() + stack.count()));
                return ItemStack.EMPTY;
            }
        }
        return stack;
    }
}
