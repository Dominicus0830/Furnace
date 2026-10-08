import java.io.*;
import java.lang.reflect.*;
import java.util.*;
public class ExportBlockAttrs {
    static final jd ZERO = new jd(0,0,0);
    public static void main(String[] a) throws Exception {
        ab.a(); akt.a();
        PrintStream out = new PrintStream(new FileOutputStream(a[0]), false, "UTF-8");
        jg<dfy> reg = (jg<dfy>) (Object) lt.e;
        ji[] dirs = ji.values();
        int id = 0;
        out.println("# id key air occlude replaceable light liquid blocksMotion solid solidRender collisionEmpty sturdyMask(DOWN,UP,NORTH,SOUTH,WEST,EAST) destroy collisionFullFaceMask(DOWN,UP,NORTH,SOUTH,WEST,EAST)");
        for (dfy block : reg) {
            String key = reg.b(block).toString();
            for (dtc st : block.l().a()) {
                String s = st.toString();
                int br = s.indexOf('[');
                String props = br < 0 ? "" : s.substring(br + 1, s.length() - 1);
                int mask = 0;
                for (int i = 0; i < dirs.length; i++) if (st.d(dcl.a, ZERO, dirs[i])) mask |= 1 << i;
                int cmask = 0;
                exv collision = st.k(dcl.a, ZERO);   // BlockState.getCollisionShape(level,pos) (캐시값)
                for (int i = 0; i < dirs.length; i++) if (dfy.a(collision, dirs[i])) cmask |= 1 << i;
                boolean collisionEmpty = collision.c();
                float destroy;
                try { destroy = st.f(dcl.a, ZERO); } catch (Throwable t) { destroy = -2f; }
                out.println(id + " " + key + " " + props.replace(' ', '_') + " " + b(st.i()) + b(st.p()) + b(st.r()) + " " + st.h() + " " + b(st.k()) + b(st.d()) + b(st.e())
                        + b(st.i(dcl.a, ZERO)) + b(collisionEmpty) + " " + mask + " " + destroy + " " + cmask);
                id++;
            }
        }
        out.close();
        System.out.println("exported " + id);
    }
    static String b(boolean v) { return v ? "1" : "0"; }
}
