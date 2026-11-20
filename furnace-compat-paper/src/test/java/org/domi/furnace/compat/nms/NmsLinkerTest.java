package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * H7a: shim 에 없는 NMS 심볼을 가리키는 바이트코드가 클래스 정의·검증을 통과하고, 그 명령이 <b>실행될 때만</b> 그 자리에서
 * JVM 과 같은 종류의 링크 오류({@link UnsupportedNmsLink}: NoSuchMethodError·NoSuchFieldError·NoClassDefFoundError 하위)를 던지는지. 플러그인 코드는 시험 안에서 실제 NMS 모양 스텁에 대고 컴파일하고, 스텁은 jar 에서 뺀다
 * (실제 Paper 용 플러그인 jar 와 같은 모양 — 서버에 그 심볼이 없다). 변환기를 빼면 같은 코드가 NoSuchMethodError·NoClassDefFoundError 다.
 */
class NmsLinkerTest {

    /** 스텁: 이 서버 shim 에 없는 멤버·클래스(Mojang 이름 1.21.1 모양). jar 에 넣지 않는다. */
    private static final Map<String, String> STUBS = Map.of(
            "net.minecraft.server.level.ServerLevel", """
                    package net.minecraft.server.level;
                    public class ServerLevel extends net.minecraft.world.level.Level {
                        protected ServerLevel() { super(null); }
                        public net.minecraft.world.level.chunk.ChunkSource getChunkSource() { return null; }
                        public long getSeed() { return 0; }
                        public ServerLevel getMinecraftWorld() { return this; }
                        public net.minecraft.world.level.chunk.ProtoChunk getProtoChunkAt(net.minecraft.core.BlockPos p) { return null; }
                        public long mixed(long a, double b, int c) { return 0; }
                        public static int staticCounter;
                        public int instanceCounter;
                    }
                    """,
            "net.minecraft.world.level.chunk.ProtoChunk", """
                    package net.minecraft.world.level.chunk;
                    public class ProtoChunk {
                        public ProtoChunk(int x, int z) {}
                        public int getHeight() { return 0; }
                    }
                    """,
            "net.minecraft.world.item.ItemStack", """
                    package net.minecraft.world.item;
                    public final class ItemStack {
                        public static final ItemStack EMPTY = null;
                        public ItemStack(String nope) {}
                        public boolean isEmpty() { return true; }
                    }
                    """,
            "net.minecraft.server.MinecraftServer", """
                    package net.minecraft.server;
                    public abstract class MinecraftServer {
                        public static MinecraftServer getServer() { return null; }
                        public static int tickCount;
                        public String getServerVersion() { return null; }
                    }
                    """);

    private static final String USER = """
            package linky;
            import java.util.function.Supplier;
            import net.minecraft.server.level.ServerLevel;
            import net.minecraft.world.level.chunk.ProtoChunk;
            public final class User {
                public static String present() { return net.minecraft.server.MinecraftServer.getServer().getServerVersion(); }
                public static Object missingMethod(ServerLevel l) { return l.getProtoChunkAt(null); }
                public static long missingWide(ServerLevel l) { return l.mixed(1L, 2.0, 3) + 5; }
                public static Object missingClassNew() { return new ProtoChunk(1, 2); }
                public static Object missingCtor(boolean f) { return new net.minecraft.world.item.ItemStack(f ? "a" : "b"); }
                public static boolean missingInstanceof(Object o) { return o instanceof ProtoChunk; }
                public static Object missingCast(Object o) { return (ProtoChunk) o; }
                public static Object missingLdc() { return ProtoChunk.class; }
                public static int missingStaticField() { return net.minecraft.server.MinecraftServer.tickCount; }
                public static void missingPutStatic() { ServerLevel.staticCounter = 3; }
                public static int missingField(ServerLevel l) { return l.instanceCounter; }
                public static void missingPutField(ServerLevel l) { l.instanceCounter = 4; }
                public static Object missingMethodRef(ServerLevel l) { Supplier<Object> s = () -> l.getProtoChunkAt(null); return s; }
                public static Object missingUnboundRef() { java.util.function.Function<ServerLevel, Object> f = ServerLevel::getSeedBoxed; return f; }
                public static Object presentStaticField() { return net.minecraft.world.item.ItemStack.EMPTY; }
                public static Object[] missingArray() { return new ProtoChunk[3]; }
                public static String untouched() { return "ok"; }
                public static long detect(ServerLevel l) { try { return l.mixed(1L, 2.0, 3); } catch (NoSuchMethodError e) { return -1L; } }
                public static long detectLinkage(ServerLevel l) { try { return l.instanceCounter; } catch (LinkageError e) { return -2L; } }
                public static Object missingLambdaType() { java.util.function.Function<ProtoChunk, String> f = z -> "x"; return f; }
            }
            """;

