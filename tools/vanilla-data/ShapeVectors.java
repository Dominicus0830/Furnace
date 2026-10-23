import java.io.*;
import java.lang.reflect.*;
import java.util.*;
// 바닐라 BlockState.updateShape(direction, neighbor, level, pos, neighborPos) 차분 테스트 벡터 생성기.
// 사용: ShapeVectors <출력> <블록이름[,블록이름...]> <이웃후보 블록이름,...> <상태당 시도 수> <시드>
// 출력 한 줄: "<state id> <dir 0..5> <cell0..cell26(후보 인덱스; 27칸, 중심=13은 state 자체)> <결과 state id>"
// 후보 풀은 모든 후보 블록의 "모든 상태"가 아니라 상태에서 무작위 추출(풀 크기 제한).
public class ShapeVectors {
    static final ji[] DIRS = ji.values();
    public static void main(String[] a) throws Exception {
        ab.a(); akt.a();
        jg<dfy> reg = (jg<dfy>) (Object) lt.e;
        bindBlockTags(reg, a.length > 5 ? a[5] : "/home/user/Furnace/furnace-server/src/main/resources/worldgen/1.21.1/block_tags.txt");
        List<dtc> all = new ArrayList<>();
        IdentityHashMap<dtc, Integer> ids = new IdentityHashMap<>();
        Map<String, List<dtc>> byName = new HashMap<>();
        for (dfy block : reg) for (dtc st : block.l().a()) {
            ids.put(st, all.size()); all.add(st);
            byName.computeIfAbsent(reg.b(block).toString().replace("minecraft:", ""), k -> new ArrayList<>()).add(st);
        }
        PrintStream out = new PrintStream(new FileOutputStream(a[0]), false, "UTF-8");
        List<String> targets = Arrays.asList(a[1].split(","));
        List<dtc> pool = new ArrayList<>();
        pool.add(all.get(0)); // air
        for (String n : a[2].split(",")) { List<dtc> l = byName.get(n.trim()); if (l == null) throw new RuntimeException("no block " + n); pool.addAll(l); }
        int tries = Integer.parseInt(a[3]);
        Random rnd = new Random(Long.parseLong(a[4]));
        int count = 0;
        for (String tn : targets) {
            List<dtc> sts = byName.get(tn.trim());
            if (sts == null) throw new RuntimeException("no block " + tn);
            for (dtc st : sts) {
                for (int t = 0; t < tries; t++) {
                    dtc[] cells = new dtc[27];
                    int[] idx = new int[27];
                    // 중심 근처(6방향 이웃)는 풀에서 무작위, 나머지는 70% 공기
                    for (int i = 0; i < 27; i++) {
                        int pi = rnd.nextInt(100) < 65 ? 0 : rnd.nextInt(pool.size());
                        idx[i] = pi; cells[i] = pool.get(pi);
                    }
                    final dtc center = st;
                    cells[13] = center;
                    final dtc[] fc = cells;
                    ddl level = (ddl) Proxy.newProxyInstance(ShapeVectors.class.getClassLoader(), new Class[]{ddl.class}, (proxy, m, args) -> {
                        if (m.isDefault()) {
                            String n = m.getName();
                            if (m.getParameterCount() >= 2 && m.getParameterTypes()[0] == jd.class
                                && (m.getParameterTypes()[1] == dfy.class || m.getParameterTypes()[1] == epd.class)) return null; // scheduleTick no-op
                            return InvocationHandler.invokeDefault(proxy, m, args);
                        }
                        String n = m.getName();
                        Class<?>[] pt = m.getParameterTypes();
                        if (pt.length == 1 && pt[0] == jd.class) {
                            jd p = (jd) args[0];
                            dtc s = cell(fc, p);
                            if (n.equals("a_")) return s;
                            if (n.equals("b_")) return s.u();
                            if (n.equals("c_")) return null;
                        }
                        Class<?> rt = m.getReturnType();
                        if (rt == boolean.class) return false;
                        if (rt == int.class) return 0;
                        if (rt == long.class) return 0L;
                        if (rt == float.class) return 0f;
                        if (rt == double.class) return 0d;
                        return null;
                    });
                    jd pos = new jd(1, 1, 1);
                    for (int d = 0; d < 6; d++) {
                        jd np = pos.a(DIRS[d]);
                        dtc nb = cell(cells, np);
                        dtc res;
                        try { res = st.a(DIRS[d], nb, level, pos, np); } catch (Throwable ex) { out.println("# EXC " + ids.get(st) + " " + d + " " + ex); continue; }
                        StringBuilder sb = new StringBuilder().append(ids.get(st)).append(' ').append(d);
                        for (int i = 0; i < 27; i++) sb.append(' ').append(ids.get(pool.get(idx[i])));
                        sb.append(' ').append(ids.get(res));
                        out.println(sb); count++;
                    }
                }
            }
        }
        out.close();
        System.out.println("vectors=" + count);
    }
    // 바닐라 부트스트랩만으로는 블록 태그가 비어 있다 — 저장소의 block_tags.txt(바닐라 데이터 생성기 결과)로 레지스트리에 바인딩한다.
    static void bindBlockTags(jg<dfy> reg, String file) throws Exception {
        Map<awu<dfy>, List<jm<dfy>>> map = new HashMap<>();
        Map<String, dfy> byName = new HashMap<>();
        for (dfy b : reg) byName.put(reg.b(b).toString(), b);
        for (String line : java.nio.file.Files.readAllLines(java.nio.file.Path.of(file))) {
            line = line.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] p = line.split(" ");
            List<jm<dfy>> holders = new ArrayList<>();
            for (int i = 1; i < p.length; i++) { if (byName.containsKey(p[i])) holders.add(reg.c(akr.b(p[i].substring("minecraft:".length()))).get()); }
            map.put(awu.a(lu.f, akr.b(p[0].substring("minecraft:".length()))), holders);
        }
        ((jz<dfy>) (Object) reg).a(map);
    }

    static dtc cell(dtc[] c, jd p) {
        int x = p.u(), y = p.v(), z = p.w();
        if (x < 0 || x > 2 || y < 0 || y > 2 || z < 0 || z > 2) return dga.a.o();
        return c[x + y * 3 + z * 9];
    }
}
