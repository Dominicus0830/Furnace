// T-263: 형식 대응 표의 한 줄. 정적 메서드 (A)B 하나 — 연결 몸통이 인자·반환을 A 에서 B 로 바꿀 때 부른다(설계 3.2 G2).
// 바뀌지 않는 값 변환만(상태 읽기·쓰기 없음, 어느 스레드에서나).
package org.domi.furnace.compat.nms.link;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface LinkConvert {
}
