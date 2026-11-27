// 이식 다리 생성물 표지 — 서버의 server.port.gen 패키지에 손 코드가 섞이지 않았는지 빌드 시험이 본다(설계 12장, 허용 목록 안 "가").
package org.domi.furnace.api.internal.port;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 이식 생성기(portgen)가 만든 클래스에만 붙는다. value 는 만든 명세 파일 이름. */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface PortGenerated {
    String value();
}
