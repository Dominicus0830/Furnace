import java.io.*;
// 상태별 빛 속성: getLightBlock(빛 막는 정도 0..15), useShapeForLightOcclusion, canOcclude. 1.21.1 난독화 이름 사용.
// 한 줄에 상태 순서대로: 첫 줄 lightBlock 16진 한 글자씩, 둘째 줄 useShape 0/1, 셋째 줄 canOcclude 0/1.
// 실행: javac -cp <server.jar>:<libraries> ExportBlockLight.java && java -cp <server.jar>:<libraries>:. ExportBlockLight <출력>
public class ExportBlockLight {
    public static void main(String[] a) throws Exception {
        ab.a(); akt.a();
        jg<dfy> reg = (jg<dfy>) (Object) lt.e;
        StringBuilder lb = new StringBuilder(), us = new StringBuilder(), co = new StringBuilder();
        int n = 0;
        for (dfy block : reg) {
            for (dtc st : block.l().a()) {
                lb.append(Integer.toHexString(st.b(dcl.a, new jd(0, 0, 0))));
                us.append(st.g() ? '1' : '0');
                co.append(st.p() ? '1' : '0');
                n++;
            }
        }
        try (PrintStream out = new PrintStream(new FileOutputStream(a[0]), false, "UTF-8")) {
            out.println(lb);
            out.println(us);
            out.println(co);
        }
        System.out.println("exported " + n);
    }
}
