package org.domi.furnace.compat.nms;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 블록·아이템·엔티티 종류 → 바닐라 클래스(T-231, 엔티티는 T-232). 표는 shim 생성기가 빌드 때 {@code src/shimspec/kind-classes-<판>.txt}(바닐라에서 뽑은 것)를 옮겨 둔
 * {@code META-INF/furnace/shim-kinds.txt} 다. 블록·아이템 객체를 그 클래스로 만들어 플러그인의 {@code instanceof CropBlock} 같은 판정이 바닐라와 같게 한다.
 * 클래스는 생성기 전용 생성자 {@code C(ShimCtor)} 로 만들고 재료(블록 종류·아이템 이름)는 표지에 실어 사슬 맨 위 손 shim 이 받는다.
 * 표에 없는 이름(데이터 팩·플러그인 종류)은 기본 클래스({@code Block}·{@code Item})다 — 바닐라에도 없는 종류라 정답이 없다.
 */
public final class NmsKinds {

    private NmsKinds() {}

    /** 빌드가 넣는 표 위치(생성기 ShimGenerator.KIND_INDEX 와 같다). */
    static final String INDEX = "META-INF/furnace/shim-kinds.txt";

    private record Table(Map<String, String> blocks, Map<String, String> items, Map<String, String> entities) {}

    private static volatile Table table;
    private static final ConcurrentHashMap<String, Constructor<?>> CTORS = new ConcurrentHashMap<>();

    private static Table table() {
        Table t = table;
        if (t == null) {
            synchronized (NmsKinds.class) {
                t = table;
                if (t == null) table = t = load();
            }
        }
        return t;
    }

    private static Table load() {
        Map<String, String> blocks = new HashMap<>();
        Map<String, String> items = new HashMap<>();
        Map<String, String> entities = new HashMap<>();
        try (InputStream in = NmsKinds.class.getClassLoader().getResourceAsStream(INDEX)) {
            if (in == null) throw new IllegalStateException("missing " + INDEX + " (built by the shim generator)");
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String l; (l = r.readLine()) != null; ) {
                if (l.isBlank()) continue;
                String[] w = l.split(" ");
                (w[0].equals("B") ? blocks : w[0].equals("I") ? items : entities).put(w[1], w[2]);
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + INDEX, e);
        }
        return new Table(Map.copyOf(blocks), Map.copyOf(items), Map.copyOf(entities));
    }

    /** 블록 이름(minecraft:wheat)의 바닐라 클래스 이진 이름. 표에 없으면 null. */
    public static String blockClass(String id) { return table().blocks().get(id); }

    /** 아이템 이름(minecraft:stone)의 바닐라 클래스 이진 이름. 표에 없으면 null. */
    public static String itemClass(String id) { return table().items().get(id); }

    /** 엔티티 종류 이름(minecraft:zombie)의 바닐라 클래스 이진 이름(T-232). 표에 없으면 null(플레이어 — 늘 ServerPlayer). */
    public static String entityClass(String id) { return table().entities().get(id); }

    /** 표의 엔티티 종류 수(시험용). */
    public static int entityCount() { return table().entities().size(); }

    /** 표의 블록 수(시험용). */
    public static int blockCount() { return table().blocks().size(); }

    /** 표의 아이템 수(시험용). */
    public static int itemCount() { return table().items().size(); }

    /**
     * 클래스 {@code className} 의 객체를 생성기 전용 생성자로 만든다(재료는 표지에 싣는다). 클래스 하나에 한 번 생성자를 찾아 둔다 — 블록 표·아이템을
     * 처음 만들 때만 불린다(핫 경로 아님).
     */
    public static <T> T create(String className, Class<T> base, Object payload) {
        Constructor<?> c = CTORS.computeIfAbsent(className, n -> {
            try {
                Class<?> k = Class.forName(n, false, NmsKinds.class.getClassLoader());
                if (!base.isAssignableFrom(k)) throw new IllegalStateException(n + " is not a " + base.getName());
                Constructor<?> ctor = k.getDeclaredConstructor(ShimCtor.class);
                ctor.setAccessible(true);
                return ctor;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("kind class without a generator constructor: " + n, e);
            }
        });
        try {
            return base.cast(c.newInstance(ShimCtor.carrying(payload)));
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            if (e.getCause() instanceof Error er) throw er;
            throw new IllegalStateException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot create " + className, e);
        }
    }
}
