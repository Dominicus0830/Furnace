// 엔티티 상호작용(우클릭·공격). 가상 엔티티(NPC) 클릭을 잡을 때 쓴다 — 서버에 없는 번호여도 이 패킷은 온다.
// hand 는 ATTACK 이면 null. target 좌표(엔티티 기준 상대 위치)는 INTERACT_AT 일 때만 뜻이 있다.
package org.domi.furnace.api.packet.serverbound;

import java.util.Objects;
import org.domi.furnace.api.entity.Hand;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.ServerboundPacket;

public record ServerboundInteractPacket(int entityId, Action action, Hand hand, float targetX, float targetY, float targetZ, boolean sneaking)
        implements ServerboundPacket {

    public enum Action { INTERACT, ATTACK, INTERACT_AT }

    public ServerboundInteractPacket {
        Objects.requireNonNull(action, "action");
        if (action == Action.ATTACK) hand = null;
        else Objects.requireNonNull(hand, "hand");
    }

    public static ServerboundInteractPacket attack(int entityId, boolean sneaking) {
        return new ServerboundInteractPacket(entityId, Action.ATTACK, null, 0, 0, 0, sneaking);
    }

    public static ServerboundInteractPacket interact(int entityId, Hand hand, boolean sneaking) {
        return new ServerboundInteractPacket(entityId, Action.INTERACT, hand, 0, 0, 0, sneaking);
    }

    public boolean isAttack() { return action == Action.ATTACK; }

    @Override
    public PacketType<ServerboundInteractPacket> type() { return PacketTypes.Serverbound.INTERACT; }
}
