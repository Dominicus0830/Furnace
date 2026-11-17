package org.domi.furnace.tools.extract;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mojang 공식 대응표(version.json 의 downloads.server_mappings, ProGuard 형식)를 읽는다.
 *
 * <pre>
 * net.minecraft.SharedConstants -> ab:
 *     int PROTOCOL_VERSION -> a
 *     16:16:void tryDetectVersion() -> a
 * </pre>
 *
 * 줄의 왼쪽이 Mojang 이름, 오른쪽이 난독화 이름이다. 도구는 난독화 jar 를 Mojang 이름으로 바꾸는 쪽(obf → mojang)만 쓴다.
 * 필드는 (난독화 이름, 난독화 서술자), 메서드는 (난독화 이름, 난독화 서술자)로 찾는다 — 난독화는 서술자가 다르면 같은 이름을 쓰기 때문이다.
 * 클래스·서술자는 JVM 내부 형식(`/` 구분)으로 보관한다.
 */
public final class MojangMappings {
    /** 클래스 한 개의 멤버 표. 키는 `난독화이름 + ':' + 난독화서술자`(필드) / `난독화이름 + 난독화서술자`(메서드). */
    public static final class ClassMap {
        public final String mojang;
        public final String obf;
        final Map<String, String> fields = new HashMap<>();
        final Map<String, String> methods = new HashMap<>();

        ClassMap(String mojang, String obf) {
            this.mojang = mojang;
            this.obf = obf;
        }

        public String field(String obfName, String obfDesc) {
            return fields.get(obfName + ":" + obfDesc);
        }

        public String method(String obfName, String obfDesc) {
            return methods.get(obfName + obfDesc);
        }

        public int fieldCount() {
            return fields.size();
        }

        public int methodCount() {
            return methods.size();
        }
    }

    private static final Pattern CLASS_LINE = Pattern.compile("^(\\S+) -> (\\S+):$");
    private static final Pattern METHOD_LINE = Pattern.compile(
            "^(?:\\d+:\\d+:)?(\\S+) (\\S+?)\\((.*?)\\)(?::\\d+(?::\\d+)?)? -> (\\S+)$");
    private static final Pattern FIELD_LINE = Pattern.compile("^(\\S+) (\\S+) -> (\\S+)$");

    private final Map<String, ClassMap> byObf = new HashMap<>();
    private final Map<String, String> mojangToObf = new HashMap<>();

    private MojangMappings() {}

    public static MojangMappings parse(Reader in) throws IOException {
        // 1패스: 클래스 이름 표(서술자 변환에 필요), 멤버 줄은 클래스별로 모아 둔다.
        record Raw(ClassMap map, List<String> lines) {}
        List<Raw> raws = new ArrayList<>();
        MojangMappings m = new MojangMappings();
        Raw cur = null;
        BufferedReader r = in instanceof BufferedReader b ? b : new BufferedReader(in);
        String line;
        while ((line = r.readLine()) != null) {
            if (line.isBlank() || line.startsWith("#")) continue;
            if (!Character.isWhitespace(line.charAt(0))) {
                Matcher cm = CLASS_LINE.matcher(line);
                if (!cm.matches()) throw new IOException("대응표 클래스 줄을 읽지 못함: " + line);
                String mojang = cm.group(1).replace('.', '/');
                String obf = cm.group(2).replace('.', '/');
                ClassMap map = new ClassMap(mojang, obf);
                m.byObf.put(obf, map);
                m.mojangToObf.put(mojang, obf);
                cur = new Raw(map, new ArrayList<>());
                raws.add(cur);
            } else {
                if (cur == null) throw new IOException("클래스 줄 앞의 멤버 줄: " + line);
                cur.lines.add(line.strip());
            }
        }
        // 2패스: 멤버 서술자를 난독화 이름 기준으로 바꿔 넣는다.
        for (Raw raw : raws) {
            for (String l : raw.lines) {
                if (l.indexOf('(') >= 0) {
                    Matcher mm = METHOD_LINE.matcher(l);
                    if (!mm.matches()) throw new IOException("대응표 메서드 줄을 읽지 못함: " + l);
                    StringBuilder desc = new StringBuilder("(");
                    String args = mm.group(3);
                    if (!args.isEmpty()) for (String a : args.split(",")) desc.append(m.descriptorOf(a.strip()));
                    desc.append(')').append(m.descriptorOf(mm.group(1)));
                    raw.map.methods.put(mm.group(4) + desc, mm.group(2));
                } else {
                    Matcher fm = FIELD_LINE.matcher(l);
                    if (!fm.matches()) throw new IOException("대응표 필드 줄을 읽지 못함: " + l);
                    raw.map.fields.put(fm.group(3) + ":" + m.descriptorOf(fm.group(1)), fm.group(2));
                }
            }
        }
        return m;
    }

    /** Mojang 형식 타입 이름(`int`, `net.minecraft.core.BlockPos[]`)을 난독화 기준 JVM 서술자로. 대응표에 없는 클래스(JDK·라이브러리)는 그대로. */
    String descriptorOf(String type) {
        int dims = 0;
        while (type.endsWith("[]")) {
            dims++;
            type = type.substring(0, type.length() - 2);
        }
        String base = switch (type) {
            case "int" -> "I";
            case "long" -> "J";
            case "float" -> "F";
            case "double" -> "D";
            case "boolean" -> "Z";
            case "byte" -> "B";
            case "short" -> "S";
            case "char" -> "C";
            case "void" -> "V";
            default -> {
                String internal = type.replace('.', '/');
                yield "L" + mojangToObf.getOrDefault(internal, internal) + ";";
            }
        };
        return "[".repeat(dims) + base;
    }

    /** 난독화 내부 이름 → 클래스 표. 대응표에 없으면 null(라이브러리·JDK). */
    public ClassMap byObf(String obfInternalName) {
        return byObf.get(obfInternalName);
    }

    /** 난독화 내부 이름 → Mojang 내부 이름. 없으면 그대로. */
    public String mapClass(String obfInternalName) {
        ClassMap c = byObf.get(obfInternalName);
        return c == null ? obfInternalName : c.mojang;
    }

    public int classCount() {
        return byObf.size();
    }
}
