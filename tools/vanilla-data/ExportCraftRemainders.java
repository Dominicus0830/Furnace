import java.io.*;
// 제작 후 남는 아이템(Item.getCraftingRemainingItem — 양동이·유리병 등). 1.21.1 난독화 이름 사용.
// 실행: javac -cp <server.jar 또는 server-1.21.1 폴더>:<libraries> ExportCraftRemainders.java && java -cp <같은 것>:. ExportCraftRemainders <출력>
public class ExportCraftRemainders {
    public static void main(String[] a) throws Exception {
        ab.a(); akt.a();
        PrintStream out = new PrintStream(new FileOutputStream(a[0]), false, "UTF-8");
        jg<cul> reg = (jg<cul>) (Object) lt.g;
        out.println("# 자동 생성(tools/vanilla-data/ExportCraftRemainders.java, 바닐라 1.21.1): 아이템 제작후남는아이템");
        int n = 0;
        for (cul item : reg) {
            if (item.v()) {
                out.println(reg.b(item) + " " + reg.b(item.u()));
                n++;
            }
        }
        out.close();
        System.out.println("exported " + n);
    }
}
