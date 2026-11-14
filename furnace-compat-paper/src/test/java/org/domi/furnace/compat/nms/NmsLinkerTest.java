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
                        public long getSeed() { return 0; }
                        public net.minecraft.world.level.chunk.LevelChunk getChunkAt(net.minecraft.core.BlockPos p) { return null; }
                        public long mixed(long a, double b, int c) { return 0; }
                        public static int staticCounter;
                        public int instanceCounter;
                    }
                    """,
            "net.minecraft.world.level.chunk.LevelChunk", """
                    package net.minecraft.world.level.chunk;
                    public class LevelChunk {
                        public LevelChunk(int x, int z) {}
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
                        public static int currentTick;
                        public String getServerVersion() { return null; }
                    }
                    """);

    private static final String USER = """
            package linky;
            import java.util.function.Supplier;
            import net.minecraft.server.level.ServerLevel;
            import net.minecraft.world.level.chunk.LevelChunk;
            public final class User {
                public static String present() { return net.minecraft.server.MinecraftServer.getServer().getServerVersion(); }
                public static Object missingMethod(ServerLevel l) { return l.getChunkAt(null); }
                public static long missingWide(ServerLevel l) { return l.mixed(1L, 2.0, 3) + 5; }
                public static Object missingClassNew() { return new LevelChunk(1, 2); }
                public static Object missingCtor(boolean f) { return new net.minecraft.world.item.ItemStack(f ? "a" : "b"); }
                public static boolean missingInstanceof(Object o) { return o instanceof LevelChunk; }
                public static Object missingCast(Object o) { return (LevelChunk) o; }
                public static Object missingLdc() { return LevelChunk.class; }
                public static int missingStaticField() { return net.minecraft.server.MinecraftServer.currentTick; }
                public static void missingPutStatic() { ServerLevel.staticCounter = 3; }
                public static int missingField(ServerLevel l) { return l.instanceCounter; }
                public static void missingPutField(ServerLevel l) { l.instanceCounter = 4; }
                public static Object missingMethodRef(ServerLevel l) { Supplier<Object> s = () -> l.getChunkAt(null); return s; }
                public static Object missingUnboundRef() { java.util.function.Function<ServerLevel, Object> f = ServerLevel::getSeedBoxed; return f; }
                public static Object presentStaticField() { return net.minecraft.world.item.ItemStack.EMPTY; }
                public static Object[] missingArray() { return new LevelChunk[3]; }
                public static String untouched() { return "ok"; }
                public static long detect(ServerLevel l) { try { return l.mixed(1L, 2.0, 3); } catch (NoSuchMethodError e) { return -1L; } }
                public static long detectLinkage(ServerLevel l) { try { return l.instanceCounter; } catch (LinkageError e) { return -2L; } }
                public static Object missingLambdaType() { java.util.function.Function<LevelChunk, String> f = z -> "x"; return f; }
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
            public class MyChunk extends net.minecraft.world.level.chunk.LevelChunk {
                public MyChunk() { super(0, 0); }
            }
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
        assertTrue(e.getMessage().contains("net.minecraft.server.level.ServerLevel#getChunkAt(BlockPos)"), e.getMessage());
        assertTrue(e.getMessage().contains("플러그인=Linky"), e.getMessage());
        assertTrue(e.getMessage().contains("호출 위치=linky.User#missingMethod:7"), e.getMessage());
        // 2칸 인자(long·double)와 2칸 반환값도 스택이 맞는다(검증 통과)
        assertTrue(trapped(c, "missingWide", level()).getMessage().contains("#mixed(long, double, int)"));
        Error f = trapped(c, "missingStaticField");
        assertInstanceOf(NoSuchFieldError.class, f);
        assertTrue(f.getMessage().contains("MinecraftServer#currentTick"));
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
        assertTrue(chunk.getMessage().contains("net.minecraft.world.level.chunk.LevelChunk"), chunk.getMessage());
        assertTrue(chunk.getMessage().contains("등급 C"), "청크 내부는 등급 C 이유를 단다: " + chunk.getMessage());
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
    void subclassingNmsIsRefusedAtDefinitionWithTheRealException() {
        NmsClassTransformer t = new NmsClassTransformer("Linky", Set.of(), NmsClassTransformer.Options.DEFAULT, NmsSymbols.RUNTIME);
        Loader loader = new Loader(classes, t);
        UnsupportedNmsCall present = assertThrows(UnsupportedNmsCall.class, () -> loader.loadClass("linky.MyEntity"));
        assertTrue(present.getMessage().contains("상속") && present.getMessage().contains("등급 C"), present.getMessage());
        UnsupportedNmsCall missing = assertThrows(UnsupportedNmsCall.class, () -> loader.loadClass("linky.MyChunk"));
        assertTrue(missing.getMessage().contains("LevelChunk"), missing.getMessage());
    }

    @Test
    void jarOwnClassesUnderNmsNamesAreNotJudged() throws Throwable {
        // 플러그인이 net.minecraft 이름 클래스를 스스로 싣고 있으면(드묾) 그 클래스는 플러그인 것이다 — 덫을 놓지 않는다
        NmsClassTransformer t = new NmsClassTransformer("Linky", Set.of("net/minecraft/world/level/chunk/LevelChunk"),
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
                        "net.minecraft.world.level.chunk.LevelChunk", STUBS.get("net.minecraft.world.level.chunk.LevelChunk"),
                        "net.minecraft.world.item.ItemStack", STUBS.get("net.minecraft.world.item.ItemStack"),
                        "net.minecraft.server.MinecraftServer", STUBS.get("net.minecraft.server.MinecraftServer")),
                Set.of("net.minecraft."));
        NmsCompatReport r = NmsCompatReport.scan("R", jar, NmsSymbols.RUNTIME);
        assertTrue(r.namespace().startsWith("Spigot(가정"), "매니페스트가 없으면 Spigot 가정: " + r.namespace());
        assertTrue(r.missing().contains("net.minecraft.world.level.chunk.LevelChunk"), r.missing().toString());
        assertTrue(r.missing().contains("net.minecraft.server.level.ServerLevel#getChunkAt(1)"), r.missing().toString());
        assertTrue(r.missing().stream().noneMatch(m -> m.startsWith("net.minecraft.world.item.ItemStack#EMPTY")), "있는 것은 없음에 들지 않는다");
        assertEquals(1, r.gradeC(), "LevelChunk 하나가 등급 C");
        assertEquals(r.missing().size(), r.gradeC() + r.unknown());
        assertTrue(r.present() >= 3, "MinecraftServer·getServer·getServerVersion·ItemStack·EMPTY …: " + r.present());
        assertEquals(r.present() + r.gradeC() + r.unknown(), r.symbols());
        assertEquals(List.copyOf(new java.util.TreeSet<>(r.missing())), r.missing(), "정렬");
        assertNull(r.ownClasses().stream().filter(n -> n.startsWith("net/")).findFirst().orElse(null), "스텁은 jar 에 없다");
    }

    @Test
    void reportCountsNmsSubclassingAsGradeCSoStrictRefusesBeforeLoading() throws IOException {
        Path jar = JarCompiler.jar(tmp.resolve("sub.jar"), "name: S\nversion: '1'\nmain: linky.MyEntity\n",
                Map.of("linky.MyEntity", SUB), Set.of("net.minecraft."));
        NmsCompatReport r = NmsCompatReport.scan("S", jar, NmsSymbols.RUNTIME);
        assertTrue(r.missing().contains("상속 linky.MyEntity → net.minecraft.world.entity.LivingEntity"), r.missing().toString());
        assertEquals(1, r.gradeC());
        assertThrows(UnsupportedNmsCall.class, () -> NmsClassTransformer.forJar("S", jar, new NmsClassTransformer.Options(true, false)),
                "strict 는 정의 거부될 클래스가 있는 jar 를 로드 전에 거절한다");
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

    /** 다른 버전 모듈 안의 호출 위치는 경고 대신 DEBUG — 같은 대상의 1.21.1 몫 경고는 남는다. */
    @Test
    void otherVersionCallSitesAreRecognised() {
        assertTrue(NmsTrap.otherVersionSite("com.sk89q.worldedit.bukkit.adapter.impl.fawe.v1_21_9.PaperweightFaweAdapter#<clinit>:149"));
        assertTrue(NmsTrap.otherVersionSite("com.sk89q.worldedit.bukkit.adapter.impl.fawe.v26_1.PaperweightFaweAdapter#<clinit>:10"));
        assertFalse(NmsTrap.otherVersionSite("com.sk89q.worldedit.bukkit.adapter.impl.fawe.v1_21_R1.PaperweightFaweAdapter#<clinit>:148"));
        assertFalse(NmsTrap.otherVersionSite(null));
    }
}
