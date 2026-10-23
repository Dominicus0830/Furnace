// 코덱이 값을 받지 못할 때(바닐라 DataResult.error 대응). 메시지는 가능한 한 바닐라 문구를 따른다.
package org.domi.furnace.server.item;

public final class CodecException extends RuntimeException {
    public CodecException(String message) { super(message, null, false, false); }
}