    /** 스텁에 getSeedBoxed 를 더하려고 ServerLevel 을 확장 — 같은 스텁 파일에 넣는다. */
    private static final String SEED_STUB_PATCH = "public Object getSeedBoxed() { return null; }";

    private static final String SUB = """
            package linky;
            public class MyEntity extends net.minecraft.world.entity.LivingEntity {
                public MyEntity() { super(null); }
            }
            """;

    private static final String SUB_MISSING = """
            package linky;
            public class MyChunk extends net.minecraft.world.level.chunk.ProtoChunk {
                public MyChunk() { super(0, 0); }
            }
            """;

    /** shim 의 상위 클래스가 final 인 경우(바닐라는 상속 가능 — H1): 껍데기는 Object 위에 서야 정의가 깨지지 않는다. */
    private static final String SUB_FINAL = """
            package linky;
            public class MyFinalParent extends net.minecraft.world.entity.animal.sniffer.Sniffer {
                public MyFinalParent() { super(); }
                public static String marker() { return "static-ok"; }
            }
            """;

    /** 상속해도 되는 shim(NmsInheritable) 하위 클래스 — 스텁의 무인자 상위 생성자는 shim 에 없다(FAWE 레벨 프록시의 15인자 생성자 꼴). */
    private static final String SUB_INHERITABLE = """
            package linky;
            public class MyLevel extends net.minecraft.server.level.ServerLevel {
                public int extra = 3;
                public MyLevel() { super(); }
                public MyLevel(int x) { this(); extra = x; }
                @Override public int getMinBuildHeight() { return 7 + extra; }
            }
            """;

    /** 컴파일용 스텁: 바닐라의 Sniffer 는 상속할 수 있지만 shim 은 final 이다(플러그인은 바닐라 모양으로 컴파일한다). */
    private static final String SNIFFER_STUB = """
            package net.minecraft.world.entity.animal.sniffer;
            public class Sniffer { public Sniffer() {} }
            """;

    @TempDir
    static Path tmp;

    static Map<String, byte[]> classes;

    @BeforeAll
    static void compile() throws IOException {
        Map<String, String> src = new HashMap<>(STUBS);
        src.put("net.minecraft.server.level.ServerLevel", STUBS.get("net.minecraft.server.level.ServerLevel")
                .replace("public static int staticCounter;", "public static int staticCounter; " + SEED_STUB_PATCH));
        src.put("linky.User", USER);
        src.put("linky.MyEntity", SUB);
        src.put("linky.MyChunk", SUB_MISSING);
        src.put("linky.MyFinalParent", SUB_FINAL);
        src.put("linky.MyLevel", SUB_INHERITABLE);
        src.put("net.minecraft.world.entity.animal.sniffer.Sniffer", SNIFFER_STUB);
        Path jar = JarCompiler.jar(tmp.resolve("linky.jar"), "name: Linky\nversion: '1'\nmain: linky.User\n", src, Set.of("net.minecraft."));
        classes = read(jar);
    }

    static Map<String, byte[]> read(Path jar) throws IOException {
        Map<String, byte[]> out = new HashMap<>();
        try (JarFile jf = new JarFile(jar.toFile())) {
            for (JarEntry e : java.util.Collections.list(jf.entries())) {
                if (!e.getName().endsWith(".class")) continue;
                try (InputStream in = jf.getInputStream(e)) {
                    out.put(e.getName().substring(0, e.getName().length() - 6).replace('/', '.'), in.readAllBytes());
                }
            }
        }
        return out;
    }

    /** 서버 PluginClassLoader 처럼: jar 클래스는 변환해 정의(자식 우선), UnsupportedOperationException 은 그대로 던진다. */
    static final class Loader extends ClassLoader {
        private final Map<String, byte[]> own;
        private final NmsClassTransformer t;

