// 이 Furnace 클래스가 이식한 바닐라 클래스 표시. 소스 전용(클래스 파일에 남지 않아 실행·크기 영향 0).
// 버전업 때 "바뀐 바닐라 클래스 → 고칠 Furnace 파일"을 찾는 대응표의 원본이다. 대응표 생성: tools/vanilla-port/vanilla-port-map.py
package org.domi.furnace.server;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface VanillaPort {
    /** 바닐라(Mojang 이름) 클래스의 전체 이름. 바깥 클래스 하나가 아니라 합친 경우 여러 개. 안쪽 클래스는 {@code Outer$Inner}. */
    String[] value();
}
