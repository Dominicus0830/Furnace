import java.io.*;
// 바닐라 MapColor(지도 색) 번호와 RGB: 줄 하나 = "번호 RGB(10진)". 번호 0..63 중 정의된 62개(번호 0 = NONE 은 RGB 0). 1.21.1 난독화 이름 사용.
// blocks/stateflags 표의 상태별 mapColor 칸은 번호가 아니라 RGB 값이라, 지도 알고리즘이 쓰는 번호로 되돌릴 때 이 표를 쓴다(RGB 는 서로 다르다).
// 실행: javac -cp <내부 서버 jar 또는 클래스 폴더> ExportMapColors.java && java -cp <같은 경로>:. ExportMapColors <출력>
public class ExportMapColors {
    public static void main(String[] a) throws Exception {
        int n = 0;
        try (PrintStream out = new PrintStream(new FileOutputStream(a[0]), false, "UTF-8")) {
            out.println("# 자동 생성: tools/vanilla-data/ExportMapColors.java (바닐라 1.21.1 MapColor). 줄: 번호 RGB");
            for (int i = 0; i < 64; i++) {
                epi c = epi.a(i);   // MapColor.byId — 없는 번호는 NONE 을 돌려준다
                if (c.al != i) continue;
                out.println(c.al + " " + c.ak);
                n++;
            }
        }
        System.out.println("exported " + n);
    }
}
