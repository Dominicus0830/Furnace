// T-263: 연결 파일(src/shimspec/links-<판>/<영역>.links) — 동작 없는 껍데기 멤버를 이미 있는 호환 계층 기능에 잇는 줄. 설계 docs/roadmap/link-inferrer-t263-2026-11-23.md 3.5.
// 전체 껍데기 생성기(FullShells)·합치기(ShellMerge)가 던지는 몸통 대신 연결 몸통을 바이트코드로 쓴다: [소유 검사] → [인자 변환] → 대상 → [반환 변환] → 반환.
// 리플렉션·invokedynamic 없음. 연결 몸통엔 @NmsShellMember 가 없어 연결기가 "있음"으로 본다.
//
// 줄 문법(명세 줄과 같은 꼴, 서술자는 늘 적음):
//   #furnace-links 1 <판> area=<영역 번호>
//   class <a.b.C>                       공개 클래스만(중첩은 안쪽 접근이 공개·보호)
//     <이름><서술자> -> <대상> <검사> [via=…] [proof=…] [body=…] [target=…]
//   이름: 공개·보호 멤버는 평문, private·패키지 멤버는 #<SHA-256("a.b.C.이름") 앞 10자리> (검토 R1 — entity_data/ids 와 같은 지문)
//   대상: a.b.D.m (@LinkTarget 정적 메서드) | this.m (같은 사슬의 살아 있는 멤버, 별칭) | @idiom(이름, 값…) (@LinkIdiom)
//   값: 정수·실수(F·D 접미)·"글"·true/false · 공개 정적 필드 a.b.C.F · private 정적 필드 #지문
//   검사: pure | read=any | read=snapshot | read=owner | write | api
// 어긴 줄은 생성을 멈춘다. 쓰이지 않은 줄(멤버가 이미 손·명세·채움으로 살아 있음, 표에 없음)도 멈춘다 — "낡은 줄"은 지운다.
package org.domi.furnace.compat.shimgen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.domi.furnace.tools.extract.SigTable;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

final class Links {
    static final String MAGIC = "#furnace-links";
    static final int FORMAT = 1;
    static final String TARGET_ANN = "Lorg/domi/furnace/compat/nms/link/LinkTarget;";
    static final String CONVERT_ANN = "Lorg/domi/furnace/compat/nms/link/LinkConvert;";
    static final String IDIOM_ANN = "Lorg/domi/furnace/compat/nms/link/LinkIdiom;";
    /** 어댑터 경계(docs/ops/adapter-boundary.md): 연결 대상·변환·관용구는 호환 계층과 furnace-api 코드뿐 — 서버 클래스를 바로 부르는 몸통은 만들지 않는다. */
    static final List<String> TARGET_PREFIXES = List.of("org/domi/furnace/compat/", "org/domi/furnace/api/");

    static boolean targetAllowed(String internalName) {
        for (String p : TARGET_PREFIXES) if (internalName.startsWith(p)) return true;
        return false;
    }

    enum Check {
        PURE("pure"), READ_ANY("read=any"), READ_SNAPSHOT("read=snapshot"), READ_OWNER("read=owner"), WRITE("write"), API("api");

        final String text;

        Check(String text) { this.text = text; }

        boolean owned() { return this == READ_OWNER || this == WRITE; }

        static Check of(String s) {
            for (Check c : values()) if (c.text.equals(s)) return c;
            return null;
        }
    }

    /** 살아 있는 메서드: 선언 클래스와 접근 값. */
    record Live(String owner, int access) {}

    sealed interface Target permits StaticTarget, Alias, Idiom {}
    record StaticTarget(String owner, String name) implements Target {}
    record Alias(String name) implements Target {}
    record Idiom(String name, List<String> values) implements Target {}

    /** 줄 하나. cls 는 내부 이름, member 는 평문 이름 또는 #지문. */
    record Line(String where, String cls, String member, String desc, Target target, Check check, Map<String, String> attrs) {
        String text() { return cls.replace('/', '.') + "#" + member + desc; }
    }

    /** 연결 몸통이 보는 세계: 서명표·호환 계층 클래스·상속 관계·멤버가 살아 있는지. FullShells 가 준다. */
    interface World {
        SigTable.Cls paperClass(String n);
        List<SigTable.Member> paperMembers(String n);
        /** 호환 계층(·JDK) 클래스 바이트, 없으면 null. */
        byte[] classBytes(String n);
        /** 바로 위 형식(상위 클래스 + 인터페이스). 모르면 빈 목록. */
        List<String> supertypes(String n);
        /** 그 클래스(사슬 포함)에서 이 메서드를 찾아 동작이 있으면(껍데기·추상·연결 아님) 선언 클래스와 접근 값, 아니면 null. */
        Live liveMethod(String owner, String name, String desc);
        /** 정적 필드가 값이 있는가(손 shim·상수·채움). */
        boolean liveStaticField(String owner, String name, String desc);
        /** 표지(@LinkIdiom·@LinkConvert)를 찾을 호환 계층 클래스 이름들. */
        Iterable<String> compatClasses();
    }

    final List<Line> lines = new ArrayList<>();
    /** 풀린 줄: 클래스 → (이름+서술자) → 줄. */
    private final Map<String, Map<String, Line>> resolved = new TreeMap<>();
    private final Set<Line> consumed = new HashSet<>();
    /** 대상이 안 맞아 문제를 적은 줄 — "쓰이지 않은 줄"로 두 번 알리지 않는다. */
    private final Set<Line> failed = new HashSet<>();
    final List<String> problems = new ArrayList<>();
    private World world;
    private Map<String, String[]> idioms;      // 이름 → {소유, 이름, 서술자, 검사, 소유 규칙}
    private Map<String, String[]> converters;  // "A>B" → {소유, 이름, 서술자}
    private Map<String, String[]> staticFieldFps;   // 지문 → {소유, 이름, 서술자}
    int linkedMembers;

