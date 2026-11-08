package org.bukkit.event.player;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

/**
 * 플레이어가 엔티티의 한 점을 오른쪽 클릭했다(interact 패킷의 INTERACT_AT — 갑옷 거치대 장비 바꾸기). 처리기 목록은 따로다.
 * Furnace 는 INTERACT_AT 이벤트를 내지 않아 이 이벤트는 아직 불리지 않는다(리스너 클래스가 이 이벤트를 써도 올라가게 클래스만 둔다).
 */
public class PlayerInteractAtEntityEvent extends PlayerInteractEntityEvent {

    private static final HandlerList handlers = new HandlerList();

    private final Vector position;

    public PlayerInteractAtEntityEvent(Player who, Entity clickedEntity, Vector position) { this(who, clickedEntity, position, EquipmentSlot.HAND); }

    public PlayerInteractAtEntityEvent(Player who, Entity clickedEntity, Vector position, EquipmentSlot hand) {
        super(who, clickedEntity, hand);
        this.position = position;
    }

    /** 엔티티 발밑을 원점으로 한 클릭 위치(사본). */
    public Vector getClickedPosition() { return position.clone(); }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
