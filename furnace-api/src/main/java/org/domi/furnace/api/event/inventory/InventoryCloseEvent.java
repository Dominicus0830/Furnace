// 창이 닫히기 직전(커서·제작 격자를 돌려주기 전) — 닫기를 처리하는 스레드에서 불린다. reason() 은 닫힌 까닭(Paper InventoryCloseEvent.Reason 과 같은 값).
package org.domi.furnace.api.event.inventory;

import java.util.Objects;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.inventory.InventorySnapshot;

public final class InventoryCloseEvent extends InventoryEvent {

    /** 창이 닫힌 까닭. */
    public enum Reason {
        /** 알 수 없음. */
        UNKNOWN,
        /** 플러그인 텔레포트(Player#teleport)가 닫았다. */
        TELEPORT,
        /** 더 쓸 수 없다(블록이 사라짐·너무 멂 등 stillValid 실패). */
        CANT_USE,
        /** 창이 매인 블록의 청크가 내려갔다. */
        UNLOADED,
        /** 다른 창을 열면서 닫았다. */
        OPEN_NEW,
        /** 플레이어(클라이언트)가 닫았다. */
        PLAYER,
        /** 접속이 끊겼다. */
        DISCONNECT,
        /** 죽었다. */
        DEATH,
        /** 플러그인이 닫았다(Player#closeMenu·closeContainer). */
        PLUGIN
    }

    private final Reason reason;

    public InventoryCloseEvent(Player player, InventorySnapshot inventory) { this(player, inventory, null, Reason.UNKNOWN); }

    /** menu: 닫히는 플러그인 메뉴. */
    public InventoryCloseEvent(Player player, InventorySnapshot inventory, org.domi.furnace.api.menu.Menu menu) { this(player, inventory, menu, Reason.UNKNOWN); }

    public InventoryCloseEvent(Player player, InventorySnapshot inventory, org.domi.furnace.api.menu.Menu menu, Reason reason) {
        super(player, inventory, menu);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Reason reason() { return reason; }
}
