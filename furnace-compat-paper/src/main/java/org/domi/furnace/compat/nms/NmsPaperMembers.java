package org.domi.furnace.compat.nms;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * 진짜 Paper 1.21.1 의 NMS·CraftBukkit 멤버 이름표(자원 {@code furnace-compat-nms/paper-1.21.1-members.txt.gz}, 생성기 {@code tools/compat/gen-paper-member-names.py}).
 * 호환 계층이 가진 클래스(와 그 조상)마다 선언한 필드·메서드 <b>이름</b>만 있다. 리플렉션으로 멤버를 찾다 못 찾은 경우 그 이름이 Paper 에도 없으면
 * (예: 1.21.1 에는 {@code MinecraftServer.nextTickTime} 대신 {@code nextTickTimeNanos}, {@code ServerPlayer.playerConnection} 대신 {@code connection} 이다)
 * Paper 에서도 같은 예외가 나는 것이라 Furnace 의 구멍이 아니다 — 경고하지 않는 근거로 쓴다.
 *
 * <p>이 표는 모르는 것을 없다고 하지 않는다: 클래스나 조상 사슬이 표에 없으면 "모름"을 준다(그때는 이전처럼 경고).
 */
public final class NmsPaperMembers {

    private static final String RESOURCE = "/furnace-compat-nms/paper-1.21.1-members.txt.gz";

    private record Row(String superName, String[] interfaces, Set<String> fields, Set<String> methods) {}

    private static volatile Map<String, Row> rows;

    private NmsPaperMembers() {}

    /** 진짜 Paper 에 그 이름의 필드가 있는가: 있다·없다, 표가 모르는 클래스면 null. 이름은 클래스 내부 이름({@code net/minecraft/...} 또는 점 이름). */
    public static Boolean hasField(String className, String name) { return has(className, name, true); }

    /** 진짜 Paper 에 그 이름의 메서드(어느 오버로드든)가 있는가. 표가 모르면 null. */
    public static Boolean hasMethod(String className, String name) { return has(className, name, false); }

    private static Boolean has(String className, String name, boolean field) {
        Map<String, Row> table = table();
        if (table.isEmpty()) return null;
        Boolean result = walk(table, className.replace('.', '/'), name, field, new HashSet<>());
        return result;
    }

    /** 사슬 어디서든 찾으면 true, 사슬 전체를 알고도 없으면 false, 사슬에 표가 모르는 클래스가 끼어 있으면 null. */
    private static Boolean walk(Map<String, Row> table, String cls, String name, boolean field, Set<String> seen) {
        if (cls == null || cls.isEmpty() || !seen.add(cls)) return Boolean.FALSE;
        Row r = table.get(cls);
        if (r == null) return cls.startsWith("java/") ? Boolean.FALSE : null;   // 자바 기본 조상은 이름이 겹칠 일이 없다고 본다(Object 메서드 등은 호출하는 쪽이 찾지 못할 일이 없다)
        if ((field ? r.fields : r.methods).contains(name)) return Boolean.TRUE;
        boolean unknown = false;
        Boolean s = walk(table, r.superName, name, field, seen);
        if (s == null) unknown = true;
        else if (s) return Boolean.TRUE;
        for (String i : r.interfaces) {
            Boolean x = walk(table, i, name, field, seen);
            if (x == null) unknown = true;
            else if (x) return Boolean.TRUE;
        }
        return unknown ? null : Boolean.FALSE;
    }

    private static Map<String, Row> table() {
        Map<String, Row> t = rows;
        if (t != null) return t;
        synchronized (NmsPaperMembers.class) {
            if (rows == null) rows = load();
            return rows;
        }
    }

    private static Map<String, Row> load() {
        Map<String, Row> out = new HashMap<>(2048);
        try (InputStream in = NmsPaperMembers.class.getResourceAsStream(RESOURCE)) {
            if (in == null) return out;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8))) {
                for (String line; (line = r.readLine()) != null; ) {
                    String[] p = line.split("\t", -1);
                    if (p.length < 5) continue;
                    out.put(p[0], new Row(p[1], p[2].isEmpty() ? new String[0] : p[2].split(","), set(p[3]), set(p[4])));
                }
            }
        } catch (IOException e) {
            out.clear();   // 읽지 못하면 "모름" — 이전처럼 경고한다
        }
        return out;
    }

    private static Set<String> set(String csv) {
        if (csv.isEmpty()) return Set.of();
        Set<String> s = new HashSet<>();
        for (String x : csv.split(",")) s.add(x);
        return s;
    }
}
