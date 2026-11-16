// Paper BeaconDeactivatedEvent: 신호기가 꺼졌을 때(블록이 지워지거나 피라미드가 무너짐). Furnace 는 아직 서버 스스로 부르지 않고(신호기 틱의 활성 바뀜 이벤트는 없다),
// 플러그인이 부른다 — FAWE 가 편집으로 지운 신호기마다 끄는 소리와 함께 부른다(T-198).
package io.papermc.paper.event.block;

import org.bukkit.Material;
import org.bukkit.block.Beacon;
import org.bukkit.block.Block;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockEvent;

public class BeaconDeactivatedEvent extends BlockEvent {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    public BeaconDeactivatedEvent(Block block) { super(block); }

    /** 그 칸이 아직 신호기면 그 상태, 아니면 null(Paper 와 같다 — 지운 뒤에 부르면 null). */
    public Beacon getBeacon() { return block.getType() == Material.BEACON ? (Beacon) block.getState() : null; }

    @Override
    public HandlerList getHandlers() { return HANDLER_LIST; }

    public static HandlerList getHandlerList() { return HANDLER_LIST; }
}
