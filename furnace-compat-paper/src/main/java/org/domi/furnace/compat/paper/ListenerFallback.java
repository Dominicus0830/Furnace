// registerEvents 대체 길(T-040a): 리스너 클래스의 메서드 서명을 반사로 훑다가 없는 클래스(대개 어댑터에 없는 Paper 이벤트) 때문에
// NoClassDefFoundError 가 나면, Bukkit/Paper 처럼 그 리스너 클래스를 통째로 버리지 않고 @EventHandler 메서드를 하나씩 따로 푼다.
//
// 왜 필요한가: Class.getMethods()/getDeclaredMethods() 는 리스너 클래스의 *모든* 메서드의 매개변수·반환 형을 한꺼번에 푼다. 보호 플러그인은 처리기를
// 한 클래스에 몰아 두는 일이 많아, 어댑터에 없는 이벤트 클래스를 쓰는 처리기 하나 때문에 BlockBreak·PlayerInteract 처리기까지 모두 등록되지 않았다.
// Paper 에는 그 클래스가 다 있으므로 Paper 에서 불리는 처리기 ⊇ Furnace 에서 불리는 처리기여야 한다(차이는 없는 이벤트뿐).
//
// 어떻게: 클래스 파일 바이트를 ASM 으로 읽어 @EventHandler 메서드의 (이름, 서술자, priority, ignoreCancelled)를 얻고, 메서드마다 매개변수 형을
// 따로 올려 본다. 풀리는 메서드만 MethodHandles.privateLookupIn(...).findVirtual 로 잡는다 — 그 메서드 하나의 서술자만 풀리므로 다른 메서드의
// 없는 형은 상관없다. 정상 경로(getMethods 가 되는 리스너)는 이 클래스를 거치지 않는다.
// 한계: 인터페이스의 default 메서드에 붙은 @EventHandler 는 이 길에서 찾지 않는다(상속 사슬의 클래스만 본다).
package org.domi.furnace.compat.paper;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public final class ListenerFallback {

    private ListenerFallback() {}

    /** 풀린 처리기 하나 — handle 은 (리스너, 이벤트) 를 받는다. */
    public record Handler(String method, Class<? extends Event> event, EventPriority priority, boolean ignoreCancelled, MethodHandle handle) {}

    /** 건너뛴 처리기 — 없는 클래스 때문에 풀지 못한 것. */
    public record Skipped(String method, String missingClass) {}

    /** @param invalid 서명이 이벤트 처리기가 아닌 @EventHandler 메서드(정상 경로와 같은 안내를 한다) */
    public record Result(List<Handler> handlers, List<Skipped> skipped, List<String> invalid) {}

    private static final String EVENT_HANDLER = Type.getDescriptor(org.bukkit.event.EventHandler.class);

    /** 클래스 파일에서 읽은 메서드 하나(@EventHandler 인지 표시). */
    private static final class Found {
        String name;
        String desc;
        int access;
        EventPriority priority = EventPriority.NORMAL;
        boolean ignoreCancelled;
        boolean handler;
    }

    /**
     * 리스너 클래스(와 부모 클래스들)의 @EventHandler 메서드를 하나씩 푼다. Class.getMethods() 와 같은 가시성을 지킨다: 이 클래스의 모든 메서드,
     * 부모 클래스의 공개 메서드(자식이 덮어쓴 것은 자식 것이 이긴다 — 주석이 없는 덮어쓰기는 처리기를 가린다).
     */
    public static Result collect(Class<?> listener) {
        ClassLoader loader = listener.getClassLoader();
        List<Handler> handlers = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Class<?> c = listener; c != null && c != Object.class; c = c.getSuperclass()) {
            List<Found> methods;
            try {
                methods = scan(c);
            } catch (IOException | RuntimeException cannotRead) {
                if (c == listener) throw new IllegalStateException("리스너 클래스 바이트를 읽을 수 없다: " + c.getName(), cannotRead);
                break;   // 부모 쪽은 읽을 수 없으면(라이브러리·JDK) 거기서 멈춘다
            }
            boolean inherited = c != listener;
            for (Found m : methods) {
                if (inherited && (m.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_PUBLIC)) != Opcodes.ACC_PUBLIC) continue;   // 부모의 공개가 아닌 메서드는 getMethods 에 없다
                if (!seen.add(m.name + m.desc)) continue;   // 자식이 덮어썼다
                if (!m.handler || (m.access & (Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC)) != 0) continue;
                String label = m.name + "(" + paramNames(m.desc) + ")";
                Type[] args = Type.getArgumentTypes(m.desc);
                if (args.length != 1 || args[0].getSort() != Type.OBJECT) {
                    invalid.add(c.getName() + "#" + label);
                    continue;
                }
                Class<?> param;
                MethodType type;
                try {
                    param = Class.forName(args[0].getClassName(), false, loader);
                    type = MethodType.fromMethodDescriptorString(m.desc, loader);
                } catch (ClassNotFoundException e) {
                    skipped.add(new Skipped(label, e.getMessage()));
                    continue;
                } catch (TypeNotPresentException e) {
                    skipped.add(new Skipped(label, e.typeName()));
                    continue;
                } catch (LinkageError e) {
                    skipped.add(new Skipped(label, missingName(e)));
                    continue;
                }
                if (!Event.class.isAssignableFrom(param)) {
                    invalid.add(c.getName() + "#" + label);
                    continue;
                }
                try {
                    MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(c, MethodHandles.lookup());
                    boolean isStatic = (m.access & Opcodes.ACC_STATIC) != 0;
                    MethodHandle mh = isStatic ? lookup.findStatic(c, m.name, type) : lookup.findVirtual(c, m.name, type);
                    if (isStatic) mh = MethodHandles.dropArguments(mh, 0, Object.class);
                    mh = mh.asType(MethodType.methodType(void.class, Object.class, Event.class));
                    handlers.add(new Handler(label, param.asSubclass(Event.class), m.priority, m.ignoreCancelled, mh));
                } catch (LinkageError e) {
                    skipped.add(new Skipped(label, missingName(e)));
                } catch (ReflectiveOperationException e) {
                    skipped.add(new Skipped(label, String.valueOf(e)));
                }
            }
        }
        return new Result(handlers, skipped, invalid);
    }

    /** NoClassDefFoundError 의 메시지("a/b/C")를 클래스 이름으로. 다른 링크 오류는 메시지 그대로. */
    private static String missingName(LinkageError e) {
        String msg = e.getMessage();
        return msg == null ? e.getClass().getSimpleName() : msg.replace('/', '.');
    }

    private static String paramNames(String desc) {
        Type[] args = Type.getArgumentTypes(desc);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            String n = args[i].getClassName();
            sb.append(n.substring(n.lastIndexOf('.') + 1));
        }
        return sb.toString();
    }

    private static List<Found> scan(Class<?> c) throws IOException {
        byte[] bytes;
        try (InputStream in = c.getResourceAsStream("/" + c.getName().replace('.', '/') + ".class")) {
            if (in == null) throw new IOException("클래스 파일을 찾을 수 없다: " + c.getName());
            bytes = in.readAllBytes();
        }
        List<Found> out = new ArrayList<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
                Found f = new Found();
                f.name = name;
                f.desc = desc;
                f.access = access;
                out.add(f);
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                        if (!EVENT_HANDLER.equals(descriptor)) return null;
                        f.handler = true;
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override
                            public void visit(String key, Object value) {
                                if ("ignoreCancelled".equals(key) && value instanceof Boolean b) f.ignoreCancelled = b;
                            }

                            @Override
                            public void visitEnum(String key, String enumDesc, String value) {
                                if (!"priority".equals(key)) return;
                                try {
                                    f.priority = EventPriority.valueOf(value);
                                } catch (IllegalArgumentException unknown) {
                                    // 모르는 우선순위 이름은 기본값 — 반사 경로도 어노테이션을 읽지 못하는 것과 같다
                                }
                            }
                        };
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return out;
    }
}
