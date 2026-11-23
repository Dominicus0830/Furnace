// T-264 P3 역할 표(설계 7장): Paper CraftBukkit 클래스의 메서드마다 누가 채우는지 a|b|c|d 로 나눈 기계가 읽는 표. T-263(연결 추론기)은 d 만 후보로 본다.
//  a = API 상속: Paper 계층에서 이 클래스가 가진 Bukkit/Paper API 형식(api 서명표)에 같은 이름·서술자의 인스턴스 메서드가 있다 — 정체성이 맞으면 상속으로 동작
//  b = 핸들 다리: ShellMerge 가 낸 좁힘 다리(@CraftBridge)
//  c = 변환 가족 W/U: CraftConversions 가 낸 레지스트리 값 변환 몸통(@CraftBridge)
//  d = 나머지(Paper 내부 동작 — 영역 10)
// 이름이 들어가므로 껍데기 jar 안에만 둔다(커밋 안 함). 열: 클래스 \t 멤버 \t 서술자 \t 역할.
package org.domi.furnace.compat.shimgen;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.domi.furnace.tools.extract.SigTable;
import org.objectweb.asm.Opcodes;

final class CraftRoles {
    static final String PATH = "META-INF/furnace/full-shells/craft-roles.tsv";
    private static final String PREFIX = "org/bukkit/craftbukkit/";

    private final FullShells g;
    private final Map<String, Set<String>> apiKeys = new HashMap<>();
    /** 역할별 수(보고서·요약). */
    final Map<String, Integer> counts = new TreeMap<>();

    CraftRoles(FullShells g) { this.g = g; }

    /** bridges: ShellMerge 가 낸 다리, conversions: CraftConversions 가 낸 변환(모두 소유#이름서술자). */
    String build(Set<String> bridges, Set<String> conversions) {
        StringBuilder sb = new StringBuilder();
        for (String n : new java.util.TreeSet<>(g.paper.classes().keySet())) {
            if (!n.startsWith(PREFIX)) continue;
            Set<String> api = null;
            for (SigTable.Member m : g.paper.members(n)) {
                if (m.kind() != 'M' || m.name().equals("<clinit>")) continue;
                if ((m.access() & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0) continue;
                String key = m.name() + m.desc();
                String role;
                if (bridges.contains(n + "#" + key)) role = "b";
                else if (conversions.contains(n + "#" + key)) role = "c";
                else {
                    boolean instance = (m.access() & Opcodes.ACC_STATIC) == 0 && !m.name().equals("<init>");
                    if (instance && api == null) api = apiKeysOf(n);
                    role = instance && api.contains(key) ? "a" : "d";
                }
                counts.merge(role, 1, Integer::sum);
                sb.append(n).append('\t').append(m.name()).append('\t').append(m.desc()).append('\t').append(role).append('\n');
            }
        }
        return sb.toString();
    }

    /** Paper 계층에서 n 이 가진 API 형식(api 서명표에 있는 형식)의 인스턴스 메서드 이름+서술자 전부. */
    private Set<String> apiKeysOf(String n) {
        Set<String> cached = apiKeys.get(n);
        if (cached != null) return cached;
        Set<String> out = new HashSet<>();
        ArrayDeque<String> todo = new ArrayDeque<>(List.of(n));
        Set<String> seen = new HashSet<>();
        while (!todo.isEmpty()) {
            String t = todo.poll();
            if (!seen.add(t)) continue;
            SigTable.Cls c = g.paper.classes().get(t);
            if (g.apiClasses.contains(t)) {
                c = g.api.classes().get(t);
                for (SigTable.Member m : g.api.members(t)) {
                    if (m.kind() == 'M' && (m.access() & Opcodes.ACC_STATIC) == 0 && !m.name().equals("<init>")) out.add(m.name() + m.desc());
                }
            }
            if (c == null) continue;
            if (c.superName() != null) todo.add(c.superName());
            todo.addAll(c.interfaces());
        }
        apiKeys.put(n, out);
        return out;
    }

    String summary() { return "craft roles " + counts; }
}
