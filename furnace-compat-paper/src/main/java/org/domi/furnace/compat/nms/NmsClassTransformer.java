package org.domi.furnace.compat.nms;

import java.nio.charset.StandardCharsets;
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
 * Bukkit 호환 플러그인 jar 의 클래스 바이트 변환(H6, 설계 H5 의 H7a 중 접미사 부분만).
 * <ol>
 *   <li>클래스·메서드·필드 참조의 CraftBukkit 버전 접미사({@code org/bukkit/craftbukkit/v1_21_R1/}) → 접미사 없는 이름.</li>
 *   <li>리플렉션 호출 {@code Class.forName(String)}·{@code Class.forName(String, boolean, ClassLoader)}·{@code ClassLoader.loadClass(String)}·
 *       {@code MethodHandles.Lookup.findClass(String)} → {@link CraftNames} 다리(문자열 이름의 접미사를 실행 때 푼다). {@code Class.forName(String)} 은
 *       부른 클래스의 로더로 찾아야 하므로(호출자 민감) 부른 클래스를 상수로 함께 넘긴다.</li>
 * </ol>
 * 바꿀 것이 없는 클래스는 null(원래 바이트)이다. 분기·지역 변수를 바꾸지 않으므로 스택 맵 프레임은 그대로 옮기고 최대 스택만 다시 센다
 * (COMPUTE_FRAMES 의 공통 상위 클래스 계산이 클래스를 로딩하는 문제를 피한다). Spigot 난독화 이름 변환(H7d)과 없는 심볼을
 * UnsupportedNmsCall 로 바꾸는 일(H7a 나머지)은 하지 않는다.
 */
public final class NmsClassTransformer implements ClassTransformer {

    private static final Logger LOG = LoggerFactory.getLogger("Furnace");

    static final String BRIDGE = Type.getInternalName(CraftNames.class);

    /** 이 바이트 조각이 하나도 없으면 바꿀 것이 없다(상수 풀의 UTF-8 이름을 바로 훑는다). */
    private static final byte[][] NEEDLES = {
            CraftNames.VERSIONED_INTERNAL.getBytes(StandardCharsets.UTF_8),
            "forName".getBytes(StandardCharsets.UTF_8),
            "loadClass".getBytes(StandardCharsets.UTF_8),
            "findClass".getBytes(StandardCharsets.UTF_8),
    };

    private final String plugin;
    private final AtomicBoolean announced = new AtomicBoolean();

    /** @param plugin 로그에 쓰는 플러그인 이름 */
    public NmsClassTransformer(String plugin) { this.plugin = plugin; }

    @Override
    public byte[] transform(String className, byte[] bytes) {
        if (!mayNeed(bytes)) return null;
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        boolean[] changed = new boolean[2];   // [0] 접미사 이름을 바꿨다, [1] 리플렉션 호출을 바꿨다
        Remapper remapper = new Remapper() {
            @Override
            public String map(String internalName) {
                String c = CraftNames.canonicalInternal(internalName);
                if (c != internalName) changed[0] = true;
                return c;
            }
        };
        reader.accept(new ClassRemapper(new ReflectionRewriter(writer, changed), remapper), 0);
        if (!changed[0] && !changed[1]) return null;
        if (changed[0] && announced.compareAndSet(false, true)) {
            LOG.info("플러그인 {}: CraftBukkit 버전 접미사 이름({}) 을 이 서버의 접미사 없는 이름으로 바꿔 올린다", plugin, CraftNames.VERSION);
        }
        return writer.toByteArray();
    }

    static boolean mayNeed(byte[] bytes) {
        for (byte[] n : NEEDLES) if (indexOf(bytes, n) >= 0) return true;
        return false;
    }

    private static int indexOf(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0, last = hay.length - needle.length; i <= last; i++) {
            for (int j = 0; j < needle.length; j++) if (hay[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }

    /** 리플렉션 호출을 다리로 돌린다. 이름은 이미 ClassRemapper 를 거친 것이다. */
    private static final class ReflectionRewriter extends ClassVisitor {
        private final boolean[] changed;
        private String self;
        private boolean canLdcClass;

        ReflectionRewriter(ClassVisitor next, boolean[] changed) {
            super(Opcodes.ASM9, next);
            this.changed = changed;
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
                public void visitMethodInsn(int opcode, String owner, String mname, String desc, boolean itf) {
                    if (opcode == Opcodes.INVOKESTATIC && owner.equals("java/lang/Class") && mname.equals("forName")) {
                        if (desc.equals("(Ljava/lang/String;)Ljava/lang/Class;") && canLdcClass) {
                            super.visitLdcInsn(Type.getObjectType(self));
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "forName", "(Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Class;", false);
                            changed[1] = true;
                            return;
                        }
                        if (desc.equals("(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;")) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "forName", desc, false);
                            changed[1] = true;
                            return;
                        }
                    }
                    if (opcode == Opcodes.INVOKEVIRTUAL && mname.equals("loadClass") && desc.equals("(Ljava/lang/String;)Ljava/lang/Class;")
                            && (owner.equals("java/lang/ClassLoader") || owner.equals("java/net/URLClassLoader") || owner.equals("java/security/SecureClassLoader"))) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "loadClass", "(Ljava/lang/ClassLoader;Ljava/lang/String;)Ljava/lang/Class;", false);
                        changed[1] = true;
                        return;
                    }
                    if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals("java/lang/invoke/MethodHandles$Lookup") && mname.equals("findClass")
                            && desc.equals("(Ljava/lang/String;)Ljava/lang/Class;")) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "findClass", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;)Ljava/lang/Class;", false);
                        changed[1] = true;
                        return;
                    }
                    super.visitMethodInsn(opcode, owner, mname, desc, itf);
                }
            };
        }
    }
}
