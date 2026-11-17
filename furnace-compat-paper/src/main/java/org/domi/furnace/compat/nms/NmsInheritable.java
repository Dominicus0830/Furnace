package org.domi.furnace.compat.nms;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 플러그인이 상속해도 되는 NMS shim 클래스 표시(T-204 V5). 다른 NMS shim 의 상속은 등급 C 껍데기다({@link NmsLinker}) — 이 표시가 붙은 shim 은
 * 바닐라처럼 재정의한 가상 메서드로 답하도록 만들었으므로(놓기 상태 다리가 문맥·레벨 값을 그 객체의 메서드로 읽는다) 플러그인 하위 클래스를 그대로
 * 정의한다. 바닐라에 있는데 shim 에 없는 상위 생성자를 부르는 생성자만 그 자리에서 UnsupportedNmsLink 를 던진다(예: FAWE 레벨 프록시는 생성자 대신
 * Unsafe 로 만든다 — Paper 에서도 필드는 null). 이 표시를 붙이는 shim 은 메서드의 final 여부가 바닐라와 같아야 한다(재정의가 정의를 깨지 않게).
 * 클래스 파일에서만 읽는다(ASM — {@link NmsSymbols}).
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface NmsInheritable {}
