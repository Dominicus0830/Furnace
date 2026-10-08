import java.io.*;
import java.util.*;
// 블록 state별 mirror→rotate 결과를 바닐라 서버 코드로 export (StructureTemplate가 state.mirror(m).rotate(r)로 배치).
// 출력: 줄 하나 = 변환이 자기 자신이 아닌 state. "id m0r0 m0r1 m0r2 m0r3 m1r0 ... m2r3" (m: NONE,LEFT_RIGHT,FRONT_BACK / r: NONE,CW90,CW180,CCW90).
public class ExportBlockTransforms {
    public static void main(String[] a) throws Exception {
        ab.a(); akt.a();
        jg<dfy> reg = (jg<dfy>) (Object) lt.e;
        List<dtc> all = new ArrayList<>();
        IdentityHashMap<dtc, Integer> ids = new IdentityHashMap<>();
        for (dfy block : reg) for (dtc st : block.l().a()) { ids.put(st, all.size()); all.add(st); }
        dkv[] mirrors = dkv.values();
        dmm[] rots = dmm.values();
        PrintStream out = new PrintStream(new FileOutputStream(a[0]), false, "UTF-8");
        out.println("# states " + all.size() + " ; 줄: id 뒤에 mirror(NONE,LEFT_RIGHT,FRONT_BACK) x rotation(NONE,CW90,CW180,CCW90) 12개 결과 state id");
        int n = 0;
        for (int id = 0; id < all.size(); id++) {
            dtc st = all.get(id);
            StringBuilder sb = new StringBuilder().append(id);
            boolean changed = false;
            for (dkv m : mirrors) for (dmm r : rots) {
                dtc res = st.a(m).a(r);
                int rid = ids.get(res);
                if (rid != id) changed = true;
                sb.append(' ').append(rid);
            }
            if (changed) { out.println(sb); n++; }
        }
        out.close();
        if (a.length > 1) {
            // 담장 연결 판정용: 상태의 충돌 모양 아래면이 담장 테스트 상자(F,G,H,I,J)를 완전히 덮는지 5비트(F=1,G=2,H=4,I=8,J=16).
            exv[] tests = {
                dfy.a(7.0, 0.0, 7.0, 9.0, 16.0, 9.0), dfy.a(7.0, 0.0, 0.0, 9.0, 16.0, 9.0), dfy.a(7.0, 0.0, 7.0, 9.0, 16.0, 16.0),
                dfy.a(0.0, 0.0, 7.0, 9.0, 16.0, 9.0), dfy.a(7.0, 0.0, 7.0, 16.0, 16.0, 9.0)};
            PrintStream o2 = new PrintStream(new FileOutputStream(a[1]), false, "UTF-8");
            o2.println("# states " + all.size() + " ; 줄: id wallTestMask (F=post,G=north,H=south,I=west,J=east 5비트: 위 블록 아래면이 해당 테스트 상자를 완전히 덮음)");
            jd zero = new jd(0, 0, 0);
            for (int id = 0; id < all.size(); id++) {
                exv face = all.get(id).k(dcl.a, zero).a(ji.a);
                int mask = 0;
                for (int i = 0; i < 5; i++) if (!exs.c(tests[i], face, exg.e)) mask |= 1 << i;
                if (mask != 0) o2.println(id + " " + mask);
            }
            o2.close();
        }
        if (a.length > 2) {
            // 지지 타입별 면 마스크: CENTER(횃불·랜턴), RIGID(레일·압력판). 비트0~5 = DOWN,UP,NORTH,SOUTH,WEST,EAST.
            PrintStream o3 = new PrintStream(new FileOutputStream(a[2]), false, "UTF-8");
            o3.println("# states " + all.size() + " ; 줄: id centerMask rigidMask collisionFaceNonEmptyMask");
            ji[] dirs = ji.values();
            jd zero2 = new jd(0, 0, 0);
            for (int id = 0; id < all.size(); id++) {
                int cm = 0, rm = 0, nm = 0;
                exv coll = all.get(id).k(dcl.a, zero2);
                for (int i = 0; i < dirs.length; i++) {
                    if (all.get(id).a(dcl.a, zero2, dirs[i], doa.b)) cm |= 1 << i;
                    if (all.get(id).a(dcl.a, zero2, dirs[i], doa.c)) rm |= 1 << i;
                    if (!coll.a(dirs[i]).c()) nm |= 1 << i;
                }
                if (cm != 0 || rm != 0 || nm != 0) o3.println(id + " " + cm + " " + rm + " " + nm);
            }
            o3.close();
        }
        System.out.println("states=" + all.size() + " changed=" + n + " mirrors=" + Arrays.toString(mirrors) + " rots=" + Arrays.toString(rots));
    }
}
