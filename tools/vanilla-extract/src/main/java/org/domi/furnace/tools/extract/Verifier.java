package org.domi.furnace.tools.extract;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * 새로 뽑은 파일과 저장소에 커밋된 파일을 바이트 단위로 견준다.
 * `.gz` 는 gzip 헤더에 시각이 들어가 압축 결과가 매번 달라질 수 있어 푼 내용을 견준다(압축 전 바이트가 같으면 같은 것으로 본다).
 */
public final class Verifier {
    public enum Kind { IDENTICAL, IDENTICAL_UNZIPPED, EQUIVALENT, DIFFERENT, MISSING_COMMITTED }

    public record Outcome(Kind kind, String detail) {
        public boolean ok() {
            return kind == Kind.IDENTICAL || kind == Kind.IDENTICAL_UNZIPPED || kind == Kind.EQUIVALENT;
        }
    }

    private Verifier() {}

    public static Outcome compare(Path generated, Path committed) throws IOException {
        if (!Files.isRegularFile(committed)) return new Outcome(Kind.MISSING_COMMITTED, "커밋된 파일 없음: " + committed);
        byte[] a = Files.readAllBytes(generated);
        byte[] b = Files.readAllBytes(committed);
        if (Arrays.equals(a, b)) return new Outcome(Kind.IDENTICAL, a.length + " 바이트 동일");
        if (generated.toString().endsWith(".gz")) {
            byte[] ua = gunzip(a), ub = gunzip(b);
            if (Arrays.equals(ua, ub)) return new Outcome(Kind.IDENTICAL_UNZIPPED, "푼 내용 " + ua.length + " 바이트 동일(압축 바이트는 다름)");
            return new Outcome(Kind.DIFFERENT, firstDifference(ua, ub));
        }
        if (generated.getFileName().toString().startsWith("soundtypes_")) {
            String why = soundTypesDifference(new String(a, java.nio.charset.StandardCharsets.UTF_8), new String(b, java.nio.charset.StandardCharsets.UTF_8));
            if (why == null) return new Outcome(Kind.EQUIVALENT, "바이트는 다르지만 블록→소리(크기·높이·소리 5개)와 상태별 덮어쓰기가 모두 같다(종류 번호 순서만 다름)");
            return new Outcome(Kind.DIFFERENT, why);
        }
        return new Outcome(Kind.DIFFERENT, firstDifference(a, b));
    }

    /**
     * soundtypes 는 종류 번호 순서가 옛 도구에서는 난독화 필드 이름순(이름 풀기로는 되살릴 수 없는 우연)이었다.
     * 번호를 소리 내용으로 풀어 블록마다 견준다. 같으면 null, 다르면 첫 차이 설명.
     */
    static String soundTypesDifference(String x, String y) {
        Map<String, String> mx = soundMap(x), my = soundMap(y);
        if (mx.equals(my)) return null;
        for (var e : mx.entrySet()) {
            if (!e.getValue().equals(my.get(e.getKey()))) return e.getKey() + ": 새 " + e.getValue() + " / 커밋 " + my.get(e.getKey());
        }
        for (String k : my.keySet()) if (!mx.containsKey(k)) return k + ": 새 쪽에 없음";
        return "차이를 찾지 못함";
    }

    private static Map<String, String> soundMap(String text) {
        Map<String, String> types = new java.util.HashMap<>();
        Map<String, String> res = new java.util.TreeMap<>();
        String section = "";
        for (String line : text.split("\n")) {
            if (line.isBlank() || line.startsWith("#")) continue;
            if (line.startsWith("[")) {
                section = line.strip();
                continue;
            }
            String[] c = line.strip().split(" ", 2);
            switch (section) {
                case "[types]" -> types.put(c[0], c[1]);
                case "[blocks]" -> res.put("block " + c[0], types.get(c[1]));
                case "[state-overrides]" -> {
                    String[] d = c[1].split(" ", 2);
                    res.put("override " + c[0] + " " + d[0], types.get(d[1]));
                }
                default -> { }
            }
        }
        return res;
    }

    static byte[] gunzip(byte[] z) throws IOException {
        try (InputStream in = new GZIPInputStream(new java.io.ByteArrayInputStream(z))) {
            return in.readAllBytes();
        }
    }

    /** 처음 달라지는 줄과 두 쪽의 그 줄(앞부분)을 설명한다. */
    static String firstDifference(byte[] a, byte[] b) {
        String[] la = new String(a, java.nio.charset.StandardCharsets.UTF_8).split("\n", -1);
        String[] lb = new String(b, java.nio.charset.StandardCharsets.UTF_8).split("\n", -1);
        int n = Math.min(la.length, lb.length);
        for (int i = 0; i < n; i++) {
            if (!la[i].equals(lb[i])) {
                return "줄 " + (i + 1) + " 부터 다름 (새 " + la.length + "줄 / 커밋 " + lb.length + "줄)\n    새  : " + cut(la[i]) + "\n    커밋: " + cut(lb[i]);
            }
        }
        return "줄 수가 다름 (새 " + la.length + "줄 / 커밋 " + lb.length + "줄), 공통 " + n + "줄은 같음";
    }

    private static String cut(String s) {
        return s.length() <= 160 ? s : s.substring(0, 160) + "…";
    }
}
