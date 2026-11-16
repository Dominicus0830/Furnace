package org.domi.furnace.server;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 Furnace 클래스가 옮겨 온 바닐라 클래스(Mojang 이름, 1.21.1 기준)를 적는 소스 전용 표지.
 * 버전을 올릴 때 "바뀐 바닐라 클래스 → 고칠 Furnace 파일"을 찾는 대응표(tools/ci/vanilla-port-map.py)의 원본이다.
 *
 * <p>{@code SOURCE} 보존이라 클래스 파일에 남지 않는다(실행·크기 영향 0). 여러 바닐라 클래스를 합친 클래스는 값을 여러 개 적는다.
 * 중첩 클래스는 {@code 바깥.안쪽} 으로 적는다. 바닐라에 대응이 없는 Furnace 고유 클래스에는 붙이지 않는다.
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface VanillaPort {
    /** 바닐라 클래스의 완전한 이름(예: {@code net.minecraft.world.entity.animal.Fox}). */
    String[] value();
}
