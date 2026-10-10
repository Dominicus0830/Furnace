// 이름 → 프로필 조회(바닐라 GameProfileRepository.findProfilesByNames). 온라인 모드에서는 Mojang API 구현이 꽂힌다.
package org.domi.furnace.server.players;

import java.util.Optional;

@FunctionalInterface
public interface ProfileLookup {

    ProfileLookup NONE = name -> Optional.empty();

    Optional<GameProfile> findProfileByName(String name);
}
