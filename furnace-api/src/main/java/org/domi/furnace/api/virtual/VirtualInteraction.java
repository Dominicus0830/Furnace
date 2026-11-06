// 가상 엔티티를 클릭했을 때의 정보.
package org.domi.furnace.api.virtual;

import org.domi.furnace.api.entity.Player;

public record VirtualInteraction(Player player, VirtualEntity entity, boolean attack, boolean sneaking) {

    /** 우클릭(상호작용). */
    public boolean isInteract() { return !attack; }
}
