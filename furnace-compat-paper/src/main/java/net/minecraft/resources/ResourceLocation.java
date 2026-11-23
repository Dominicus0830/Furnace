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

    /**
     * 바닐라 {@code read(StringReader)}(NMS shim 5e-3c — 블록 상태 글 읽기): 위치에 쓸 수 있는 글자를 끝까지 읽어 위치로, 틀리면 읽기 전 자리로 돌아가
     * "Invalid ID" 명령 오류. Brigadier 가 붙은 서버에서만 부를 수 있다(플러그인이 StringReader 를 건넨다).
     */
    public static ResourceLocation read(com.mojang.brigadier.StringReader reader) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return Lib.read(reader);
    }

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

    /**
     * {@link #read} 의 몸(5e-3c). Brigadier 클래스를 던지는 코드를 따로 두어(이름 {@code $Lib} — CompatLinkTest 규칙), Brigadier 묶음이 없는 서버에서도 이 클래스가
     * 검증·로드된다(바닐라는 {@code ERROR_INVALID} 를 이 클래스 필드로 둔다).
     */
    private static final class Lib {
        static final com.mojang.brigadier.exceptions.SimpleCommandExceptionType ERROR_INVALID =
                new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(org.domi.furnace.compat.nms.NmsBrigadier.translatable("argument.id.invalid"));

        private Lib() {}

        /** 바닐라 read: 위치에 쓸 수 있는 글자를 끝까지 읽어 parse, 틀리면 처음 자리로 돌아가 "Invalid ID". */
        static ResourceLocation read(com.mojang.brigadier.StringReader reader) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
            int start = reader.getCursor();
            String s = readGreedy(reader);
            try {
                return ResourceLocation.parse(s);
            } catch (net.minecraft.ResourceLocationException e) {
                reader.setCursor(start);
                throw ERROR_INVALID.createWithContext(reader);
            }
        }

        private static String readGreedy(com.mojang.brigadier.StringReader reader) {
            int start = reader.getCursor();
            while (reader.canRead() && ResourceLocation.isAllowedInResourceLocation(reader.peek())) reader.skip();
            return reader.getString().substring(start, reader.getCursor());
        }
    }
}
