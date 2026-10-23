import java.io.*;
// 블록별 destroyTime(경도, BlockState.getDestroySpeed) — 기본 state 기준(같은 블록의 state 는 모두 같다). 1.21.1 난독화 이름 사용.
// 실행: javac -cp <vanilla-full.jar> ExportBlockHardness.java && java -cp <vanilla-full.jar>:. ExportBlockHardness <출력>
public class ExportBlockHardness {
    static final jd ZERO = new jd(0, 0, 0);
    public static void main(String[] a) throws Exception {
        ab.a(); akt.a();
        PrintStream out = new PrintStream(new FileOutputStream(a[0]), false, "UTF-8");
        jg<dfy> reg = (jg<dfy>) (Object) lt.e;
        out.println("# 자동 생성(tools/vanilla-data/ExportBlockHardness.java, 바닐라 1.21.1): 블록 이름 destroyTime(-1 = 부술 수 없음)");
        int n = 0;
        for (dfy block : reg) {
            out.println(reg.b(block) + " " + block.o().h(dcl.a, ZERO));
            n++;
        }
        out.close();
        System.out.println("exported " + n);
    }
}
