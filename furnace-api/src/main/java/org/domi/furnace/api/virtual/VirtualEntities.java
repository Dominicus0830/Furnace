// 가상 엔티티·가짜 플레이어(NPC)·가짜 탭 줄의 입구 — Furnace.server().virtualEntities().
// 패킷 API(Furnace.server().packets()) 위에 쌓은 것이다: 보이는 범위 추적, 플레이어별 보이기/숨기기, 월드 이동 뒤 다시 보내기,
// 클릭 잡기, 플러그인이 꺼질 때 정리를 이 서비스가 맡는다.
package org.domi.furnace.api.virtual;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.entity.ProfileProperty;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.world.Location;

public interface VirtualEntities {

    /** 가상 엔티티를 만든다. entityType 은 "minecraft:villager" 같은 이름("minecraft:player" 는 {@link #spawnPlayer} 로). */
    VirtualEntity spawn(FurnacePlugin owner, String entityType, Location location);

    /** 가짜 플레이어. name 은 프로필 이름(1~16자), skin 이 비면 기본 스킨. */
    VirtualPlayer spawnPlayer(FurnacePlugin owner, String name, List<ProfileProperty> skin, Location location);

    /** 그 플레이어의 가짜 탭 줄 손잡이. */
    VirtualTabList tabList(Player viewer);

    /** 번호로 찾는다(없으면 비어 있음). */
    Optional<VirtualEntity> byEntityId(int entityId);

    /** 그 플러그인이 만든 살아 있는 가상 엔티티. */
    Collection<VirtualEntity> entities(FurnacePlugin owner);
}
