package org.domi.furnace.compat.nms;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 껍데기 참조 틀 표지(설계 docs/roadmap/shell-refs-and-field-redirect-2026-11-27.md 2장). 명세 {@code ref} 줄이 만든 컴파일용 클래스에만 붙는다:
 * 손 코드가 전체 껍데기(T-260)에만 있는 형식을 이름으로 쓰게 모양만 내고, 실행 때는 앞에 실린 껍데기 jar 의 진짜 껍데기가 이 클래스를 가린다.
 * 그래서 실행 중인 서버에서 이 표지가 달린 클래스가 로더에서 나오면 순서가 깨진 것이다 — {@link NmsShellRefCheck} 가 기동 때 본다.
 * 클래스 파일에만 남는다(리플렉션에는 안 보인다).
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface NmsShellRef {
}
