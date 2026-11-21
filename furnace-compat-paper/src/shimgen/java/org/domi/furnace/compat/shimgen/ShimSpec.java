// T-229·T-232: 연결 명세(src/shimspec/*.spec) 읽개. 사람이 쓰는 글이고 판과 무관하다 — 서술자·상속·플래그는 잠금 파일(shim-<판>.lock)이 서명표에서 채운다.
// 형식 전문: furnace-compat-paper/src/shimspec/README.md
package org.domi.furnace.compat.shimgen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class ShimSpec {

    /** 소유 검사 종류. 위임(->) 줄마다 하나를 반드시 적는다. */
    enum Kind {
        /** 공유 상태 없음(값 계산). 검사 없음. */
        PURE("pure"),
        /** 어느 스레드든 읽어도 됨(불변·원자 값). 검사 없음. */
        READ_ANY("read=any"),
        /** 공개 스냅샷을 읽음(다른 스레드는 직전 틱 값). 검사 없음 — 대상이 스냅샷을 돌려준다. */
        READ_SNAPSHOT("read=snapshot"),
        /** 주인 스레드에서만 읽기. 위임 전에 소유 규칙 클래스의 read(this, 이름) 를 부른다. */
        READ_OWNER("read=owner"),
        /** 쓰기. 위임 전에 소유 규칙 클래스의 write(this, 이름) 를 부른다. */
        WRITE("write");

        final String token;

        Kind(String token) { this.token = token; }

        static Kind of(String token) {
            for (Kind k : values()) if (k.token.equals(token)) return k;
            return null;
        }

        boolean checked() { return this == READ_OWNER || this == WRITE; }
    }

    /** 몸통 종류. */
    enum Body {
        /** 선언만: 바닐라가 추상인 메서드(추상 선언), 초기값 없는 필드, 열거형 상수. */
        DECLARE,
        /** 정적 위임: 대상 클래스의 정적 메서드(인스턴스면 this 를 첫 인자로). */
        DELEGATE,
        /** 같은 클래스의 다른 메서드로(바닐라 별칭). */
        SELF,
        /** 손 조각(src/shims/java)이 몸통을 쓴다. 생성기는 아무것도 쓰지 않고 시험이 있는지 본다. */
        HAND,
        /** 핫 경로 맞춤 설계(설계 4.6) — 손 조각이 쓰고, 근거 설계 문서를 적는다. */
        FAST,
        /** 빈 몸통(void 메서드·생성자). */
        EMPTY,
        /** 필드 초기값(자바 식). */
        VALUE
    }

    /** 멤버 줄. desc 는 적힌 만큼(없으면 null, 메서드는 "(…)" 만 적어도 된다). */
    record Member(String name, String desc, Body body, String target, Kind kind, String file, int line) {
        String where() { return file + ":" + line; }

        /** 필드 줄인가(이름 뒤 ':' 서술자, '=' 값, 또는 열거형 상수·필드 선언처럼 괄호 없이 적은 줄 — 잠금 파일에서 판정). */
        boolean fieldDesc() { return desc != null && !desc.startsWith("("); }
    }

    /** class 줄과 그 아래 멤버들. */
    record Cls(String name, String owner, List<Member> members, String file, int line) {
        String where() { return file + ":" + line; }
    }

    final Map<String, Cls> classes = new LinkedHashMap<>();

    /** 폴더의 *.spec 전부(이름 순). */
    static ShimSpec readDir(Path dir) throws IOException {
        ShimSpec s = new ShimSpec();
        if (!Files.isDirectory(dir)) return s;
        try (Stream<Path> st = Files.list(dir)) {
            for (Path p : st.filter(x -> x.getFileName().toString().endsWith(".spec")).sorted().toList()) {
                s.parse(dir.relativize(p).toString(), Files.readString(p, StandardCharsets.UTF_8));
            }
        }
        return s;
    }

    private static final Pattern NAME = Pattern.compile("[A-Za-z_$<][A-Za-z0-9_$<>]*");

    void parse(String file, String text) {
        Map<String, String> imports = new LinkedHashMap<>();
        String[] lines = text.split("\r?\n", -1);
        String curName = null;
        String curOwner = null;
        List<Member> cur = null;
        int curLine = 0;
        for (int n = 0; n < lines.length; n++) {
            String raw = lines[n];
            String line = stripComment(raw);
            if (line.isBlank()) continue;
            int no = n + 1;
            boolean indented = Character.isWhitespace(raw.charAt(0));
            String t = line.strip();
            if (!indented) {
                if (cur != null) { put(new Cls(curName, curOwner, List.copyOf(cur), file, curLine)); cur = null; }
                if (t.startsWith("import ")) {
                    String fq = t.substring(7).strip();
                    imports.put(fq.substring(fq.lastIndexOf('.') + 1), fq);
                    continue;
                }
                if (!t.startsWith("class ")) throw err(file, no, "only import or class lines start at column 0: " + t);
                String[] w = t.substring(6).strip().split("\\s+");
                curName = w[0];
                if (!curName.startsWith("net.minecraft.")) throw err(file, no, "the spec covers net.minecraft classes only: " + curName);
                curOwner = null;
                for (int i = 1; i < w.length; i++) {
                    if (w[i].startsWith("owner=")) {
                        try {
                            curOwner = resolve(imports, w[i].substring(6));
                        } catch (IllegalArgumentException e) {
                            throw err(file, no, e.getMessage());
                        }
                    }
                    else throw err(file, no, "unknown class attribute: " + w[i]);
                }
                cur = new ArrayList<>();
                curLine = no;
                continue;
            }
            if (cur == null) throw err(file, no, "member line before any class line: " + t);
            cur.add(member(imports, t, file, no));
        }
        if (cur != null) put(new Cls(curName, curOwner, List.copyOf(cur), file, curLine));
    }

    private void put(Cls c) {
        Cls prev = classes.put(c.name(), c);
        if (prev != null) throw new IllegalArgumentException(c.where() + ": the same class appears twice (first at " + prev.where() + "): " + c.name());
        Map<String, Member> seen = new LinkedHashMap<>();
        for (Member m : c.members()) {
            String key = m.name() + (m.desc() == null ? "" : m.desc());
            Member p = seen.put(key, m);
            if (p != null) throw new IllegalArgumentException(m.where() + ": the same member appears twice (first at " + p.where() + "): " + key);
        }
    }

    private static Member member(Map<String, String> imports, String t, String file, int no) {
        // 이름[서술자]
        Matcher nm = NAME.matcher(t);
        if (!nm.lookingAt()) throw err(file, no, "no member name: " + t);
        String name = nm.group();
        int i = nm.end();
        String desc = null;
        if (i < t.length() && t.charAt(i) == '(') {
            int close = t.indexOf(')', i);
            if (close < 0) throw err(file, no, "unclosed descriptor parenthesis: " + t);
            int end = close + 1;
            while (end < t.length() && !Character.isWhitespace(t.charAt(end)) && t.charAt(end) != '=') end++;
            desc = t.substring(i, end);
            i = end;
        } else if (i < t.length() && t.charAt(i) == ':') {
            int end = i + 1;
            while (end < t.length() && !Character.isWhitespace(t.charAt(end))) end++;
            desc = t.substring(i + 1, end);
            i = end;
        }
        String rest = t.substring(i).strip();
        if (rest.isEmpty()) return new Member(name, desc, Body.DECLARE, null, null, file, no);
        if (rest.startsWith("=")) {
            String expr = rest.substring(1).strip();
            if (expr.isEmpty()) throw err(file, no, "no value (Java expression) after =: " + t);
            if (expr.equals("@hand")) return new Member(name, desc, Body.HAND, null, null, file, no);
            return new Member(name, desc, Body.VALUE, expr, null, file, no);
        }
        if (!rest.startsWith("->")) throw err(file, no, "after the name write -> target, = value, or nothing: " + t);
        String[] w = rest.substring(2).strip().split("\\s+");
        String target = w[0];
        Kind kind = null;
        for (int k = 1; k < w.length; k++) {
            Kind kk = Kind.of(w[k]);
            if (kk == null) throw err(file, no, "unknown attribute: " + w[k] + " (pure, read=any, read=snapshot, read=owner, write)");
            if (kind != null) throw err(file, no, "only one check kind per line: " + t);
            kind = kk;
        }
        Body body;
        String tgt = null;
        if (target.equals("@hand")) body = Body.HAND;
        else if (target.equals("@empty")) body = Body.EMPTY;
        else if (target.startsWith("@fast(") && target.endsWith(")")) {
            body = Body.FAST;
            tgt = target.substring(6, target.length() - 1);
            if (tgt.isBlank()) throw err(file, no, "@fast needs the design note path: " + t);
        } else if (target.startsWith("this.")) {
            body = Body.SELF;
            tgt = target.substring(5);
        } else {
            int dot = target.lastIndexOf('.');
            if (dot <= 0) throw err(file, no, "write the delegate target as Class.method: " + target);
            body = Body.DELEGATE;
            try {
                tgt = resolve(imports, target.substring(0, dot)) + "." + target.substring(dot + 1);
            } catch (IllegalArgumentException e) {
                throw err(file, no, e.getMessage());
            }
        }
        if ((body == Body.DELEGATE || body == Body.SELF) && kind == null) {
            throw err(file, no, "a delegating line needs a check kind (pure, read=any, read=snapshot, read=owner, write): " + t);
        }
        if ((body == Body.HAND || body == Body.FAST || body == Body.EMPTY) && kind != null) {
            throw err(file, no, "check kinds go on delegating lines only (hand bodies do their own checks): " + t);
        }
        return new Member(name, desc, body, tgt, kind, file, no);
    }

    private static String resolve(Map<String, String> imports, String name) {
        if (name.contains(".")) return name;
        String fq = imports.get(name);
        if (fq == null) throw new IllegalArgumentException("name not imported: " + name + " (add an import line or use the full name)");
        return fq;
    }

    /** '#' 뒤는 주석(따옴표 안의 '#' 은 그대로). */
    static String stripComment(String line) {
        boolean q = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"' && (i == 0 || line.charAt(i - 1) != '\\')) q = !q;
            if (c == '#' && !q) return line.substring(0, i);
        }
        return line;
    }

    private static IllegalArgumentException err(String file, int line, String msg) {
        return new IllegalArgumentException(file + ":" + line + ": " + msg);
    }
}
