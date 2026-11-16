package org.domi.furnace.server;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 클래스가 옮겨 온 바닐라 클래스(Mojang 이름, 중첩 클래스는 바깥 클래스까지만).
 * <p>
 * 바닐라 버전을 올릴 때 "바뀐 바닐라 클래스 → 고칠 Furnace 파일" 을 찾는 대응표의 원본이다.
 * 여러 바닐라 클래스를 합친 클래스는 값을 여러 개 적는다. 바닐라에 대응이 없는 Furnace 고유 클래스에는 붙이지 않는다.
 * <p>
 * 소스 전용이라 클래스 파일에 남지 않는다(실행·크기 영향 0). 표는 {@code tools/ci/vanilla-port-map.py} 가 만든다.
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface VanillaPort {
    String[] value();
}
