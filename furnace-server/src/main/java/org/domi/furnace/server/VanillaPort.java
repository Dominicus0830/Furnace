// SPDX-License-Identifier: GPL-3.0-only
package org.domi.furnace.server;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 Furnace 클래스가 옮겨 온 바닐라(Mojang 이름) 클래스. 버전업 때 "바뀐 바닐라 클래스 → 고칠 Furnace 파일" 을 찾는 대응표(V-06)의 원본이다.
 * 소스에서만 읽고(SOURCE) 클래스 파일에는 남지 않아 실행·크기에 영향이 없다. 값은 바닐라 클래스의 전체 이름이고, 안쪽 클래스는 {@code $} 로 잇는다.
 * 여러 바닐라 클래스를 합쳐 옮겼으면 값을 여러 개 쓴다. 바닐라에 없는 Furnace 전용 클래스에는 붙이지 않는다.
 * 대응표는 {@code python3 tools/ci/vanilla-port-map.py} 가 소스를 훑어 만든다(docs/roadmap/vanilla-port-map.md).
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE})
public @interface VanillaPort {
    /** 바닐라 클래스 전체 이름(예: {@code net.minecraft.world.entity.animal.Fox}). */
    String[] value();
}
