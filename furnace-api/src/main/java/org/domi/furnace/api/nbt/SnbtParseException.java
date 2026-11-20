// SNBT 문법 오류. position 은 오류가 난 글자 위치(0부터).
package org.domi.furnace.api.nbt;

public final class SnbtParseException extends IllegalArgumentException {

    private final int position;
    private final String reason;

    public SnbtParseException(String message, int position) { this(message, position, message); }

    /** reason 은 위치·문맥을 뺀 문구(예 "Expected key") — 바닐라 TagParser 오류 문구와 같다. */
    public SnbtParseException(String message, int position, String reason) {
        super(message);
        this.position = position;
        this.reason = reason;
    }

    public int position() { return position; }

    /** 위치·문맥을 뺀 문구. */
    public String reason() { return reason; }
}