    // ───────────────────────── 읽기 ─────────────────────────

    /** 폴더의 *.links 를 이름 순으로 읽는다. 폴더가 없으면 빈 연결. */
    static Links read(Path dir) throws IOException {
        Links l = new Links();
        if (dir == null || !Files.isDirectory(dir)) return l;
        try (var s = Files.list(dir)) {
            for (Path p : s.filter(x -> x.toString().endsWith(".links")).sorted().toList()) {
                l.parse(p.getFileName().toString(), Files.readAllLines(p, StandardCharsets.UTF_8));
            }
        }
        return l;
    }

    void parse(String file, List<String> text) {
        String cls = null;
        boolean header = false;
        for (int i = 0; i < text.size(); i++) {
            String raw = text.get(i).replace("\r", "");
            String where = file + ":" + (i + 1);
            if (i == 0) {
                String[] h = raw.trim().split("\\s+");
                if (h.length < 3 || !h[0].equals(MAGIC) || !h[1].equals(String.valueOf(FORMAT))) {
                    problems.add(where + ": first line must be '" + MAGIC + " " + FORMAT + " <version> area=<n>'");
                    return;
                }
                header = true;
                continue;
            }
            // 주석은 "# " 로 시작하는 줄이나 줄 끝의 " # …" — 지문(#ab12…)은 # 뒤에 빈칸이 없어 주석이 아니다
            String s = raw.strip();
            if (s.equals("#") || s.startsWith("# ")) s = "";
            int hash = outsideQuotes(s, " # ");
            if (hash >= 0) s = s.substring(0, hash).strip();
            if (s.isEmpty()) continue;
            if (s.startsWith("class ")) {
                cls = s.substring(6).trim().replace('.', '/');
                continue;
            }
            if (cls == null) {
                problems.add(where + ": member line before any class line");
                continue;
            }
            Line ln = parseMember(where, cls, s);
            if (ln != null) lines.add(ln);
        }
        if (!header) problems.add(file + ": empty links file");
    }

    private Line parseMember(String where, String cls, String s) {
        int arrow = s.indexOf("->");
        if (arrow < 0) {
            problems.add(where + ": member line needs '->'");
            return null;
        }
        String left = s.substring(0, arrow).trim();
        int p = left.indexOf('(');
        if (p <= 0) {
            problems.add(where + ": member needs a full descriptor: " + left);
            return null;
        }
        String member = left.substring(0, p), desc = left.substring(p);
        try {
            Type.getMethodType(desc);
        } catch (RuntimeException e) {
            problems.add(where + ": bad descriptor " + desc);
            return null;
        }
        String right = s.substring(arrow + 2).trim();
        Target target;
        String rest;
        if (right.startsWith("@idiom(")) {
            int close = outsideQuotes(right, ")");
            if (close < 0) {
                problems.add(where + ": unclosed @idiom(");
                return null;
            }
            List<String> parts = splitArgs(right.substring(7, close));
            if (parts.isEmpty() || parts.get(0).isEmpty()) {
                problems.add(where + ": @idiom needs a name");
                return null;
            }
            target = new Idiom(parts.get(0), List.copyOf(parts.subList(1, parts.size())));
            rest = right.substring(close + 1).trim();
        } else {
            int sp = right.indexOf(' ');
            String t = sp < 0 ? right : right.substring(0, sp);
            rest = sp < 0 ? "" : right.substring(sp + 1).trim();
            if (t.startsWith("this.")) target = new Alias(t.substring(5));
            else {
                int d = t.lastIndexOf('.');
                if (d <= 0) {
                    problems.add(where + ": target must be a.b.C.m, this.m or @idiom(...): " + t);
                    return null;
                }
                target = new StaticTarget(t.substring(0, d).replace('.', '/'), t.substring(d + 1));
            }
        }
        String[] words = rest.isEmpty() ? new String[0] : rest.split("\\s+");
        if (words.length == 0) {
            problems.add(where + ": missing check kind (pure, read=any, read=snapshot, read=owner, write, api)");
            return null;
        }
        Check check = Check.of(words[0]);
        if (check == null) {
            problems.add(where + ": unknown check kind " + words[0]);
            return null;
        }
        Map<String, String> attrs = new LinkedHashMap<>();
        for (int k = 1; k < words.length; k++) {
            int eq = words[k].indexOf('=');
            if (eq <= 0) {
                problems.add(where + ": attribute must be key=value: " + words[k]);
                return null;
            }
            attrs.put(words[k].substring(0, eq), words[k].substring(eq + 1));
        }
        return new Line(where, cls, member, desc, target, check, attrs);
    }

