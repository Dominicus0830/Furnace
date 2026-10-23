import java.io.*;
// 아이템별 기본 최대 스택·최대 내구도·인챈트 수치(Item.getEnchantmentValue — 1.21.1 은 컴포넌트가 아니라 코드에 있다). 1.21.1 난독화 이름 사용.
// 실행: javac -cp <server.jar>:<libraries> ExportItemProps.java && java -cp <server.jar>:<libraries>:. ExportItemProps <출력>
public class ExportItemProps {
    public static void main(String[] a) throws Exception {
        ab.a(); akt.a();
        PrintStream out = new PrintStream(new FileOutputStream(a[0]), false, "UTF-8");
        jg<cul> reg = (jg<cul>) (Object) lt.g;
        out.println("# 자동 생성(tools/vanilla-data/ExportItemProps.java, 바닐라 1.21.1): 아이템 최대스택 최대내구도 인챈트수치");
        int n = 0;
        for (cul item : reg) {
            cuq stack = new cuq(item);
            out.println(reg.b(item) + " " + stack.j() + " " + stack.o() + " " + item.g());
            n++;
        }
        out.close();
        System.out.println("exported " + n);
    }
}
