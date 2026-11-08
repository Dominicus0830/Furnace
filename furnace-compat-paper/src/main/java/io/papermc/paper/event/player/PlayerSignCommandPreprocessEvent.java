package io.papermc.paper.event.player;

import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * 플레이어가 표지판을 눌러 run_command 클릭이 명령을 돌리기 직전(Paper 1.21.1 과 같다). {@link PlayerCommandPreprocessEvent} 의 하위라서
 * 그 이벤트의 처리기도 받는다 — 명령 기록·보안 플러그인이 표지판 명령을 보고 막을 수 있다. 취소하면 돌리지 않는다.
 * 명령은 표지판 소스(권한 2)로 돈다 — 플레이어가 평소 못 치는 명령도 표지판으로는 돈다.
 *
 * <p>Furnace 와 다른 점: Bukkit {@code BlockState} 가 아직 없어 Paper 의 {@code getSign()} 은 없다(면은 {@link #getSide()} 로 알 수 있다).
 */
public class PlayerSignCommandPreprocessEvent extends PlayerCommandPreprocessEvent {

    private final Side side;

    public PlayerSignCommandPreprocessEvent(Player player, String message, Side side) {
        super(player, message);
        this.side = side;
    }

    /** 명령이 나온 표지판의 면. */
    public Side getSide() { return side; }
}
