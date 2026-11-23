package org.domi.furnace.compat.nms;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 이미 있는 클래스(손 shim)에 Paper 1.21.1 은 있으나 Furnace 가 아직 들고 있지 않은 인스턴스 필드의 색인(T-261).
 * 살아 있는 객체마다 메모리가 들어 필드를 덧붙이지 않고 껍데기 jar 의 자원({@code META-INF/furnace/full-shells/instance-fields.tsv})에 이름만 둔다.
 * 리플렉션 다리({@link NmsReflect})가 그 필드를 못 찾으면 이 색인을 보고 "Paper 에 있으나 아직 없음"으로 {@link NmsGaps} 에 센다 — 예외는 원래 그대로다
 * (값을 지어내지 않는다). 껍데기 jar 가 없으면(표 없는 빌드·시험) 색인이 비어 예전과 같다. 영역 스레드가 필드 읽기를 Furnace 값으로 돌릴 때 이 색인이 목록이다.
 */
public final class NmsShellFields {
    static final String WHY = "Paper has this instance field but Furnace does not hold it yet (T-261 index)";
    static final String RESOURCE = "META-INF/furnace/full-shells/instance-fields.tsv";

    /** 소유 클래스(내부 이름) → (필드 이름 → 서술자). */
    private static volatile Map<String, Map<String, String>> index;

    private NmsShellFields() {}

    /** 이 클래스(내부 이름)가 Paper 에서 선언하지만 Furnace 에 아직 없는 인스턴스 필드의 서술자. 없으면 null. */
    public static String descriptor(String owner, String name) {
        Map<String, String> m = index().get(owner);
        return m == null ? null : m.get(name);
    }

    /** 클래스와 그 상위 사슬에서 찾는다: 찾으면 "소유#이름:서술자", 없으면 null. */
    static String find(Class<?> c, String name) {
        if (name == null) return null;
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            String owner = k.getName().replace('.', '/');
            String d = descriptor(owner, name);
            if (d != null) return k.getName() + "#" + name + ":" + d;
        }
        return null;
    }

    private static Map<String, Map<String, String>> index() {
        Map<String, Map<String, String>> i = index;
        if (i == null) {
            synchronized (NmsShellFields.class) {
                if (index == null) index = load(NmsShellFields.class.getClassLoader());
                i = index;
            }
        }
        return i;
    }

    static Map<String, Map<String, String>> load(ClassLoader cl) {
        Map<String, Map<String, String>> out = new HashMap<>();
        try (InputStream in = cl == null ? ClassLoader.getSystemResourceAsStream(RESOURCE) : cl.getResourceAsStream(RESOURCE)) {
            if (in == null) return Map.of();
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = r.readLine()) != null; ) {
                String[] f = line.split("\t");
                if (f.length < 3) continue;
                out.computeIfAbsent(f[0], k -> new HashMap<>()).put(f[1], f[2]);
            }
        } catch (IOException e) {
            return Map.of();
        }
        return out;
    }

    /** 시험용: 색인을 바꾼다(null 이면 다음에 다시 읽는다). */
    static void setForTest(Map<String, Map<String, String>> m) { index = m; }
}
