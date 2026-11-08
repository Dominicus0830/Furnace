package org.domi.furnace.compat.nms;

import java.lang.invoke.MethodHandles;

/**
 * CraftBukkit 버전 접미사 규칙과 리플렉션 다리(H6).
 *
 * <p>Spigot 은 CraftBukkit 을 {@code org.bukkit.craftbukkit.v1_21_R1.*} 에 두고, Paper(1.20.5 이후)와 Furnace 는 접미사 없는
 * {@code org.bukkit.craftbukkit.*} 에 둔다. 두 이름 모두로 컴파일된 플러그인이 돌도록 플러그인 클래스를 올릴 때 바이트코드의 클래스 참조를
 * 바꾸고({@link NmsClassTransformer}), 문자열로 이름을 만드는 리플렉션({@code Class.forName}·{@code ClassLoader.loadClass}·
 * {@code Lookup.findClass})은 이 클래스의 정적 메서드로 돌려 이름을 푼다.
 *
 * <p>이 서버 버전(1.21.1)의 접미사 {@value #VERSION} 만 푼다 — 다른 접미사({@code v1_20_R3} 등)는 그대로라 진짜처럼 없다
 * ({@code ClassNotFoundException}). 여러 버전 모듈을 싣고 "어느 버전 클래스가 있나"로 고르는 라이브러리가 엉뚱한 모듈을 고르지 않게 하기 위해서다.
 * 접미사가 없는 이름은 아무것도 바꾸지 않는다.
 */
public final class CraftNames {

    /** CraftBukkit 1.21·1.21.1 의 패키지 접미사. */
    public static final String VERSION = "v1_21_R1";

    static final String PLAIN = "org.bukkit.craftbukkit.";
    static final String VERSIONED = PLAIN + VERSION + ".";
    static final String PLAIN_INTERNAL = "org/bukkit/craftbukkit/";
    static final String VERSIONED_INTERNAL = PLAIN_INTERNAL + VERSION + "/";

    private CraftNames() {}

    /** 이진 이름(점 구분, 배열 이름 {@code [Lorg.bukkit...;} 포함)의 이 서버 버전 접미사를 지운다. 없으면 같은 문자열. */
    public static String canonical(String name) {
        if (name == null || name.indexOf(VERSIONED) < 0) return name;
        return name.replace(VERSIONED, PLAIN);
    }

    /** 내부 이름(슬래시 구분)의 이 서버 버전 접미사를 지운다. 없으면 같은 문자열. */
    public static String canonicalInternal(String internalName) {
        if (internalName == null || !internalName.startsWith(VERSIONED_INTERNAL)) return internalName;
        return PLAIN_INTERNAL + internalName.substring(VERSIONED_INTERNAL.length());
    }

    /**
     * 접미사 없는 CraftBukkit 이름에 이 서버 버전 접미사를 붙인다(옵션 {@code version-package=versioned} 의 이름 읽기).
     * 패키지 이름({@code org.bukkit.craftbukkit})·클래스 이름·배열 이름 모두. CraftBukkit 이 아니거나 이미 붙었으면 그대로.
     */
    public static String versioned(String name) {
        if (name == null) return null;
        if (name.equals("org.bukkit.craftbukkit")) return VERSIONED.substring(0, VERSIONED.length() - 1);
        int i = name.indexOf(PLAIN);
        if (i < 0 || name.startsWith(VERSION + ".", i + PLAIN.length())) return name;
        return name.substring(0, i) + VERSIONED + name.substring(i + PLAIN.length());
    }

    // ------------------------------------------------------------------ 리플렉션 다리 (변환된 플러그인 바이트코드가 부른다)

    /** {@code Class.forName(name)} 대신 — 부른 클래스의 로더로 찾고 초기화한다(원래와 같다). 없으면 원래 이름으로 ClassNotFoundException. */
    public static Class<?> forName(String name, Class<?> caller) throws ClassNotFoundException { return forName(name, caller, null); }

    /** 위와 같고, 없는 NMS 이름이면 플러그인 이름으로 한 번 경고한다(H7a). */
    public static Class<?> forName(String name, Class<?> caller, String plugin) throws ClassNotFoundException {
        String n = canonical(name);
        try {
            return Class.forName(n, true, caller.getClassLoader());
        } catch (ClassNotFoundException e) {
            NmsReflect.classMissing(plugin, name);
            if (n == name) throw e;
            throw new ClassNotFoundException(name, e);
        }
    }

    /** {@code Class.forName(name, initialize, loader)} 대신. */
    public static Class<?> forName(String name, boolean initialize, ClassLoader loader) throws ClassNotFoundException {
        return forName(name, initialize, loader, null);
    }

    public static Class<?> forName(String name, boolean initialize, ClassLoader loader, String plugin) throws ClassNotFoundException {
        String n = canonical(name);
        try {
            return Class.forName(n, initialize, loader);
        } catch (ClassNotFoundException e) {
            NmsReflect.classMissing(plugin, name);
            if (n == name) throw e;
            throw new ClassNotFoundException(name, e);
        }
    }

    /** {@code loader.loadClass(name)} 대신(loader 가 null 이면 원래처럼 NullPointerException). */
    public static Class<?> loadClass(ClassLoader loader, String name) throws ClassNotFoundException { return loadClass(loader, name, null); }

    public static Class<?> loadClass(ClassLoader loader, String name, String plugin) throws ClassNotFoundException {
        String n = canonical(name);
        try {
            return loader.loadClass(n);
        } catch (ClassNotFoundException e) {
            NmsReflect.classMissing(plugin, name);
            if (n == name) throw e;
            throw new ClassNotFoundException(name, e);
        }
    }

    /** {@code lookup.findClass(name)} 대신. */
    public static Class<?> findClass(MethodHandles.Lookup lookup, String name) throws ClassNotFoundException, IllegalAccessException {
        return findClass(lookup, name, null);
    }

    public static Class<?> findClass(MethodHandles.Lookup lookup, String name, String plugin) throws ClassNotFoundException, IllegalAccessException {
        String n = canonical(name);
        try {
            return lookup.findClass(n);
        } catch (ClassNotFoundException e) {
            NmsReflect.classMissing(plugin, name);
            if (n == name) throw e;
            throw new ClassNotFoundException(name, e);
        }
    }
}
