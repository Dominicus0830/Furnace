package org.domi.furnace.compat.nms;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 플러그인 jar 를 처음 열 때 한 번 남기는 NMS 호환 보고(설계 H5 5.3, H7a). 상수 풀이 NMS·CraftBukkit 이름을 담은 클래스만 읽어
 * 바이트코드가 가리키는 심볼(클래스·필드·메서드)을 세고, 이 서버의 shim 에 있는지·등급 C 인지·아직 없는지로 나눈다.
 * 리플렉션 문자열로만 닿는 이름은 세지 않는다(실행 때 다리가 경고한다).
 *
 * @param plugin    플러그인 이름
 * @param namespace 이름 체계 판정(매니페스트 {@code paperweight-mappings-namespace})
 * @param symbols   센 심볼 수(서로 다른 것)
 * @param present   shim 에 있는 것
 * @param gradeC    등급 C(만들 수 없는 표면)
 * @param unknown   아직 shim 이 없는 것
 * @param obfuscatedLooking 없는 것 중 이름이 1~2글자인 멤버(Spigot 난독화 이름으로 보이는 것)
 * @param missing   없는 심볼 이름(정렬, 전부)
 * @param ownClasses 이 jar 의 클래스(내부 이름) — 변환기가 플러그인 자기 클래스를 판정하지 않게
 * @param otherVersionClasses 다른 버전용 모듈로 보이는 클래스 수 — 세지 않았다({@link #otherVersion(String)})
 * @param otherVersionSymbols 다른 버전 이름({@code org.bukkit.craftbukkit.v1_20_R3}, {@code net.minecraft.server.v1_8_R3})을 가리킨 심볼 수 — 세지 않았다
 * @param spigotNamed 매니페스트로 보아 Spigot 이름 jar 인가(속성이 없거나 {@code spigot}) — Paper 와 같은 판정
 * @param spigotMapped Spigot 이름 대응표(H7d)로 바꾼 뒤의 이름으로 셌다(대응표가 있고 Spigot 이름 jar 일 때)
 */
public record NmsCompatReport(String plugin, String namespace, int symbols, int present, int gradeC, int unknown,
                              int obfuscatedLooking, List<String> missing, Set<String> ownClasses,
                              int otherVersionClasses, int otherVersionSymbols, boolean spigotNamed, boolean spigotMapped) {

    private static final Logger LOG = LoggerFactory.getLogger("Furnace");

    /**
     * 클래스 이름에 다른 Minecraft 버전 표지가 있는가(여러 버전을 싣는 라이브러리의 버전 모듈: {@code nms/v1_20_1}, {@code NMS_1_20_R3},
     * {@code Wrapper1_8_R3}, {@code v1_21_2_3}). 그런 모듈은 서버 버전을 보고 고르므로 1.21.1 에서는 올리지 않는다 — 보고·strict 에서 뺀다.
     * 1.21.1 표지({@code 1_21_R1}, {@code 1_21_1}, 범위 {@code 1_21_0_1})나 부 버전 없는 {@code 1_21} 은 다른 버전이 아니다.
     */
    static boolean otherVersion(String internalName) {
        if (YEAR_VERSION_TOKEN.matcher(internalName).find()) return true;   // 연도식 새 버전(26.1 = v26_1) — 1.21.1 보다 새롭다
        java.util.regex.Matcher m = VERSION_TOKEN.matcher(internalName);
        boolean other = false;
        while (m.find()) {
            int minor = Integer.parseInt(m.group(1));
            if (minor != 21) {
                other = true;
                continue;
            }
            if (m.group(2) != null) {                      // _R<n>: 1.21/1.21.1 은 R1
                if (!m.group(2).equals("1")) other = true;
                else return false;
                continue;
            }
            if (m.group(3) == null) return false;           // 1_21 — 부 버전 없음(현재로 친다)
            String[] parts = m.group(3).substring(1).split("_");
            int lo = Integer.parseInt(parts[0]);
            int hi = Integer.parseInt(parts[parts.length - 1]);
            if (lo <= 1 && 1 <= hi) return false;
            other = true;
        }
        return other;
    }

    /** 연도식 버전 이름의 패키지·클래스 조각: {@code v26_1}, {@code v26_2_1}(앞이 글자·숫자가 아닌 'v' 로 시작). */
    private static final java.util.regex.Pattern YEAR_VERSION_TOKEN =
            java.util.regex.Pattern.compile("(?<![0-9A-Za-z])v(?:2[6-9]|[3-9][0-9])_\\d{1,2}(?:_\\d{1,2})?(?![0-9])");

    private static final java.util.regex.Pattern VERSION_TOKEN =
            java.util.regex.Pattern.compile("(?<![0-9])1_(\\d{1,2})(?:_R(\\d+)|((?:_\\d{1,2})+))?(?![0-9])");

    private static final java.util.concurrent.ConcurrentHashMap<String, Boolean> SEND_UNSUPPORTED = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 스키마로 생성한 clientbound 패킷 shim 인데 보내기 변환이 아직 없는가({@link SchemaPacket} 이고 {@link NmsPacket} 이 아니며
     * {@link SchemaPacketBridge#supports} 도 아니다 — 손 코덱·타입 레코드 종류, 값 shim 이 빈 껍데기인 종류).
     * 그런 패킷을 만드는 플러그인은 보낼 때 실패하므로 보고·strict 가 미지원으로 센다. 클래스는 초기화하지 않고 본다.
     */
    static boolean sendUnsupported(String internalName) {
        return SEND_UNSUPPORTED.computeIfAbsent(internalName, n -> {
            String bin = n.replace('/', '.');
            var def = org.domi.furnace.api.packet.schema.PacketSchema.protocol767().packetByClass(bin);
            if (def.isEmpty() || def.get().direction() != org.domi.furnace.api.packet.PacketDirection.CLIENTBOUND) return false;
            try {
                Class<?> c = Class.forName(bin, false, NmsCompatReport.class.getClassLoader());
                return SchemaPacket.class.isAssignableFrom(c) && !NmsPacket.class.isAssignableFrom(c) && !SchemaPacketBridge.supports(def.get());
            } catch (ClassNotFoundException | LinkageError ex) {
                return false;
            }
        });
    }

    /** 미지원(등급 C + 아직 없음) 심볼 수. */
    public int unsupported() { return gradeC + unknown; }

    /** jar 를 훑는다(Spigot 이름 대응표 없이). */
    public static NmsCompatReport scan(String plugin, Path jar, NmsSymbols index) throws IOException { return scan(plugin, jar, index, null); }

    /**
     * 1단계 — jar 를 한 번 읽은 결과. 바이트를 읽는 일은 여기서 끝나므로, 서버가 "Spigot 이름 대응표가 필요한가"를 따로 jar 를 훑어 묻지 않고
     * {@link #needsSpigotMappings()} 로 답을 얻은 뒤(필요할 때만 대응표를 준비해) {@link #analyze} 로 이어 갈 수 있다.
     *
     * @param nmsReference 어느 클래스든 {@code net/minecraft/} 나 {@code net.minecraft.}(리터럴·리플렉션 문자열) 를 담았다
     */
    public record Read(String namespace, boolean spigotNamed, Set<String> own, List<byte[]> candidates, boolean nmsReference) {
        /**
         * 이 jar 에 Spigot → Mojang 이름 대응표가 필요한가: 매니페스트에 {@code paperweight-mappings-namespace: mojang} 이 없고(Paper 와 같은 판정 —
         * 속성이 없으면 Spigot 이름으로 본다) NMS 이름을 담은 클래스가 하나라도 있다.
         */
        public boolean needsSpigotMappings() { return spigotNamed && nmsReference; }
    }

    private static final int SLASHED = 1, DOTTED = 2, CRAFT = 4;
    private static final byte[] NET_SLASHED = "net/minecraft/".getBytes(StandardCharsets.UTF_8);
    private static final byte[] NET_DOTTED = "net.minecraft.".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CRAFTBUKKIT = "org/bukkit/craftbukkit/".getBytes(StandardCharsets.UTF_8);

    /** 클래스 바이트를 한 번 훑어 {@code net/minecraft/}·{@code net.minecraft.}·{@code org/bukkit/craftbukkit/} 가 있는지 비트로 돌려준다(세 번 따로 훑지 않는다). */
    static int scanNames(byte[] b) {
        int hits = 0;
        for (int i = 0, n = b.length; i < n; i++) {
            byte c = b[i];
            if (c == 'n') {
                if ((hits & SLASHED) == 0 && at(b, i, NET_SLASHED)) hits |= SLASHED;
                else if ((hits & DOTTED) == 0 && at(b, i, NET_DOTTED)) hits |= DOTTED;
            } else if (c == 'o' && (hits & CRAFT) == 0 && at(b, i, CRAFTBUKKIT)) {
                hits |= CRAFT;
            }
            if (hits == (SLASHED | DOTTED | CRAFT)) break;
        }
        return hits;
    }

    private static boolean at(byte[] hay, int i, byte[] needle) {
        if (i + needle.length > hay.length) return false;
        for (int j = 1; j < needle.length; j++) if (hay[i + j] != needle[j]) return false;
        return true;
    }

    /** jar 를 훑는다. 읽지 못하는 클래스는 건너뛴다. */
    public static Read read(Path jar) throws IOException {
        Set<String> own = new HashSet<>();
        List<byte[]> candidates = new ArrayList<>();
        String namespace;
        boolean spigotNamed;
        boolean nmsReference = false;
        try (JarFile jf = new JarFile(jar.toFile(), false, java.util.zip.ZipFile.OPEN_READ, Runtime.version())) {
            var man = jf.getManifest();
            String ns = man == null ? null : man.getMainAttributes().getValue("paperweight-mappings-namespace");
            spigotNamed = ns == null || ns.equalsIgnoreCase("spigot");
            namespace = ns == null ? "Spigot(가정 — 매니페스트에 paperweight-mappings-namespace 없음)" : ns.equalsIgnoreCase("mojang") ? "Mojang" : ns.equalsIgnoreCase("spigot") ? "Spigot" : ns;
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                String n = e.getName();
                if (!n.endsWith(".class") || n.equals("module-info.class") || n.endsWith("/module-info.class")) continue;
                if (n.startsWith("META-INF/versions/")) {
                    int s = n.indexOf('/', "META-INF/versions/".length());
                    if (s < 0) continue;
                    n = n.substring(s + 1);
                }
                own.add(n.substring(0, n.length() - ".class".length()));
                try (InputStream in = jf.getInputStream(e)) {
                    byte[] b = in.readAllBytes();
                    int hits = scanNames(b);
                    if ((hits & (SLASHED | CRAFT)) != 0) candidates.add(b);
                    if ((hits & (SLASHED | DOTTED)) != 0) nmsReference = true;
                }
            }
        }
        return new Read(namespace, spigotNamed, own, candidates, nmsReference);
    }

    /** jar 를 훑는다(Spigot 이름 대응표 있으면 먼저 이름을 바꾼 뒤 센다). {@link #read} + {@link #analyze}. */
    public static NmsCompatReport scan(String plugin, Path jar, NmsSymbols index, SpigotMappings spigot) throws IOException {
        return analyze(plugin, read(jar), index, spigot);
    }

    /**
     * 2단계 — 읽은 결과를 센다. {@code spigot} 이 있고 jar 가 Spigot 이름이면 변환기와 같은 이름 바꾸기를 먼저 건 뒤 센다.
     */
    public static NmsCompatReport analyze(String plugin, Read read, NmsSymbols index, SpigotMappings spigot) {
        Set<String> own = read.own();
        List<byte[]> candidates = read.candidates();
        String namespace = read.namespace();
        boolean spigotNamed = read.spigotNamed();
        Set<String> seen = new HashSet<>();
        TreeSet<String> missing = new TreeSet<>();
        int[] counts = new int[6];   // present, gradeC, unknown, obf, 다른 버전 클래스, 다른 버전 심볼
        SpigotRemapper sp = spigot != null && spigotNamed ? new SpigotRemapper(spigot, index, own) : null;
        Remapper suffix = sp == null ? new Remapper() {
            @Override
            public String map(String internalName) { return CraftNames.canonicalInternal(internalName); }
        } : new Remapper() {
            @Override
            public String map(String internalName) { return CraftNames.canonicalInternal(sp.map(internalName)); }

            @Override
            public String mapMethodName(String owner, String name, String descriptor) { return sp.mapMethodName(owner, name, descriptor); }

            @Override
            public String mapFieldName(String owner, String name, String descriptor) { return sp.mapFieldName(owner, name, descriptor); }

            @Override
            public String mapInvokeDynamicMethodName(String name, String descriptor) {
                return sp.mapInvokeDynamicMethodName(name, descriptor);
            }
        };
        for (byte[] b : candidates) {
            try {
                new ClassReader(b).accept(new ClassRemapper(new Collector(index, own, seen, missing, counts), suffix),
                        ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
            } catch (RuntimeException ignored) {
                // 읽지 못하는 클래스 — 보고에서 뺀다
            }
        }
        int total = counts[0] + counts[1] + counts[2];
        return new NmsCompatReport(plugin, namespace, total, counts[0], counts[1], counts[2], counts[3], List.copyOf(missing), Set.copyOf(own),
                counts[4], counts[5], spigotNamed, sp != null);
    }

    /** 로드 로그. NMS 에 닿지 않는 jar 는 아무것도 남기지 않는다. */
    public void log() {
        if (symbols == 0 && otherVersionClasses == 0 && otherVersionSymbols == 0) return;
        String hint = spigotMapped
                ? " Spigot 이름 → Mojang 이름 대응표로 바꿔 올린다(plugins.compat.nms.spigot-mappings)." + (obfuscatedLooking > 0
                        ? " 대응표로도 풀리지 않은 난독화 이름 멤버 " + obfuscatedLooking + "개는 그 호출이 실패한다." : "")
                : spigotNamed && missing.stream().anyMatch(n -> n.startsWith("net.minecraft."))
                ? " Spigot 이름 jar 로 보인다" + (obfuscatedLooking > 0 ? "(난독화 이름 멤버 " + obfuscatedLooking + "개)" : "")
                        + " — Spigot→Mojang 대응표가 없어 shim 에 없는 그 이름의 호출은 실패한다. furnace.properties 에 plugins.compat.nms.spigot-mappings=download(기본)"
                        + " 로 두면 처음 한 번 Mojang·Spigot 매핑을 받아 바꿔 올린다(이미 download 라면 시작 로그의 받기 실패 경고를 보라)."
                : obfuscatedLooking > 0
                ? " 난독화 이름으로 보이는 멤버 " + obfuscatedLooking + "개는 그 호출이 실패한다."
                : "";
        String other = otherVersionClasses + otherVersionSymbols > 0
                ? " 다른 버전용 모듈 클래스 " + otherVersionClasses + "개·다른 버전 이름 " + otherVersionSymbols + "개는 1.21.1 에서 쓰이지 않아 세지 않았다."
                : "";
        LOG.info("플러그인 {}: NMS·CraftBukkit 심볼 {}개 중 shim 있음 {}, 등급 C {}, 아직 없음 {}. 이름 체계={}.{}{}",
                plugin, symbols, present, gradeC, unknown, namespace, hint, other);
        if (!missing.isEmpty()) {
            List<String> head = missing.subList(0, Math.min(20, missing.size()));
            LOG.warn("플러그인 {}: 미지원 NMS 심볼 {}개(처음 {}개): {} — 그 호출이 실행될 때만 UnsupportedNmsCall 을 던진다(나머지 기능은 돈다)",
                    plugin, missing.size(), head.size(), head);
        }
    }

    // ------------------------------------------------------------------ 수집

    private static final class Collector extends ClassVisitor {
        private final NmsSymbols index;
        private final Set<String> own;
        private final Set<String> seen;
        private final Set<String> missing;
        private final int[] counts;

        private boolean skip;   // 다른 버전용 모듈 클래스

        Collector(NmsSymbols index, Set<String> own, Set<String> seen, Set<String> missing, int[] counts) {
            super(Opcodes.ASM9);
            this.index = index;
            this.own = own;
            this.seen = seen;
            this.missing = missing;
            this.counts = counts;
        }

        private boolean judged(String owner) {
            if (skip) return false;
            String e = NmsSymbols.element(owner);
            if (e == null || !NmsSymbols.isNms(e) || own.contains(e)) return false;
            if (otherVersion(e)) {   // 다른 버전 접미사 이름(v1_21_R1 은 CraftNames 가 이미 풀었다)
                if (seen.add("V " + e)) counts[5]++;
                return false;
            }
            return true;
        }

        private void cls(String internalName) {
            if (!judged(internalName)) return;
            String e = NmsSymbols.element(internalName);
            if (!seen.add("C " + e)) return;
            if (index.hasClass(e)) counts[0]++;
            else miss(e, NmsLinker.dots(e), false);
        }

        private void member(String owner, String name, String desc, boolean field) {
            if (!judged(owner)) return;
            String e = NmsSymbols.element(owner);
            if (!index.hasClass(e)) {
                cls(e);
                return;
            }
            if (owner.charAt(0) == '[') return;   // 배열의 clone 등
            if (!seen.add((field ? "F " : "M ") + owner + "#" + name + desc)) return;
            boolean ok = field ? index.hasField(owner, name, desc) : index.hasMethod(owner, name, desc);
            if (ok && !field && name.equals("<init>") && sendUnsupported(e)) {
                // 생성 패킷 중 보내기 다리가 아직 없는 것: 만들 수는 있지만 보내면 UnsupportedNmsCall — 보고·strict 에서 미지원으로 센다
                miss(owner, NmsLinker.dots(owner) + "#<init>(" + Type.getArgumentTypes(desc).length + ") — 생성 패킷, 보내기 미지원", false);
                return;
            }
            if (ok) counts[0]++;
            else miss(owner, NmsLinker.dots(owner) + "#" + name + (field ? "" : "(" + Type.getArgumentTypes(desc).length + ")"),
                    name.length() <= 2 && !name.equals("<init>"));
        }

        private void methodType(String desc) {
            Type mt = Type.getMethodType(desc);
            for (Type t : mt.getArgumentTypes()) if (t.getSort() == Type.OBJECT || t.getSort() == Type.ARRAY) cls(t.getInternalName());
            Type r = mt.getReturnType();
            if (r.getSort() == Type.OBJECT || r.getSort() == Type.ARRAY) cls(r.getInternalName());
        }

        private void handle(Handle h) {
            boolean field = h.getTag() <= Opcodes.H_PUTSTATIC;
            member(h.getOwner(), h.getName(), h.getDesc(), field);
            if (!field) methodType(h.getDesc());
        }

        private void miss(String owner, String label, boolean obf) {
            if (NmsGrades.isGradeC(owner)) counts[1]++;
            else counts[2]++;
            if (obf) counts[3]++;
            missing.add(label);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            skip = otherVersion(name);
            if (skip) {
                counts[4]++;
                return;
            }
            // 있는 NMS 클래스라도 상속은 정의 때 거부된다(NmsLinker.visit 와 같은 판정) — 등급 C 로 센다
            if (superName != null && superName.startsWith("net/minecraft/") && !own.contains(superName) && !otherVersion(superName)
                    && index.hasClass(superName)) {
                if (seen.add("S " + name)) {
                    counts[1]++;
                    missing.add("상속 " + NmsLinker.dots(name) + " → " + NmsLinker.dots(superName));
                }
            } else if (superName != null) {
                cls(superName);
            }
            if (interfaces != null) for (String i : interfaces) cls(i);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitTypeInsn(int opcode, String type) { cls(type); }

                @Override
                public void visitFieldInsn(int opcode, String owner, String n, String d) { member(owner, n, d, true); }

                @Override
                public void visitMethodInsn(int opcode, String owner, String n, String d, boolean itf) { member(owner, n, d, false); }

                @Override
                public void visitLdcInsn(Object value) {
                    if (value instanceof Type t && (t.getSort() == Type.OBJECT || t.getSort() == Type.ARRAY)) cls(t.getInternalName());
                    else if (value instanceof Type t && t.getSort() == Type.METHOD) methodType(t.getDescriptor());
                    else if (value instanceof Handle h) handle(h);
                }

                @Override
                public void visitMultiANewArrayInsn(String d, int dims) { cls(Type.getType(d).getInternalName()); }

                @Override
                public void visitInvokeDynamicInsn(String n, String d, Handle bsm, Object... args) {
                    methodType(d);
                    for (Object a : args) {
                        if (a instanceof Handle h) handle(h);
                        else if (a instanceof Type t && t.getSort() == Type.METHOD) methodType(t.getDescriptor());
                        else if (a instanceof Type t && (t.getSort() == Type.OBJECT || t.getSort() == Type.ARRAY)) cls(t.getInternalName());
                    }
                }
            };
        }
    }
}
