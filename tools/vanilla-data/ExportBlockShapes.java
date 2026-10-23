import java.io.*;
import java.util.*;
// 바닐라 1.21.1 블록 상태별 모양(VoxelShape → 상자 목록)을 뽑는다. 난독화 이름 사용 — 1.21.1 전용.
//   dtc = BlockState, dtb$a 메서드: j(getter,pos)=getShape, k=getCollisionShape, l=getBlockSupportShape,
//   c(getter,pos)=getOcclusionShape, m=getInteractionShape; exv = VoxelShape, exv.e() = toAabbs(), ewx = AABB(a..f).
// 출력: 모양 사전(S <번호> <상자;…>) + 상태 줄(<상태 id> outline collision support occlusion interaction).
public class ExportBlockShapes {
    static final jd ZERO = new jd(0, 0, 0);
    static final Map<String, Integer> dict = new LinkedHashMap<>();

    public static void main(String[] a) throws Exception {
        ab.a(); akt.a();
        PrintStream out = new PrintStream(new FileOutputStream(a[0]), false, "UTF-8");
        jg<dfy> reg = (jg<dfy>) (Object) lt.e;
        List<String> rows = new ArrayList<>();
        int id = 0;
        for (dfy block : reg) {
            for (dtc st : block.l().a()) {
                // 위치에 따라 어긋나는 블록(꽃·대나무 등): (0,0,0)의 어긋남을 빼서 기준 모양으로(어긋남 식은 Furnace 가 계산)
                exc off = st.n(dcl.a, ZERO);
                boolean offset = off.a() != 0 || off.b() != 0 || off.c() != 0 || !st.n(dcl.a, new jd(5, 0, 11)).equals(off);
                rows.add(id + " " + sid(st.j(dcl.a, ZERO), off) + " " + sid(st.k(dcl.a, ZERO), off) + " " + sid(st.l(dcl.a, ZERO), off)
                        + " " + sid(st.c(dcl.a, ZERO), off) + " " + sid(st.m(dcl.a, ZERO), off) + (offset ? " o" : ""));
                id++;
            }
        }
        out.println("# 바닐라 1.21.1 블록 모양: S <번호> <minX,minY,minZ,maxX,maxY,maxZ;…> / <상태 id> outline collision support occlusion interaction [o = 위치 어긋남 블록, 모양은 어긋남 뺀 기준]");
        for (var e : dict.entrySet()) out.println("S " + e.getValue() + " " + e.getKey());
        for (String r : rows) out.println(r);
        out.close();
        System.out.println("states " + id + " shapes " + dict.size());
    }

    static int sid(exv shape, exc off) {
        StringBuilder sb = new StringBuilder();
        for (ewx b : shape.e()) {
            if (sb.length() > 0) sb.append(';');
            sb.append(b.a - off.a()).append(',').append(b.b - off.b()).append(',').append(b.c - off.c()).append(',')
              .append(b.d - off.a()).append(',').append(b.e - off.b()).append(',').append(b.f - off.c());
        }
        String k = sb.length() == 0 ? "-" : sb.toString();
        return dict.computeIfAbsent(k, x -> dict.size());
    }
}
