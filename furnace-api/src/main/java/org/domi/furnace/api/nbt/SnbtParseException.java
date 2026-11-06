// SNBT 문법 오류. position 은 오류가 난 글자 위치(0부터).
package org.domi.furnace.api.nbt;

public final class SnbtParseException extends IllegalArgumentException {

    private final int position;

    public SnbtParseException(String message, int position) {
        super(message);
        this.position = position;
    }

    public int position() { return position; }
}
