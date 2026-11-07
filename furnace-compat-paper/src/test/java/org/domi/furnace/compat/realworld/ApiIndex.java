package org.domi.furnace.compat.realworld;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 호환 계층이 내놓은 클래스에 대해 "이 이름·서명이 있나"를 JVM 의 심볼 해석 규칙(JVMS 5.4.3)대로 답한다.
 * 서명은 반환형까지 정확히 맞아야 한다 — 실제 Bukkit 으로 컴파일된 바이트코드가 그 서명 그대로 찾기 때문이다.
 */
public final class ApiIndex {

    private final ClassLoader loader;
    private final Map<String, Class<?>> classes = new HashMap<>();
    private final Set<String> absent = new HashSet<>();

    public ApiIndex(ClassLoader loader) {
        this.loader = loader;
    }

    /** 내부 이름으로 클래스를 찾는다(초기화 없이). 없으면 {@code null}. */
    public Class<?> find(String internalName) {
        if (absent.contains(internalName)) return null;
        Class<?> c = classes.get(internalName);
        if (c != null) return c;
        try {
            c = Class.forName(internalName.replace('/', '.'), false, loader);
            classes.put(internalName, c);
            return c;
        } catch (ClassNotFoundException | LinkageError e) {
            absent.add(internalName);
            return null;
        }
    }

    public boolean hasMember(ClassRefs.Member m) {
        Class<?> c = find(m.owner());
        if (c == null) return false;
        if (m.field()) return hasField(c, m.name(), m.descriptor(), new HashSet<>());
        if (m.name().equals("<init>")) return hasConstructor(c, m.descriptor());
        return hasMethod(c, m.name(), m.descriptor(), new HashSet<>()) || objectHas(m.name(), m.descriptor());
    }

    private static boolean hasConstructor(Class<?> c, String desc) {
        for (Constructor<?> k : c.getDeclaredConstructors()) {
            if (descriptor(k.getParameterTypes(), void.class).equals(desc)) return true;
        }
        return false;
    }

    /** 필드는 이름과 형식(서술자)이 모두 맞아야 한다 — 다르면 런타임에 NoSuchFieldError. */
    private static boolean hasField(Class<?> c, String name, String desc, Set<Class<?>> seen) {
        if (c == null || !seen.add(c)) return false;
        try {
            if (descriptor(c.getDeclaredField(name).getType()).equals(desc)) return true;
        } catch (NoSuchFieldException e) {
            // 상위로
        }
        for (Class<?> i : c.getInterfaces()) if (hasField(i, name, desc, seen)) return true;
        return hasField(c.getSuperclass(), name, desc, seen);
    }

    private static boolean hasMethod(Class<?> c, String name, String desc, Set<Class<?>> seen) {
        if (c == null || !seen.add(c)) return false;
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals(name) && descriptor(m.getParameterTypes(), m.getReturnType()).equals(desc)) return true;
        }
        if (hasMethod(c.getSuperclass(), name, desc, seen)) return true;
        for (Class<?> i : c.getInterfaces()) if (hasMethod(i, name, desc, seen)) return true;
        return false;
    }

    private static boolean objectHas(String name, String desc) {
        return hasMethod(Object.class, name, desc, new HashSet<>());
    }

    static String descriptor(Class<?>[] params, Class<?> ret) {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> p : params) sb.append(descriptor(p));
        return sb.append(')').append(descriptor(ret)).toString();
    }

    static String descriptor(Class<?> c) {
        if (c.isArray()) return "[" + descriptor(c.getComponentType());
        if (c == void.class) return "V";
        if (c == int.class) return "I";
        if (c == long.class) return "J";
        if (c == boolean.class) return "Z";
        if (c == byte.class) return "B";
        if (c == char.class) return "C";
        if (c == short.class) return "S";
        if (c == float.class) return "F";
        if (c == double.class) return "D";
        return "L" + c.getName().replace('.', '/') + ";";
    }
}
