package org.domi.furnace.compat.nms;

/**
 * NMS 값 객체가 없는 데이터 컴포넌트 종류의 값: 저장 꼴 SNBT 그대로. 바닐라는 종류마다 값 클래스(BundleContents 등)가 있지만 shim 은
 * 그 클래스를 두지 않아, 값을 {@code streamCodec()} 으로 읽고 쓰거나 패치 항목({@code DataComponentPatch.entrySet()})으로 넘기는 일만 한다.
 */
public record RawComponentValue(String snbt) {}
