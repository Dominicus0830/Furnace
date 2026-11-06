// 게임 프로필 속성(스킨 "textures" 등). signature 는 없어도 되지만 없으면 클라이언트가 스킨을 거부할 수 있다.
package org.domi.furnace.api.entity;

import java.util.Objects;

public record ProfileProperty(String name, String value, String signature) {

    public ProfileProperty {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
    }

    /** 스킨 속성("textures"): value 는 Base64 텍스처 JSON, signature 는 Mojang 서명. */
    public static ProfileProperty textures(String value, String signature) { return new ProfileProperty("textures", value, signature); }
}
