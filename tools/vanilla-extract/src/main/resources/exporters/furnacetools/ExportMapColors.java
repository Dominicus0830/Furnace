package furnacetools;

import java.io.FileOutputStream;
import java.io.PrintStream;
import net.minecraft.world.level.material.MapColor;

/**
 * MapColor(지도 색) 번호와 RGB: 줄 하나 = "번호 RGB(10진)". 번호 0..63 중 정의된 62개(번호 0 = NONE 은 RGB 0).
 * blocks/stateflags 표의 상태별 mapColor 칸은 번호가 아니라 RGB 값이라, 지도 알고리즘이 쓰는 번호로 되돌릴 때 이 표를 쓴다(RGB 는 서로 다르다). 인자: <출력>.
 */
public class ExportMapColors {
    public static void main(String[] a) throws Exception {
        int n = 0;
        try (PrintStream out = new PrintStream(new FileOutputStream(a[0]), false, "UTF-8")) {
            out.println("# 자동 생성: " + Vanilla.generator("ExportMapColors") + " (바닐라 " + Vanilla.mc() + " MapColor). 줄: 번호 RGB");
            for (int i = 0; i < 64; i++) {
                MapColor c = MapColor.byId(i);   // 없는 번호는 NONE 을 돌려준다
                if (c.id != i) continue;
                out.println(c.id + " " + c.col);
                n++;
            }
        }
        System.out.println("exported " + n);
    }
}
