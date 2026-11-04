// 발전 과제를 막 달성했다(보상을 주기 직전) — 발전 과제를 준 스레드에서 불린다(보통 플레이어 region, /advancement 는 명령 스레드).
// 채팅 알림 문구를 바꾸거나 null 로 알리지 않을 수 있다. 알림이 꺼진 과제(announce_to_chat 거짓·게임 규칙)는 message() 가 처음부터 null.
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.text.Text;

public final class PlayerAdvancementDoneEvent extends PlayerEvent {

    private final String advancement;
    private Text message;

    public PlayerAdvancementDoneEvent(Player player, String advancement, Text message) {
        super(player);
        this.advancement = advancement;
        this.message = message;
    }

    /** 발전 과제 id(minecraft:story/mine_stone 등). */
    public String advancement() { return advancement; }

    public Text message() { return message; }

    public void setMessage(Text message) { this.message = message; }
}
