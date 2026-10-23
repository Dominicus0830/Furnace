// 마크업 해석 오류 — 잘못된 태그 인자, strict 모드의 닫히지 않은/어긋난 태그 등. 가능하면 원문 위치를 담는다.
package org.domi.furnace.api.text.markup;

public class MarkupParseException extends IllegalArgumentException {

    private final String markup;
    private final int position;

    public MarkupParseException(String message) { this(message, null, -1); }

    /** @param position 원문에서의 위치(모르면 -1) */
    public MarkupParseException(String message, String markup, int position) {
        super(message);
        this.markup = markup;
        this.position = position;
    }

    /** 오류가 난 원문(모르면 null). */
    public String markup() { return markup; }

    /** 원문에서의 위치(모르면 -1). */
    public int position() { return position; }

    MarkupParseException withLocation(String markup, int position) {
        return this.markup != null ? this : new MarkupParseException(getMessage(), markup, position);
    }

    @Override
    public String getMessage() {
        String m = super.getMessage();
        if (markup == null) return m;
        return position >= 0 ? m + " (위치 " + position + "): " + markup : m + ": " + markup;
    }
}
