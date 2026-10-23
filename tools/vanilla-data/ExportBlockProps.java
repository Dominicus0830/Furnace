import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
// 바닐라 1.21.1 블록 속성 추출(난독화 이름 — 1.21.1 전용). 두 파일:
//  <출력1> 블록별: 이름 hasCollision explosionResistance requiresCorrectTool randomlyTicking friction speedFactor jumpFactor
//          canOcclude ignitedByLava pushReaction replaceableProp dynamicShape offset soundType(volume,pitch,break,step,place,hit,fall) lootTable
//  <출력2>(gzip) 상태별: id FULL CENTER RIGID(DUNSWE 비트 6자리씩) replaceable legacySolid blocksMotion hasBlockEntity hasAnalogOutput
//          redstoneConductor suffocating viewBlocking hasPostProcess emissive lightEmission mapColorId
// dtb.d(Properties) 필드: c hasCollision, d soundType, f explosionResistance, g destroyTime, h requiresCorrectToolForDrops,
//   i isRandomlyTicking, j friction, k speedFactor, l jumpFactor, n canOcclude, p ignitedByLava, t pushReaction, w replaceable,
//   y isRedstoneConductor, z isSuffocating, A isViewBlocking, B hasPostProcess, C emissiveRendering, D dynamicShape, F offsetFunction.
public class ExportBlockProps {
    public static void main(String[] a) throws Exception {
        ab.a(); akt.a();
        jg<dfy> reg = (jg<dfy>) (Object) lt.e;
        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(a[0]), "UTF-8"));
             PrintWriter s = new PrintWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(a[1])), "UTF-8"))) {
            w.println("# 자동 생성: tools/vanilla-data/ExportBlockProps.java. 블록 hasCollision explosionResistance requiresCorrectTool randomlyTicking friction speedFactor jumpFactor canOcclude ignitedByLava pushReaction replaceable dynamicShape offset volume pitch break step place hit fall lootTable");
            s.println("# 자동 생성: tools/vanilla-data/ExportBlockProps.java. id FULL CENTER RIGID replaceable legacySolid blocksMotion hasBlockEntity hasAnalogOutput redstoneConductor suffocating viewBlocking hasPostProcess emissive light mapColor");
            for (dfy block : reg) {
                dtb.d p = block.t();
                dnm st = p.d;
                String[] snd = sounds(st);
                w.println(reg.b(block) + " " + b(p.c) + " " + p.f + " " + b(p.h) + " " + b(p.i) + " " + p.j + " " + p.k + " " + p.l + " " + b(p.n)
                        + " " + b(p.p) + " " + p.t.name().toLowerCase() + " " + b(p.w) + " " + b(p.D) + " " + (p.F != null ? 1 : 0)
                        + " " + st.a() + " " + st.b() + " " + String.join(" ", snd) + " " + block.v().a());
                for (dtc t : block.l().a()) {
                    StringBuilder sb = new StringBuilder().append(dfy.i(t));
                    for (doa type : doa.values()) {
                        sb.append(' ');
                        for (ji d : ji.values()) sb.append(t.a(dcl.a, jd.c, d, type) ? '1' : '0');
                    }
                    sb.append(' ').append(b(t.r())).append(b(t.e())).append(b(t.d())).append(b(t.t())).append(b(t.n()))
                      .append(b(p.y.test(t, dcl.a, jd.c))).append(b(p.z.test(t, dcl.a, jd.c))).append(b(p.A.test(t, dcl.a, jd.c)))
                      .append(b(p.B.test(t, dcl.a, jd.c))).append(b(p.C.test(t, dcl.a, jd.c)))
                      .append(' ').append(t.h()).append(' ').append(mapColor(p.b.apply(t)));
                    s.println(sb);
                }
            }
        }
        System.out.println("done");
    }

    static String b(boolean v) { return v ? "1" : "0"; }

    /** SoundType 의 소리 이벤트 5개(break step place hit fall) — 레코드 접근자 중 SoundEvent 를 돌려주는 것들을 선언 순서로. */
    static String[] sounds(dnm st) throws Exception {
        List<String> out = new ArrayList<>();
        for (Field f : dnm.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.getType() == float.class) continue;
            f.setAccessible(true);
            Object v = f.get(st);
            out.add(v instanceof avo e ? e.a().toString() : String.valueOf(v));   // avo = SoundEvent, a() = 위치
        }
        return out.toArray(new String[0]);
    }

    static int mapColor(Object c) throws Exception {
        for (Field f : c.getClass().getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) && f.getType() == int.class) { f.setAccessible(true); return f.getInt(c); }
        }
        return -1;
    }
}