        Loader(Map<String, byte[]> own, NmsClassTransformer t) {
            super(NmsLinkerTest.class.getClassLoader());
            this.own = own;
            this.t = t;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> c = findLoadedClass(name);
                if (c == null && own.containsKey(name)) {
                    byte[] b = own.get(name);
                    if (t != null) {
                        byte[] x = t.transform(name, b);
                        if (x != null) b = x;
                    }
                    c = defineClass(name, b, 0, b.length);
                }
                return c != null ? c : super.loadClass(name, resolve);
            }
        }
    }

    private static Class<?> user(boolean transform) throws ClassNotFoundException {
        NmsClassTransformer t = transform ? new NmsClassTransformer("Linky", Set.of(), NmsClassTransformer.Options.DEFAULT, NmsSymbols.RUNTIME) : null;
        return new Loader(classes, t).loadClass("linky.User");
    }

    private static Object call(Class<?> c, String name, Object... args) throws Throwable {
        for (var m : c.getMethods()) {
            if (m.getName().equals(name)) {
                try {
                    return m.invoke(null, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            }
        }
        throw new AssertionError("no method " + name);
    }

    private static Error trapped(Class<?> c, String name, Object... args) {
        Throwable t = assertThrows(Throwable.class, () -> call(c, name, args));
        assertInstanceOf(UnsupportedNmsLink.class, t, () -> name + " 이 덫 대신 " + t + " 를 던졌다");
        return (Error) t;
    }

    private static net.minecraft.server.level.ServerLevel level() { return null; }

    @Test
    void presentSymbolsAreUntouchedAndTheClassVerifies() throws Throwable {
        Class<?> c = user(true);
        assertEquals("ok", call(c, "untouched"));
        // 있는 심볼은 그대로 링크된다(정적 다리·정적 필드)
        assertSame(net.minecraft.world.item.ItemStack.EMPTY, call(c, "presentStaticField"));
    }

    @Test
    void missingMembersThrowAtTheCallSiteWithPluginSiteAndTarget() throws Throwable {
        Class<?> c = user(true);
        Error e = trapped(c, "missingMethod", level());
        assertInstanceOf(NoSuchMethodError.class, e, "JVM 과 같은 종류");
        assertTrue(e.getMessage().contains("net.minecraft.server.level.ServerLevel#getProtoChunkAt(BlockPos)"), e.getMessage());
        assertTrue(e.getMessage().contains("plugin=Linky"), e.getMessage());
        assertTrue(e.getMessage().contains("call site=linky.User#missingMethod:7"), e.getMessage());
        // 2칸 인자(long·double)와 2칸 반환값도 스택이 맞는다(검증 통과)
        assertTrue(trapped(c, "missingWide", level()).getMessage().contains("#mixed(long, double, int)"));
        Error f = trapped(c, "missingStaticField");
        assertInstanceOf(NoSuchFieldError.class, f);
        assertTrue(f.getMessage().contains("MinecraftServer#tickCount"));
        trapped(c, "missingPutStatic");
        trapped(c, "missingField", level());
        trapped(c, "missingPutField", level());
    }

    @Test
    void featureDetectionByCatchingLinkageErrorsStillTakesTheFallback() throws Throwable {
        // 여러 버전 플러그인: try { 새 메서드 } catch (NoSuchMethodError) { 옛 경로 } — 변환 없을 때(JVM)와 같은 결과
        assertEquals(-1L, call(user(false), "detect", level()));
        assertEquals(-1L, call(user(true), "detect", level()));
        assertEquals(-2L, call(user(true), "detectLinkage", level()));
    }

    @Test
    void missingClassesAndConstructorsThrowWhenReached() throws Throwable {
        Class<?> c = user(true);
        Error chunk = trapped(c, "missingClassNew");
        assertInstanceOf(NoClassDefFoundError.class, chunk);
        assertTrue(chunk.getMessage().contains("net.minecraft.world.level.chunk.ProtoChunk"), chunk.getMessage());
        assertTrue(chunk.getMessage().contains("Grade C"), "청크 내부는 등급 C 이유를 단다: " + chunk.getMessage());
        // 있는 클래스의 없는 생성자 — 인자 계산 중 분기(미초기화 값이 프레임에 걸린다)가 있어도 검증을 통과한다
        Error ctor = trapped(c, "missingCtor", true);
        assertInstanceOf(NoSuchMethodError.class, ctor);
        assertTrue(ctor.getMessage().contains("ItemStack#<init>(String)"));
        trapped(c, "missingInstanceof", "x");
        trapped(c, "missingCast", "x");
        // JVM 처럼 null 은 없는 클래스를 찾지 않고 통과한다
        assertNull(call(c, "missingCast", (Object) null));
        assertEquals(false, call(c, "missingInstanceof", (Object) null));
        assertNull(call(user(false), "missingCast", (Object) null), "변환 없을 때도 같다");
        trapped(c, "missingLdc");
        trapped(c, "missingArray");
    }

    @Test
    void lambdasAndMethodReferencesToMissingSymbolsFailOnlyWhenRun() throws Throwable {
        Class<?> c = user(true);
        // 람다 본문은 만들 때가 아니라 부를 때 덫에 걸린다
        Object s = call(c, "missingMethodRef", level());
        assertInstanceOf(java.util.function.Supplier.class, s);
        assertThrows(NoSuchMethodError.class, ((java.util.function.Supplier<?>) s)::get);
        // 없는 메서드를 가리키는 메서드 참조(invokedynamic 핸들)는 그 자리에서
        trapped(c, "missingUnboundRef");
        // 람다 서술자에 없는 클래스(instantiatedMethodType) — JVM 은 링크 때 NoClassDefFoundError
        assertInstanceOf(NoClassDefFoundError.class, trapped(c, "missingLambdaType"));
        assertThrows(NoClassDefFoundError.class, () -> call(user(false), "missingLambdaType"));
    }

    @Test
    void withoutTheTransformerTheSameCodeFailsWithLinkageErrors() throws Throwable {
        Class<?> c = user(false);
        assertEquals("ok", call(c, "untouched"));
        assertThrows(NoSuchMethodError.class, () -> call(c, "missingMethod", level()));
        assertThrows(NoClassDefFoundError.class, () -> call(c, "missingClassNew"));
    }

    @Test
    void subclassingNmsDefinesAHuskThatRefusesConstruction() throws Throwable {
        // N2b-6: 예전에는 정의를 거부했다. 이제 껍데기로 정의하고(가리키는 코드 — 람다·메서드 참조·instanceof — 는 된다) 만들 때 같은 문구로 거부한다
        NmsClassTransformer t = new NmsClassTransformer("Linky", Set.of(), NmsClassTransformer.Options.DEFAULT, NmsSymbols.RUNTIME);
        Loader loader = new Loader(classes, t);
        Class<?> present = loader.loadClass("linky.MyEntity");
        assertEquals(net.minecraft.world.entity.LivingEntity.class, present.getSuperclass(), "defined on top of the shim parent");
        assertEquals(0, java.util.Arrays.stream(present.getDeclaredMethods()).filter(m -> !java.lang.reflect.Modifier.isStatic(m.getModifiers())).count(),
                "instance methods are removed");
        var e = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> present.getDeclaredConstructors()[0].newInstance(new Object[present.getDeclaredConstructors()[0].getParameterCount()]));
        assertInstanceOf(UnsupportedNmsCall.class, e.getCause());
        assertTrue(e.getCause().getMessage().contains("extends") && e.getCause().getMessage().contains("Grade C"), e.getCause().getMessage());
        Class<?> missing = loader.loadClass("linky.MyChunk");
        assertEquals(Object.class, missing.getSuperclass(), "on Object when there is no parent shim");
        var e2 = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> missing.getDeclaredConstructor().newInstance());
        assertTrue(e2.getCause().getMessage().contains("ProtoChunk"), e2.getCause().getMessage());
    }

    @Test
    void inheritableShimParentKeepsTheSubclassAndTrapsOnlyAMissingSuperConstructor() throws Throwable {
        // T-204 V5: 상속해도 되는 shim 은 껍데기가 아니다 — 재정의가 남고, shim 에 없는 상위 생성자를 부르는 생성자만 그 자리에서 링크 오류
        NmsClassTransformer t = new NmsClassTransformer("Linky", Set.of(), NmsClassTransformer.Options.DEFAULT, NmsSymbols.RUNTIME);
        Class<?> c = new Loader(classes, t).loadClass("linky.MyLevel");
        assertEquals(net.minecraft.server.level.ServerLevel.class, c.getSuperclass());
        var e = assertThrows(InvocationTargetException.class, () -> c.getDeclaredConstructor().newInstance());
        assertInstanceOf(UnsupportedNmsLink.class, e.getCause());
        assertInstanceOf(NoSuchMethodError.class, e.getCause(), "JVM 이 냈을 종류");
        assertTrue(e.getCause().getMessage().contains("ServerLevel#<init>()"), e.getCause().getMessage());
        var e2 = assertThrows(InvocationTargetException.class, () -> c.getDeclaredConstructor(int.class).newInstance(5));
        assertInstanceOf(UnsupportedNmsLink.class, e2.getCause(), "this(...) 로 그 생성자를 부른다");
        // 생성자 없이(Unsafe) 만든 객체는 재정의로 답한다 — 필드 초기화는 돌지 않는다(Paper 에서도 같다)
        var f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        var level = (net.minecraft.server.level.ServerLevel) ((sun.misc.Unsafe) f.get(null)).allocateInstance(c);
        assertEquals(7, level.getMinBuildHeight());
        assertTrue(NmsLinker.inheritable(NmsSymbols.RUNTIME, "net/minecraft/world/item/context/BlockPlaceContext"));
        assertFalse(NmsLinker.inheritable(NmsSymbols.RUNTIME, "net/minecraft/world/entity/LivingEntity"));
    }

    @Test
    void huskOnAFinalShimParentIsDefinedOnObjectAndKeepsItsStaticMembers() throws Throwable {
        // H1: 상위 shim 이 final 이면 그 위에 정의하면 IncompatibleClassChangeError — Object 위에 세우고 정적 멤버는 그대로 쓴다
        assertTrue(java.lang.reflect.Modifier.isFinal(net.minecraft.world.entity.animal.sniffer.Sniffer.class.getModifiers()), "premise: the shim parent is final");
        NmsClassTransformer t = new NmsClassTransformer("Linky", Set.of(), NmsClassTransformer.Options.DEFAULT, NmsSymbols.RUNTIME);
        Loader loader = new Loader(classes, t);
        Class<?> c = loader.loadClass("linky.MyFinalParent");
        assertEquals(Object.class, c.getSuperclass());
        assertEquals("static-ok", c.getMethod("marker").invoke(null));
        var e = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> c.getDeclaredConstructor().newInstance());
        assertInstanceOf(UnsupportedNmsCall.class, e.getCause());
    }

    @Test
    void jarOwnClassesUnderNmsNamesAreNotJudged() throws Throwable {
        // 플러그인이 net.minecraft 이름 클래스를 스스로 싣고 있으면(드묾) 그 클래스는 플러그인 것이다 — 덫을 놓지 않는다
        NmsClassTransformer t = new NmsClassTransformer("Linky", Set.of("net/minecraft/world/level/chunk/ProtoChunk"),
                NmsClassTransformer.Options.DEFAULT, NmsSymbols.RUNTIME);
        Class<?> c = new Loader(classes, t).loadClass("linky.User");
        assertThrows(NoClassDefFoundError.class, () -> call(c, "missingClassNew"), "덫이 아니라 JVM 의 링크 오류(이 시험 jar 에는 실제로 없다)");
        trapped(c, "missingMethod", level());
    }

    @Test
    void compatReportCountsPresentGradeCAndMissing() throws IOException {
        Path jar = JarCompiler.jar(tmp.resolve("report.jar"), "name: R\nversion: '1'\nmain: linky.User\n",
                Map.of("linky.User", USER, "net.minecraft.server.level.ServerLevel", STUBS.get("net.minecraft.server.level.ServerLevel")
                                .replace("public static int staticCounter;", "public static int staticCounter; " + SEED_STUB_PATCH),
                        "net.minecraft.world.level.chunk.ProtoChunk", STUBS.get("net.minecraft.world.level.chunk.ProtoChunk"),
                        "net.minecraft.world.item.ItemStack", STUBS.get("net.minecraft.world.item.ItemStack"),
                        "net.minecraft.server.MinecraftServer", STUBS.get("net.minecraft.server.MinecraftServer")),
                Set.of("net.minecraft."));
        NmsCompatReport r = NmsCompatReport.scan("R", jar, NmsSymbols.RUNTIME);
        assertTrue(NmsCompatReport.NAMESPACE_ASSUMED.equals(r.namespace()), "매니페스트가 없으면 Spigot 가정: " + r.namespace());
        assertTrue(r.missing().contains("net.minecraft.world.level.chunk.ProtoChunk"), r.missing().toString());
        assertTrue(r.missing().contains("net.minecraft.server.level.ServerLevel#getProtoChunkAt(1)"), r.missing().toString());
        assertTrue(r.missing().stream().noneMatch(m -> m.startsWith("net.minecraft.world.item.ItemStack#EMPTY")), "있는 것은 없음에 들지 않는다");
        assertEquals(1, r.gradeC(), "ProtoChunk 하나가 등급 C");
        assertEquals(r.missing().size(), r.gradeC() + r.unknown());
        // compat gaps(T-233): 말투와 무관한 고정 이름으로 같은 구멍을 담는다
        assertTrue(r.gaps().contains("class\tnet.minecraft.world.level.chunk.ProtoChunk"), r.gaps().toString());
        assertTrue(r.gaps().contains("method\tnet.minecraft.server.level.ServerLevel#getProtoChunkAt(BlockPos)"), r.gaps().toString());
        assertEquals(r.missing().size(), r.gaps().size());
        assertTrue(r.present() >= 3, "MinecraftServer·getServer·getServerVersion·ItemStack·EMPTY …: " + r.present());
        assertEquals(r.present() + r.gradeC() + r.unknown(), r.symbols());
        assertEquals(List.copyOf(new java.util.TreeSet<>(r.missing())), r.missing(), "정렬");
        assertNull(r.ownClasses().stream().filter(n -> n.startsWith("net/")).findFirst().orElse(null), "스텁은 jar 에 없다");
    }

    /** T-233: 로드 때 보고와 덫이 같은 이름을 쓴다 — 한 심볼이 한 줄(referenced=true, calls=1)이 된다. */
    @Test
    void gapRowsFromLoadReportAndTrapMergeIntoOneRowPerSymbol() throws Throwable {
        Path jar = JarCompiler.jar(tmp.resolve("gaps.jar"), "name: Linky\nversion: '1'\nmain: linky.User\n",
                Map.of("linky.User", USER, "net.minecraft.server.level.ServerLevel", STUBS.get("net.minecraft.server.level.ServerLevel")
                                .replace("public static int staticCounter;", "public static int staticCounter; " + SEED_STUB_PATCH),
                        "net.minecraft.world.level.chunk.ProtoChunk", STUBS.get("net.minecraft.world.level.chunk.ProtoChunk"),
                        "net.minecraft.world.item.ItemStack", STUBS.get("net.minecraft.world.item.ItemStack"),
                        "net.minecraft.server.MinecraftServer", STUBS.get("net.minecraft.server.MinecraftServer")),
                Set.of("net.minecraft."));
        NmsTrap.resetWarnings();
        NmsCompatReport.scan("Linky", jar, NmsSymbols.RUNTIME).log();
        Class<?> c = user(true);
        trapped(c, "missingMethod", level());
        trapped(c, "missingField", level());
        trapped(c, "missingStaticField");
        List<org.domi.furnace.api.plugin.CompatGap> g = NmsGaps.snapshot();
        for (String target : List.of("net.minecraft.server.level.ServerLevel#getProtoChunkAt(BlockPos)",
                "net.minecraft.server.level.ServerLevel#instanceCounter : int",
                "net.minecraft.server.MinecraftServer#tickCount : int")) {
            List<org.domi.furnace.api.plugin.CompatGap> rows = g.stream().filter(x -> x.target().equals(target)).toList();
            assertEquals(1, rows.size(), target + " 은 한 줄이어야 한다: " + g);
            assertTrue(rows.get(0).referenced(), target);
            assertEquals(1, rows.get(0).calls(), target);
        }
        assertTrue(g.stream().noneMatch(x -> x.target().matches(".*#\\w+\\(\\d+\\)")), "인자 수 꼴 이름이 남으면 안 된다: " + g);
        NmsTrap.resetWarnings();
    }

    @Test
    void reportCountsNmsSubclassingAsGradeCSoStrictRefusesBeforeLoading() throws IOException {
        Path jar = JarCompiler.jar(tmp.resolve("sub.jar"), "name: S\nversion: '1'\nmain: linky.MyEntity\n",
                Map.of("linky.MyEntity", SUB), Set.of("net.minecraft."));
        NmsCompatReport r = NmsCompatReport.scan("S", jar, NmsSymbols.RUNTIME);
        assertTrue(r.missing().contains("extends linky.MyEntity -> net.minecraft.world.entity.LivingEntity"), r.missing().toString());
        assertTrue(r.gaps().contains("extends\tnet.minecraft.world.entity.LivingEntity"), r.gaps().toString());
        assertEquals(1, r.gradeC());
        assertThrows(UnsupportedNmsCall.class, () -> NmsClassTransformer.forJar("S", jar, new NmsClassTransformer.Options(true, false)),
                "strict 는 정의 거부될 클래스가 있는 jar 를 로드 전에 거절한다");
    }

    @Test
    void reportLeavesInheritableShimSubclassesOutOfGradeCButCountsAMissingSuperConstructor() throws IOException {
        // 검토 #480: 변환기와 같은 판정(NmsLinker.inheritable) — 상속해도 되는 shim 의 하위 클래스는 등급 C 가 아니다. 없는 상위 생성자는 "아직 없음"
        Path jar = JarCompiler.jar(tmp.resolve("inherit.jar"), "name: I\nversion: '1'\nmain: linky.MyEntity\n",
                Map.of("linky.MyEntity", SUB, "linky.MyLevel", SUB_INHERITABLE,
                        "net.minecraft.server.level.ServerLevel", STUBS.get("net.minecraft.server.level.ServerLevel"),
                        "net.minecraft.world.level.chunk.ProtoChunk", STUBS.get("net.minecraft.world.level.chunk.ProtoChunk")),
                Set.of("net.minecraft."));
        NmsCompatReport r = NmsCompatReport.scan("I", jar, NmsSymbols.RUNTIME);
        assertTrue(r.missing().contains("extends linky.MyEntity -> net.minecraft.world.entity.LivingEntity"), r.missing().toString());
        assertTrue(r.missing().stream().noneMatch(m -> m.contains("extends linky.MyLevel")), r.missing().toString());
        assertTrue(r.missing().contains("net.minecraft.server.level.ServerLevel#<init>(0)"), "Object 의 생성자로 찾지 않는다: " + r.missing());
        assertEquals(1, r.gradeC(), "LivingEntity 상속 하나만 등급 C");
    }

    /** 상속해도 되는 shim(과 그 상위 사슬)의 final 인스턴스 메서드는 바닐라도 final 인 것뿐이어야 한다 — 재정의가 정의를 깨지 않게(검토 #480). */
    @Test
    void inheritableShimsKeepVanillaFinality() {
        // Paper 1.21.1 에서 final: Level#getChunkIfLoadedImmediately(T-211 3단계, jar 대조)
        Set<String> vanillaFinal = Set.of("UseOnContext#getHitResult", "Level#moonrise$getEntityLookup", "Level#getChunkIfLoadedImmediately");
        for (Class<?> c : List.of(net.minecraft.world.item.context.UseOnContext.class, net.minecraft.world.item.context.BlockPlaceContext.class,
                net.minecraft.server.level.ServerLevel.class, net.minecraft.server.level.ServerPlayer.class)) {
            assertTrue(NmsLinker.inheritable(NmsSymbols.RUNTIME, c.getName().replace('.', '/')), c.getName());
            for (Class<?> k = c; k != null && k.getName().startsWith("net.minecraft."); k = k.getSuperclass()) {
                for (var m : k.getDeclaredMethods()) {
                    int mod = m.getModifiers();
                    if (!java.lang.reflect.Modifier.isFinal(mod) || java.lang.reflect.Modifier.isStatic(mod) || java.lang.reflect.Modifier.isPrivate(mod)) continue;
                    assertTrue(vanillaFinal.contains(k.getSimpleName() + "#" + m.getName()), "바닐라와 다른 final: " + k.getName() + "#" + m.getName());
                }
            }
        }
    }

    @Test
    void otherVersionModulesAreLeftOutOfTheReport() {
        // 실제 표본 jar 의 버전 모듈 이름(IF·commandapi·signgui)과 접미사 이름
        for (String n : List.of("com/x/nms/v1_20_1/Inv", "dev/jorel/commandapi/nms/NMS_1_20_R3", "de/rapha149/signgui/version/Wrapper1_8_R3",
                "com/x/nms/v1_21_2_3/Inv", "com/x/nms/v1_21_0/Inv", "com/x/nms/v1_21_11/Inv", "org/bukkit/craftbukkit/v1_20_R3/CraftServer",
                "net/minecraft/server/v1_16_R3/World",
                // 연도식 새 버전(FAWE 2.16 의 v26_1·v26_3 어댑터 모듈)
                "com/sk89q/worldedit/bukkit/adapter/impl/fawe/v26_1/PaperweightLevelProxy", "com/x/nms/v26_2_1/Inv")) {
            assertTrue(NmsCompatReport.otherVersion(n), n);
        }
        for (String n : List.of("com/x/nms/v1_21_1/Inv", "dev/jorel/commandapi/nms/NMS_1_21_R1", "com/x/nms/v1_21_0_1/Inv", "com/x/nms/v1_21/Inv",
                "net/minecraft/server/level/ServerLevel", "com/x/Base64_1", "com/x/nms/v1_21_R1/Inv",
                "com/x/Rev26_1/Inv", "com/x/nms/v2_6/Inv")) {
            assertFalse(NmsCompatReport.otherVersion(n), n);
        }
    }

    /** T-247: 여러 판을 담은 jar 에서 이 서버가 고르지 않을 어댑터(Spigot 이름 짝·다른 프로토콜)는 세지 않는다. */
    @Test
    void unusedAdaptersAreReadFromTheJarLayout() {
        Set<String> own = Set.of(
                // Orebfuscator 꼴: 같은 판의 Spigot 이름 어댑터와 _mojang 어댑터
                "net/imprex/ore/nms/v1_21_R1/NmsManager", "net/imprex/ore/nms/v1_21_R1_mojang/NmsManager", "net/imprex/ore/nms/v1_21_R2/NmsManager",
                // Geyser 꼴: 프로토콜 번호 어댑터(767 이하 최대 = 766)와 spigot·paper 갈래
                "org/geyser/adapters/paper/v766/Adapter", "org/geyser/adapters/paper/v768/Adapter", "org/geyser/adapters/paper/v774/Adapter",
                "org/geyser/adapters/spigot/v1_21_R1/Adapter", "org/geyser/adapters/spigot/SpigotAdapters", "org/geyser/adapters/AdapterLoader",
                // 짝이 없는 것은 그대로
                "com/x/nms/v1_21_R1/Inv", "com/y/api/v100/Thing", "com/z/spigot/Util");
        assertEquals(Set.of("net/imprex/ore/nms/v1_21_R1/", "org/geyser/adapters/paper/v768/", "org/geyser/adapters/paper/v774/",
                "org/geyser/adapters/spigot/v1_21_R1/"), NmsCompatReport.unusedAdapters(own));
        Set<String> unused = NmsCompatReport.unusedAdapters(own);
        assertTrue(NmsCompatReport.inUnusedAdapter(unused, "org/geyser/adapters/spigot/v1_21_R1/Adapter"));
        assertFalse(NmsCompatReport.inUnusedAdapter(unused, "net/imprex/ore/nms/v1_21_R1_mojang/NmsManager"));
        assertFalse(NmsCompatReport.inUnusedAdapter(unused, "org/geyser/adapters/paper/v766/Adapter"));
        assertEquals(Set.of(), NmsCompatReport.unusedAdapters(Set.of("com/x/nms/v1_21_R1/Inv", "com/x/Main")), "관례가 안 보이면 전부 센다");
        assertFalse(NmsCompatReport.inUnusedAdapter(unused, "org/geyser/adapters/spigot/SpigotAdapters"), "spigot 바로 아래(고르는 쪽 코드)는 센다");
        // 플랫폼 모듈 관례: paper 모듈이 spigot 모듈을 함께 쓴다 — 판 이름 패키지가 없으면 빼지 않는다(검토 C1)
        assertEquals(Set.of(), NmsCompatReport.unusedAdapters(Set.of("com/p/platform/spigot/SpigotHooks", "com/p/platform/paper/PaperHooks")));
        assertEquals(Set.of(), NmsCompatReport.unusedAdapters(Set.of("com/p/platform/spigot/v1_21_R1/Hooks", "com/p/platform/paper/PaperHooks")),
                "paper 쪽에 판 이름 패키지가 없으면 판별 어댑터 묶음이 아니다");
    }

    @Test
    void unusedAdapterClassesAreNotCountedInTheReport() throws IOException {
        String body = "{ public static Object f() { return new net.minecraft.world.level.chunk.ProtoChunk(0, 0); } }";
        Path jar = JarCompiler.jar(tmp.resolve("adapters.jar"), "name: A\nversion: '1'\nmain: ada.Main\n",
                Map.of("ada.Main", "package ada; public class Main {}",
                        "ada.nms.v1_21_R1.Gap", "package ada.nms.v1_21_R1; public class Gap " + body,
                        "ada.nms.v1_21_R1_mojang.Ok", "package ada.nms.v1_21_R1_mojang; public class Ok { public static Object f() { return net.minecraft.world.item.ItemStack.EMPTY; } }",
                        "net.minecraft.world.level.chunk.ProtoChunk", STUBS.get("net.minecraft.world.level.chunk.ProtoChunk"),
                        "net.minecraft.world.item.ItemStack", STUBS.get("net.minecraft.world.item.ItemStack")),
                Set.of("net.minecraft."));
        NmsCompatReport r = NmsCompatReport.scan("A", jar, NmsSymbols.RUNTIME);
        assertEquals(List.of(), r.missing(), "Spigot 이름 짝 어댑터의 ProtoChunk 는 이 서버에서 안 탄다");
        assertEquals(1, r.otherVersionClasses());
        assertTrue(r.present() >= 1, "_mojang 어댑터는 센다: " + r.present());
    }

    /** 다른 버전 모듈 안의 호출 위치는 경고 대신 DEBUG — 같은 대상의 1.21.1 몫 경고는 남는다. */
    @Test
    void otherVersionCallSitesAreRecognised() {
        assertTrue(NmsTrap.otherVersionSite("com.sk89q.worldedit.bukkit.adapter.impl.fawe.v1_21_9.PaperweightFaweAdapter#<clinit>:149"));
        assertTrue(NmsTrap.otherVersionSite("com.sk89q.worldedit.bukkit.adapter.impl.fawe.v26_1.PaperweightFaweAdapter#<clinit>:10"));
        assertFalse(NmsTrap.otherVersionSite("com.sk89q.worldedit.bukkit.adapter.impl.fawe.v1_21_R1.PaperweightFaweAdapter#<clinit>:148"));
        assertFalse(NmsTrap.otherVersionSite(null));
    }
}
