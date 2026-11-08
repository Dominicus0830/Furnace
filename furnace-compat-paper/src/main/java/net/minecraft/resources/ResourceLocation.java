package net.minecraft.resources;

import net.minecraft.ResourceLocationException;

/**
 * 이름공간:경로 식별자(Mojang 이름 shim, H7b, 등급 A, 불변). 허용 글자·오류 문구·parse/tryParse 규칙·equals·hashCode
 * (31·namespace + path)·compareTo(경로 먼저, 같으면 이름공간)·toString 은 바닐라 1.21.1 바이트코드와 같다. Codec·StreamCodec·
 * brigadier 읽기(read(StringReader))는 그 라이브러리가 서버에 없어 두지 않았다.
 */
public final class ResourceLocation implements Comparable<ResourceLocation> {

    public static final char NAMESPACE_SEPARATOR = ':';
    public static final String DEFAULT_NAMESPACE = "minecraft";
    public static final String REALMS_NAMESPACE = "realms";

    private final String namespace;
    private final String path;

    private ResourceLocation(String namespace, String path) {
        this.namespace = namespace;
        this.path = path;
    }

    private static ResourceLocation createUntrusted(String namespace, String path) {
        return new ResourceLocation(assertValidNamespace(namespace, path), assertValidPath(namespace, path));
    }

    public static ResourceLocation fromNamespaceAndPath(String namespace, String path) { return createUntrusted(namespace, path); }

    /** {@code ns:path} 또는 {@code path}(minecraft). 틀리면 ResourceLocationException. */
    public static ResourceLocation parse(String s) { return bySeparator(s, NAMESPACE_SEPARATOR); }

    public static ResourceLocation withDefaultNamespace(String path) { return new ResourceLocation(DEFAULT_NAMESPACE, assertValidPath(DEFAULT_NAMESPACE, path)); }

    /** 틀리면 null. */
    public static ResourceLocation tryParse(String s) { return tryBySeparator(s, NAMESPACE_SEPARATOR); }

    /** 틀리면 null. */
    public static ResourceLocation tryBuild(String namespace, String path) {
        return isValidNamespace(namespace) && isValidPath(path) ? new ResourceLocation(namespace, path) : null;
    }

    public static ResourceLocation bySeparator(String s, char sep) {
        int i = s.indexOf(sep);
        if (i >= 0) {
            String path = s.substring(i + 1);
            if (i != 0) return createUntrusted(s.substring(0, i), path);
            return withDefaultNamespace(path);
        }
        return withDefaultNamespace(s);
    }

    public static ResourceLocation tryBySeparator(String s, char sep) {
        int i = s.indexOf(sep);
        if (i >= 0) {
            String path = s.substring(i + 1);
            if (!isValidPath(path)) return null;
            if (i != 0) {
                String ns = s.substring(0, i);
                return isValidNamespace(ns) ? new ResourceLocation(ns, path) : null;
            }
            return new ResourceLocation(DEFAULT_NAMESPACE, path);
        }
        return isValidPath(s) ? new ResourceLocation(DEFAULT_NAMESPACE, s) : null;
    }

    public String getPath() { return path; }

    public String getNamespace() { return namespace; }

    public ResourceLocation withPath(String p) { return new ResourceLocation(namespace, assertValidPath(namespace, p)); }

    public ResourceLocation withPrefix(String prefix) { return withPath(prefix + path); }

    public ResourceLocation withSuffix(String suffix) { return withPath(path + suffix); }

    @Override
    public String toString() { return namespace + ":" + path; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof ResourceLocation r && namespace.equals(r.namespace) && path.equals(r.path);
    }

    @Override
    public int hashCode() { return 31 * namespace.hashCode() + path.hashCode(); }

    @Override
    public int compareTo(ResourceLocation o) {
        int c = path.compareTo(o.path);
        if (c == 0) c = namespace.compareTo(o.namespace);
        return c;
    }

    /** 바닐라 toDebugFileName: ':' 와 '/' 를 '_' 로. */
    public String toDebugFileName() { return toString().replace('/', '_').replace(':', '_'); }

    public String toLanguageKey() { return namespace + "." + path; }

    public String toShortLanguageKey() { return namespace.equals(DEFAULT_NAMESPACE) ? path : toLanguageKey(); }

    public String toLanguageKey(String type) { return type + "." + toLanguageKey(); }

    public String toLanguageKey(String type, String suffix) { return type + "." + toLanguageKey() + "." + suffix; }

    /** 위치 문자열에 쓸 수 있는 글자(0-9 a-z _ : / . -). */
    public static boolean isAllowedInResourceLocation(char c) {
        return c >= '0' && c <= '9' || c >= 'a' && c <= 'z' || c == '_' || c == ':' || c == '/' || c == '.' || c == '-';
    }

    public static boolean validPathChar(char c) {
        return c == '_' || c == '-' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '/' || c == '.';
    }

    private static boolean validNamespaceChar(char c) {
        return c == '_' || c == '-' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '.';
    }

    public static boolean isValidPath(String path) {
        for (int i = 0; i < path.length(); i++) if (!validPathChar(path.charAt(i))) return false;
        return true;
    }

    public static boolean isValidNamespace(String ns) {
        for (int i = 0; i < ns.length(); i++) if (!validNamespaceChar(ns.charAt(i))) return false;
        return true;
    }

    private static String assertValidNamespace(String ns, String path) {
        if (!isValidNamespace(ns)) throw new ResourceLocationException("Non [a-z0-9_.-] character in namespace of location: " + ns + ":" + path);
        return ns;
    }

    private static String assertValidPath(String ns, String path) {
        if (!isValidPath(path)) throw new ResourceLocationException("Non [a-z0-9/._-] character in path of location: " + ns + ":" + path);
        return path;
    }
}
