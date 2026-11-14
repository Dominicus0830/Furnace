package furnacetools;

import java.io.PrintStream;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Predicate;

/**
 * 실행(1.21.1 전용 — 난독화 이름): 번들러 jar 의 META-INF/versions/1.21.1/server-1.21.1.jar 와 META-INF/libraries/**.jar 를 CP 로
 *   javac -proc:none -d out -cp "$CP" ExportContextCollisionShapes.java
 *   java -cp "out:$CP" furnacetools.ExportContextCollisionShapes furnace-server/src/test/resources/collision/context-shapes-1.21.1.tsv
 * 서명된 jar 라 이 클래스는 기본 패키지가 아닌 패키지(furnacetools)에 둔다. 결과는 ContextCollisionShapeVanillaTest 의 정답표.
 *
 * 바닐라 1.21.1 서버 jar 를 직접 돌려 비계·물·용암의 문맥별 충돌 모양(BlockState.getCollisionShape(level, pos, ctx))을 뽑는다.
 * 난독화 이름: ab=SharedConstants, akt=Bootstrap, lt.e=BLOCK 레지스트리, dtc=BlockState, dtb$a.b(dcc,jd,exh)=getCollisionShape,
 * dtc.u()=getFluidState, dcc=BlockGetter(a_=getBlockState, b_=getFluidState, c_=getBlockEntity), dcy=LevelHeightAccessor,
 * exm=EntityCollisionContext(5인자 생성자), exh=CollisionContext(a()=empty), cuq.l=ItemStack.EMPTY, exv=VoxelShape(e()=toAabbs, a(Axis)=getCoords, c()=isEmpty),
 * ewx=AABB(a..f), jd=BlockPos, ji$a=Axis(b=Y).
 */
public class ExportContextCollisionShapes {
    static Object fluidAbove;

