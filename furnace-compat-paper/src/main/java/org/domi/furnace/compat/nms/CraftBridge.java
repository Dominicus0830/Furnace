package org.domi.furnace.compat.nms;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * T-264 P3 핸들 다리 표지: 껍데기 생성기(shimgen ShellMerge)가 CraftBukkit 클래스에 덧붙인 "상위 접근자 + 반환형 좁힘" 메서드(예: 종류별
 * {@code getHandle()}). 진짜 동작이라 {@link NmsShellMember} 가 아니다 — 상위 접근자의 값이 Paper 반환형이면 그대로, 아니면 껍데기와 같은 오류로 던진다.
 * 클래스 파일에만 남고 보고서·시험(CraftBridgeTest)이 센다.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface CraftBridge {
}
