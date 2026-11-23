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

    /** 소유 검사 종류. 메서드 연결(->) 줄마다(손 몸통·빈 몸통 포함, 생성자 제외) 하나를 반드시 적는다. */
    enum Kind {
        /** 공유 상태 없음(값 계산). 검사 없음. */
        PURE("pure"),
        /** 어느 스레드든 읽어도 됨(불변·원자 값). 검사 없음. */
        READ_ANY("read=any"),
        /** 공개 스냅샷을 읽음(다른 스레드는 직전 틱 값). 검사 없음 — 대상이 스냅샷을 돌려준다. */
        READ_SNAPSHOT("read=snapshot"),
        /** 주인 스레드에서만 읽기. 몸통 첫 호출이 소유 규칙 클래스의 read(this, 이름) — 생성 몸통은 생성기가 넣고, 손 몸통은 빌드 시험이 본다. */
        READ_OWNER("read=owner"),
        /** 쓰기. 몸통 첫 호출이 소유 규칙 클래스의 write(this, 이름) — 생성 몸통은 생성기가 넣고, 손 몸통은 빌드 시험이 본다. */
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
        /** 빈 몸통(void 메서드·생성자). read=owner·write 면 소유 검사만 있는 몸통. */
        EMPTY,
        /** 필드 초기값(자바 식). */
        VALUE,
        /**
         * 상위 공통 연결(T-231): 바닐라가 추상인 메서드는 추상 그대로 두고, 이를 물려받는 생성 구체 클래스마다(스스로 잇지 않았으면)
         * 대상 정적 메서드로 위임하는 몸통을 만든다. 쓰기: {@code newBlockEntity -> @each(NmsBlockEntityClasses.newBlockEntity) read=any}.
         */
        EACH,
        /**
         * 엔티티 종류 표(E 줄)의 클래스마다 명세 없이 붙는 바닐라 생성자 {@code (EntityType, Level)}: 아직 월드에 없는 엔티티를 만든다
         * ({@code EntityType#create} 와 같은 객체 — 재료를 표지에 실어 생성기 전용 생성자로 넘긴다). 명세에 직접 쓰지 않는다.
         */
        FRESH
    }

    /** 엔티티 종류 클래스의 바닐라 생성자 서술자({@link Body#FRESH}). */
    static final String FRESH_CTOR_DESC = "(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)V";

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

    /** 종류 표(kind-classes-&lt;판&gt;.txt, T-231): 레지스트리 종류 → 바닐라 클래스. 줄: B|I|E 이름 클래스(E 는 엔티티 종류, T-232). 비어 있으면 표가 없다. */
    final List<String[]> kinds = new ArrayList<>();

    /** 종류 표 파일 이름 앞부분. 판마다 하나만 둔다(판 이름은 잠금 파일과 같다). */
    static final String KIND_TABLE_PREFIX = "kind-classes-";

    /** 폴더의 *.spec 전부(이름 순) + 종류 표. 종류 표의 클래스는 명세에 없으면 멤버 없는 class 줄로 더한다(instanceof·상속 사슬용 선언). */
    static ShimSpec readDir(Path dir) throws IOException {
        ShimSpec s = new ShimSpec();
        if (!Files.isDirectory(dir)) return s;
        try (Stream<Path> st = Files.list(dir)) {
            for (Path p : st.filter(x -> x.getFileName().toString().endsWith(".spec")).sorted().toList()) {
                s.parse(dir.relativize(p).toString(), Files.readString(p, StandardCharsets.UTF_8));
            }
        }
        List<Path> kindFiles;
        try (Stream<Path> st = Files.list(dir)) {
            kindFiles = st.filter(x -> { String f = x.getFileName().toString(); return f.startsWith(KIND_TABLE_PREFIX) && f.endsWith(".txt"); }).sorted().toList();
        }
        if (kindFiles.size() > 1) throw new IllegalArgumentException("more than one kind table (keep only the current version's): " + kindFiles);
        for (Path p : kindFiles) s.parseKinds(dir.relativize(p).toString(), Files.readString(p, StandardCharsets.UTF_8));
        return s;
    }

    void parseKinds(String file, String text) {
        String[] lines = text.split("\r?\n", -1);
        Map<String, Integer> firstLine = new LinkedHashMap<>();
        for (int n = 0; n < lines.length; n++) {
            String t = lines[n].strip();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String[] w = t.split("\\s+");
            if (w.length != 3 || !(w[0].equals("B") || w[0].equals("I") || w[0].equals("E"))) throw err(file, n + 1, "kind table lines are 'B|I|E <registry name> <class>': " + t);
            if (!w[2].startsWith("net.minecraft.")) throw err(file, n + 1, "kind classes are net.minecraft classes: " + w[2]);
            kinds.add(w);
            firstLine.putIfAbsent(w[2], n + 1);
        }
        for (Map.Entry<String, Integer> e : firstLine.entrySet()) {
            if (!classes.containsKey(e.getKey())) classes.put(e.getKey(), new Cls(e.getKey(), null, List.of(), file, e.getValue()));
        }
        // 엔티티 종류 클래스: 바닐라 생성자 (EntityType, Level) — 플러그인이 new Shulker(EntityType.SHULKER, level) 처럼 직접 만든다
        for (String[] k : kinds) {
            if (!k[0].equals("E")) continue;
            Cls c = classes.get(k[2]);
            boolean has = false;
            for (Member m : c.members()) if (m.name().equals("<init>") && FRESH_CTOR_DESC.equals(m.desc())) has = true;
            if (has) continue;
            List<Member> ms = new ArrayList<>(c.members());
            ms.add(new Member("<init>", FRESH_CTOR_DESC, Body.FRESH, null, null, file, firstLine.get(k[2])));
            classes.put(k[2], new Cls(c.name(), c.owner(), List.copyOf(ms), c.file(), c.line()));
        }
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
        else if (target.startsWith("@each(") && target.endsWith(")")) {
            body = Body.EACH;
            String inner = target.substring(6, target.length() - 1);
            int dot = inner.lastIndexOf('.');
            if (dot <= 0) throw err(file, no, "write the @each target as Class.method: " + target);
            try {
                tgt = resolve(imports, inner.substring(0, dot)) + "." + inner.substring(dot + 1);
            } catch (IllegalArgumentException e) {
                throw err(file, no, e.getMessage());
            }
        }
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
        // 메서드 연결 줄은 전부(손 몸통·빈 몸통 포함) 검사 종류를 적는다 — 생성 몸통은 생성기가, 손 몸통은 빌드 시험(ShimSpecBuildTest)이
        // read=owner·write 의 첫 호출이 소유 검사인지 본다. 생성자는 아직 주인이 없으니 적지 않는다.
        if (name.equals("<init>")) {
            if (kind != null) throw err(file, no, "constructors take no check kind (the object has no owner yet): " + t);
        } else if (kind == null) {
            throw err(file, no, "a method line needs a check kind (pure, read=any, read=snapshot, read=owner, write), hand bodies too: " + t);
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
