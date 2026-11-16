// 플레이어 프로필 값(UUID, 이름, 속성). 서버의 사용자 캐시나 세션 서버에서 찾은 결과를 플러그인에 주는 값 클래스다.
package org.domi.furnace.api.entity;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @param id         UUID
 * @param name       이름
 * @param properties 속성(세션 서버가 준 스킨 "textures" 등. 순서를 보존한다). 사용자 캐시에서 찾은 값은 비어 있다.
 */
public record ProfileData(UUID id, String name, List<ProfileProperty> properties) {

    public ProfileData {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        properties = List.copyOf(properties);
    }

    public ProfileData(UUID id, String name) { this(id, name, List.of()); }
}
