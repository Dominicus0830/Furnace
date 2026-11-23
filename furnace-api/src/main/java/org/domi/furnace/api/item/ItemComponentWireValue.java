// 컴포넌트 값 하나를 네트워크 바이트에서 읽은 결과.
package org.domi.furnace.api.item;

/**
 * @param snbt   저장 꼴 SNBT
 * @param length 읽은 바이트 수
 */
public record ItemComponentWireValue(String snbt, int length) {}
