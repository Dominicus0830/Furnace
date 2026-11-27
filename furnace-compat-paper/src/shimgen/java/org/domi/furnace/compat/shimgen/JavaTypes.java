// T-229: JVM 서술자·제네릭 서명 문자열을 자바 소스 글로 바꾼다(shim 생성기가 선언 머리·멤버 서명을 쓸 때).
// 서명 문법은 JVMS 4.7.9.1. 이름은 언제나 완전 이름(가져오기 없이)으로 쓴다 — 손 조각의 import 와 부딪치지 않게.
package org.domi.furnace.compat.shimgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

final class JavaTypes {
    private JavaTypes() {}

    /** 내부 이름(net/minecraft/core/Direction$Axis) → 소스 이름(net.minecraft.core.Direction.Axis). */
    /** "A, B<C, D>, E" 처럼 쉼표로 이은 형식 목록을 꺾쇠 밖 쉼표로 가른다(공백은 다듬는다). */
    static List<String> splitTypeList(String list) {
        List<String> out = new java.util.ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < list.length(); i++) {
            char ch = list.charAt(i);
            if (ch == '<') depth++;
            else if (ch == '>') depth--;
            else if (ch == ',' && depth == 0) { out.add(list.substring(start, i).trim()); start = i + 1; }
        }
        String last = list.substring(start).trim();
        if (!last.isEmpty()) out.add(last);
        return out;
    }

    static String source(String internalName) {
        return internalName.replace('/', '.').replace('$', '.');
    }

    /** 내부 이름의 단순 이름(Direction$Axis → Axis). */
    static String simple(String internalName) {
        int s = Math.max(internalName.lastIndexOf('/'), internalName.lastIndexOf('$'));
        return internalName.substring(s + 1);
    }

    /** 서술자 하나(필드 형식) → 소스 글. */
    static String descType(String desc) {
        return new Parser(desc).fieldOrBase();
    }

    /** 메서드 서술자의 인자 형식들(소스 글). */
    static List<String> descParams(String methodDesc) {
        Parser p = new Parser(methodDesc);
        p.expect('(');
        List<String> out = new ArrayList<>();
        while (p.peek() != ')') out.add(p.fieldOrBase());
        return out;
    }

    /** 메서드 서술자의 반환 형식(소스 글). */
    static String descReturn(String methodDesc) {
        Parser p = new Parser(methodDesc.substring(methodDesc.indexOf(')') + 1));
        return p.fieldOrBase();
    }

    /** 서술자·서명에 나오는 클래스 내부 이름 전부(형식 인자·경계 안쪽 포함, 안쪽 클래스는 바깥$안쪽). ASM 서명 읽개로 훑는다. */
    static Set<String> referencedClasses(String descOrSignature) {
        Set<String> out = new TreeSet<>();
        if (descOrSignature == null) return out;
        SignatureVisitor v = new SignatureVisitor(Opcodes.ASM9) {
            String cur;

            @Override
            public void visitClassType(String name) {
                cur = name;
                out.add(name);
            }

            @Override
            public void visitInnerClassType(String name) {
                cur = cur + '$' + name;
                out.add(cur);
            }
        };
        SignatureReader r = new SignatureReader(descOrSignature);
        char c = descOrSignature.charAt(0);
        if (c == '(' || c == '<' && descOrSignature.contains("(")) r.accept(v);   // 메서드 서술자·서명
        else if (c == '<') r.accept(v);                                             // 클래스 서명(형식 매개변수부터)
        else if (isClassSignature(descOrSignature)) r.accept(v);
        else r.acceptType(v);
        return out;
    }

    /** 메서드 서명이 형식 변수를 쓰나(상속받은 추상 메서드의 던지는 몸통을 만들 때 형식 인자를 갈아 끼울 수 없어 생성을 거절한다). */
    static boolean usesTypeVariables(String methodSignature) {
        if (methodSignature == null) return false;
        boolean[] hit = {false};
        new SignatureReader(methodSignature).accept(new SignatureVisitor(Opcodes.ASM9) {
            @Override
            public void visitTypeVariable(String name) { hit[0] = true; }
        });
        return hit[0];
    }

    /** 형식 하나가 아니라 "상위 + 인터페이스" 가 이어진 클래스 서명인가(형식 하나를 다 읽고도 글이 남는다). */
    private static boolean isClassSignature(String s) {
        try {
            Parser p = new Parser(s);
            p.typeSig();
            return !p.end();
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ───────────────────────── 제네릭 서명 ─────────────────────────

    /** 클래스 서명을 쪼갠 결과: 형식 매개변수 글(없으면 ""), 상위, 인터페이스들. */
    record ClassSig(String typeParams, String superType, List<String> interfaces) {}

    static ClassSig classSignature(String sig) {
        Parser p = new Parser(sig);
        String tp = p.peek() == '<' ? p.typeParams() : "";
        String sup = p.fieldType();
        List<String> itf = new ArrayList<>();
        while (!p.end()) itf.add(p.fieldType());
        return new ClassSig(tp, sup, itf);
    }

    /** 메서드 서명을 쪼갠 결과. */
    record MethodSig(String typeParams, List<String> params, String ret) {}

    static MethodSig methodSignature(String sig) {
        Parser p = new Parser(sig);
        String tp = p.peek() == '<' ? p.typeParams() : "";
        p.expect('(');
        List<String> params = new ArrayList<>();
        while (p.peek() != ')') params.add(p.typeSig());
        p.expect(')');
        String ret = p.typeSig();
        // 던짐(^…)은 서명표에 예외 표가 없어 쓰지 않는다
        return new MethodSig(tp, params, ret);
    }

    /** 필드 서명(형식 하나). */
    static String fieldSignature(String sig) {
        return new Parser(sig).typeSig();
    }

    /** 가변 인자 메서드의 마지막 인자 글: 배열 [] 를 ... 로. */
    static String varargs(String paramType) {
        if (!paramType.endsWith("[]")) throw new IllegalArgumentException("varargs slot is not an array: " + paramType);
        return paramType.substring(0, paramType.length() - 2) + "...";
    }

    private static final class Parser {
        final String s;
        int i;

        Parser(String s) { this.s = s; }

        boolean end() { return i >= s.length(); }

        char peek() { return s.charAt(i); }

        void expect(char c) {
            if (s.charAt(i) != c) throw new IllegalArgumentException("cannot parse signature (expected " + c + ", at " + i + "): " + s);
            i++;
        }

        String ident() {
            int st = i;
            while (i < s.length() && ":;<>/.".indexOf(s.charAt(i)) < 0) i++;
            return s.substring(st, i);
        }

        String typeParams() {
            expect('<');
            List<String> ps = new ArrayList<>();
            while (peek() != '>') {
                String name = ident();
                expect(':');
                List<String> bounds = new ArrayList<>();
                if (peek() != ':' && peek() != '>' ) {
                    // 클래스 경계(비어 있을 수 있음 — 인터페이스 경계만 있을 때)
                    if ("LT[".indexOf(peek()) >= 0) bounds.add(fieldType());
                }
                while (!end() && peek() == ':') {
                    i++;
                    bounds.add(fieldType());
                }
                bounds.removeIf(b -> b.equals("java.lang.Object"));
                ps.add(bounds.isEmpty() ? name : name + " extends " + String.join(" & ", bounds));
            }
            expect('>');
            return "<" + String.join(", ", ps) + ">";
        }

        String typeSig() {
            char c = peek();
            if ("BCDFIJSZV".indexOf(c) >= 0) { i++; return base(c); }
            return fieldType();
        }

        String fieldOrBase() { return typeSig(); }

        String fieldType() {
            char c = peek();
            switch (c) {
                case 'L': return classType();
                case 'T': {
                    i++;
                    String n = ident();
                    expect(';');
                    return n;
                }
                case '[': {
                    i++;
                    return typeSig() + "[]";
                }
                default:
                    if ("BCDFIJSZ".indexOf(c) >= 0) { i++; return base(c); }
                    throw new IllegalArgumentException("cannot parse signature (expected a type, at " + i + "): " + s);
            }
        }

        String classType() {
            expect('L');
            StringBuilder out = new StringBuilder();
            int st = i;
            while (peek() != ';' && peek() != '<' && peek() != '.') i++;
            out.append(source(s.substring(st, i)));
            if (peek() == '<') out.append(typeArgs());
            while (peek() == '.') {
                i++;
                int st2 = i;
                while (peek() != ';' && peek() != '<' && peek() != '.') i++;
                out.append('.').append(s, st2, i);
                if (peek() == '<') out.append(typeArgs());
            }
            expect(';');
            return out.toString();
        }

        String typeArgs() {
            expect('<');
            List<String> as = new ArrayList<>();
            while (peek() != '>') {
                char c = peek();
                if (c == '*') { i++; as.add("?"); }
                else if (c == '+') { i++; as.add("? extends " + fieldType()); }
                else if (c == '-') { i++; as.add("? super " + fieldType()); }
                else as.add(fieldType());
            }
            expect('>');
            return "<" + String.join(", ", as) + ">";
        }

        static String base(char c) {
            return switch (c) {
                case 'B' -> "byte";
                case 'C' -> "char";
                case 'D' -> "double";
                case 'F' -> "float";
                case 'I' -> "int";
                case 'J' -> "long";
                case 'S' -> "short";
                case 'Z' -> "boolean";
                case 'V' -> "void";
                default -> throw new IllegalArgumentException("not a primitive: " + c);
            };
        }
    }
}
