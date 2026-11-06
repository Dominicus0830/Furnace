import java.io.*;
// 수상한 스튜 효과(SuspiciousEffectHolder — 작은 꽃과 벌집?: 꽃 아이템 → 효과·지속 틱). 1.21.1 난독화 이름 사용.
// 실행: ExportCraftRemainders 와 같은 방법. 출력 줄: <아이템> <효과>:<지속틱>[ <효과>:<지속틱>…]
public class ExportStewEffects {
    public static void main(String[] a) throws Exception {
        ab.a(); akt.a();
        PrintStream out = new PrintStream(new FileOutputStream(a[0]), false, "UTF-8");
        jg<cul> items = (jg<cul>) (Object) lt.g;
        ju<brx> effects = (ju<brx>) (Object) lt.d;
        out.println("# 자동 생성(tools/vanilla-data/ExportStewEffects.java, 바닐라 1.21.1): 아이템 효과:지속틱 …");
        int n = 0;
        for (cul item : items) {
            dob h = dob.a((dcv) item);
            if (h == null) continue;
            StringBuilder sb = new StringBuilder(items.b(item).toString());
            for (cxw.a e : h.b().a()) {
                sb.append(' ').append(effects.b(e.b().a()).toString()).append(':').append(e.c());
            }
            out.println(sb);
            n++;
        }
        out.close();
        System.out.println("exported " + n);
    }
}
