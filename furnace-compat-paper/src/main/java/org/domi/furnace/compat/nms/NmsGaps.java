package org.domi.furnace.compat.nms;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import org.domi.furnace.api.plugin.CompatGap;

/**
 * (플러그인, 심볼)별 "서버에 없는 것" 표(T-233). 두 곳에서 모인다: 플러그인 jar 를 열 때 가리키는 것을 센 보고({@link NmsCompatReport}),
 * 실행 중 덫({@link NmsTrap})이 던진 횟수. 덫 안에서만 세므로 정상 호출 경로의 비용은 0이다. 밖으로 보내지 않고
 * {@code /furnace compat gaps} 가 파일로 내보낸다. 표는 (플러그인, 심볼) 수만큼만 커지며 {@link #MAX_ENTRIES} 에서 멈춘다.
 */
public final class NmsGaps {

    /** 표 크기 상한. 넘으면 새 항목은 버린다(이미 있는 항목의 횟수는 계속 센다). */
    static final int MAX_ENTRIES = 50_000;

    private record Key(String plugin, String target) {}

    private static final class Entry {
        volatile String kind;
        volatile boolean referenced;
        final LongAdder calls = new LongAdder();

        Entry(String kind) { this.kind = kind; }
    }

    private static final ConcurrentHashMap<Key, Entry> TABLE = new ConcurrentHashMap<>();

    private NmsGaps() {}

    private static Entry entry(String plugin, String target, String kind) {
        Key k = new Key(plugin, target);
        Entry e = TABLE.get(k);
        if (e == null) {
            if (TABLE.size() >= MAX_ENTRIES) return null;
            e = TABLE.computeIfAbsent(k, x -> new Entry(kind));
        }
        if ("unknown".equals(e.kind) && !"unknown".equals(kind)) e.kind = kind;
        return e;
    }

    /** 덫이 던졌다. kind 는 {@link UnsupportedNmsLink} 의 종류 상수. */
    static void trapped(String plugin, String target, int kind) {
        Entry e = entry(plugin, target, switch (kind) {
            case UnsupportedNmsLink.CLASS -> "class";
            case UnsupportedNmsLink.FIELD -> "field";
            default -> "method";
        });
        if (e != null) e.calls.increment();
    }

    /** 상속한 껍데기 클래스를 만들려 했다. */
    static void huskBuilt(String plugin, String parent, String cls) {
        Entry e = entry(plugin, "extends " + cls + " -> " + parent, "extends");
        if (e != null) e.calls.increment();
    }

    /** 로드 때 보고가 센 없는 심볼들("종류\t대상" 줄). */
    static void referenced(String plugin, Collection<String> kindTabTarget) {
        for (String line : kindTabTarget) {
            int tab = line.indexOf('\t');
            String kind = tab < 0 ? "unknown" : line.substring(0, tab);
            String target = tab < 0 ? line : line.substring(tab + 1);
            // 상속 거부는 덫 쪽 대상 이름("extends A -> B")과 같은 모양으로 맞춘다
            if (kind.equals("extends") && !target.startsWith("extends ")) target = "extends " + target;
            Entry e = entry(plugin, target, kind);
            if (e != null) e.referenced = true;
        }
    }

    /** 지금까지의 표(횟수 많은 순, 같으면 플러그인·대상 순). */
    public static List<CompatGap> snapshot() {
        List<CompatGap> out = new ArrayList<>(TABLE.size());
        TABLE.forEach((k, e) -> out.add(new CompatGap(k.plugin(), e.kind, k.target(), e.referenced, e.calls.sum())));
        out.sort(Comparator.comparingLong(CompatGap::calls).reversed().thenComparing(CompatGap::plugin).thenComparing(CompatGap::target));
        return out;
    }

    /** 시험용. */
    static void reset() { TABLE.clear(); }
}
