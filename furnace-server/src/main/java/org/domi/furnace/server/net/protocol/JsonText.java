// 상태/로그인 Disconnect 등에 쓰는 최소 JSON 문자열 이스케이프(제어문자 포함, RFC 8259).
package org.domi.furnace.server.net.protocol;

public final class JsonText {

    private JsonText() {}

    /** 따옴표를 포함한 JSON 문자열 리터럴. */
    public static String quote(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }

    /** {"text":"..."} 텍스트 컴포넌트 JSON. */
    public static String textComponent(String text) {
        return "{\"text\":" + quote(text) + "}";
    }
}
