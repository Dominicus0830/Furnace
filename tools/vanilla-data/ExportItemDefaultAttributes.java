import java.io.*;
import java.util.regex.*;
// 아이템의 기본 속성 수정자(Item.getDefaultAttributeModifiers — 1.21.1 방어구는 컴포넌트가 아니라 이 메서드에 있다). 1.21.1 난독화 이름 사용.
// 실행: javac -cp <server.jar>:<libraries> ExportItemDefaultAttributes.java && java -cp <server.jar>:<libraries>:. ExportItemDefaultAttributes <출력>
public class ExportItemDefaultAttributes {
    static final String[] SLOTS = {"any", "mainhand", "offhand", "hand", "feet", "legs", "chest", "head", "armor", "body"};
    static final String[] OPS = {"add_value", "add_multiplied_base", "add_multiplied_total"};
    public static void main(String[] a) throws Exception {
        ab.a(); akt.a();
        PrintStream out = new PrintStream(new FileOutputStream(a[0]), false, "UTF-8");
        jg<cul> reg = (jg<cul>) (Object) lt.g;
        out.println("# 자동 생성(tools/vanilla-data/ExportItemDefaultAttributes.java, 바닐라 1.21.1): 아이템 속성 수정자id 양 연산 칸");
        Pattern p = Pattern.compile("minecraft:attribute / ([^\\]]+)\\]");
        int n = 0;
        for (cul item : reg) {
            for (cxn.b e : item.j().b()) {
                Matcher m = p.matcher(String.valueOf(e.a()));
                String attr = m.find() ? m.group(1) : String.valueOf(e.a());
                out.println(reg.b(item) + " " + attr + " " + e.b().b() + " " + e.b().c() + " " + OPS[e.b().d().ordinal()] + " " + SLOTS[e.c().ordinal()]);
                n++;
            }
        }
        out.close();
        System.out.println("exported " + n);
    }
}
