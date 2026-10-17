import java.io.*;
import java.util.*;
// 바닐라 1.21.1 소리 블록 악기 추출(난독화 이름 — 1.21.1 전용). BlockState.instrument()(dtc.A() → dud NoteBlockInstrument)를
// 상태마다 읽어, 기본값(harp)이 아닌 블록을 악기별로 모은다. 한 블록 안에서 상태마다 다르면 오류로 멈춘다.
// 출력 줄: <악기 이름> <블록 이름> <블록 이름> ...
public class ExportNoteInstruments {
    public static void main(String[] a) throws Exception {
        ab.a(); akt.a();
        jg<dfy> reg = (jg<dfy>) (Object) lt.e;
        Map<String, List<String>> by = new TreeMap<>();
        for (dfy block : reg) {
            String inst = null;
            for (dtc t : block.l().a()) {
                String i = t.A().c();
                if (inst == null) inst = i;
                else if (!inst.equals(i)) throw new IllegalStateException("상태마다 악기가 다름: " + reg.b(block));
            }
            if (!"harp".equals(inst)) by.computeIfAbsent(inst, k -> new ArrayList<>()).add(reg.b(block).toString());
        }
        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(a[0]), "UTF-8"))) {
            w.println("# 자동 생성: tools/vanilla-data/ExportNoteInstruments.java. 악기 블록... (적히지 않은 블록은 harp)");
            for (var e : by.entrySet()) w.println(e.getKey() + " " + String.join(" ", e.getValue()));
        }
        System.out.println("done");
    }
}
