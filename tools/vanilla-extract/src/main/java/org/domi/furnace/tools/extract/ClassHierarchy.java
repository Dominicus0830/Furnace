package org.domi.furnace.tools.extract;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;

/**
 * jar 안 클래스의 부모·인터페이스 표. 대응표는 메서드·필드를 "선언한 클래스" 아래에만 적으므로,
 * 바이트코드가 하위 클래스 이름으로 부른 멤버는 부모 쪽으로 올라가며 찾아야 한다.
 */
public final class ClassHierarchy {
    record Node(String superName, String[] interfaces) {}

    private final Map<String, Node> nodes = new HashMap<>();

    public static ClassHierarchy read(ZipFile jar) throws IOException {
        ClassHierarchy h = new ClassHierarchy();
        var entries = jar.entries();
        while (entries.hasMoreElements()) {
            ZipEntry e = entries.nextElement();
            if (e.isDirectory() || !e.getName().endsWith(".class")) continue;
            try (InputStream in = jar.getInputStream(e)) {
                ClassReader r = new ClassReader(in);
                h.nodes.put(r.getClassName(), new Node(r.getSuperName(), r.getInterfaces()));
            }
        }
        return h;
    }

    /** 테스트용: 직접 채운다. */
    public static ClassHierarchy of(Map<String, String[]> superAndInterfaces) {
        ClassHierarchy h = new ClassHierarchy();
        superAndInterfaces.forEach((k, v) -> h.nodes.put(k, new Node(v.length == 0 ? null : v[0],
                v.length <= 1 ? new String[0] : java.util.Arrays.copyOfRange(v, 1, v.length))));
        return h;
    }

    /** owner 에서 시작해 부모 → 인터페이스 순으로 깊이 우선 탐색하며 처음 값이 나오는 클래스의 결과를 돌려준다. */
    public <T> T findUp(String owner, java.util.function.Function<String, T> lookup) {
        return findUp(owner, lookup, new java.util.HashSet<>());
    }

    private <T> T findUp(String owner, java.util.function.Function<String, T> lookup, java.util.Set<String> seen) {
        if (owner == null || !seen.add(owner)) return null;
        T t = lookup.apply(owner);
        if (t != null) return t;
        Node n = nodes.get(owner);
        if (n == null) return null;
        T up = findUp(n.superName, lookup, seen);
        if (up != null) return up;
        for (String i : n.interfaces) {
            up = findUp(i, lookup, seen);
            if (up != null) return up;
        }
        return null;
    }
}
