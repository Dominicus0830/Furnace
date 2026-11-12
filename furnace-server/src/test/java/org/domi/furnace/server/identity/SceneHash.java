// 장면 해시: 정해진 시작 상태와 입력으로 장면을 돌리며 "이름 해시" 줄을 쌓고, 금 파일(Golden)과 비교한다(O15 보고서 3.3).
//
//   SceneHash.assertGolden("worldgen-noise-quick", rec -> {
//       for (...) rec.line("chunk overworld 12345 0 0", hashColumn(g));
//   });
//
// 비교 전에 **결정성 자기 점검**을 한다: 같은 빌드·같은 JVM 에서 장면을 두 번(-Dfurnace.identity.determinismRuns) 돌려
// 줄이 하나라도 다르면 "결정적이지 않음"으로 실패한다 — 금 파일과의 차이가 최적화 탓인지 장면의 비결정(정체 해시 순서·
// 시각·nanoTime 씨앗 난수·여러 region 스레드 순서) 탓인지 섞이지 않게. 장면은 매번 새 상태(새 월드·새 소스)로 시작해야 한다.
//
// 장면 줄은 나중에 금 파일 diff 로 읽히므로 "무엇 어디" 를 앞에, 해시를 뒤에 둔다. 마지막에 TOTAL 줄이 자동으로 붙는다.
package org.domi.furnace.server.identity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SceneHash {

    @FunctionalInterface
    public interface Scene {
        void run(Recorder rec) throws Exception;
    }

    /** 장면이 결과를 적는 곳. */
    public static final class Recorder {
        private final List<String> lines = new ArrayList<>();
        private final IdentityHash total = new IdentityHash();

        /** "라벨 해시16자리". */
        public Recorder line(String label, long hash) {
            return text(label + " " + IdentityHash.hex(hash));
        }

        /** 빈 해시(아무것도 섞지 않음)는 장면이 아무것도 안 본 것이라 실패시킨다. */
        public Recorder line(String label, IdentityHash h) {
            if (h.count() == 0) throw new AssertionError("장면 줄 '" + label + "' 의 해시에 섞인 값이 없다");
            return line(label, h.value());
        }

        /** 해시가 아닌 값(개수·짧은 상태)을 그대로 적는다. */
        public Recorder text(String line) {
            if (line.isBlank() || line.startsWith("#") || line.startsWith("TOTAL ") || line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0) {
                throw new IllegalArgumentException("장면 줄로 쓸 수 없다: '" + line + "'");
            }
            lines.add(line);
            total.add(line);
            return this;
        }

        public int size() { return lines.size(); }

        List<String> finish() {
            List<String> out = new ArrayList<>(lines);
            out.add("TOTAL " + total.hex() + " lines=" + lines.size());
            return Collections.unmodifiableList(out);
        }
    }

    private SceneHash() {}

    /** 장면을 한 번 돌려 줄 목록(끝에 TOTAL)을 돌려준다. */
    public static List<String> record(Scene scene) {
        Recorder rec = new Recorder();
        try {
            scene.run(rec);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (rec.size() == 0) throw new AssertionError("장면이 줄을 하나도 적지 않았다");
        return rec.finish();
    }

    public static int determinismRuns() {
        return Math.max(2, Integer.getInteger("furnace.identity.determinismRuns", 2));
    }

    /** 결정성 자기 점검: 여러 번 돌려 모두 같아야 한다. 첫 결과를 돌려준다. */
    public static List<String> deterministic(String name, Scene scene) {
        return deterministic(name, scene, determinismRuns());
    }

    public static List<String> deterministic(String name, Scene scene, int runs) {
        List<String> first = record(scene);
        for (int run = 2; run <= runs; run++) {
            List<String> again = record(scene);
            if (!first.equals(again)) {
                int i = 0;
                while (i < Math.min(first.size(), again.size()) && first.get(i).equals(again.get(i))) i++;
                throw new AssertionError("[" + name + "] 장면이 결정적이지 않다 — 같은 빌드에서 " + run + "번째 실행이 첫 실행과 다르다(금 파일 비교 전 점검)"
                        + "\n  첫 차이 " + (i + 1) + "번째 줄\n    1회: " + (i < first.size() ? first.get(i) : "(없음)")
                        + "\n    " + run + "회: " + (i < again.size() ? again.get(i) : "(없음)")
                        + "\n  흔한 원인: 정체 해시 순서(HashMap<객체>), ConcurrentHashMap 순회, nanoTime 씨앗 난수, 여러 region 스레드, 이전 실행이 남긴 정적 캐시");
            }
        }
        return first;
    }

    /** 결정성 점검 → 금 파일 비교. */
    public static List<String> assertGolden(String name, Scene scene) {
        Golden.checkName(name);
        List<String> lines = deterministic(name, scene);
        Golden.assertMatches(name, lines);
        return lines;
    }
}
