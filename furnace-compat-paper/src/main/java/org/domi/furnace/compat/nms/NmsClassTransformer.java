package org.domi.furnace.compat.nms;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.domi.furnace.api.plugin.ClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bukkit 호환 플러그인 jar 의 클래스 바이트 변환(H6 + H7a). 한 클래스마다 차례로:
 * <ol>
 *   <li>클래스·메서드·필드 참조의 CraftBukkit 버전 접미사({@code org/bukkit/craftbukkit/v1_21_R1/}) → 접미사 없는 이름(H6).</li>
 *   <li>shim 에 없는 NMS·CraftBukkit 심볼을 가리키는 명령 → 그 자리에서 {@link UnsupportedNmsCall} 을 던지는 덫({@link NmsLinker}, H7a).
 *       없는 NMS 를 상속한 클래스는 정의 때 거절한다.</li>
 *   <li>리플렉션 호출 → 다리: {@code Class.forName}·{@code ClassLoader.loadClass}·{@code Lookup.findClass}({@link CraftNames}, 문자열 이름의
 *       접미사를 실행 때 푼다. {@code Class.forName(String)} 은 호출자 민감이라 부른 클래스를 상수로 넘긴다), NMS 이름을 담은 클래스의
 *       {@code Class#getDeclaredField} 류·{@code Lookup#find*}({@link NmsReflect}, 없으면 경고 한 번 뒤 원래 예외).</li>
 *   <li>Paper 가 옛 플러그인을 위해 바꿔 주는 Bukkit 필드 이름({@link ApiRenames} — 예: {@code ItemFlag.HIDE_POTION_EFFECTS} →
 *       {@code HIDE_ADDITIONAL_TOOLTIP})과 그 {@code valueOf}.</li>
 *   <li>옵션 {@code version-package=versioned} 이면 {@code Class#getName}·{@code getPackageName}·{@code Package#getName} → 접미사를 붙여 읽는 다리.</li>
 * </ol>
 * 바꿀 것이 없는 클래스는 null(원래 바이트)이다. 덫·다리는 분기·지역 변수와 프레임 지점의 스택 모양을 바꾸지 않으므로 스택 맵 프레임은 그대로
 * 옮기고 최대 스택만 다시 센다(COMPUTE_FRAMES 의 공통 상위 클래스 계산이 클래스를 로딩하는 문제를 피한다). Spigot 난독화 이름 변환(H7d)은 하지 않는다.
 */
public final class NmsClassTransformer implements ClassTransformer {

    private static final Logger LOG = LoggerFactory.getLogger("Furnace");

    static final String BRIDGE = Type.getInternalName(CraftNames.class);
    static final String REFLECT = Type.getInternalName(NmsReflect.class);

    /**
     * 서버 옵션.
     *
     * @param strict           {@code plugins.compat.nms.strict} — 미지원 NMS 심볼이 하나라도 있으면 플러그인 로드를 거절한다
     * @param versionedPackage {@code plugins.compat.nms.version-package=versioned} — 플러그인이 읽는 CraftBukkit 이름에 버전 접미사를 붙인다
     */
    public record Options(boolean strict, boolean versionedPackage) {
        public static final Options DEFAULT = new Options(false, false);
    }

    /** 이 바이트 조각이 하나도 없으면 바꿀 것이 없다(상수 풀의 UTF-8 이름을 바로 훑는다). */
    private static final byte[][] NEEDLES = utf8(CraftNames.VERSIONED_INTERNAL, "forName", "loadClass", "findClass", "net/minecraft/", "org/bukkit/craftbukkit/",
            ApiRenames.ITEM_FLAG);
    /** 리플렉션 멤버 다리는 NMS 이름(리터럴 조각 포함)을 담은 클래스에만 건다 — 일반 플러그인 코드의 리플렉션은 건드리지 않는다. */
    private static final byte[][] NMS_HINTS = utf8("minecraft", "craftbukkit");
    private static final byte[][] NAME_READS = utf8("getName", "getPackageName");

    private final String plugin;
    private final Set<String> own;
    private final Options options;
    private final NmsSymbols symbols;
    private final AtomicBoolean announced = new AtomicBoolean();

    /** @param plugin 로그에 쓰는 플러그인 이름 */
    public NmsClassTransformer(String plugin) { this(plugin, Set.of(), Options.DEFAULT, NmsSymbols.RUNTIME); }

    /**
     * @param own 이 플러그인 jar 의 클래스(내부 이름) — NMS 이름이어도 판정하지 않는다
     */
    public NmsClassTransformer(String plugin, Set<String> own, Options options, NmsSymbols symbols) {
        this.plugin = plugin;
        this.own = own;
        this.options = options;
        this.symbols = symbols;
    }

    /**
     * jar 를 한 번 훑어 호환 보고를 남기고 변환기를 만든다. strict 이고 미지원 심볼이 있으면 {@link UnsupportedNmsCall}(플러그인 로드 거절).
     */
    public static NmsClassTransformer forJar(String plugin, Path jar, Options options) {
        NmsCompatReport report;
        try {
            report = NmsCompatReport.scan(plugin, jar, NmsSymbols.RUNTIME);
        } catch (IOException e) {
            LOG.warn("플러그인 {}: NMS 호환 보고를 만들지 못했다({}) — 변환은 그대로 한다", plugin, e.toString());
            return new NmsClassTransformer(plugin, Set.of(), options, NmsSymbols.RUNTIME);
        }
        report.log();
        if (options.strict() && report.unsupported() > 0) {
            throw new UnsupportedNmsCall("플러그인 " + plugin + " 로드",
                    "plugins.compat.nms.strict=true 이고 미지원 NMS 심볼이 " + report.unsupported() + "개 있다(처음 것: "
                            + report.missing().subList(0, Math.min(5, report.missing().size())) + "). strict 를 끄면 그 기능만 실패하고 나머지는 돈다");
        }
        return new NmsClassTransformer(plugin, report.ownClasses(), options, NmsSymbols.RUNTIME);
    }

    @Override
    public byte[] transform(String className, byte[] bytes) {
        boolean names = options.versionedPackage() && contains(bytes, NAME_READS);
        if (!contains(bytes, NEEDLES) && !names) return null;
        boolean members = contains(bytes, NMS_HINTS);
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        boolean[] changed = new boolean[3];   // [0] 접미사 이름을 바꿨다, [1] 리플렉션·이름 읽기 호출을 바꿨다, [2] 덫을 넣었다
        Remapper remapper = new Remapper() {
            @Override
            public String map(String internalName) {
                String c = CraftNames.canonicalInternal(internalName);
                if (c != internalName) changed[0] = true;
                return c;
            }
        };
        ClassVisitor chain = new ReflectionRewriter(writer, changed, plugin, members, names);
        chain = new NmsLinker(chain, symbols, plugin, own, changed, 2);
        reader.accept(new ClassRemapper(chain, remapper), 0);
        if (!changed[0] && !changed[1] && !changed[2]) return null;
        if (changed[0] && announced.compareAndSet(false, true)) {
            LOG.info("플러그인 {}: CraftBukkit 버전 접미사 이름({}) 을 이 서버의 접미사 없는 이름으로 바꿔 올린다", plugin, CraftNames.VERSION);
        }
        return writer.toByteArray();
    }

    static boolean mayNeed(byte[] bytes) { return contains(bytes, NEEDLES); }

    static boolean contains(byte[] bytes, byte[][] needles) {
        for (byte[] n : needles) if (indexOf(bytes, n) >= 0) return true;
        return false;
    }

    private static byte[][] utf8(String... s) {
        byte[][] out = new byte[s.length][];
        for (int i = 0; i < s.length; i++) out[i] = s[i].getBytes(StandardCharsets.UTF_8);
        return out;
    }

    private static int indexOf(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0, last = hay.length - needle.length; i <= last; i++) {
            for (int j = 0; j < needle.length; j++) if (hay[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }

    /** 리플렉션·이름 읽기 호출을 다리로 돌린다. 이름은 이미 ClassRemapper 를 거친 것이다. */
    private static final class ReflectionRewriter extends ClassVisitor {
        private static final String S = "Ljava/lang/String;";
        private final boolean[] changed;
        private final String plugin;
        private final boolean members;
        private final boolean names;
        private String self;
        private boolean canLdcClass;

        ReflectionRewriter(ClassVisitor next, boolean[] changed, String plugin, boolean members, boolean names) {
            super(Opcodes.ASM9, next);
            this.changed = changed;
            this.plugin = plugin;
            this.members = members;
            this.names = names;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            self = name;
            canLdcClass = (version & 0xFFFF) >= Opcodes.V1_5;   // 클래스 상수(ldc Foo.class)는 49 부터
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            return mv == null ? null : new MethodVisitor(Opcodes.ASM9, mv) {
                @Override
                public void visitFieldInsn(int opcode, String owner, String fname, String desc) {
                    String renamed = opcode == Opcodes.GETSTATIC ? ApiRenames.field(owner, fname) : fname;
                    if (!renamed.equals(fname)) changed[1] = true;
                    super.visitFieldInsn(opcode, owner, renamed, desc);
                }

                /** 원래 스택 뒤에 플러그인 이름을 얹고 다리를 부른다. */
                private void bridge(String owner, String mname, String desc) {
                    super.visitLdcInsn(plugin);
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, owner, mname, desc, false);
                    changed[1] = true;
                }

                @Override
                public void visitMethodInsn(int opcode, String owner, String mname, String desc, boolean itf) {
                    if (opcode == Opcodes.INVOKESTATIC && mname.equals("valueOf") && ApiRenames.reroutesValueOf(owner, desc)) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, ApiRenames.INTERNAL, ApiRenames.valueOfName(owner), desc, false);
                        changed[1] = true;
                        return;
                    }
                    if (opcode == Opcodes.INVOKESTATIC && owner.equals("java/lang/Class") && mname.equals("forName")) {
                        if (desc.equals("(Ljava/lang/String;)Ljava/lang/Class;") && canLdcClass) {
                            super.visitLdcInsn(Type.getObjectType(self));
                            bridge(BRIDGE, "forName", "(Ljava/lang/String;Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Class;");
                            return;
                        }
                        if (desc.equals("(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;")) {
                            bridge(BRIDGE, "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;Ljava/lang/String;)Ljava/lang/Class;");
                            return;
                        }
                    }
                    if (opcode == Opcodes.INVOKEVIRTUAL && mname.equals("loadClass") && desc.equals("(Ljava/lang/String;)Ljava/lang/Class;")
                            && (owner.equals("java/lang/ClassLoader") || owner.equals("java/net/URLClassLoader") || owner.equals("java/security/SecureClassLoader"))) {
                        bridge(BRIDGE, "loadClass", "(Ljava/lang/ClassLoader;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Class;");
                        return;
                    }
                    if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals("java/lang/invoke/MethodHandles$Lookup")) {
                        String lk = "Ljava/lang/invoke/MethodHandles$Lookup;";
                        if (mname.equals("findClass") && desc.equals("(Ljava/lang/String;)Ljava/lang/Class;")) {
                            bridge(BRIDGE, "findClass", "(" + lk + S + S + ")Ljava/lang/Class;");
                            return;
                        }
                        if (members && desc.startsWith("(Ljava/lang/Class;") && switch (mname) {
                            case "findVirtual", "findStatic", "findConstructor", "findGetter", "findSetter", "findStaticGetter", "findStaticSetter" -> true;
                            default -> false;
                        }) {
                            bridge(REFLECT, mname, "(" + lk + desc.substring(1, desc.indexOf(')')) + S + desc.substring(desc.indexOf(')')));
                            return;
                        }
                    }
                    if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals("java/lang/Class")) {
                        if (members && switch (mname) {
                            case "getDeclaredField", "getField", "getDeclaredMethod", "getMethod", "getDeclaredConstructor", "getConstructor" -> true;
                            default -> false;
                        }) {
                            bridge(REFLECT, mname, "(Ljava/lang/Class;" + desc.substring(1, desc.indexOf(')')) + S + desc.substring(desc.indexOf(')')));
                            return;
                        }
                        if (names && desc.equals("()Ljava/lang/String;") && (mname.equals("getName") || mname.equals("getPackageName"))) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, REFLECT, mname.equals("getName") ? "versionedName" : "versionedPackageName",
                                    "(Ljava/lang/Class;)Ljava/lang/String;", false);
                            changed[1] = true;
                            return;
                        }
                    }
                    if (names && opcode == Opcodes.INVOKEVIRTUAL && owner.equals("java/lang/Package") && mname.equals("getName") && desc.equals("()Ljava/lang/String;")) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, REFLECT, "versionedPackage", "(Ljava/lang/Package;)Ljava/lang/String;", false);
                        changed[1] = true;
                        return;
                    }
                    super.visitMethodInsn(opcode, owner, mname, desc, itf);
                }
            };
        }
    }
}
