package org.domi.furnace.compat.nms;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * 이 서버가 실제로 가진 NMS·CraftBukkit 심볼 색인(H7a). 플러그인 바이트코드가 가리키는 클래스·필드·메서드가 shim 에 있는지를
 * 클래스를 <b>로딩하지 않고</b> 클래스 파일만 읽어(ASM, 코드 건너뜀) 답한다 — 변환 중에 클래스를 로딩하면 병렬 로더에서 순서가 꼬인다.
 * 멤버 찾기는 JVM 의 해석 규칙(JVMS 5.4.3.2·5.4.3.3)처럼 상위 클래스·상위 인터페이스를 따라간다(그 사이의 java.*·org.bukkit.* 도 읽는다).
 *
 * <p>대상 이름공간은 {@code net/minecraft/} 와 {@code org/bukkit/craftbukkit/} 뿐이다. 다른 이름(Bukkit API·서드파티)은 여기서 판정하지 않는다.
 */
public final class NmsSymbols {

    /** 모든 플러그인이 같이 쓰는 색인(호환 모듈의 클래스로더 기준 — 플러그인 로더의 부모). */
    public static final NmsSymbols RUNTIME = new NmsSymbols(NmsSymbols.class.getClassLoader());

    private static final Info MISSING = new Info(null, List.of(), false, Set.of(), Set.of());

    private final ClassLoader loader;
    private final ConcurrentHashMap<String, Info> cache = new ConcurrentHashMap<>();

    NmsSymbols(ClassLoader loader) { this.loader = loader; }

    /** NMS·CraftBukkit 이름공간의 내부 이름인가(배열이면 원소 종류로). */
    public static boolean isNms(String internalName) {
        String n = element(internalName);
        return n != null && (n.startsWith("net/minecraft/") || n.startsWith("org/bukkit/craftbukkit/"));
    }

    /** 배열 서술자({@code [[Lfoo/Bar;})면 원소 클래스 내부 이름, 기본형 배열이면 null, 아니면 그대로. */
    static String element(String internalName) {
        if (internalName == null || internalName.isEmpty() || internalName.charAt(0) != '[') return internalName;
        int i = 0;
        while (i < internalName.length() && internalName.charAt(i) == '[') i++;
        if (i >= internalName.length() || internalName.charAt(i) != 'L') return null;
        return internalName.substring(i + 1, internalName.length() - 1);
    }

    /** 클래스(배열이면 원소 클래스)가 있는가. */
    public boolean hasClass(String internalName) {
        String n = element(internalName);
        return n == null || info(n) != MISSING;
    }

    /** 필드가 있는가(선언 클래스 → 상위 인터페이스 → 상위 클래스 순, JVMS 5.4.3.2). */
    public boolean hasField(String owner, String name, String desc) {
        return findField(element(owner), name + ' ' + desc, new HashSet<>());
    }

    /** 메서드가 있는가(클래스 사슬 → 상위 인터페이스, JVMS 5.4.3.3/5.4.3.4). 배열의 clone 등 Object 메서드도 된다. */
    public boolean hasMethod(String owner, String name, String desc) {
        String key = name + ' ' + desc;
        if (owner.charAt(0) == '[') return info("java/lang/Object").methods.contains(key) || name.equals("clone");
        Set<String> seen = new HashSet<>();
        for (String c = owner; c != null; ) {
            Info i = info(c);
            if (i == MISSING) return false;
            if (i.methods.contains(key)) return true;
            c = i.superName;   // 인터페이스 클래스 파일의 상위는 java/lang/Object 다 — 인터페이스 메서드 해석도 Object 메서드를 본다
        }
        // 인터페이스 쪽(기본 메서드·추상 메서드 모두 해석은 된다)
        return findInInterfaces(owner, key, seen);
    }

    private boolean findInInterfaces(String c, String key, Set<String> seen) {
        if (c == null || !seen.add(c)) return false;
        Info i = info(c);
        if (i == MISSING) return false;
        for (String itf : i.interfaces) {
            Info ii = info(itf);
            if (ii.methods.contains(key)) return true;
            if (findInInterfaces(itf, key, seen)) return true;
        }
        return findInInterfaces(i.superName, key, seen);
    }

    private boolean findField(String c, String key, Set<String> seen) {
        if (c == null || !seen.add(c)) return false;
        Info i = info(c);
        if (i == MISSING) return false;
        if (i.fields.contains(key)) return true;
        for (String itf : i.interfaces) if (findField(itf, key, seen)) return true;
        return findField(i.superName, key, seen);
    }

    /** 이 클래스의 바로 위 클래스(없거나 모르면 null). */
    String superOf(String internalName) { return info(internalName).superName; }

    boolean isInterface(String internalName) { return info(internalName).isInterface; }

    /** 이 클래스가 바로 구현하는 인터페이스(없거나 모르면 빈 목록). */
    List<String> interfacesOf(String internalName) { return info(internalName).interfaces; }

    private Info info(String internalName) {
        Info i = cache.get(internalName);
        if (i != null) return i;
        Info read = read(internalName);
        Info prev = cache.putIfAbsent(internalName, read);
        return prev != null ? prev : read;
    }

    private Info read(String internalName) {
        try (InputStream in = loader == null ? ClassLoader.getSystemResourceAsStream(internalName + ".class")
                : loader.getResourceAsStream(internalName + ".class")) {
            if (in == null) return MISSING;
            ClassReader r = new ClassReader(in.readAllBytes());
            Set<String> fields = new HashSet<>();
            Set<String> methods = new HashSet<>();
            Object[] head = new Object[3];
            r.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                    head[0] = superName;
                    head[1] = interfaces == null ? List.of() : List.of(interfaces);
                    head[2] = (access & Opcodes.ACC_INTERFACE) != 0;
                }

                @Override
                // private 멤버는 다른 클래스에서 링크할 수 없다 — 플러그인 바이트코드가 보기에는 없는 것과 같다
                public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    if ((access & Opcodes.ACC_PRIVATE) == 0) fields.add(name + ' ' + descriptor);
                    return null;
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if ((access & Opcodes.ACC_PRIVATE) == 0) methods.add(name + ' ' + descriptor);
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            @SuppressWarnings("unchecked") List<String> itfs = (List<String>) head[1];
            return new Info((String) head[0], itfs, (Boolean) head[2], Set.copyOf(fields), Set.copyOf(methods));
        } catch (IOException | RuntimeException e) {
            return MISSING;
        }
    }

    private record Info(String superName, List<String> interfaces, boolean isInterface, Set<String> fields, Set<String> methods) {}
}
