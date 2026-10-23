// 전리품 JSON 을 읽다 생긴 오류(바닐라 Codec DataResult.error 에 해당).
package org.domi.furnace.server.loot;

public final class LootParseException extends RuntimeException {
    public LootParseException(String message) { super(message); }
}
