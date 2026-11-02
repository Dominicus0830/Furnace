// 관전자의 teleport_to_entity 패킷(바닐라 handleTeleportToEntityPacket): 대상 엔티티의 레벨·위치·회전으로 옮긴다.
// /tp 와 같은 옮기기 경로(Teleports)를 쓴다 — 플레이어는 연결의 보정 텔레포트, 다른 차원이면 차원 변경.
package org.domi.furnace.server.command.builtin;

import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.entity.Player;

public final class SpectatorTeleports {
    private SpectatorTeleports() { }

    public static void toPlayer(ServerCore core, Player spectator, Player target) {
        CommandSource source = CommandSource.player(core, spectator);
        Teleports.teleport(source, spectator, target.dimension(), target.x(), target.y(), target.z(), target.yaw(), target.pitch())
                .exceptionally(t -> null);   // 대상이 사라졌거나 소유 region 을 못 찾으면 조용히 끝(바닐라도 실패 시 아무 일 없음)
    }
}
