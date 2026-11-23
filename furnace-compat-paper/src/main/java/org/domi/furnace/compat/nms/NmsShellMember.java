package org.domi.furnace.compat.nms;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 전체 껍데기(T-260) 표지: 서명표에서 만든 클래스의 멤버 가운데 동작이 아직 없는 것. 클래스 파일에만 남고(리플렉션에는 안 보인다)
 * {@link NmsSymbols} 가 이 표지가 붙은 멤버를 "없음"으로 보아 플러그인 호출 자리에 덫을 건다. 껍데기 생성기(shimgen FullShells)만 단다.
 * 클래스에 붙으면 그 클래스 자체가 껍데기다(없는 이벤트 처리기 건너뛰기 등). {@link #initTrap()} 이면 클래스 초기화가 던진다(NMS 밖 범위의 값 없는 필드).
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
public @interface NmsShellMember {
    /** 클래스 표지에서만: 정적 초기화가 {@code NmsShell.unsupportedClass} 로 던진다. */
    boolean initTrap() default false;
}
