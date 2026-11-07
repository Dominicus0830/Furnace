// 표준 서비스: 권한 제공자(권한 플러그인이 올린다). 소비자는 ServiceRegistry.load(PermissionService.class) 로 얻는다.
// 코어의 PermissionManager(노드 등록)·Permissible(판정)과 별개로, 오프라인 플레이어까지 UUID 로 묻는 그룹/노드 보기다.
package org.domi.furnace.api.service;

import java.util.List;
import java.util.UUID;

public interface PermissionService {

    /** 제공자 이름(예: "MyPerms"). */
    String name();

    /** 그 플레이어가 노드를 가졌는가(접속 중이 아니어도). 구현이 막힐 수 있으니 region 스레드에서는 메모리 캐시 구현을 쓴다. */
    boolean has(UUID player, String node);

    /** 노드를 값으로 정한다(개인 권한). 지원하지 않으면 false. */
    default boolean set(UUID player, String node, boolean value) { return false; }

    /** 개인 권한을 지운다. 지원하지 않으면 false. */
    default boolean unset(UUID player, String node) { return false; }

    List<String> groups(UUID player);

    default String primaryGroup(UUID player) {
        List<String> g = groups(player);
        return g.isEmpty() ? null : g.get(0);
    }

    default boolean inGroup(UUID player, String group) {
        for (String g : groups(player)) if (g.equalsIgnoreCase(group)) return true;
        return false;
    }

    default boolean addToGroup(UUID player, String group) { return false; }

    default boolean removeFromGroup(UUID player, String group) { return false; }

    /** 채팅 접두사·접미사(없으면 빈 문자열). */
    default String prefix(UUID player) { return ""; }

    default String suffix(UUID player) { return ""; }
}
