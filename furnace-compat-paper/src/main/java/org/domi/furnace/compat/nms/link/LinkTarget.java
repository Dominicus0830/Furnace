// T-263: 연결 추론기가 껍데기 멤버를 이을 수 있는 대상(다리). 정적 메서드에 단다 — 인스턴스 멤버를 이을 때는 받는 쪽(this)이 첫 인자로 간다.
// 검사 종류는 추론기가 짐작하지 않고 이 표지에서 가져온다(설계 docs/roadmap/link-inferrer-t263-2026-11-23.md 3.3).
package org.domi.furnace.compat.nms.link;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface LinkTarget {
    /** 검사 종류: pure, read=any, read=snapshot, read=owner, write, api(대상이 스스로 Bukkit 규칙대로 주인에게 넘김). */
    String check();

    /** read=owner·write 의 소유 규칙 클래스(static read/write(Object, String)). 그 밖에는 비워 둔다. */
    Class<?> owner() default Void.class;

    /** 이 대상이 바꾸는가(api 의 읽기·쓰기 구별 — 쓰기는 자동 채택하지 않는다). write 는 늘 true 로 본다. */
    boolean mutates() default false;
}
