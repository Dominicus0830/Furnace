// T-263: 관용구 구현(설계 3.2 G1). 바닐라에서 같은 모양으로 반복되는 몸통 하나를 Furnace 쪽에서 한 번 구현한 정적 메서드.
// 인자 순서: [받는 쪽(인스턴스 멤버면)] + 연결 줄의 값들 + 멤버 인자. 관용구 구현은 손 코드라 독립 검토를 받는다(결정 D2).
package org.domi.furnace.compat.nms.link;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface LinkIdiom {
    /** 연결 줄에서 부르는 이름(@idiom(이름, …)). 호환 계층 전체에서 하나뿐이어야 한다. */
    String value();

    /** 검사 종류(LinkTarget#check 와 같은 값). */
    String check();

    /** read=owner·write 의 소유 규칙 클래스. */
    Class<?> owner() default Void.class;
}
