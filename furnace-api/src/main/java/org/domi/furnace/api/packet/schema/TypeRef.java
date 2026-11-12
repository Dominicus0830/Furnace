// 스키마 필드 타입 식(Java 모양): 이름·타입 인자·배열 차원·와일드카드. 이름은 스키마에 적힌 그대로(단순 이름·Outer.Inner)다.
package org.domi.furnace.api.packet.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record TypeRef(String name, List<TypeRef> arguments, int arrayDimensions, Bound bound) {

    /** 와일드카드 경계. NONE 이면 보통 타입, 나머지는 {@code ?}·{@code ? extends T}·{@code ? super T}(이때 name 은 경계 타입). */
    public enum Bound { NONE, WILDCARD, EXTENDS, SUPER }

    public TypeRef {
        Objects.requireNonNull(name, "name");
        arguments = List.copyOf(arguments);
        Objects.requireNonNull(bound, "bound");
        if (arrayDimensions < 0) throw new IllegalArgumentException("arrayDimensions < 0");
    }

    /** 기본형(int·long·…·boolean)인가 — 배열이면 원소 기준. */
    public boolean isPrimitive() {
        return switch (name) {
            case "int", "long", "short", "byte", "float", "double", "boolean", "char" -> bound == Bound.NONE;
            default -> false;
        };
    }

    /** 배열 한 겹을 벗긴 원소 타입. */
    public TypeRef component() {
        if (arrayDimensions == 0) throw new IllegalStateException("배열이 아니다: " + this);
        return new TypeRef(name, arguments, arrayDimensions - 1, bound);
    }

    /** 스키마에 쓰는 모양으로 되돌린다. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        switch (bound) {
            case WILDCARD -> { return "?"; }
            case EXTENDS -> sb.append("? extends ");
            case SUPER -> sb.append("? super ");
            default -> { }
        }
        sb.append(name);
        if (!arguments.isEmpty()) {
            sb.append('<');
            for (int i = 0; i < arguments.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(arguments.get(i));
            }
            sb.append('>');
        }
        sb.append("[]".repeat(arrayDimensions));
        return sb.toString();
    }

    /** 타입 식 하나를 읽는다. 예: {@code Map<ResourceKey<? extends Registry<?>>,TagNetworkSerialization.NetworkPayload>}, {@code byte[]}. */
    public static TypeRef parse(String text) {
        Parser p = new Parser(text.replace(" ", ""));
        TypeRef t = p.type();
        if (p.pos != p.s.length()) throw new IllegalArgumentException("타입 식 끝에 남은 글자: " + text);
        return t;
    }

    private static final class Parser {
        final String s;
        int pos;

        Parser(String s) { this.s = s; }

        TypeRef type() {
            if (peek('?')) {
                pos++;
                if (s.startsWith("extends", pos)) { pos += 7; return withBound(type(), Bound.EXTENDS); }
                if (s.startsWith("super", pos)) { pos += 5; return withBound(type(), Bound.SUPER); }
                return new TypeRef("?", List.of(), 0, Bound.WILDCARD);
            }
            int start = pos;
            while (pos < s.length() && (Character.isJavaIdentifierPart(s.charAt(pos)) || s.charAt(pos) == '.')) pos++;
            if (start == pos) throw new IllegalArgumentException("타입 이름이 없다(" + pos + "): " + s);
            String name = s.substring(start, pos);
            List<TypeRef> args = new ArrayList<>();
            if (peek('<')) {
                pos++;
                args.add(type());
                while (peek(',')) { pos++; args.add(type()); }
                expect('>');
            }
            int dims = 0;
            while (s.startsWith("[]", pos)) { pos += 2; dims++; }
            return new TypeRef(name, args, dims, Bound.NONE);
        }

        static TypeRef withBound(TypeRef t, Bound b) { return new TypeRef(t.name(), t.arguments(), t.arrayDimensions(), b); }

        boolean peek(char c) { return pos < s.length() && s.charAt(pos) == c; }

        void expect(char c) {
            if (!peek(c)) throw new IllegalArgumentException("'" + c + "' 가 와야 한다(" + pos + "): " + s);
            pos++;
        }
    }
}
