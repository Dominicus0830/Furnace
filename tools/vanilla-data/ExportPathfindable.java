import java.io.*;
import java.util.*;
public class ExportPathfindable {
    public static void main(String[] a) throws Exception {
        ab.a(); akt.a();
        PrintStream out = new PrintStream(new FileOutputStream(a[0]), false, "UTF-8");
        jg<dfy> reg = (jg<dfy>) (Object) lt.e;
        int id = 0;
        out.println("# id key land water air  (BlockState.isPathfindable(PathComputationType))");
        for (dfy block : reg) {
            String key = reg.b(block).toString();
            for (dtc st : block.l().a()) {
                out.println(id + " " + key + " " + (st.a(ept.a) ? 1 : 0) + (st.a(ept.b) ? 1 : 0) + (st.a(ept.c) ? 1 : 0));
                id++;
            }
        }
        out.close();
        System.out.println("exported " + id);
    }
}