    public static void main(String[] args) throws Exception {
        Class.forName("ab").getMethod("a").invoke(null);
        Class.forName("akt").getMethod("a").invoke(null);
        Iterable<?> reg = (Iterable<?>) Class.forName("lt").getField("e").get(null);
        Method keyOf = reg.getClass().getMethod("b", Object.class);
        Map<String, Object> blocks = new HashMap<>();
        for (Object b : reg) blocks.put(keyOf.invoke(reg, b).toString(), b);
        Class<?> dfy = Class.forName("dfy"), dtc = Class.forName("dtc"), dtba = Class.forName("dtb$a"), dcc = Class.forName("dcc"), jd = Class.forName("jd"),
                exh = Class.forName("exh"), exm = Class.forName("exm"), cuq = Class.forName("cuq"), bsr = Class.forName("bsr"), exv = Class.forName("exv"),
                ewx = Class.forName("ewx"), axis = Class.forName("ji$a");
        Method states = dfy.getMethod("l");   // getStateDefinition
        Method possible = Class.forName("dtd").getMethod("a");   // getPossibleStates
        Method collision = dtba.getMethod("b", dcc, jd, exh);
        Method fluidOf = dtba.getMethod("u");
        Constructor<?> ctxNew = exm.getDeclaredConstructor(boolean.class, double.class, cuq, Predicate.class, bsr);
        ctxNew.setAccessible(true);
        Object emptyStack = cuq.getField("l").get(null);
        Object emptyCtx = exh.getMethod("a").invoke(null);
        Object Y = axis.getField("b").get(null);
        Method toAabbs = exv.getMethod("e"), coords = exv.getMethod("a", axis);
        Object pos = jd.getConstructor(int.class, int.class, int.class).newInstance(0, 64, 0);
        Object level = Proxy.newProxyInstance(ExportContextCollisionShapes.class.getClassLoader(), new Class<?>[] {dcc}, (proxy, m, a) -> {
            switch (m.getName()) {
                case "b_": return fluidAbove;                    // getFluidState(pos.above()) 만 묻는다
                case "a_": throw new IllegalStateException("getBlockState 를 묻지 않아야 한다");
                case "c_": return null;
                case "J_": return 384;
                case "I_": return -64;
                default:
                    if (m.isDefault()) return InvocationHandler.invokeDefault(proxy, m, a);
                    throw new IllegalStateException(m.toString());
            }
        });
        // 유체 상태: 물·용암 블록 상태의 getFluidState
        List<?> waterStates = (List<?>) possible.invoke(states.invoke(blocks.get("minecraft:water")));
        List<?> lavaStates = (List<?>) possible.invoke(states.invoke(blocks.get("minecraft:lava")));
        Map<String, Object> fluids = new LinkedHashMap<>();
        fluids.put("empty", fluidOf.invoke(((List<?>) possible.invoke(states.invoke(blocks.get("minecraft:air")))).get(0)));
        Set<Object> waterFluids = new HashSet<>(), lavaFluids = new HashSet<>();
        for (Object s : waterStates) waterFluids.add(fluidOf.invoke(s));
        for (Object s : lavaStates) lavaFluids.add(fluidOf.invoke(s));
        for (Object s : waterStates) { String l = props(s); if (l.equals("level=0")) fluids.put("water_source", fluidOf.invoke(s)); if (l.equals("level=3")) fluids.put("water_flowing3", fluidOf.invoke(s)); if (l.equals("level=8")) fluids.put("water_falling", fluidOf.invoke(s)); }
        for (Object s : lavaStates) { String l = props(s); if (l.equals("level=0")) fluids.put("lava_source", fluidOf.invoke(s)); if (l.equals("level=3")) fluids.put("lava_flowing3", fluidOf.invoke(s)); }

        out = new ArrayList<>();
        double[] offs = {-1.5, -1.0, -1.0E-5, -9.999999747378752E-6, -5.0E-6, 0.0, 0.25, 0.4999, 0.5 - 9.999999747378752E-6, 0.4999901, 0.5, 0.75, 0.99999, 1.0 - 9.999999747378752E-6, 0.99999001, 1.0, 1.5};
        double[] liquidOffs = {-1.0, 0.0, 0.4999, 0.5 - 9.999999747378752E-6, 0.4999901, 0.5, 1.0, 1.5};
        int n = 0;
        for (Object s : (List<?>) possible.invoke(states.invoke(blocks.get("minecraft:scaffolding")))) {
            fluidAbove = fluids.get("empty");
            out.add(row("scaffolding", props(s), "none", "empty", collision.invoke(s, level, pos, emptyCtx), toAabbs, coords, Y)); n++;
            for (double o : offs) for (boolean desc : new boolean[] {false, true}) {
                Object ctx = ctxNew.newInstance(desc, 64 + o, emptyStack, (Predicate<Object>) f -> false, null);
                out.add(row("scaffolding", props(s), ctxKey(64 + o, desc, false, false), "empty", collision.invoke(s, level, pos, ctx), toAabbs, coords, Y)); n++;
            }
        }
        for (String liquid : new String[] {"water", "lava"}) {
            for (Object s : (List<?>) possible.invoke(states.invoke(blocks.get("minecraft:" + liquid)))) {
                if (!Set.of("level=0", "level=1", "level=7", "level=8", "level=15").contains(props(s))) continue;
                for (var fa : fluids.entrySet()) {
                    fluidAbove = fa.getValue();
                    out.add(row(liquid, props(s), "none", fa.getKey(), collision.invoke(s, level, pos, emptyCtx), toAabbs, coords, Y)); n++;
                    for (double o : liquidOffs) for (int stand = 0; stand < 4; stand++) {
                        boolean w = (stand & 1) != 0, l = (stand & 2) != 0;
                        Predicate<Object> p = f -> w && waterFluids.contains(f) || l && lavaFluids.contains(f);
                        boolean desc = stand == 3;   // 웅크림은 액체 모양에 영향이 없어야 한다 — 한 경우만 섞는다
                        Object ctx = ctxNew.newInstance(desc, 64 + o, emptyStack, p, null);
                        out.add(row(liquid, props(s), ctxKey(64 + o, desc, w, l), fa.getKey(), collision.invoke(s, level, pos, ctx), toAabbs, coords, Y)); n++;
                    }
                }
            }
        }
        try (PrintStream ps = new PrintStream(args[0], "UTF-8")) {
            ps.println("# 바닐라 1.21.1 문맥 충돌 모양: BlockState.getCollisionShape(level, (0,64,0), ctx) — 바닐라 서버 jar 를 직접 돌려 뽑았다(도구 tools/vanilla-data/ExportContextCollisionShapes.java).");
            ps.println("# shape 줄: 번호, 상자들(minX,minY,minZ,maxX,maxY,maxZ;… 빔은 -), getCoords(Y)");
            ps.println("# 나머지 줄: 블록, 속성, 문맥(none = CollisionContext.empty(), 아니면 발높이;웅크림;물위서기;용암위서기), 위 칸 유체, shape 번호");
            for (var e : shapeIds.entrySet()) ps.println("shape\t" + e.getValue() + "\t" + e.getKey());
            for (String r : out) ps.println(r);
        }
        System.out.println("rows " + n + " shapes " + shapeIds.size());
    }

    static List<String> out;
    static final Map<String, Integer> shapeIds = new LinkedHashMap<>();

    static String props(Object state) {
        String s = state.toString();
        int i = s.indexOf('[');
        return i < 0 ? "" : s.substring(i + 1, s.length() - 1);
    }

    static String ctxKey(double bottom, boolean desc, boolean w, boolean l) {
        return Double.toString(bottom) + ";" + (desc ? 1 : 0) + ";" + (w ? 1 : 0) + ";" + (l ? 1 : 0);
    }

    static String row(String block, String props, String ctx, String above, Object shape, Method toAabbs, Method coords, Object Y) throws Exception {
        List<?> boxes = (List<?>) toAabbs.invoke(shape);
        StringBuilder b = new StringBuilder();
        for (Object box : boxes) {
            Class<?> c = box.getClass();
            if (b.length() > 0) b.append(';');
            b.append(c.getField("a").get(box)).append(',').append(c.getField("b").get(box)).append(',').append(c.getField("c").get(box)).append(',')
                    .append(c.getField("d").get(box)).append(',').append(c.getField("e").get(box)).append(',').append(c.getField("f").get(box));
        }
        if (b.length() == 0) b.append('-');
        Object ys = coords.invoke(shape, Y);
        String key = b + "\t" + ys.toString().replace(" ", "");
        Integer id = shapeIds.computeIfAbsent(key, k -> shapeIds.size());
        return block + "\t" + props + "\t" + ctx + "\t" + above + "\t" + id;
    }
}