    /** "글" 밖에서 처음 나오는 what 의 자리(없으면 -1). */
    static int outsideQuotes(String s, String what) {
        boolean q = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) q = !q;
            else if (!q && s.startsWith(what, i)) return i;
        }
        return -1;
    }

    /** 쉼표로 나누되 "글" 안의 쉼표는 두고, 앞뒤 공백은 뗀다. */
    static List<String> splitArgs(String s) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) q = !q;
            if (c == ',' && !q) {
                out.add(cur.toString().trim());
                cur.setLength(0);
            } else cur.append(c);
        }
        if (!cur.toString().isBlank() || !out.isEmpty()) out.add(cur.toString().trim());
        return out;
    }

    static String fingerprint(String internalClass, String name) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest((internalClass.replace('/', '.') + "." + name).getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder("#");
            for (int i = 0; i < 5; i++) b.append(String.format("%02x", h[i]));
            return b.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static boolean visible(int access) { return (access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0; }

    // ───────────────────────── 풀기·검사 ─────────────────────────

    /** 이름·지문을 서명표로 풀고 이름 규칙(공개·보호만 평문)을 본다. 몸통 대상 검사는 emit 때. */
    void resolve(World w) {
        this.world = w;
        for (Line ln : lines) {
            SigTable.Cls c = w.paperClass(ln.cls());
            if (c == null) {
                problems.add(ln.where() + ": class not in the Paper signature table: " + ln.cls().replace('/', '.'));
                continue;
            }
            if (!visible(c.effectiveAccess()) || c.innerAccess() < 0 && (c.access() & Opcodes.ACC_PUBLIC) == 0) {
                problems.add(ln.where() + ": class is not public - links may only name public classes in plain text: " + ln.cls().replace('/', '.'));
                continue;
            }
            SigTable.Member found = null;
            for (SigTable.Member m : w.paperMembers(ln.cls())) {
                if (m.kind() != 'M' || !m.desc().equals(ln.desc())) continue;
                boolean match = ln.member().startsWith("#") ? fingerprint(ln.cls(), m.name()).equals(ln.member()) : m.name().equals(ln.member());
                if (match) found = m;
            }
            if (found == null) {
                problems.add(ln.where() + ": member not in the Paper signature table: " + ln.text());
                continue;
            }
            if (!ln.member().startsWith("#") && !visible(found.access())) {
                problems.add(ln.where() + ": private or package member written in plain text - use " + fingerprint(ln.cls(), found.name()) + ": " + ln.text());
                continue;
            }
            if (ln.member().startsWith("#") && visible(found.access())) {
                problems.add(ln.where() + ": public/protected member written as a fingerprint - write the name: " + ln.member());
                continue;
            }
            boolean isStatic = (found.access() & Opcodes.ACC_STATIC) != 0;
            if (isStatic && ln.check().owned()) problems.add(ln.where() + ": static member cannot take " + ln.check().text + " (the target checks itself)");
            if (found.name().equals("<init>")) problems.add(ln.where() + ": constructors cannot be linked");
            if ((found.access() & Opcodes.ACC_ABSTRACT) != 0) problems.add(ln.where() + ": abstract member cannot be linked");
            Line prev = resolved.computeIfAbsent(ln.cls(), k -> new TreeMap<>()).put(found.name() + found.desc(), ln);
            if (prev != null) problems.add(ln.where() + ": duplicate line for " + ln.text() + " (also " + prev.where() + ")");
        }
    }

    Line lineFor(String cls, String key) {
        Map<String, Line> m = resolved.get(cls);
        return m == null ? null : m.get(key);
    }

    boolean has(String cls, String key) { return lineFor(cls, key) != null; }

    /** 쓰이지 않은 줄(낡은 줄 등). 생성이 끝난 뒤 부른다. */
    List<String> unconsumed(java.util.function.Function<Line, String> reason) {
        List<String> out = new ArrayList<>();
        for (Map<String, Line> m : resolved.values()) {
            for (Line ln : m.values()) if (!consumed.contains(ln) && !failed.contains(ln)) out.add(ln.where() + ": line not used - " + reason.apply(ln) + ": " + ln.text());
        }
        return out;
    }

    // ───────────────────────── 몸통 ─────────────────────────

    /**
     * 연결 줄이 있으면 mv 에 몸통을 쓰고(visitCode ~ visitEnd) true. 없으면 false(아무것도 안 씀).
     * 대상이 안 맞으면 problems 에 적고 던지는 몸통 대신 쓸 수 없으므로 false — 생성 끝에 problems 가 있으면 멈춘다.
     */
    boolean emit(MethodVisitor mv, String owner, int access, String name, String desc) {
        Line ln = lineFor(owner, name + desc);
        if (ln == null) return false;
        boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
        Plan plan = plan(ln, owner, isStatic, name, desc);
        if (plan == null) {
            failed.add(ln);
            return false;
        }
        consumed.add(ln);
        linkedMembers++;
        mv.visitCode();
        int stack = 0, maxStack = 2;
        if (ln.check().owned()) {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitLdcInsn(JavaTypes.simple(owner) + "#" + name);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, plan.ownerRule, ln.check() == Check.WRITE ? "write" : "read", "(Ljava/lang/Object;Ljava/lang/String;)V", false);
        }
        for (Push p : plan.pushes) {
            p.emit(mv);
            stack += p.size;
            maxStack = Math.max(maxStack, stack + 1);
        }
        mv.visitMethodInsn(plan.opcode, plan.callOwner, plan.callName, plan.callDesc, plan.callItf);
        Type ret = Type.getReturnType(desc);
        if (plan.retConvert != null) plan.retConvert.accept(mv);
        mv.visitInsn(ret.getOpcode(Opcodes.IRETURN));
        int locals = (Type.getArgumentsAndReturnSizes(desc) >> 2) - (isStatic ? 1 : 0);
        mv.visitMaxs(maxStack + 2, locals);   // 변환 중 잠깐 더 큰 값(long→객체)·대상 반환값 몫으로 넉넉히 — 큰 쪽은 해가 없다
        mv.visitEnd();
        return true;
    }

    /** 스택에 올릴 것 하나(받는 쪽·값·인자 — 변환 포함). size 는 변환 뒤 슬롯 수. */
    record Push(int size, java.util.function.Consumer<MethodVisitor> emitter) {
        void emit(MethodVisitor mv) { emitter.accept(mv); }
    }

    private static final class Plan {
        String ownerRule;
        final List<Push> pushes = new ArrayList<>();
        int opcode;
        String callOwner, callName, callDesc;
        boolean callItf;
        java.util.function.Consumer<MethodVisitor> retConvert;
    }

    private Plan plan(Line ln, String owner, boolean isStatic, String name, String desc) {
        Type[] args = Type.getArgumentTypes(desc);
        Type ret = Type.getReturnType(desc);
        Plan p = new Plan();
        List<Type> supplied = new ArrayList<>();   // 대상에 넘기는 값의 원래 형식(변환 전)
        List<Integer> slots = new ArrayList<>();
        int slot = isStatic ? 0 : 1;
        for (Type a : args) {
            slots.add(slot);
            slot += a.getSize();
        }
        switch (ln.target()) {
            case Alias a -> {
                if (ln.check().owned()) {
                    problems.add(ln.where() + ": an alias line cannot take " + ln.check().text + " (the aliased member does its own checks)");
                    return null;
                }
                // 이름 규칙(검토 R1): 대상 이름도 공개·보호만 평문, private·패키지는 선언 클래스로 만든 #지문
                String tname = aliasName(ln, owner, a.name());
                if (tname == null) return null;
                // 같은 사슬의 살아 있는 멤버: 같은 인자, 반환은 같은 형식(또는 대입 가능)
                String found = null;
                for (String cand : methodDescsInChain(owner, tname)) {
                    if (!Type.getArgumentTypes(cand).equals(args) && !java.util.Arrays.equals(Type.getArgumentTypes(cand), args)) continue;
                    if (!assignable(Type.getReturnType(cand), ret)) continue;
                    if (found != null && !found.equals(cand)) {
                        problems.add(ln.where() + ": alias target is ambiguous: this." + a.name());
                        return null;
                    }
                    found = cand;
                }
                Live live = found == null ? null : world.liveMethod(owner, tname, found);
                if (live == null) {
                    problems.add(ln.where() + ": alias target has no behaviour or does not match: this." + a.name() + " (same arguments needed)");
                    return null;
                }
                boolean tStatic = (live.access() & Opcodes.ACC_STATIC) != 0;
                if (tStatic != isStatic) {
                    problems.add(ln.where() + ": alias target is " + (tStatic ? "static" : "an instance method") + " but the member is not: this." + a.name());
                    return null;
                }
                if (tStatic && !live.owner().equals(owner)) {
                    problems.add(ln.where() + ": a static alias target must be declared in the same class: this." + a.name());
                    return null;
                }
                if (!accessibleFrom(owner, live.owner(), live.access())) {
                    problems.add(ln.where() + ": alias target is not accessible from " + owner.replace('/', '.') + ": this." + a.name());
                    return null;
                }
                if (tname.equals(name) && found.equals(desc)) {
                    problems.add(ln.where() + ": alias to itself");
                    return null;
                }
                if (!isStatic) p.pushes.add(new Push(1, mv -> mv.visitVarInsn(Opcodes.ALOAD, 0)));
                for (int i = 0; i < args.length; i++) p.pushes.add(load(args[i], slots.get(i), null));
                boolean itf = isInterface(owner);
                p.opcode = isStatic ? Opcodes.INVOKESTATIC : itf ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL;
                p.callOwner = owner;
                p.callName = tname;
                p.callDesc = found;
                p.callItf = itf;
                return p;
            }
            case StaticTarget st -> {
                if (!targetAllowed(st.owner())) {
                    problems.add(ln.where() + ": link targets must be compat or furnace-api code (adapter boundary): " + st.owner().replace('/', '.'));
                    return null;
                }
                Map<String, AnnInfo> anns = annotatedStatics(st.owner(), TARGET_ANN);
                List<String> candidates = new ArrayList<>();
                for (Map.Entry<String, AnnInfo> e : anns.entrySet()) if (e.getKey().startsWith(st.name() + "(")) candidates.add(e.getKey());
                if (candidates.isEmpty()) {
                    problems.add(ln.where() + ": no @LinkTarget static method " + st.owner().replace('/', '.') + "." + st.name());
                    return null;
                }
                Plan best = null;
                for (String key : candidates) {
                    String tdesc = key.substring(st.name().length());
                    Plan q = callPlan(ln, owner, isStatic, args, slots, ret, List.of(), tdesc, ln.where(), false);
                    if (q == null) continue;
                    if (best != null) {
                        problems.add(ln.where() + ": several @LinkTarget overloads fit " + st.name() + " - make one");
                        return null;
                    }
                    AnnInfo ai = anns.get(key);
                    if (!checkMatches(ln, ai)) return null;
                    q.ownerRule = ai.owner;
                    q.opcode = Opcodes.INVOKESTATIC;
                    q.callOwner = st.owner();
                    q.callName = st.name();
                    q.callDesc = tdesc;
                    best = q;
                }
                if (best == null) problems.add(ln.where() + ": no @LinkTarget overload of " + st.name() + " takes these arguments (with @LinkConvert)");
                return best;
            }
            case Idiom id -> {
                String[] info = idioms().get(id.name());
                if (info == null) {
                    problems.add(ln.where() + ": no @LinkIdiom(\"" + id.name() + "\")");
                    return null;
                }
                AnnInfo ai = new AnnInfo(info[3], info[4], false);
                if (!checkMatches(ln, ai)) return null;
                Plan q = callPlan(ln, owner, isStatic, args, slots, ret, id.values(), info[2], ln.where(), true);
                if (q == null) return null;
                q.ownerRule = ai.owner;
                q.opcode = Opcodes.INVOKESTATIC;
                q.callOwner = info[0];
                q.callName = info[1];
                q.callDesc = info[2];
                return q;
            }
        }
    }

    /** 별칭 대상 이름을 푼다: #지문이면 사슬에서 선언 클래스 지문이 같은 메서드 이름, 평문이면 사슬의 같은 이름이 전부 공개·보호여야 한다. 어기면 null + 문제. */
    private String aliasName(Line ln, String owner, String written) {
        if (written.startsWith("#")) {
            for (String c : chainOrder(owner)) {
                for (SigTable.Member m : world.paperMembers(c)) {
                    if (m.kind() != 'M' || !fingerprint(c, m.name()).equals(written)) continue;
                    if (visible(m.access())) {
                        problems.add(ln.where() + ": public/protected alias target written as a fingerprint - write this." + m.name());
                        return null;
                    }
                    return m.name();
                }
            }
            problems.add(ln.where() + ": no method with fingerprint " + written + " in " + owner.replace('/', '.') + " or its supertypes");
            return null;
        }
        for (String c : chainOrder(owner)) {
            for (SigTable.Member m : world.paperMembers(c)) {
                if (m.kind() == 'M' && m.name().equals(written) && !visible(m.access())) {
                    problems.add(ln.where() + ": private or package alias target written in plain text - use this." + fingerprint(c, written));
                    return null;
                }
            }
        }
        return written;
    }

    private boolean checkMatches(Line ln, AnnInfo ai) {
        Check want = Check.of(ai.check);
        if (want == null) {
            problems.add(ln.where() + ": target declares an unknown check kind: " + ai.check);
            return false;
        }
        if (want != ln.check()) {
            problems.add(ln.where() + ": check kind " + ln.check().text + " differs from the target's " + want.text);
            return false;
        }
        if (want.owned()) {
            if (ai.owner == null || ai.owner.equals("java/lang/Void")) {
                problems.add(ln.where() + ": target with " + want.text + " must name its owner rule class");
                return false;
            }
            if (!targetAllowed(ai.owner)) {
                problems.add(ln.where() + ": owner rule class must be compat or furnace-api code (adapter boundary): " + ai.owner.replace('/', '.'));
                return false;
            }
            byte[] b = world.classBytes(ai.owner);
            if (b == null || !hasStatic(b, want == Check.WRITE ? "write" : "read", "(Ljava/lang/Object;Ljava/lang/String;)V")) {
                problems.add(ln.where() + ": owner rule class lacks static " + (want == Check.WRITE ? "write" : "read") + "(Object, String): " + ai.owner);
                return false;
            }
        }
        return true;
    }

    /** 대상 서술자 tdesc 에 [받는 쪽] + 값 + 인자를 맞춘다. 맞지 않으면 null(문제는 적지 않음 — 여러 겹침을 고를 때 씀). */
    /**
     * idiom: 관용구 대상이면 Object 자리의 원시 인자를 박싱하고, Object 반환을 멤버 반환형으로 CHECKCAST(원시면 박스형 + xxxValue)한다.
     * 관용구는 검토 받은 손 코드이고 틀 맞추기가 바닐라 몸통 모양(박싱 포함)을 정확히 보므로 형식은 바닐라가 보증한다 — 다리(@LinkTarget)에는 쓰지 않는다.
     */
    private Plan callPlan(Line ln, String owner, boolean isStatic, Type[] args, List<Integer> slots, Type ret, List<String> values, String tdesc, String where, boolean idiom) {
        Type[] params = Type.getArgumentTypes(tdesc);
        int need = (isStatic ? 0 : 1) + values.size() + args.length;
        if (params.length != need) return null;
        Plan p = new Plan();
        int k = 0;
        if (!isStatic) {
            Push self = load(Type.getObjectType(owner), 0, params[k++]);
            if (self == null) return null;
            p.pushes.add(self);
        }
        for (String v : values) {
            Push pv = value(ln, v, params[k++], where);
            if (pv == null) return null;
            p.pushes.add(pv);
        }
        for (int i = 0; i < args.length; i++) {
            Push pa = idiom ? loadBoxing(args[i], slots.get(i), params[k++]) : load(args[i], slots.get(i), params[k++]);
            if (pa == null) return null;
            p.pushes.add(pa);
        }
        Type tret = Type.getReturnType(tdesc);
        if (ret.getSort() == Type.VOID) {
            if (tret.getSort() != Type.VOID) return null;
        } else if (!assignable(tret, ret)) {
            if (idiom && tret.getDescriptor().equals("Ljava/lang/Object;")) {
                p.retConvert = unbox(ret);
            } else {
                String[] conv = converter(tret, ret);
                if (conv == null) return null;
                p.retConvert = mv -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, conv[0], conv[1], conv[2], false);
            }
        }
        return p;
    }

    /** 관용구 인자: 원시를 Object 자리에 넣을 때 박싱, 나머지는 load 와 같다. */
    private Push loadBoxing(Type from, int slot, Type want) {
        if (from.getSort() == Type.OBJECT || from.getSort() == Type.ARRAY || !want.getDescriptor().equals("Ljava/lang/Object;")) return load(from, slot, want);
        String box = box(from);
        int op = from.getOpcode(Opcodes.ILOAD);
        return new Push(1, mv -> {
            mv.visitVarInsn(op, slot);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, box, "valueOf", "(" + from.getDescriptor() + ")L" + box + ";", false);
        });
    }

    /** 관용구 반환 Object → 멤버 반환형. */
    private static java.util.function.Consumer<MethodVisitor> unbox(Type ret) {
        if (ret.getSort() == Type.OBJECT || ret.getSort() == Type.ARRAY) {
            String cast = ret.getSort() == Type.ARRAY ? ret.getDescriptor() : ret.getInternalName();
            return mv -> mv.visitTypeInsn(Opcodes.CHECKCAST, cast);
        }
        String box = box(ret);
        return mv -> {
            mv.visitTypeInsn(Opcodes.CHECKCAST, box);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, box, ret.getClassName() + "Value", "()" + ret.getDescriptor(), false);
        };
    }

    static String box(Type prim) {
        return switch (prim.getSort()) {
            case Type.BOOLEAN -> "java/lang/Boolean";
            case Type.BYTE -> "java/lang/Byte";
            case Type.CHAR -> "java/lang/Character";
            case Type.SHORT -> "java/lang/Short";
            case Type.INT -> "java/lang/Integer";
            case Type.LONG -> "java/lang/Long";
            case Type.FLOAT -> "java/lang/Float";
            case Type.DOUBLE -> "java/lang/Double";
            default -> throw new IllegalArgumentException(prim.toString());
        };
    }

    /** 지역 변수(slot)의 형식 from 을 want 로(같거나 대입 가능, 아니면 @LinkConvert). want 가 null 이면 그대로. */
    private Push load(Type from, int slot, Type want) {
        int op = from.getOpcode(Opcodes.ILOAD);
        if (want == null || assignable(from, want)) return new Push(from.getSize(), mv -> mv.visitVarInsn(op, slot));
        String[] conv = converter(from, want);
        if (conv == null) return null;
        return new Push(want.getSize(), mv -> {
            mv.visitVarInsn(op, slot);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, conv[0], conv[1], conv[2], false);
        });
    }

    /** 값 글 하나를 want 형식으로 올린다. 맞지 않으면 null + 문제. */
    private Push value(Line ln, String v, Type want, String where) {
        // 정적 필드(#지문 또는 a.b.C.F)는 원시 형식 자리에도 온다
        if (v.startsWith("#") || !v.isEmpty() && Character.isJavaIdentifierStart(v.charAt(0)) && !v.equals("true") && !v.equals("false")) return staticField(ln, v, want, where);
        try {
            switch (want.getSort()) {
                case Type.BOOLEAN -> {
                    if (!v.equals("true") && !v.equals("false")) return bad(where, v, want);
                    int b = v.equals("true") ? 1 : 0;
                    return new Push(1, mv -> mv.visitInsn(Opcodes.ICONST_0 + b));
                }
                case Type.INT, Type.SHORT, Type.BYTE, Type.CHAR -> {
                    int i = Integer.parseInt(v);
                    return new Push(1, mv -> mv.visitLdcInsn(i));
                }
                case Type.LONG -> {
                    long l = Long.parseLong(v.endsWith("L") ? v.substring(0, v.length() - 1) : v);
                    return new Push(2, mv -> mv.visitLdcInsn(l));
                }
                case Type.FLOAT -> {
                    if (!v.endsWith("F")) return bad(where, v, want);
                    float f = Float.parseFloat(v.substring(0, v.length() - 1));
                    return new Push(1, mv -> mv.visitLdcInsn(f));
                }
                case Type.DOUBLE -> {
                    double d = Double.parseDouble(v.endsWith("D") ? v.substring(0, v.length() - 1) : v);
                    return new Push(2, mv -> mv.visitLdcInsn(d));
                }
                default -> {
                    if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
                        if (!want.getDescriptor().equals("Ljava/lang/String;") && !want.getDescriptor().equals("Ljava/lang/Object;")) return bad(where, v, want);
                        String s = v.substring(1, v.length() - 1).replace("\\\"", "\"");
                        return new Push(1, mv -> mv.visitLdcInsn(s));
                    }
                    return staticField(ln, v, want, where);
                }
            }
        } catch (NumberFormatException e) {
            return bad(where, v, want);
        }
    }

    private Push bad(String where, String v, Type want) {
        problems.add(where + ": value " + v + " does not fit parameter " + want.getClassName());
        return null;
    }

    /**
     * 정적 필드 값: 공개 a.b.C.F 또는 private·패키지 #지문. 필드가 값을 가져야(손 shim·상수·채움) 한다.
     * 지문 필드는 몸통이 들어갈 클래스(줄의 클래스)에서 읽을 수 있어야 한다: private 은 그 클래스 자신의 것만, 패키지 필드는 같은 패키지·사슬 안만.
     * 그리고 사슬에서 더 가까운 클래스가 같은 이름의 정적 필드를 가지면 멈춘다 — 이름은 같고 선언 클래스가 다른 지문(예: 상위의 같은 이름 상수)을 잘못 고른 것이다.
     */
    private Push staticField(Line ln, String v, Type want, String where) {
        String fo, fn, fd;
        if (v.startsWith("#")) {
            String[] f = staticFieldFps().get(v);
            if (f == null) {
                problems.add(where + ": no static field with fingerprint " + v + " in the class or its supertypes");
                return null;
            }
            fo = f[0];
            fn = f[1];
            fd = f[2];
            int fa = Integer.parseInt(f[3]);
            if (visible(fa)) {
                problems.add(where + ": public/protected field written as a fingerprint - write " + fo.replace('/', '.') + "." + fn);
                return null;
            }
            List<String> chain = chainOrder(ln.cls());
            int at = chain.indexOf(fo);
            if (at < 0) {
                problems.add(where + ": fingerprint " + v + " names a field of a class outside " + ln.cls().replace('/', '.') + " and its supertypes");
                return null;
            }
            if ((fa & Opcodes.ACC_PRIVATE) != 0 && !fo.equals(ln.cls())) {
                problems.add(where + ": fingerprint " + v + " names a private field of the supertype " + fo.replace('/', '.') + " - not readable from " + ln.cls().replace('/', '.'));
                return null;
            }
            if (!accessibleFrom(ln.cls(), fo, fa)) {
                problems.add(where + ": fingerprint " + v + " names a package field of another package: " + fo.replace('/', '.'));
                return null;
            }
            for (int i = 0; i < at; i++) {
                for (SigTable.Member m : world.paperMembers(chain.get(i))) {
                    if (m.kind() == 'F' && m.name().equals(fn) && (m.access() & Opcodes.ACC_STATIC) != 0) {
                        problems.add(where + ": fingerprint " + v + " names " + fo.replace('/', '.') + "'s field, but the nearer " + chain.get(i).replace('/', '.')
                                + " has a field of the same name (" + fingerprint(chain.get(i), fn) + ") - same name, different declaring class");
                        return null;
                    }
                }
            }
        } else {
            int d = v.lastIndexOf('.');
            if (d <= 0) {
                problems.add(where + ": value is not a literal or a.b.C.FIELD: " + v);
                return null;
            }
            fo = v.substring(0, d).replace('.', '/');
            fn = v.substring(d + 1);
            fd = null;
            for (SigTable.Member m : world.paperMembers(fo)) {
                if (m.kind() == 'F' && m.name().equals(fn) && (m.access() & Opcodes.ACC_STATIC) != 0) {
                    if (!visible(m.access())) {
                        problems.add(where + ": private or package field written in plain text - use " + fingerprint(fo, fn));
                        return null;
                    }
                    fd = m.desc();
                }
            }
            if (fd == null) {
                problems.add(where + ": no static field " + v + " in the Paper signature table");
                return null;
            }
            for (SigTable.Member m : world.paperMembers(fo)) {
                if (m.kind() == 'F' && m.name().equals(fn) && (m.access() & Opcodes.ACC_PUBLIC) == 0 && !chainOrder(ln.cls()).contains(fo) && !pkg(fo).equals(pkg(ln.cls()))) {
                    problems.add(where + ": protected field " + v + " is not readable from " + ln.cls().replace('/', '.'));
                    return null;
                }
            }
            SigTable.Cls fc = world.paperClass(fo);
            if (fc == null || !visible(fc.effectiveAccess())) {
                problems.add(where + ": field's class is not public: " + v);
                return null;
            }
        }
        if (!world.liveStaticField(fo, fn, fd)) {
            problems.add(where + ": static field has no value in the shim yet: " + (v.startsWith("#") ? v : fo.replace('/', '.') + "." + fn));
            return null;
        }
        Type ft = Type.getType(fd);
        if (!assignable(ft, want)) return bad(where, v, want);
        String o = fo, n = fn, dd = fd;
        return new Push(ft.getSize(), mv -> mv.visitFieldInsn(Opcodes.GETSTATIC, o, n, dd));
    }

    // ───────────────────────── 형식 ─────────────────────────

    boolean assignable(Type from, Type to) {
        if (from.equals(to)) return true;
        if (from.getSort() != Type.OBJECT && from.getSort() != Type.ARRAY) return false;
        if (to.getSort() != Type.OBJECT) return false;
        if (to.getInternalName().equals("java/lang/Object")) return true;
        if (from.getSort() == Type.ARRAY) return false;
        ArrayDeque<String> todo = new ArrayDeque<>(List.of(from.getInternalName()));
        Set<String> seen = new HashSet<>();
        while (!todo.isEmpty()) {
            String s = todo.poll();
            if (!seen.add(s)) continue;
            if (s.equals(to.getInternalName())) return true;
            todo.addAll(world.supertypes(s));
        }
        return false;
    }

    private String[] converter(Type from, Type to) {
        return converters().get(from.getDescriptor() + ">" + to.getDescriptor());
    }

    private boolean isInterface(String owner) {
        SigTable.Cls c = world.paperClass(owner);
        if (c != null) return (c.access() & Opcodes.ACC_INTERFACE) != 0;
        byte[] b = world.classBytes(owner);
        return b != null && (new ClassReader(b).getAccess() & Opcodes.ACC_INTERFACE) != 0;
    }

    /** 사슬(상위 클래스·인터페이스)에서 그 이름의 메서드 서술자들(서명표). */
    private List<String> methodDescsInChain(String owner, String name) {
        List<String> out = new ArrayList<>();
        ArrayDeque<String> todo = new ArrayDeque<>(List.of(owner));
        Set<String> seen = new HashSet<>();
        while (!todo.isEmpty()) {
            String s = todo.poll();
            if (!seen.add(s)) continue;
            for (SigTable.Member m : world.paperMembers(s)) {
                if (m.kind() == 'M' && m.name().equals(name) && !out.contains(m.desc())) out.add(m.desc());
            }
            todo.addAll(world.supertypes(s));
        }
        return out;
    }

    // ───────────────────────── 표지 읽기 ─────────────────────────

    record AnnInfo(String check, String owner, boolean mutates) {}

    /** 클래스의 정적 메서드 중 표지 ann 이 있는 것: 이름+서술자 → 표지 값. */
    Map<String, AnnInfo> annotatedStatics(String cls, String ann) {
        Map<String, AnnInfo> out = new LinkedHashMap<>();
        byte[] b = world.classBytes(cls);
        if (b == null || (new ClassReader(b).getAccess() & Opcodes.ACC_PUBLIC) == 0) return out;   // 몸통이 다른 패키지에서 부르므로 공개 클래스만
        new ClassReader(b).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                if ((access & Opcodes.ACC_STATIC) == 0 || (access & Opcodes.ACC_PUBLIC) == 0) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String d, boolean visible) {
                        if (!d.equals(ann)) return null;
                        String[] v = {"", null, "false", null};
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override
                            public void visit(String n, Object value) {
                                switch (n) {
                                    case "check" -> v[0] = (String) value;
                                    case "owner" -> v[1] = ((Type) value).getInternalName();
                                    case "mutates" -> v[2] = String.valueOf(value);
                                    case "value" -> v[3] = (String) value;
                                    default -> { }
                                }
                            }

                            @Override
                            public void visitEnd() {
                                out.put(name + desc, new AnnInfo(v[0], v[1], Boolean.parseBoolean(v[2])));
                                if (v[3] != null) idiomNames.put(cls + "#" + name + desc, v[3]);
                            }
                        };
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return out;
    }

    private final Map<String, String> idiomNames = new HashMap<>();

    private Map<String, String[]> idioms() {
        if (idioms != null) return idioms;
        idioms = new HashMap<>();
        for (String c : world.compatClasses()) {
            for (Map.Entry<String, AnnInfo> e : annotatedStatics(c, IDIOM_ANN).entrySet()) {
                String nm = idiomNames.get(c + "#" + e.getKey());
                int p = e.getKey().indexOf('(');
                String[] prev = idioms.put(nm, new String[] {c, e.getKey().substring(0, p), e.getKey().substring(p), e.getValue().check(), e.getValue().owner()});
                if (prev != null) problems.add("two @LinkIdiom(\"" + nm + "\"): " + prev[0] + " and " + c);
            }
        }
        return idioms;
    }

    private Map<String, String[]> converters() {
        if (converters != null) return converters;
        converters = new HashMap<>();
        for (String c : world.compatClasses()) {
            for (String key : annotatedStatics(c, CONVERT_ANN).keySet()) {
                int p = key.indexOf('(');
                String d = key.substring(p);
                Type[] a = Type.getArgumentTypes(d);
                Type r = Type.getReturnType(d);
                if (a.length != 1 || r.getSort() == Type.VOID) {
                    problems.add("@LinkConvert must be (A)B: " + c + "#" + key);
                    continue;
                }
                String[] prev = converters.put(a[0].getDescriptor() + ">" + r.getDescriptor(), new String[] {c, key.substring(0, p), d});
                if (prev != null) problems.add("two @LinkConvert for " + a[0].getClassName() + " -> " + r.getClassName() + ": " + prev[0] + " and " + c);
            }
        }
        return converters;
    }

    private Map<String, String[]> staticFieldFps() {
        if (staticFieldFps != null) return staticFieldFps;
        staticFieldFps = new HashMap<>();
        return staticFieldFps;
    }

    /** 지문 값이 가리킬 수 있는 정적 필드 — 줄에 쓰인 지문만 풀면 되므로 그 클래스들(받는 클래스 사슬)만 훑는다. */
    void indexStaticFields(Iterable<String> classes) {
        Map<String, String[]> idx = staticFieldFps();
        for (String c : classes) {
            for (SigTable.Member m : world.paperMembers(c)) {
                if (m.kind() == 'F' && (m.access() & Opcodes.ACC_STATIC) != 0) idx.putIfAbsent(fingerprint(c, m.name()), new String[] {c, m.name(), m.desc(), String.valueOf(m.access())});
            }
        }
    }

    private static boolean hasStatic(byte[] b, String name, String desc) {
        boolean[] f = {false};
        new ClassReader(b).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String n, String d, String s, String[] e) {
                if ((access & Opcodes.ACC_STATIC) != 0 && n.equals(name) && d.equals(desc)) f[0] = true;
                return null;
            }
        }, ClassReader.SKIP_CODE);
        return f[0];
    }

    /** 클래스와 그 상위 형식을 가까운 순서(너비 우선)로. */
    List<String> chainOrder(String cls) {
        List<String> out = new ArrayList<>();
        ArrayDeque<String> todo = new ArrayDeque<>(List.of(cls));
        while (!todo.isEmpty()) {
            String s = todo.poll();
            if (out.contains(s)) continue;
            out.add(s);
            todo.addAll(world.supertypes(s));
        }
        return out;
    }

    /** from 클래스의 몸통이 decl 의 멤버(접근 값 access)를 부를 수 있나. protected 는 사슬 안이라고 보고 허용한다(줄의 클래스 사슬에서만 찾으므로). */
    static boolean accessibleFrom(String from, String decl, int access) {
        if ((access & Opcodes.ACC_PUBLIC) != 0 || (access & Opcodes.ACC_PROTECTED) != 0) return true;
        if ((access & Opcodes.ACC_PRIVATE) != 0) return from.equals(decl);
        return pkg(from).equals(pkg(decl));
    }

    static String pkg(String internalName) {
        int s = internalName.lastIndexOf('/');
        return s < 0 ? "" : internalName.substring(0, s);
    }

    /** 줄에 나오는 클래스들(정적 필드 지문을 풀 범위 — 받는 클래스와 그 사슬). */
    Set<String> lineClasses() {
        Set<String> out = new HashSet<>();
        for (Line ln : lines) {
            ArrayDeque<String> todo = new ArrayDeque<>(List.of(ln.cls()));
            while (!todo.isEmpty()) {
                String s = todo.poll();
                if (!out.add(s)) continue;
                todo.addAll(world.supertypes(s));
            }
        }
        return out;
    }
}
