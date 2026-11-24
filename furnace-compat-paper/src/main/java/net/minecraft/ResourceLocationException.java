package net.minecraft;

/** 잘못된 리소스 위치(Mojang 이름 shim, H7b). 바닐라와 같은 RuntimeException 하위이며 메시지는 바닐라처럼 자바 문자열 이스케이프(비 ASCII 는 \\uXXXX 대문자)를 거친다. */
public class ResourceLocationException extends RuntimeException {

    public ResourceLocationException(String message) { super(escapeJava(message)); }

    public ResourceLocationException(String message, Throwable cause) { super(escapeJava(message), cause); }

    /** commons-lang3 StringEscapeUtils.escapeJava 와 같은 규칙: 따옴표·역슬래시·\b\n\t\f\r 은 짧은 꼴, 0x20 미만과 0x7f 이상은 \\uXXXX(대문자). null 은 null. */
    private static String escapeJava(String s) {
        if (s == null) return null;
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\b' -> b.append("\\b");
                case '\n' -> b.append("\\n");
                case '\t' -> b.append("\\t");
                case '\f' -> b.append("\\f");
                case '\r' -> b.append("\\r");
                default -> {
                    if (c < 32 || c > 0x7f) b.append(String.format("\\u%04X", (int) c)); else b.append(c);
                }
            }
        }
        return b.toString();
    }
}
