// 폐기된 API 표식. 대체 API 를 먼저 내고, 폐기한 뒤에도 최소 두 마이너 버전은 남긴다.
// 서버는 폐기된 이벤트 클래스에 처리기가 등록되면 한 번 경고한다.
package org.domi.furnace.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR})
public @interface DeprecatedApi {

    /** 폐기를 알린 API 버전("1.2"). */
    String since();

    /** 이 버전 이후 지울 수 있다("1.4"). since 보다 minor 가 둘 이상 커야 한다. */
    String removeIn();

    /** 대신 쓸 API(없으면 빈 문자열). */
    String replacement() default "";
}
