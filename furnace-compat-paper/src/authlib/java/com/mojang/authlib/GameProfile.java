package com.mojang.authlib;

import com.mojang.authlib.properties.PropertyMap;
import java.util.Objects;
import java.util.UUID;

/**
 * authlib GameProfile 의 컴파일·시험용 대역(배포 jar 에 넣지 않는다). 서버에서는 플러그인이 authlib 을 쓰면 받는 진짜 authlib 6.0.54 를 쓴다.
 * 호환 계층이 부르는 멤버만, 진짜와 같은 이름·서술자로 둔다: 생성자(UUID, String), getId·getName·getProperties.
 */
public class GameProfile {

    private final UUID id;
    private final String name;
    private final PropertyMap properties = new PropertyMap();

    public GameProfile(UUID id, String name) {
        this.id = Objects.requireNonNull(id, "Profile ID must not be null");
        this.name = Objects.requireNonNull(name, "Profile name must not be null");
    }

    public UUID getId() { return id; }

    public String getName() { return name; }

    public PropertyMap getProperties() { return properties; }

    @Override
    public String toString() { return "GameProfile[id=" + id + ", name=" + name + ", properties=" + properties + "]"; }
}
