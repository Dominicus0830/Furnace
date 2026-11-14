import java.io.*;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;

// N2b-1 정답기(V1): 바닐라 PalettedContainer·LevelChunkSection 을 떨어진 객체로 직접 만들어 같은 입력(시드로 다시 만든다)을 넣고,
// 직렬화 바이트(write — 비트 수·팔레트 순서·long 배치가 다 들어 있다)의 해시·크기·maybeHas·개수 3개를 적는다.
// 출력은 바닐라 데이터가 아니라 "이 입력에 바닐라가 낸 숫자"뿐이다. 생성기(Cases)는 시험 쪽(NmsDetachedSectionOracleTest)과 글자 그대로 같아야 한다.
// 실행: ExportSectionOracle 와 같다(서명 지운 내부 jar + 라이브러리). 인자: <출력> <서버 jar(바이옴 수를 센다)>
//
// 이름(1.21.1 난독화): dvq=PalettedContainer(a(x,y,z,v)=getAndSet, b(..)=getAndSetUnchecked, c(..)=set, a(x,y,z)=get, a(vw)=read,
// b(vw)=write, c()=getSerializedSize, a(Predicate)=maybeHas, d()=copy, e()=recreate, a(dvq.b)=count), dvq.d.d/e=Strategy.SECTION_STATES/BIOMES,
// dvq.d.a(jr,int)=getConfiguration, dvq$a=Configuration(a()=factory, b()=bits), azd=SimpleBitStorage, azu=ZeroBitStorage,
// dvj=LevelChunkSection(a(x,y,z,s,b)=setBlockState, a(vw)=read, c(vw)=write, e,f,g=개수), jr=IdMap, dfy.q=Block.BLOCK_STATE_REGISTRY, vw=FriendlyByteBuf.
public class ExportPaletteOracle {
    static final int VERSION = 1;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void main(String[] a) throws Exception {
        ab.a(); akt.a();
        js<dtc> ids = dfy.q;
        int n = ids.c();
        dtc air = ids.a(0);
        Field fe = dvj.class.getDeclaredField("e"), ff = dvj.class.getDeclaredField("f"), fg = dvj.class.getDeclaredField("g");
        fe.setAccessible(true); ff.setAccessible(true); fg.setAccessible(true);
        StringBuilder out = new StringBuilder();
        out.append("version ").append(VERSION).append('\n');
        out.append("states ").append(n).append('\n');
        int biomes = 0;
        try (ZipFile z = new ZipFile(a[1])) {
            for (var e = z.entries(); e.hasMoreElements(); ) {
                String name = e.nextElement().getName();
                if (name.startsWith("data/minecraft/worldgen/biome/") && name.endsWith(".json")) biomes++;
            }
        }
        out.append("biomes ").append(biomes).append('\n');
        for (long seed = 1; seed <= Cases.COUNT; seed++) {
            Cases c = Cases.make(seed, n);
            jr idmap;
            Object[] values;
            if (c.generic) {
                Integer[] ints = new Integer[c.registrySize];
                for (int i = 0; i < ints.length; i++) ints[i] = Integer.valueOf(i);   // 칸마다 한 객체(값마다 정체성이 다르면 된다)
                values = ints;
                idmap = new IntIds(ints);
            } else {
                values = null;
                idmap = ids;
            }
            Object[] vals = values;
            java.util.function.IntFunction<Object> val = i -> vals != null ? vals[i] : ids.a(i);
            dvq.d strategy = c.biomeStrategy ? dvq.d.e : dvq.d.d;
            dvq pc;
            dvj section = null;
            if (c.kind == Cases.DEFAULT || c.kind == Cases.SECTION) {
                pc = new dvq(idmap, val.apply(c.pal[0]), strategy);
            } else {
                dvq.d confFrom = c.kind == Cases.CTOR_MISMATCH ? dvq.d.d : strategy;
                var conf = confFrom.a(idmap, c.ctorBits);   // Configuration 은 private 레코드라 이름을 못 쓴다
                axc storage = c.ctorBits == 0 ? new azu(strategy.a()) : new azd(c.ctorBits, strategy.a(), c.ctorCells);
                List list = new ArrayList();
                for (int v : c.ctorList) list.add(val.apply(v));
                pc = new dvq(idmap, strategy, conf, storage, list);
            }
            if (c.kind == Cases.SECTION) {
                Integer[] bi = new Integer[70];
                for (int i = 0; i < bi.length; i++) bi[i] = Integer.valueOf(i);
                section = new dvj(pc, new dvq(new IntIds(bi), bi[c.biome], dvq.d.e));
            }
            int size = strategy.a(), side = c.biomeStrategy ? 4 : 16, sh = c.biomeStrategy ? 2 : 4;
            String err = "-";
            for (int k = 0; k < c.opIdx.length; k++) {
                int i = c.opIdx[k] % size;
                int x = i & (side - 1), y = i >> (sh * 2), zz = (i >> sh) & (side - 1);
                Object v = val.apply(c.opVal[k]);
                try {
                    if (section != null) section.a(x, y, zz, (dtc) v, c.opKind[k] != 1);
                    else if (c.opKind[k] == 0) pc.a(x, y, zz, v);
                    else if (c.opKind[k] == 1) pc.b(x, y, zz, v);
                    else pc.c(x, y, zz, v);
                } catch (RuntimeException e) {
                    err = k + ":" + kindOf(e);
                    break;
                }
            }
            Object probe = val.apply(c.pal[0]);
            dvj s = section;
            out.append("case ").append(seed).append(' ').append(c.kind).append(' ').append(c.hash()).append(' ').append(err)
               .append(' ').append(safe(() -> hash(b -> pc.b(b)))).append(' ').append(safe(() -> "" + pc.c()))
               .append(' ').append(safe(() -> pc.a((java.util.function.Predicate) o -> o == probe) ? "1" : "0"))
               .append(' ').append(safe(() -> { int[] sum = new int[1]; pc.a((dvq.b) (o, cnt) -> sum[0] += cnt); return "" + sum[0]; }))
               .append(' ').append(safe(() -> hash(b -> pc.d().b(b)))).append(' ').append(safe(() -> hash(b -> pc.e().b(b))));
            if (s != null) {
                out.append(' ').append(fe.getShort(s)).append(' ').append(ff.getShort(s)).append(' ').append(fg.getShort(s))
                   .append(' ').append(safe(() -> hash(b -> s.c(b))));
            }
            out.append('\n');
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(a[0]), "UTF-8")) { w.write(out.toString()); }
        System.out.println("case " + Cases.COUNT + "개, 바이옴 " + biomes);
    }

    interface Writes { void to(vw buf) throws Exception; }

    interface Text { String get() throws Exception; }

    /** 예외면 그 종류(시험 쪽과 같은 분류). */
    static String safe(Text t) {
        try {
            return t.get();
        } catch (Exception e) {
            return "x:" + kindOf(e);
        }
    }

    static String kindOf(Exception e) {
        String m = e.getMessage();
        if (m != null && m.startsWith("Missing Palette entry for index")) return "MPE";
        if (e instanceof IllegalArgumentException) return "IAE";
        if (e instanceof IllegalStateException) return "ISE";
        if (e instanceof ArrayIndexOutOfBoundsException) return "AIOOBE";
        return e.getClass().getName();
    }

    static String hash(Writes w) throws Exception {
        // 기본 패키지에 난독화 클래스 io 가 있어 io.netty 를 이름으로 못 쓴다 — 리플렉션으로 만든다
        Object raw = Class.forName("io.netty.buffer.Unpooled").getMethod("buffer").invoke(null);
        vw buf = (vw) vw.class.getConstructor(Class.forName("io.netty.buffer.ByteBuf")).newInstance(raw);
        w.to(buf);
        byte[] bytes = new byte[buf.readableBytes()];
        buf.getBytes(0, bytes);
        byte[] d = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 8; i++) s.append(String.format("%02x", d[i]));
        return s.toString();
    }

    /** 0..n-1 의 정수 객체 표(바이옴 레지스트리 대신 — 전략 규칙은 값 종류와 무관하다). */
    static final class IntIds implements jr<Integer> {
        final Integer[] v;
        IntIds(Integer[] v) { this.v = v; }
        public int a(Integer o) { for (int i = 0; i < v.length; i++) if (v[i] == o) return i; return -1; }
        public Integer a(int i) { return i >= 0 && i < v.length ? v[i] : null; }
        public int c() { return v.length; }
        public Iterator<Integer> iterator() { return Arrays.asList(v).iterator(); }
    }

    // ---- 아래 생성기는 시험(NmsDetachedSectionOracleTest)과 글자 그대로 같아야 한다 ----

    static final class Cases {
        static final int COUNT = 240;
        static final int DEFAULT = 0, CTOR = 1, CTOR_MISMATCH = 2, SECTION = 3;
        static final int[] STATE_K = {1, 2, 3, 4, 5, 9, 16, 17, 40, 256, 257, 600};
        static final int[] GENERIC_SIZE = {4, 9, 64, 65, 200};
        static final int[] CTOR_BITS = {0, 1, 2, 3, 4, 5, 6, 8, 15};

        int kind, registrySize, ctorBits, biome;
        boolean generic, biomeStrategy;
        int[] pal, ctorCells, ctorList, opIdx, opVal, opKind;

        static Cases make(long seed, int nStates) {
            SplittableRandom r = new SplittableRandom(seed * 0x9E3779B97F4A7C15L);
            Cases c = new Cases();
            c.kind = (int) (seed % 4);
            c.biomeStrategy = c.kind != SECTION && r.nextInt(3) == 0;
            c.generic = c.biomeStrategy;
            c.registrySize = c.generic ? GENERIC_SIZE[r.nextInt(GENERIC_SIZE.length)] : nStates;
            int k = c.generic ? 1 + r.nextInt(c.registrySize) : STATE_K[r.nextInt(STATE_K.length)];
            LinkedHashSet<Integer> pal = new LinkedHashSet<>();
            while (pal.size() < k) pal.add(r.nextInt(c.registrySize));
            c.pal = pal.stream().mapToInt(Integer::intValue).toArray();
            int size = c.biomeStrategy ? 64 : 4096;
            if (c.kind == CTOR || c.kind == CTOR_MISMATCH) {
                c.ctorBits = c.generic ? 1 + r.nextInt(8) : CTOR_BITS[r.nextInt(CTOR_BITS.length)];
                if (c.ctorBits == 15) c.ctorBits = 32 - Integer.numberOfLeadingZeros(nStates - 1);
                boolean global = c.ctorBits > 8 || (c.biomeStrategy && c.kind == CTOR && c.ctorBits > 3);
                int listLen = c.ctorBits == 0 ? 1 : global ? 0 : Math.min(c.pal.length, 1 << c.ctorBits);
                if (!global && listLen == 0) listLen = 1;
                c.ctorList = Arrays.copyOf(c.pal, Math.min(listLen, c.pal.length));
                c.ctorCells = new int[size];
                if (c.ctorBits > 0) {
                    for (int i = 0; i < size; i++) {
                        int p = c.pal[r.nextInt(c.pal.length)];
                        c.ctorCells[i] = global ? (p < (1 << c.ctorBits) ? p : 0) : r.nextInt(c.ctorList.length);
                    }
                }
            } else {
                c.ctorList = new int[0];
                c.ctorCells = new int[0];
            }
            c.biome = r.nextInt(70);
            int m = r.nextInt(c.biomeStrategy ? 300 : 6000);
            c.opIdx = new int[m];
            c.opVal = new int[m];
            c.opKind = new int[m];
            for (int i = 0; i < m; i++) {
                c.opIdx[i] = r.nextInt(size);
                c.opVal[i] = r.nextInt(10) == 0 ? 0 : c.pal[r.nextInt(c.pal.length)];
                c.opKind[i] = r.nextInt(3);
            }
            return c;
        }

        long hash() {
            long h = 1125899906842597L;
            h = 31 * h + kind; h = 31 * h + registrySize; h = 31 * h + ctorBits; h = 31 * h + biome;
            h = 31 * h + (generic ? 1 : 0); h = 31 * h + (biomeStrategy ? 1 : 0);
            for (int[] arr : new int[][]{pal, ctorCells, ctorList, opIdx, opVal, opKind}) for (int v : arr) h = 31 * h + v;
            return h;
        }
    }
}
