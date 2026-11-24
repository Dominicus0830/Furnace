// T-229·T-232: 빌드 때 도는 shim 생성기. 연결 명세(src/shimspec/*.spec) + 잠금 파일(shim-<판>.lock) + 손 조각(src/shims/java) → build/generated 의 shim 소스.
// 바닐라 jar·네트워크 없이 돈다(잠금 파일이 서명표에서 푼 선언을 담는다). 결과물은 커밋하지 않는다.
//
// 만드는 것
//  - 명세 클래스와 그 상위 사슬(바깥 클래스·상위·인터페이스)의 선언: 접근·final·abstract·종류·상속·제네릭 — 잠금 파일 그대로.
//    손 shim(src/main/java)·패킷 생성물이 이미 있는 클래스는 만들지 않는다.
//  - 명세 멤버만의 몸통: 정적 위임(+ 소유 검사), 같은 클래스 별칭, 빈 몸통, 필드 값, 추상 선언, 열거형 상수.
//  - 손 조각이 있는 클래스는 조각의 몸통에 선언 머리를 붙이고 생성 멤버를 끼운다(패킷 덧붙임 @@ 의 일반화 — "부분 클래스").
//  - 생성기 전용 생성자 C(ShimCtor): 생성 클래스가 기본 생성자(바닐라에 없는 public C())를 얻지 않게, 그리고 생성 하위 클래스가 부를 수 있게.
//  - 구체 클래스가 생성 상위의 추상 메서드를 명세에 안 적었으면 던지는 몸통 + 색인(META-INF/furnace/shim-unimplemented.txt) —
//    NmsSymbols 가 그 메서드를 "없음"으로 보고 링크 때 덫을 건다(설계 4.3).
// 명세에 없는 멤버는 만들지 않는다 → 클래스 파일에 없으므로 플러그인 호출은 지금처럼 NmsLinker 덫이 잡는다.
package org.domi.furnace.compat.shimgen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class ShimGenerator {
    static final String SHIM_CTOR = "org.domi.furnace.compat.nms.ShimCtor";
    static final String UNSUPPORTED = "org.domi.furnace.compat.nms.UnsupportedNmsCall";
    /** 실행 때 NmsSymbols 가 읽는 색인. */
    public static final String UNIMPLEMENTED_INDEX = "META-INF/furnace/shim-unimplemented.txt";
    /** 생성·합친 클래스 목록(시험·보고용). 줄: G|F 내부이름 (G 생성, F 손 조각과 합침). */
    public static final String GENERATED_INDEX = "META-INF/furnace/shim-generated.txt";
    /** 실행 때 NmsKinds 가 읽는 종류 표(T-231). 줄: B|I 레지스트리이름 클래스(이진 이름). */
    public static final String KIND_INDEX = "META-INF/furnace/shim-kinds.txt";

    /** 클래스가 어디서 오나. */
    enum Origin { GEN, FRAGMENT, HAND, PACKET }

    final ShimSpec spec;
    final ShimLock lock;
    final Path fragments;
    final ShimSources hand;
    final ShimSources packets;
    final ShimSources fragmentSrc;

    final Map<String, Origin> origin = new TreeMap<>();
    /** 명세 멤버 ↔ 잠금 멤버(클래스별, 명세 순서). */
    final Map<String, List<Bound>> bound = new LinkedHashMap<>();
    final Set<String> needsMarker = new TreeSet<>();
    final List<String> unimplemented = new ArrayList<>();
    final List<String> problems = new ArrayList<>();

    record Bound(ShimSpec.Member spec, ShimLock.Member lock) {}

    ShimGenerator(ShimSpec spec, ShimLock lock, Path fragments, List<Path> handRoots, List<Path> packetRoots) {
        this.spec = spec;
        this.lock = lock;
        this.fragments = fragments;
        this.hand = new ShimSources(handRoots);
        this.packets = new ShimSources(packetRoots);
        this.fragmentSrc = new ShimSources(List.of(fragments));
    }

    /** args: 명세 폴더, 잠금 파일, 조각 폴더, 소스 출력, 자원 출력, 손 폴더(:로 여럿), 패킷 생성 폴더(:로 여럿, 비어도 됨). */
    public static void main(String[] args) throws IOException {
        Path specDir = Path.of(args[0]);
        Path lockFile = Path.of(args[1]);
        Path frag = Path.of(args[2]);
        Path outSrc = Path.of(args[3]);
        Path outRes = Path.of(args[4]);
        List<Path> handRoots = paths(args[5]);
        List<Path> packetRoots = args.length > 6 ? paths(args[6]) : List.of();
        ShimSpec spec = ShimSpec.readDir(specDir);
        ShimLock lock = ShimLock.read(lockFile);
        if (lock == null) {
            if (!spec.classes.isEmpty()) throw new IllegalStateException("no shim lock file: " + lockFile + " - create it with ./gradlew -PskipOracle=true :furnace-compat-paper:shimLock");
            lock = new ShimLock("-");
        }
        ShimGenerator g = new ShimGenerator(spec, lock, frag, handRoots, packetRoots);
        Result r = g.generate();
        deleteTree(outSrc);
        deleteTree(outRes);
        for (Map.Entry<String, String> e : r.files().entrySet()) {
            Path p = outSrc.resolve(e.getKey() + ".java");
            Files.createDirectories(p.getParent());
            Files.writeString(p, e.getValue(), StandardCharsets.UTF_8);
        }
        Path ui = outRes.resolve(UNIMPLEMENTED_INDEX);
        Files.createDirectories(ui.getParent());
        Files.writeString(ui, String.join("\n", r.unimplemented()) + (r.unimplemented().isEmpty() ? "" : "\n"), StandardCharsets.UTF_8);
        Files.writeString(outRes.resolve(GENERATED_INDEX), String.join("\n", r.generated()) + (r.generated().isEmpty() ? "" : "\n"), StandardCharsets.UTF_8);
        Files.writeString(outRes.resolve(KIND_INDEX), String.join("\n", r.kinds()) + (r.kinds().isEmpty() ? "" : "\n"), StandardCharsets.UTF_8);
        System.out.println("[shimgen] shim spec: " + spec.classes.size() + " classes -> " + r.files().size() + " files ("
                + r.generated().size() + " generated or merged classes, " + r.unimplemented().size() + " unimplemented abstract methods)");
    }

    private static List<Path> paths(String s) {
        List<Path> out = new ArrayList<>();
        for (String p : s.split(java.io.File.pathSeparator)) if (!p.isBlank()) out.add(Path.of(p));
        return out;
    }

    /** 결과: 최상위 내부 이름 → 소스 글, 색인 줄들. */
    record Result(Map<String, String> files, List<String> unimplemented, List<String> generated, List<String> kinds) {}

    Result generate() throws IOException {
        bind();
        classify();
        if (!problems.isEmpty()) throw fail();
        markers();
        stubs();
        if (!problems.isEmpty()) throw fail();
        // 최상위 단위로 모은다
        Map<String, List<String>> units = new TreeMap<>();
        for (Map.Entry<String, Origin> e : origin.entrySet()) {
            if (e.getValue() == Origin.GEN || e.getValue() == Origin.FRAGMENT) {
                units.computeIfAbsent(ShimSources.topLevel(e.getKey()), k -> new ArrayList<>()).add(e.getKey());
            }
        }
        Map<String, String> files = new TreeMap<>();
        for (Map.Entry<String, List<String>> u : units.entrySet()) {
            String top = u.getKey();
            Origin o = origin.get(top);
            if (o == Origin.FRAGMENT) files.put(top, mergeFragment(top));
            else if (o == Origin.GEN) files.put(top, generatedFile(top));
            else problems.add("nested classes to generate live in a hand-written or packet-generated outer class (move the outer class to a fragment, or write them by hand): " + u.getValue());
        }
        if (!problems.isEmpty()) throw fail();
        List<String> gen = new ArrayList<>();
        origin.forEach((k, v) -> { if (v == Origin.GEN) gen.add("G " + k); else if (v == Origin.FRAGMENT) gen.add("F " + k); });
        Set<String> un = new TreeSet<>(unimplemented);
        List<String> kinds = new ArrayList<>();
        for (String[] k : spec.kinds) kinds.add(k[0] + " " + k[1] + " " + k[2]);
        return new Result(files, List.copyOf(un), gen, kinds);
    }

    private IllegalStateException fail() {
        return new IllegalStateException("shim generation failed (" + problems.size() + " problems):\n  " + String.join("\n  ", problems));
    }

    // ───────────────────────── 명세 ↔ 잠금 ─────────────────────────

    /** 명세 멤버를 잠금 멤버에 잇고, 잠금이 명세와 맞는지(낡지 않았는지) 본다. */
    void bind() {
        Set<String> closure = new TreeSet<>();
        List<String> work = new ArrayList<>();
        for (ShimSpec.Cls c : spec.classes.values()) work.add(c.name().replace('.', '/'));
        while (!work.isEmpty()) {
            String n = work.remove(work.size() - 1);
            if (!closure.add(n)) continue;
            ShimLock.Cls c = lock.classes.get(n);
            if (c == null) { problems.add("class not in the shim lock: " + n + " - rerun :furnace-compat-paper:shimLock after changing the spec"); continue; }
            int d = n.lastIndexOf('$');
            if (d > 0) work.add(n.substring(0, d));
            if (c.superName() != null && ShimLock.isNms(c.superName())) work.add(c.superName());
            for (String i : c.interfaces()) if (ShimLock.isNms(i)) work.add(i);
        }
        for (String n : lock.classes.keySet()) {
            if (!closure.contains(n)) problems.add("stale shim lock (class not in the spec): " + n + " - rerun :furnace-compat-paper:shimLock");
        }
        for (ShimSpec.Cls c : spec.classes.values()) {
            String owner = c.name().replace('.', '/');
            List<ShimLock.Member> lm = new ArrayList<>(lock.members(owner));
            List<Bound> out = new ArrayList<>();
            for (ShimSpec.Member m : c.members()) {
                ShimLock.Member hit = null;
                for (ShimLock.Member x : lm) {
                    if (!x.name().equals(m.name())) continue;
                    if (m.desc() != null) {
                        if (m.fieldDesc() != (x.kind() == 'F')) continue;
                        if (m.fieldDesc() ? !x.desc().equals(m.desc()) : !x.desc().startsWith(m.desc())) continue;
                    }
                    hit = x;
                    break;
                }
                if (hit == null) {
                    problems.add(m.where() + ": member not in the shim lock: " + owner + "#" + m.name() + " - rerun :furnace-compat-paper:shimLock");
                    continue;
                }
                lm.remove(hit);
                out.add(new Bound(m, hit));
            }
            for (ShimLock.Member x : lm) problems.add("stale shim lock (member not in the spec): " + owner + "#" + x.name() + x.desc());
            bound.put(owner, out);
        }
        for (String o : lock.members.keySet()) {
            if (!spec.classes.containsKey(o.replace('/', '.'))) problems.add("stale shim lock (members of a class not in the spec): " + o);
        }
    }

    /** 잠금 클래스마다 어디서 오는지 정한다. */
    void classify() {
        for (String n : lock.classes.keySet()) {
            boolean inHand = hand.has(n);
            boolean inPacket = packets.has(n);
            boolean fragTop = fragmentSrc.topIn(fragments, n);
            boolean nested = n.indexOf('$') > 0;
            boolean inFragmentText = fragTop && fragmentSrc.in(fragments, n);
            if (fragTop && hand.has(ShimSources.topLevel(n))) {
                problems.add("the same class is both a fragment (src/shims/java) and a hand shim (src/main/java): " + n);
            }
            Origin o;
            if (inHand) o = Origin.HAND;
            else if (inPacket) o = Origin.PACKET;
            else if (!nested && fragTop) o = Origin.FRAGMENT;
            else if (nested && inFragmentText) o = Origin.HAND;   // 손 조각 안에 손으로 쓴 안쪽 클래스
            else o = Origin.GEN;
            origin.put(n, o);
            ShimLock.Cls c = lock.classes.get(n);
            if ((o == Origin.GEN || o == Origin.FRAGMENT) && c.isAnnotation()) {
                problems.add("annotation types are not generated yet (T-230): " + n + " - keep it as a hand shim");
            }
            if ((o == Origin.GEN || o == Origin.FRAGMENT) && c.isEnum()) {
                // 상수를 일부만 적으면 서수·values() 가 바닐라와 달라진다 — 전부(바닐라 순서로) 적어야 한다
                Integer want = lock.enumConstants.get(n);
                int listed = enumConstants(n).size();
                if (want == null) problems.add("the shim lock has no enum constant count for " + n + " - rerun :furnace-compat-paper:shimLock");
                else if (listed != want) problems.add("enum " + n + " lists " + listed + " of its " + want + " vanilla constants in the spec;"
                        + " list every constant in vanilla order (ordinals and values() must match vanilla)");
            }
        }
        // 종류 표의 클래스는 레지스트리 객체가 되는 구체 클래스다(바닐라에서 이미 그렇다 — 서명표가 바뀌면 알린다)
        for (String[] k : spec.kinds) {
            ShimLock.Cls c = lock.classes.get(k[2].replace('.', '/'));
            if (c != null && (c.isAbstract() || c.isInterface())) problems.add("kind class " + k[2] + " of " + k[1] + " is abstract or an interface in the shim lock");
        }
        for (ShimSpec.Cls c : spec.classes.values()) {
            String n = c.name().replace('.', '/');
            Origin o = origin.get(n);
            if (o == null) continue;
            List<Bound> bs = bound.getOrDefault(n, List.of());
            if ((o == Origin.HAND || o == Origin.PACKET) && !bs.isEmpty()) {
                problems.add(c.where() + ": " + n + " is " + (o == Origin.HAND ? "a hand shim (src/main/java)" : "packet-generated") + "; spec members cannot be attached"
                        + (o == Origin.HAND ? " - move it to a fragment (src/shims/java)" : " - use packet-shim-extras.txt"));
            }
            for (Bound b : bs) {
                ShimSpec.Member m = b.spec();
                if (m.body() == ShimSpec.Body.EACH && !(b.lock().kind() == 'M' && b.lock().isAbstract() && !b.lock().isStatic())) {
                    problems.add(m.where() + ": @each is for methods that are abstract in vanilla (each generated concrete subclass gets the delegate): " + n + "#" + m.name());
                }
                if (b.lock().kind() == 'M' && b.lock().isAbstract() && m.body() != ShimSpec.Body.DECLARE && m.body() != ShimSpec.Body.EACH) {
                    problems.add(m.where() + ": " + n + "#" + m.name() + " is abstract in vanilla"
                            + (lock.classes.get(n).isInterface() ? " (an interface method without a default body)" : "")
                            + "; write the name only (an abstract declaration) - a body would change the class shape");
                }
                if ((m.body() == ShimSpec.Body.HAND || m.body() == ShimSpec.Body.FAST) && o != Origin.FRAGMENT) {
                    problems.add(m.where() + ": @hand/@fast bodies go in the fragment src/shims/java/" + n + ".java, which does not exist");
                }
                if (m.kind() != null && m.kind().checked()) {
                    if (c.owner() == null) problems.add(m.where() + ": " + m.kind().token + " links need owner=<owner rule class> on the class line");
                    if (b.lock().isStatic()) problems.add(m.where() + ": no owner check is generated for static methods (no this) - let the target check and mark it pure/read=any, or use @hand");
                }
            }
        }
    }

    // ───────────────────────── 생성자 표지·구현 없는 추상 메서드 ─────────────────────────

    boolean isShim(String n) { return n != null && origin.containsKey(n) || n != null && ShimLock.isNms(n) && hand.has(n); }

    boolean isRecord(String n) { return lock.classes.get(n) != null && lock.classes.get(n).isRecord(); }

    boolean emitted(String n) { Origin o = origin.get(n); return o == Origin.GEN || o == Origin.FRAGMENT; }

    private boolean hasCtorSpec(String n) {
        for (Bound b : bound.getOrDefault(n, List.of())) if (b.lock().name().equals("<init>")) return true;
        return false;
    }

    /** 생성기 전용 생성자가 필요한 클래스: 생성자 명세가 없는 생성 클래스, 그리고 그 상위 사슬의 생성·조각 클래스. */
    void markers() {
        List<String> work = new ArrayList<>();
        for (Map.Entry<String, Origin> e : origin.entrySet()) {
            ShimLock.Cls c = lock.classes.get(e.getKey());
            if (e.getValue() != Origin.GEN || c.isInterface() || c.isEnum() || isRecord(e.getKey())) continue;
            if (!hasCtorSpec(e.getKey())) work.add(e.getKey());
            for (Bound b : bound.getOrDefault(e.getKey(), List.of())) {
                if (b.lock().name().equals("<init>") && b.spec().body() == ShimSpec.Body.EMPTY && isShim(c.superName())) work.add(c.superName());
            }
        }
        // 종류 표의 클래스는 NmsKinds 가 C(ShimCtor) 로 만든다 — 조각이어도 생성자가 있어야 한다(T-232: ItemEntity 등)
        // 엔티티 종류 클래스는 바닐라 생성자(FRESH)가 이 생성자로 넘기므로 생성 클래스여도 필요하다
        for (String[] k : spec.kinds) {
            String n = k[2].replace('.', '/');
            if (origin.get(n) == Origin.FRAGMENT || origin.get(n) == Origin.GEN) work.add(n);
        }
        while (!work.isEmpty()) {
            String n = work.remove(work.size() - 1);
            if (!emitted(n)) {
                if (origin.get(n) == Origin.PACKET) problems.add("a generated class extends a packet-generated class (cannot call the generator constructor): " + n);
                continue;   // 손 shim 은 스스로 C(ShimCtor) 를 가져야 한다(없으면 컴파일 오류가 알려 준다)
            }
            if (isRecord(n)) { problems.add("a record cannot take the generator constructor (it is also a kind class or a generated superclass): " + n); continue; }
            if (!needsMarker.add(n)) continue;
            ShimLock.Cls c = lock.classes.get(n);
            if (isShim(c.superName())) work.add(c.superName());
        }
    }

    /** 구체 생성 클래스가 생성 상위(클래스·인터페이스)의 추상 메서드를 안 갖추면 던지는 몸통을 붙이고 색인에 적는다. */
    void stubs() {
        for (Map.Entry<String, Origin> e : origin.entrySet()) {
            String n = e.getKey();
            ShimLock.Cls c = lock.classes.get(n);
            if (!emitted(n) || c.isInterface() || c.isAbstract()) continue;
            // 상위 클래스 사슬에 손 shim(모르는 몸통)이 있으면 던지는 몸통은 판단하지 않는다 — 빠졌으면 컴파일 오류가 알려 준다.
            // 상위 공통 연결(@each)은 그래도 만든다. 단 손 상위가 같은 이름의 메서드를 가지면 생성 몸통이 그것을 말없이 가리므로 생성을 멈춘다
            // (아래 handHides — 손 소스 글에서 그 이름의 메서드 선언을 찾는다).
            boolean unknown = false;
            List<String> chain = new ArrayList<>();
            List<String> handSupers = new ArrayList<>();
            for (String s = n; s != null && ShimLock.isNms(s); s = lock.classes.containsKey(s) ? lock.classes.get(s).superName() : null) {
                if (!emitted(s)) { unknown = true; handSupers.add(s); continue; }
                if (!unknown) chain.add(s);
            }
            Set<String> have = new HashSet<>();
            Map<String, ShimLock.Member> abstracts = new TreeMap<>();
            Map<String, Bound> each = new TreeMap<>();
            for (String s : chain) {
                for (Bound b : bound.getOrDefault(s, List.of())) {
                    if (b.lock().kind() != 'M') continue;
                    if (b.spec().body() == ShimSpec.Body.EACH) { abstracts.putIfAbsent(overrideKey(b.lock()), b.lock()); each.putIfAbsent(overrideKey(b.lock()), b); }
                    else if (b.spec().body() == ShimSpec.Body.DECLARE && b.lock().isAbstract()) abstracts.putIfAbsent(overrideKey(b.lock()), b.lock());
                    else if (!b.lock().isStatic()) have.add(overrideKey(b.lock()));
                }
            }
            // 인터페이스(생성·조각만, 상위 인터페이스 포함). 기본 메서드는 갖춘 것으로 친다.
            Set<String> seen = new HashSet<>();
            List<String> itfs = new ArrayList<>();
            for (String s : chain) itfs.addAll(lock.classes.get(s).interfaces());
            while (!itfs.isEmpty()) {
                String i = itfs.remove(itfs.size() - 1);
                if (!seen.add(i) || !lock.classes.containsKey(i)) continue;
                itfs.addAll(lock.classes.get(i).interfaces());
                if (!emitted(i)) {
                    // 손 인터페이스(src/main/java)가 몸통 없이 선언한 추상 메서드 — 생성 클래스가 그것을 구현하는 곳(손 BlockEntity 아래 Sign 의 CommandSource 등)
                    if (hand.has(i)) {
                        for (ShimLock.Member lm : lock.handAbstracts.getOrDefault(i, List.of())) {
                            if (abstractDeclared(hand.text(i), lm.name(), descParams(lm.desc()))) abstracts.putIfAbsent(overrideKey(lm), lm);
                        }
                    }
                    continue;
                }
                for (Bound b : bound.getOrDefault(i, List.of())) {
                    if (b.lock().kind() != 'M' || b.lock().isStatic()) continue;
                    if (b.spec().body() == ShimSpec.Body.EACH) { abstracts.putIfAbsent(overrideKey(b.lock()), b.lock()); each.putIfAbsent(overrideKey(b.lock()), b); }
                    else if (b.spec().body() == ShimSpec.Body.DECLARE) abstracts.putIfAbsent(overrideKey(b.lock()), b.lock());
                    else have.add(overrideKey(b.lock()));
                }
            }
            for (Map.Entry<String, ShimLock.Member> am : abstracts.entrySet()) {
                ShimLock.Member m = am.getValue();
                if (have.contains(am.getKey())) continue;
                Bound fill = each.get(am.getKey());
                if (fill != null) {
                    for (String h : handSupers) {
                        if (handDeclares(h, m.name())) {
                            problems.add(n + ": @each " + m.name() + " (from " + m.owner() + ") would hide a method of the same name in the hand shim " + h
                                    + " - remove the hand method or link it in the spec instead of @each");
                        }
                    }
                    eachFor.computeIfAbsent(n, k -> new ArrayList<>()).add(fill);
                    continue;
                }
                // 상위 사슬에 손 shim 이 끼어 있어도(unknown) 그 손 shim 들이 같은 이름의 메서드를 하나도 안 가졌다면 던지는 몸통을 만든다
                // (손 BlockEntity 아래 생성 Container 가족: 바닐라 Container·CommandSource 의 추상 메서드). 가졌다면 말없이 가리지 않게 건너뛴다.
                if (unknown) {
                    boolean handHas = false;
                    for (String h : handSupers) if (handDeclares(h, m.name())) { handHas = true; break; }
                    if (!handHas) handHas = implementedByHandInterface(n, m);
                    if (handHas) continue;
                }
                // 손 조각이 이미 구현한 메서드(조각 소스에 선언이 있다)는 던지는 몸통으로 가리지 않는다 — 상위 클래스·인터페이스 기본 메서드 조각 모두(추상을 선언한 조각 자신은 뺀다)
                if (implementedByFragment(n, m) || implementedByHandInterface(n, m)) continue;
                if (JavaTypes.usesTypeVariables(m.signature())) {
                    problems.add(n + " does not link " + m.name() + m.desc() + ", an abstract method of a generated supertype whose signature uses type variables;"
                            + " a throwing body cannot be generated - link it in the spec of " + n);
                    continue;
                }
                stubsFor.computeIfAbsent(n, k -> new ArrayList<>()).add(m);
                unimplemented.add(n + " " + m.name() + " " + m.desc());
            }
        }
    }

    /** n 의 상위 사슬(클래스·인터페이스)에 있는 손 조각 중, 추상을 선언한 쪽이 아닌 조각이 m 과 같은 이름의 메서드를 직접 선언하나. */
    private boolean implementedByFragment(String n, ShimLock.Member m) {
        Set<String> seen = new HashSet<>();
        List<String> work = new ArrayList<>(List.of(n));
        while (!work.isEmpty()) {
            String s = work.remove(work.size() - 1);
            if (s == null || !seen.add(s) || !lock.classes.containsKey(s)) continue;
            if (!s.equals(m.owner()) && origin.get(s) == Origin.FRAGMENT && fragmentMethodDeclared(s, m.name())) return true;
            ShimLock.Cls c = lock.classes.get(s);
            work.add(c.superName());
            work.addAll(c.interfaces());
        }
        return false;
    }

    /** 조각 소스에 그 이름의 메서드 몸통(여는 중괄호까지)이 있나 — 호출식(return name( …)·추상 선언은 안 센다. */
    private boolean fragmentMethodDeclared(String fragment, String method) {
        return bodyDeclared(fragmentSrc.text(fragment), method);
    }

    /** 그 이름·인자 수의 메서드 몸통(여는 중괄호까지)이 있나 — 같은 이름의 오버로드를 헷갈리지 않게 인자 수까지 본다. */
    private static boolean bodyDeclaredWith(String text, String method, int params) {
        if (text == null) return false;
        java.util.regex.Matcher m = Pattern.compile("(?m)^\\s*(?:@\\w+\\s+)*(?:public |protected |private |static |final |default |synchronized )*(?:<[^>]+>\\s+)?[\\w.<>\\[\\]?, ]+\\s+"
                + Pattern.quote(method) + "\\s*\\(([^;{)]*)\\)\\s*(?:throws [\\w., ]+)?\\{").matcher(text);
        while (m.find()) if (paramCount(m.group(1)) == params) return true;
        return false;
    }

    /** 손 인터페이스 소스에 그 이름·인자 수의 추상 선언(몸통 없이 세미콜론으로 끝남)이 있나 — 같은 이름의 오버로드를 헷갈리지 않게 인자 수까지 본다. */
    private static boolean abstractDeclared(String text, String method, int params) {
        if (text == null) return false;
        java.util.regex.Matcher m = Pattern.compile("(?m)^\\s*(?:@\\w+\\s+)*(?:public |abstract )*(?:<[^>]+>\\s+)?[\\w.<>\\[\\]?, ]+\\s+"
                + Pattern.quote(method) + "\\s*\\(([^;{)]*)\\)\\s*(?:throws [\\w., ]+)?;").matcher(text);
        while (m.find()) if (paramCount(m.group(1)) == params) return true;
        return false;
    }

    /** 메서드 서술자의 인자 수. */
    static int descParams(String desc) {
        int n = 0;
        for (int i = 1; desc.charAt(i) != ')'; i++) {
            while (desc.charAt(i) == '[') i++;
            if (desc.charAt(i) == 'L') i = desc.indexOf(';', i);
            n++;
        }
        return n;
    }

    /** 인자 목록 글의 인자 수(제네릭 꺾쇠 안의 쉼표는 세지 않는다). */
    static int paramCount(String list) {
        if (list.isBlank()) return 0;
        int n = 1, depth = 0;
        for (char ch : list.toCharArray()) {
            if (ch == '<') depth++;
            else if (ch == '>') depth--;
            else if (ch == ',' && depth == 0) n++;
        }
        return n;
    }

    private static boolean bodyDeclared(String text, String method) {
        if (text == null) return false;
        return Pattern.compile("(?m)^\\s*(?:@\\w+\\s+)*(?:public |protected |private |static |final |default |synchronized )*(?:<[^>]+>\\s+)?[\\w.<>\\[\\]?, ]+\\s+"
                + Pattern.quote(method) + "\\s*\\([^;{]*\\)\\s*(?:throws [\\w., ]+)?\\{").matcher(text).find();
    }

    /**
     * 손 인터페이스(src/main/java)의 기본(default) 메서드 몸통이 같은 이름을 이미 구현하나 — n 의 상위 사슬 전체의 인터페이스를 따라간다
     * (손 LevelReader·BlockGetter·Packet 등. 손 상위 클래스가 구현한 인터페이스도 포함).
     */
    private boolean implementedByHandInterface(String n, ShimLock.Member m) {
        Set<String> seen = new HashSet<>();
        List<String> work = new ArrayList<>(List.of(n));
        while (!work.isEmpty()) {
            String s = work.remove(work.size() - 1);
            if (s == null || !seen.add(s) || !lock.classes.containsKey(s)) continue;
            ShimLock.Cls c = lock.classes.get(s);
            if (c.isInterface() && !s.equals(m.owner()) && !emitted(s) && hand.has(s) && bodyDeclaredWith(hand.text(s), m.name(), descParams(m.desc()))) return true;
            work.add(c.superName());
            work.addAll(c.interfaces());
        }
        return false;
    }

    final Map<String, List<ShimLock.Member>> stubsFor = new TreeMap<>();

    /** 손 shim(src/main/java) 소스에 그 이름의 메서드 선언이 있나(정규식 — "형식 이름(" 꼴. 주석 속 글도 걸리지만 그때는 고쳐 쓰면 된다). */
    boolean handDeclares(String handClass, String method) {
        String text = hand.text(handClass);
        if (text == null) return false;
        return Pattern.compile("[\\w>\\]]\\s+" + Pattern.quote(method) + "\\s*\\(").matcher(text).find();
    }
    /** 상위 공통 연결(@each)로 위임 몸통을 받는 구체 클래스 → 그 연결 줄들. */
    final Map<String, List<Bound>> eachFor = new TreeMap<>();

    /** 재정의 판정 열쇠: 이름 + 인자 서술자(반환형은 공변 재정의가 있어 뺀다 — javac 가 다리 메서드를 만든다). */
    static String overrideKey(ShimLock.Member m) { return m.name() + m.desc().substring(0, m.desc().indexOf(')') + 1); }

    // ───────────────────────── 소스 쓰기 ─────────────────────────

    private static final String GEN_NOTE = "// Generated by the shim generator (T-229) from src/shimspec and the shim lock. Do not edit this file";

    String generatedFile(String top) {
        StringBuilder sb = new StringBuilder();
        sb.append(GEN_NOTE).append(".\n");
        String pkg = top.substring(0, top.lastIndexOf('/')).replace('/', '.');
        sb.append("package ").append(pkg).append(";\n\n");
        sb.append(declaration(top, ""));
        return sb.toString();
    }

    /** 클래스 하나의 선언(머리 + 생성 멤버 + 생성 안쪽 클래스). */
    String declaration(String n, String indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent).append("/** Generated from the shim spec. Declaration source: ").append(lock.classes.get(n).source()).append(". */\n");
        sb.append(indent).append(header(n)).append(" {\n");
        sb.append(body(n, indent + "    "));
        sb.append(indent).append("}\n");
        return sb.toString();
    }

    /** 생성 멤버 + 생성 안쪽 클래스(최상위 조각에도 같이 쓴다). */
    String body(String n, String in) {
        ShimLock.Cls c = lock.classes.get(n);
        StringBuilder sb = new StringBuilder();
        String simple = JavaTypes.simple(n);
        List<Bound> bs = bound.getOrDefault(n, List.of());
        // 열거형 상수(명세 순서). 조각은 mergeFragment 가 머리 바로 뒤에 쓴다.
        if (c.isEnum() && origin.get(n) == Origin.GEN) {
            List<String> consts = enumConstants(n);
            if (consts.stream().anyMatch(k -> k.contains("("))) problems.add("enum " + n + " has constants with constructor arguments: it needs a fragment (src/shims/java) with the constructor and fields");
            sb.append(in).append(String.join(", ", consts)).append(";\n");
        }
        boolean rec = c.isRecord();
        if (rec) recordAutoMembers(n, bs);
        if (rec) explicitRecordMethods(n);
        // 필드
        for (Bound b : bs) {
            ShimLock.Member m = b.lock();
            if (m.kind() != 'F' || (m.access() & 0x4000) != 0) continue;
            if (rec && isComponentField(n, m)) continue;
            switch (b.spec().body()) {
                case HAND, FAST -> { }
                case VALUE -> sb.append(in).append(fieldMods(c, m)).append(fieldType(m)).append(' ').append(m.name()).append(" = ").append(b.spec().target()).append(";\n");
                case DECLARE -> {
                    if ((m.access() & 0x0018) == 0x0018) problems.add(b.spec().where() + ": write static final fields as = value or = @hand: " + m.name());
                    else if (c.isInterface()) problems.add(b.spec().where() + ": write interface fields as = value: " + m.name());
                    else sb.append(in).append(fieldMods(c, m)).append(fieldType(m)).append(' ').append(m.name()).append(";\n");
                }
                default -> problems.add(b.spec().where() + ": fields take = value, = @hand, or a bare declaration: " + m.name());
            }
        }
        // 생성기 전용 생성자
        if (needsMarker.contains(n) && !fragmentDeclaresMarker(n)) {
            sb.append(in).append("/** Generator-only constructor (not in vanilla; plugin bytecode never calls this descriptor). */\n");
            sb.append(in).append("protected ").append(simple).append('(').append(SHIM_CTOR).append(" tag) { ");
            if (isShim(c.superName())) sb.append("super(tag); ");
            sb.append("}\n");
        }
        // 메서드·생성자
        for (Bound b : bs) {
            ShimLock.Member m = b.lock();
            if (m.kind() != 'M') continue;
            if (rec && isRecordAuto(n, m)) continue;
            String head = methodHead(c, m, simple);
            switch (b.spec().body()) {
                case HAND, FAST -> { }
                case EACH -> sb.append(in).append(head).append(";\n");   // 추상 그대로(구체 하위 클래스가 위임 몸통을 받는다)
                case DECLARE -> {
                    if (!m.isAbstract()) problems.add(b.spec().where() + ": a method that is not abstract in vanilla needs -> target, -> @hand or -> @empty: " + m.name());
                    else sb.append(in).append(head).append(";\n");
                }
                case EMPTY -> {
                    if (!m.name().equals("<init>") && !JavaTypes.descReturn(m.desc()).equals("void")) {
                        problems.add(b.spec().where() + ": @empty is for void methods and constructors only: " + m.name());
                    } else if (m.name().equals("<init>") && isShim(c.superName())) {
                        sb.append(in).append(head).append(" { super(").append(SHIM_CTOR).append(".TAG); }\n");
                    } else if (b.spec().kind() != null && b.spec().kind().checked()) {
                        sb.append(in).append(head).append(" {\n").append(ownerCheck(n, b, in + "    ")).append(in).append("}\n");
                    } else {
                        sb.append(in).append(head).append(" { }\n");
                    }
                }
                case FRESH -> sb.append(in).append(head).append(" { this(").append(SHIM_CTOR)
                        .append(".carrying(new org.domi.furnace.compat.nms.NmsEntityKinds.Fresh(a0, a1))); }\n");
                case DELEGATE, SELF -> {
                    if (m.name().equals("<init>")) { problems.add(b.spec().where() + ": constructors take @empty or @hand"); continue; }
                    if (m.isAbstract()) continue;   // classify 가 이미 알렸다
                    sb.append(in).append(head).append(" {\n");
                    sb.append(delegateBody(n, b, in + "    "));
                    sb.append(in).append("}\n");
                }
                default -> problems.add(b.spec().where() + ": methods cannot take = value: " + m.name());
            }
        }
        // 상위 공통 연결(@each): 물려받은 추상 메서드의 위임 몸통
        for (Bound b : eachFor.getOrDefault(n, List.of())) {
            ShimLock.Member m = b.lock();
            ShimLock.Member concrete = new ShimLock.Member('M', n, m.name(), m.desc(), m.access() & ~0x0400 & ~0x0200, m.signature());
            sb.append(in).append("/** Linked once for every subclass (@each in the shim spec of ").append(JavaTypes.simple(m.owner())).append("). */\n");
            sb.append(in).append("@Override\n");
            sb.append(in).append(methodHead(c, concrete, simple)).append(" {\n");
            sb.append(delegateBody(m.owner(), b, in + "    ", n));
            sb.append(in).append("}\n");
        }
        // 구현 없는 추상 메서드
        for (ShimLock.Member m : stubsFor.getOrDefault(n, List.of())) {
            ShimLock.Member concrete = new ShimLock.Member('M', n, m.name(), m.desc(), m.access() & ~0x0400 & ~0x0200, m.signature());
            sb.append(in).append("/** Not linked in the shim spec yet. NmsSymbols reports it as missing, so plugin calls through this class get a link-time trap; only calls through the interface reach this body. */\n");
            sb.append(in).append("@Override\n");
            sb.append(in).append(methodHead(c, concrete, simple)).append(" {\n");
            sb.append(in).append("    throw new ").append(UNSUPPORTED).append("(\"").append(simple).append('#').append(m.name())
                    .append("\", \"not linked in the Furnace shim spec yet\");\n");
            sb.append(in).append("}\n");
        }
        // 생성 안쪽 클래스
        for (String inner : origin.keySet()) {
            if (!emitted(inner) || inner.equals(n)) continue;
            int d = inner.lastIndexOf('$');
            if (d > 0 && inner.substring(0, d).equals(n)) sb.append('\n').append(declaration(inner, in));
        }
        return sb.toString();
    }

    /**
     * 최상위 조각이 생성기 전용 생성자 {@code C(ShimCtor …)} 를 스스로 쓰는가(T-232). 사슬 맨 위 조각(엔티티 사슬의 {@code Entity})은 표지에 실린 재료를
     * 받아야 해서 몸통이 있는 생성자를 쓴다 — 그때 생성기는 같은 서술자의 생성자를 만들지 않는다(겹치면 컴파일 오류).
     */
    boolean fragmentDeclaresMarker(String n) {
        if (origin.get(n) != Origin.FRAGMENT || n.indexOf('$') > 0) return false;
        try {
            String text = Files.readString(fragments.resolve(n + ".java"), StandardCharsets.UTF_8);
            return Pattern.compile("(?m)^\\s*(protected|public|private)?\\s*" + Pattern.quote(JavaTypes.simple(n)) + "\\s*\\(\\s*(org\\.domi\\.furnace\\.compat\\.nms\\.)?ShimCtor\\s+\\w+\\s*\\)")
                    .matcher(text).find();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    boolean isComponentField(String n, ShimLock.Member m) {
        if (m.kind() != 'F' || m.isStatic()) return false;
        for (ShimLock.Component k : lock.recordComponents.getOrDefault(n, List.of())) if (k.name().equals(m.name()) && k.desc().equals(m.desc())) return true;
        return false;
    }

    /** 바닐라가 직접 쓴 equals·hashCode·toString 이 있는 레코드는 그 메서드를 손 조각이 써야 한다(자동 생성은 final 이라 모양이 다르다). */
    private void explicitRecordMethods(String n) {
        List<String> want = lock.explicitObjectMethods.get(n);
        if (want == null) return;
        String text = origin.get(n) == Origin.FRAGMENT ? fragmentSrc.text(n) : null;
        for (String m : want) {
            boolean has = text != null && Pattern.compile("(?m)^\\s*(@Override\\s+)?public\\s+(boolean|int|String|java\\.lang\\.String)\\s+" + m + "\\s*\\(").matcher(text).find();
            if (!has) problems.add("record " + n + " writes " + m + " itself in vanilla (not final, no generated body): write it in the fragment src/shims/java/" + n + ".java as a hand method");
        }
    }

    /** 레코드에서 컴파일러가 만드는 멤버: 표준 생성자, 성분 접근자, equals·hashCode·toString. */
    boolean isRecordAuto(String n, ShimLock.Member m) {
        if (m.kind() != 'M' || m.isStatic()) return false;
        List<ShimLock.Component> cs = lock.recordComponents.getOrDefault(n, List.of());
        switch (m.name() + m.desc()) {
            case "equals(Ljava/lang/Object;)Z", "hashCode()I", "toString()Ljava/lang/String;" -> { return true; }
            default -> { }
        }
        if (m.name().equals("<init>")) {
            StringBuilder d = new StringBuilder("(");
            for (ShimLock.Component k : cs) d.append(k.desc());
            return m.desc().equals(d.append(")V").toString());
        }
        for (ShimLock.Component k : cs) if (k.name().equals(m.name()) && m.desc().equals("()" + k.desc())) return true;
        return false;
    }

    /** 레코드 명세 줄이 컴파일러가 만드는 멤버를 잇거나(몸통이 있으면 위임·@hand 를 뜻하므로) 적으면 알린다 — 이름만 적은 줄은 필드·표준 생성자·접근자에 한해 무시한다. */
    private void recordAutoMembers(String n, List<Bound> bs) {
        for (Bound b : bs) {
            ShimLock.Member m = b.lock();
            boolean auto = isComponentField(n, m) || isRecordAuto(n, m);
            if (auto && b.spec().body() != ShimSpec.Body.DECLARE) {
                problems.add(b.spec().where() + ": " + n + "#" + m.name() + " is generated by the compiler in a record (components, canonical constructor, accessors, equals, hashCode, toString);"
                        + " remove the spec line - a different body would not be the vanilla record");
            }
        }
    }

    /**
     * 명세 순서의 열거형 상수. 명세 줄이 {@code NAME = (인자…)} 이면 생성자 인자가 붙은 {@code NAME(인자…)} 로 쓴다
     * (인자는 자바 식 그대로 — 생성자·필드는 손 조각이 쓴다).
     */
    List<String> enumConstants(String n) {
        List<String> consts = new ArrayList<>();
        for (Bound b : bound.getOrDefault(n, List.of())) {
            if (b.lock().kind() != 'F' || (b.lock().access() & 0x4000) == 0) continue;
            if (b.spec().body() == ShimSpec.Body.VALUE) {
                String a = b.spec().target().trim();
                if (!a.startsWith("(") || !a.endsWith(")")) problems.add(b.spec().where() + ": enum constant " + b.lock().name() + " takes constructor arguments as = (args)");
                else consts.add(b.lock().name() + a);
            } else consts.add(b.lock().name());
        }
        return consts;
    }

    /** read=owner·write 의 소유 검사 줄(몸통 첫 줄). 다른 종류는 빈 글. */
    private String ownerCheck(String owner, Bound b, String in) { return ownerCheck(owner, b, in, owner); }

    /** owner 는 명세 줄이 있는 클래스(소유 규칙을 정한다), named 는 검사 문구의 클래스 이름(@each 는 받는 하위 클래스). */
    private String ownerCheck(String owner, Bound b, String in, String named) {
        ShimSpec.Member s = b.spec();
        if (s.kind() == null || !s.kind().checked()) return "";
        ShimSpec.Cls sc = spec.classes.get(owner.replace('/', '.'));
        String method = s.kind() == ShimSpec.Kind.WRITE ? "write" : "read";
        return in + sc.owner().replace('$', '.') + '.' + method + "(this, \"" + JavaTypes.simple(named) + '#' + b.lock().name() + "\");\n";
    }

    private String delegateBody(String owner, Bound b, String in) { return delegateBody(owner, b, in, owner); }

    private String delegateBody(String owner, Bound b, String in, String named) {
        ShimLock.Member m = b.lock();
        ShimSpec.Member s = b.spec();
        StringBuilder sb = new StringBuilder();
        sb.append(ownerCheck(owner, b, in, named));
        List<String> args = new ArrayList<>();
        int n = JavaTypes.descParams(m.desc()).size();
        for (int i = 0; i < n; i++) args.add("a" + i);
        boolean isVoid = JavaTypes.descReturn(m.desc()).equals("void");
        sb.append(in);
        if (!isVoid) sb.append("return ");
        if (s.body() == ShimSpec.Body.SELF) {
            sb.append(m.isStatic() ? JavaTypes.source(owner) : "this").append('.').append(s.target());
        } else {   // DELEGATE·EACH
            sb.append(s.target().replace('$', '.'));   // 소스 이름(안쪽 클래스는 '.')
            if (!m.isStatic()) args.add(0, "this");
        }
        sb.append('(').append(String.join(", ", args)).append(");\n");
        return sb.toString();
    }

    // ───────────────────────── 머리 ─────────────────────────

    String header(String n) {
        ShimLock.Cls c = lock.classes.get(n);
        int a = c.effectiveAccess();
        StringBuilder sb = new StringBuilder();
        for (String an : lock.classAnnotations.getOrDefault(n, List.of())) {
            if (annotationApplies(n, an) && !annotationInFragment(n, an)) sb.append('@').append(JavaTypes.descType(an)).append(' ');
        }
        sb.append(visibility(a));
        boolean nested = n.indexOf('$') > 0;
        boolean rec = c.isRecord();
        if (nested && (a & 0x0008) != 0 && !c.isInterface() && !c.isEnum() && !rec) sb.append("static ");
        if (!c.isInterface() && !c.isEnum() && !rec) {
            if ((a & 0x0400) != 0) sb.append("abstract ");
            if ((a & 0x0010) != 0) sb.append("final ");
        }
        sb.append(c.isInterface() ? "interface " : c.isEnum() ? "enum " : rec ? "record " : "class ");
        sb.append(JavaTypes.simple(n));
        String sup;
        List<String> itfs;
        String tp = "";
        if (c.signature() != null) {
            JavaTypes.ClassSig cs = JavaTypes.classSignature(c.signature());
            tp = cs.typeParams();
            sup = cs.superType();
            itfs = cs.interfaces();
        } else {
            sup = c.superName() == null ? null : JavaTypes.source(c.superName());
            itfs = c.interfaces().stream().map(JavaTypes::source).toList();
        }
        sb.append(tp);
        if (rec) {
            sb.append('(');
            List<ShimLock.Component> cs = lock.recordComponents.get(n);
            for (int i = 0; i < cs.size(); i++) {
                ShimLock.Component k = cs.get(i);
                if (i > 0) sb.append(", ");
                sb.append(k.signature() != null ? JavaTypes.fieldSignature(k.signature()) : JavaTypes.descType(k.desc())).append(' ').append(k.name());
            }
            sb.append(')');
            if (!itfs.isEmpty()) sb.append(" implements ").append(String.join(", ", itfs));
            return sb.toString();
        }
        if (c.isInterface()) {
            if (!itfs.isEmpty()) sb.append(" extends ").append(String.join(", ", itfs));
        } else {
            if (!c.isEnum() && sup != null && !sup.equals("java.lang.Object")) sb.append(" extends ").append(sup);
            if (!itfs.isEmpty()) sb.append(" implements ").append(String.join(", ", itfs));
        }
        return sb.toString();
    }

    /**
     * @FunctionalInterface 는 추상 메서드가 정확히 하나일 때만 컴파일된다 — 명세가 그 추상 메서드를 이은 인터페이스에만 붙인다
     * (안 이은 껍데기 인터페이스에 붙이면 javac 오류. 그때는 추상 메서드를 명세에 적으면 붙는다).
     */
    private boolean annotationApplies(String n, String desc) {
        if (!desc.equals("Ljava/lang/FunctionalInterface;")) return true;
        int abstracts = 0;
        for (Bound b : bound.getOrDefault(n, List.of())) if (b.lock().kind() == 'M' && b.lock().isAbstract()) abstracts++;
        return abstracts == 1;
    }

    /** 손 조각이 머리 앞에 이미 같은 애노테이션을 쓰면 또 붙이지 않는다(겹치면 컴파일 오류). */
    private boolean annotationInFragment(String n, String desc) {
        if (origin.get(n) != Origin.FRAGMENT || n.indexOf('$') > 0) return false;
        String text = fragmentSrc.text(n);
        if (text == null) return false;
        String simple = JavaTypes.simple(desc.substring(1, desc.length() - 1));
        return Pattern.compile("(?m)^\\s*@(?:java\\.lang\\.)?" + Pattern.quote(simple) + "\\b").matcher(text).find();
    }

    private static String visibility(int a) {
        if ((a & 0x0001) != 0) return "public ";
        if ((a & 0x0004) != 0) return "protected ";
        if ((a & 0x0002) != 0) return "private ";
        return "";
    }

    private static String fieldMods(ShimLock.Cls owner, ShimLock.Member m) {
        int a = m.access();
        StringBuilder sb = new StringBuilder(visibility(a));
        if ((a & 0x0008) != 0) sb.append("static ");
        if ((a & 0x0010) != 0) sb.append("final ");
        if ((a & 0x0040) != 0) sb.append("volatile ");
        if ((a & 0x0080) != 0) sb.append("transient ");
        return sb.toString();
    }

    private static String fieldType(ShimLock.Member m) {
        return m.signature() != null ? JavaTypes.fieldSignature(m.signature()) : JavaTypes.descType(m.desc());
    }

    /** 메서드·생성자 머리(몸통 앞까지). */
    String methodHead(ShimLock.Cls c, ShimLock.Member m, String simple) {
        int a = m.access();
        StringBuilder sb = new StringBuilder();
        if (m.name().equals("<init>")) {
            sb.append(visibility(a));
        } else {
            sb.append(visibility(a));
            boolean itf = c.isInterface();
            if ((a & 0x0008) != 0) sb.append("static ");
            if ((a & 0x0400) != 0 && !itf) sb.append("abstract ");
            if (itf && (a & 0x0408) == 0 && (a & 0x0002) == 0) sb.append("default ");
            if ((a & 0x0010) != 0) sb.append("final ");
            if ((a & 0x0020) != 0) sb.append("synchronized ");
        }
        List<String> params;
        String ret;
        String tp = "";
        List<String> descParams = JavaTypes.descParams(m.desc());
        JavaTypes.MethodSig ms = m.signature() != null ? JavaTypes.methodSignature(m.signature()) : null;
        if (ms != null && ms.params().size() == descParams.size()) {
            tp = ms.typeParams();
            params = new ArrayList<>(ms.params());
            ret = ms.ret();
        } else {
            params = new ArrayList<>(descParams);
            ret = JavaTypes.descReturn(m.desc());
        }
        if ((a & 0x0080) != 0 && !params.isEmpty()) params.set(params.size() - 1, JavaTypes.varargs(params.get(params.size() - 1)));
        if (!tp.isEmpty()) sb.append(tp).append(' ');
        if (m.name().equals("<init>")) sb.append(simple);
        else sb.append(ret).append(' ').append(m.name());
        sb.append('(');
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(params.get(i)).append(" a").append(i);
        }
        sb.append(')');
        return sb.toString();
    }

    // ───────────────────────── 손 조각 합치기 ─────────────────────────

    /** 조각 머리는 "class 이름 {" 꼴(접근·상속을 적지 않는다 — 잠금 파일이 정한다). 앞의 애노테이션·문서 주석은 그대로 둔다. */
    String mergeFragment(String top) throws IOException {
        Path p = fragments.resolve(top + ".java");
        String text = Files.readString(p, StandardCharsets.UTF_8);
        ShimLock.Cls c = lock.classes.get(top);
        String simple = JavaTypes.simple(top);
        String pkg = top.substring(0, top.lastIndexOf('/')).replace('/', '.');
        if (!Pattern.compile("(?m)^package\\s+" + Pattern.quote(pkg) + "\\s*;").matcher(text).find()) {
            problems.add(p + ": the package line is not " + pkg + "");
            return "";
        }
        Matcher h = Pattern.compile("(?m)^(class|interface|enum|record)\\s+" + Pattern.quote(simple) + "\\s*\\{").matcher(text);
        if (!h.find()) {
            problems.add(p + ": fragment header not found - write '" + (c.isInterface() ? "interface" : c.isEnum() ? "enum" : c.isRecord() ? "record" : "class") + " " + simple
                    + " {' at the start of a line (access, extends and implements come from the shim lock)");
            return "";
        }
        String kw = c.isInterface() ? "interface" : c.isEnum() ? "enum" : c.isRecord() ? "record" : "class";
        if (!h.group(1).equals(kw)) problems.add(p + ": fragment header kind " + h.group(1) + " differs from vanilla (" + kw + ")");
        if (h.find()) problems.add(p + ": more than one fragment header");
        int close = text.lastIndexOf('}');
        h.reset();
        h.find();
        StringBuilder sb = new StringBuilder();
        sb.append(GEN_NOTE).append(" - edit the hand bodies in src/shims/java/").append(top).append(".java.\n");
        sb.append(text, 0, h.start());
        sb.append(header(top)).append(" {");
        if (c.isEnum()) sb.append("\n    // -- enum constants (shim spec, vanilla order) --\n    ").append(String.join(", ", enumConstants(top))).append(';');
        sb.append(text, h.end(), close);
        String gen = body(top, "    ");
        if (!gen.isEmpty()) sb.append("\n    // -- generated members (shim spec) --\n").append(gen);
        sb.append(text.substring(close));
        return sb.toString();
    }

    static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (Stream<Path> s = Files.walk(p)) {
            for (Path x : s.sorted(Comparator.reverseOrder()).toList()) Files.delete(x);
        }
    }
}
