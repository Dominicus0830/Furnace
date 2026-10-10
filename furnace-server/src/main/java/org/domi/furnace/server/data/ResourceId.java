// 리소스 위치(바닐라 ResourceLocation) — namespace:path. 정렬은 바닐라처럼 path 먼저, 그다음 namespace.
package org.domi.furnace.server.data;

public record ResourceId(String namespace, String path) implements Comparable<ResourceId> {

    public ResourceId {
        if (!validNamespace(namespace)) throw new IllegalArgumentException("잘못된 namespace: " + namespace + ":" + path);
        if (!validPath(path)) throw new IllegalArgumentException("잘못된 path: " + namespace + ":" + path);
    }

    /** "ns:path" 또는 "path"(minecraft). */
    public static ResourceId parse(String s) {
        int i = s.indexOf(':');
        return i < 0 ? new ResourceId("minecraft", s) : new ResourceId(i == 0 ? "minecraft" : s.substring(0, i), s.substring(i + 1));
    }

    /** 잘못된 문자열이면 null. */
    public static ResourceId tryParse(String s) {
        try {
            return parse(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static ResourceId tryBuild(String namespace, String path) {
        return validNamespace(namespace) && validPath(path) ? new ResourceId(namespace, path) : null;
    }

    public static boolean validNamespace(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(c == '_' || c == '-' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '.')) return false;
        }
        return true;
    }

    public static boolean validPath(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(c == '_' || c == '-' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '/' || c == '.')) return false;
        }
        return true;
    }

    /** path 앞뒤를 바꾼 새 id. */
    public ResourceId withPath(String p) { return new ResourceId(namespace, p); }

    @Override
    public int compareTo(ResourceId o) {
        int c = path.compareTo(o.path);
        return c != 0 ? c : namespace.compareTo(o.namespace);
    }

    @Override
    public String toString() { return namespace + ":" + path; }
}
