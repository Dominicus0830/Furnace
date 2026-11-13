// T-033: 이벤트 칸 스냅샷 — (a) 패치의 API 모양 캐시, (b) 칸 참조 배열 복사 + 칸별 게으른 변환(LazyList)이 옛 즉시 변환과 같은 결과를 내는지.
//  - 아이템 변환: 옛 ApiConvert#item(LegacyApiConvert, since 2b28b2ff)과 새 item 이 무작위 스택(컴포넌트 0~6개·지움 표시·저장 안 하는 종류)에서
//    같은 API 아이템을 낸다. 캐시가 찬 뒤(같은 패치를 다른 아이템·개수로 다시 변환)도 같다.
//  - 스냅샷: 옛 즉시 변환 스냅샷(List.copyOf)과 새 게으른 스냅샷에 같은 무작위 조작열(범위 안·밖 get, 반복, equals·hashCode·toString,
//    contains(null), subList, toArray, 바꾸기 시도, getFirst …)을 돌려 값·예외(종류·메시지)·"어느 조작이 같은 객체를 받는가"가 같다.
//  - 이벤트 시점: 처리기가 칸을 이벤트 뒤에(용기가 바뀐 뒤에) 읽어도 이벤트 시점의 내용이다. 읽지 않은 칸은 변환하지 않는다.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.domi.furnace.api.event.LazyList;
import org.domi.furnace.api.inventory.InventorySnapshot;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.identity.Differential;
import org.domi.furnace.server.identity.legacy.plugin.LegacyApiConvert;
import org.domi.furnace.server.item.ComponentPatch;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.ItemRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class LazySnapshotIdentityTest {

    private static ItemRegistry items;

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
        items = ItemRegistry.load("items/1.21.1.txt");
    }

    // ------------------------------------------------------------------ 입력: 순수 데이터 명세 → 매번 새 서버 스택(새 패치 = 차가운 캐시)

    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) out.put((String) kv[i], kv[i + 1]);
        return out;
    }

    private static final String[] ITEMS = {"minecraft:diamond_sword", "minecraft:stone", "minecraft:potion", "minecraft:written_book",
            "minecraft:leather_chestplate", "minecraft:player_head", "minecraft:firework_rocket", "minecraft:enchanted_book", "minecraft:stick",
            "minecraft:shulker_box", "minecraft:bow", "minecraft:filled_map"};

    /** 컴포넌트 값 표본(정규화 전 꼴). 변형 번호 v 로 값이 조금씩 다르다. */
    private static Object sample(String comp, int v) {
        return switch (comp) {
            case "minecraft:damage" -> v * 7 % 500;
            case "minecraft:repair_cost" -> v % 40;
            case "minecraft:custom_model_data" -> v * 104729 - 3;
            case "minecraft:custom_name" -> v % 2 == 0 ? "\"이름" + v + "\"" : "{\"text\":\"b" + v + "\",\"italic\":false,\"color\":\"gold\"}";
            case "minecraft:item_name" -> "{\"text\":\"n" + v + "\",\"bold\":true}";
            case "minecraft:lore" -> List.of("\"첫 줄 " + v + "\"", "{\"text\":\"둘째\",\"italic\":false}");
            case "minecraft:enchantments" -> v % 2 == 0 ? m("sharpness", 1 + v % 5)
                    : m("levels", m("unbreaking", 3, "sharpness", 1 + v % 5), "show_in_tooltip", false);
            case "minecraft:stored_enchantments" -> m("mending", 1, "efficiency", 1 + v % 5);
            case "minecraft:custom_data" -> v % 2 == 0 ? m("a", v, "b", "x", "PublicBukkitValues", m("myplugin:level", v)) : "{a:1b,b:[I;1," + v + "]}";
            case "minecraft:dyed_color" -> v * 65599 & 0xFFFFFF;
            case "minecraft:potion_contents" -> v % 2 == 0 ? "minecraft:swiftness" : m("custom_effects", List.of(m("id", "speed", "amplifier", v % 4, "duration", 100)));
            case "minecraft:written_book_content" -> m("title", "T" + v, "author", "A", "pages", List.of("\"hello\"", "{\"text\":\"a\",\"bold\":true}"));
            case "minecraft:trim" -> m("material", "minecraft:gold", "pattern", "minecraft:coast");
            case "minecraft:fireworks" -> m("flight_duration", v % 3, "explosions", List.of(m("shape", "star", "colors", List.of(v))));
            case "minecraft:profile" -> "Notch";
            case "minecraft:can_break" -> m("blocks", v % 2 == 0 ? "stone" : "#minecraft:logs");
            case "minecraft:attribute_modifiers" -> List.of(m("type", "generic.armor", "id", "a:b" + v, "amount", 2.5 + v, "operation", "add_multiplied_base"));
            case "minecraft:container" -> List.of(m("slot", v % 27, "item", m("id", "stone", "count", 1 + v % 64)));
            case "minecraft:creative_slot_lock", "minecraft:intangible_projectile", "minecraft:hide_tooltip" -> m();
            case "minecraft:map_id" -> v;
            default -> throw new IllegalArgumentException(comp);
        };
    }

    private static final String[] COMPS = {"minecraft:damage", "minecraft:repair_cost", "minecraft:custom_model_data", "minecraft:custom_name",
            "minecraft:item_name", "minecraft:lore", "minecraft:enchantments", "minecraft:stored_enchantments", "minecraft:custom_data",
            "minecraft:dyed_color", "minecraft:potion_contents", "minecraft:written_book_content", "minecraft:trim", "minecraft:fireworks",
            "minecraft:profile", "minecraft:can_break", "minecraft:attribute_modifiers", "minecraft:container", "minecraft:creative_slot_lock",
            "minecraft:intangible_projectile", "minecraft:hide_tooltip", "minecraft:map_id"};

    /** 칸 하나: kind 0 = null, 1 = EMPTY, 2 = 아이템. comps[k] = 컴포넌트 번호, vars[k] = 변형(-1 = 지움 표시). */
    record SlotSpec(int kind, int item, int count, int[] comps, int[] vars) {
        ItemStack build() {
            if (kind == 0) return null;
            if (kind == 1) return ItemStack.EMPTY;
            Map<String, Object> raw = new LinkedHashMap<>();
            for (int k = 0; k < comps.length; k++) raw.put(COMPS[comps[k]], vars[k] < 0 ? null : sample(COMPS[comps[k]], vars[k]));
            return new ItemStack(items.idOf(ITEMS[item]), count, raw.isEmpty() ? ComponentPatch.EMPTY : ComponentPatch.of(raw));
        }

        @Override
        public String toString() {
            if (kind < 2) return kind == 0 ? "null" : "EMPTY";
            StringBuilder sb = new StringBuilder(ITEMS[item]).append('x').append(count).append('{');
            for (int k = 0; k < comps.length; k++) sb.append(vars[k] < 0 ? "!" : "").append(COMPS[comps[k]].substring(10)).append(vars[k] < 0 ? "" : "=" + vars[k]).append(',');
            return sb.append('}').toString();
        }
    }

    private static SlotSpec slot(SplittableRandom r, boolean allowNull) {
        int kind = r.nextInt(10);
        if (kind == 0 && allowNull) return new SlotSpec(0, 0, 0, new int[0], new int[0]);
        if (kind <= 2) return new SlotSpec(1, 0, 0, new int[0], new int[0]);
        int n = r.nextInt(4) == 0 ? 0 : r.nextInt(7);
        int[] comps = r.ints(n, 0, COMPS.length).distinct().toArray();
        int[] vars = new int[comps.length];
        for (int k = 0; k < comps.length; k++) vars[k] = r.nextInt(6) == 0 ? -1 : r.nextInt(50);
        return new SlotSpec(2, r.nextInt(ITEMS.length), 1 + r.nextInt(64), comps, vars);
    }

    // ------------------------------------------------------------------ (a) 아이템 변환

    /** 스택, 같은 패치를 쓰는 다른 아이템·개수 스택(캐시가 찬 뒤 변환), 레지스트리 없음 여부. */
    record ItemCase(SlotSpec spec, int twinItem, int twinCount, boolean noRegistry) {
        @Override
        public String toString() { return spec + " twin=" + ITEMS[twinItem] + "x" + twinCount + (noRegistry ? " (레지스트리 없음)" : ""); }
    }

    private interface ItemConv { org.domi.furnace.api.item.ItemStack apply(ItemStack s, ItemRegistry reg); }

    private static List<Object> convertCase(ItemCase in, ItemConv conv) {
        ItemStack s = in.spec().build();
        ItemRegistry reg = in.noRegistry() ? null : items;
        List<Object> out = new ArrayList<>();
        var a = conv.apply(s, reg);
        out.add(a);
        out.add(a.toString());   // 맵·집합 순서까지
        out.add(conv.apply(s, reg));   // 두 번째(캐시)
        if (s != null && !s.isEmpty()) {
            var twin = new ItemStack(items.idOf(ITEMS[in.twinItem()]), in.twinCount(), s.patch());
            var b = conv.apply(twin, reg);
            out.add(b);
            out.add(b.toString());
        }
        return out;
    }

    @Test
    void itemConversionMatchesLegacy() {
        var diff = Differential.<ItemCase, List<Object>>of("t033-item-api-form",
                        r -> new ItemCase(slot(r, true), r.nextInt(ITEMS.length), 1 + r.nextInt(64), r.nextInt(10) == 0))
                .seed(0x7033AL).iterations(3000);
        diff.check(in -> convertCase(in, LegacyApiConvert::item), in -> convertCase(in, ApiConvert::item));
        // 변이 점검: 캐시가 아이템 이름·개수까지 붙잡으면(패치가 같은 다른 스택에 옛 이름) 잡아야 한다
        Map<ComponentPatch, org.domi.furnace.api.item.ItemStack> wrong = new IdentityHashMap<>();
        diff.assertDetects(in -> convertCase(in, LegacyApiConvert::item), in -> {
            wrong.clear();
            return convertCase(in, (s, reg) -> s == null || s.isEmpty() ? ApiConvert.item(s, reg) : wrong.computeIfAbsent(s.patch(), p -> ApiConvert.item(s, reg)));
        });
    }

    /** 모든 아이템의 프로토타입 컴포넌트 전부(그리고 같은 이름 지움 표시)를 패치로 — 표본 생성기가 안 덮는 종류까지(PR #323 검토 R4). */
    @Test
    void everyPrototypeComponentMatchesLegacy() {
        List<String> bad = new ArrayList<>();
        int checked = 0;
        for (int id = 0; id < 4000; id++) {
            if (!items.isValid(id)) continue;
            String name = items.nameOf(id);
            Map<String, Object> d = org.domi.furnace.server.item.ItemPrototypes.defaults(name);
            if (d.isEmpty()) continue;
            Map<String, Object> rm = new LinkedHashMap<>();
            for (String k : d.keySet()) rm.put(k, null);
            for (ComponentPatch patch : new ComponentPatch[] {ComponentPatch.ofNormalized(d), ComponentPatch.ofNormalized(rm)}) {
                ItemStack s = new ItemStack(id, 3, patch);
                var old = LegacyApiConvert.item(s, items);
                for (int rep = 0; rep < 2; rep++) {   // 처음(캐시 채움)과 두 번째(캐시)
                    var now = ApiConvert.item(s, items);
                    checked += patch.size();
                    if ((!old.equals(now) || !old.toString().equals(now.toString())) && bad.size() < 5) bad.add(name + " " + old + " vs " + now);
                }
            }
        }
        assertEquals(List.of(), bad);
        assertTrue(checked > 10_000, "프로토타입 컴포넌트를 거의 못 봤다: " + checked);
    }

    /** 큰 모양(SNBT 합 8192자 초과)은 저장하지 않는다 — 값은 같고 매번 새로 만든다(PR #323 검토 R3). */
    @Test
    void largeApiFormIsNotRetained() {
        List<Object> pages = new ArrayList<>();
        for (int i = 0; i < 100; i++) pages.add("\"" + "가나다라마바사".repeat(36) + i + "\"");
        ComponentPatch book = ComponentPatch.of(m("minecraft:written_book_content", m("title", "T", "author", "A", "pages", pages)));
        ItemStack s = new ItemStack(items.idOf("minecraft:written_book"), 1, book);
        assertEquals(LegacyApiConvert.item(s, items), ApiConvert.item(s, items));
        assertEquals(LegacyApiConvert.item(s, items).toString(), ApiConvert.item(s, items).toString());
        assertTrue(book.apiForm() != book.apiForm(), "큰 모양은 붙잡지 않는다");
        ComponentPatch small = ComponentPatch.of(m("minecraft:custom_name", "\"Excalibur\"", "minecraft:enchantments", m("sharpness", 5)));
        assertSame(small.apiForm(), small.apiForm(), "작은 모양은 붙잡는다");
    }

    /** 손으로 만든 직렬화 스트림의 LazyList 는 JDK 불변 목록처럼 거부한다(PR #323 검토 R1). 정상 직렬화는 List.of 가 된다. */
    @Test
    void lazyListSerializationMatchesListOf() throws Exception {
        LazyList<String> l = LazyList.of(new Integer[] {1, 2, 3}, String::valueOf);
        var bo = new java.io.ByteArrayOutputStream();
        try (var oo = new java.io.ObjectOutputStream(bo)) { oo.writeObject(l); }
        Object back;
        try (var oi = new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bo.toByteArray()))) { back = oi.readObject(); }
        assertEquals(List.of("1", "2", "3").getClass(), back.getClass());
        assertEquals(List.of("1", "2", "3"), back);

        var crafted = new java.io.ByteArrayOutputStream();
        var d = new java.io.DataOutputStream(crafted);
        d.writeShort(0xACED);
        d.writeShort(5);
        d.writeByte(0x73);   // TC_OBJECT
        d.writeByte(0x72);   // TC_CLASSDESC
        d.writeUTF(LazyList.class.getName());
        d.writeLong(1L);
        d.writeByte(0x02);   // SC_SERIALIZABLE
        d.writeShort(0);
        d.writeByte(0x78);   // TC_ENDBLOCKDATA
        d.writeByte(0x70);   // 상위 없음
        d.flush();
        var e = assertThrows(java.io.InvalidObjectException.class, () -> {
            try (var oi = new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(crafted.toByteArray()))) { oi.readObject(); }
        });
        assertEquals("not serial proxy", e.getMessage());
    }

    @Test
    void apiFormIsReadOnlyAndShared() {
        ItemStack s = new SlotSpec(2, 0, 1, new int[] {6, 3}, new int[] {1, 2}).build();
        var form = s.patch().apiForm();
        assertSame(form, s.patch().apiForm(), "한 번 만든 모양을 계속 쓴다");
        assertThrows(UnsupportedOperationException.class, () -> form.components().put("minecraft:x", "1"));
        assertThrows(UnsupportedOperationException.class, () -> form.removed().add("minecraft:x"));
        assertSame(ComponentPatch.EMPTY.apiForm(), ComponentPatch.EMPTY.apiForm());
        assertTrue(ComponentPatch.EMPTY.apiForm().components().isEmpty());
    }

    // ------------------------------------------------------------------ (b) 게으른 스냅샷

    /** 칸들과 조작열. 조작 = {종류, 인자1, 인자2}. */
    record SnapCase(SlotSpec[] slots, int[][] ops) {
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("slots=").append(slots.length).append(" ops=");
            for (int[] o : ops) sb.append(o[0]).append('(').append(o[1]).append(',').append(o[2]).append(") ");
            return sb.toString();
        }
    }

    private static final int OP_KINDS = 30;

    private static SnapCase snapCase(SplittableRandom r) {
        int n = switch (r.nextInt(6)) {
            case 0 -> 0;
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 5;
            default -> r.nextInt(64);
        };
        SlotSpec[] slots = new SlotSpec[n];
        for (int i = 0; i < n; i++) slots[i] = slot(r, true);
        int[][] ops = new int[1 + r.nextInt(25)][];
        for (int k = 0; k < ops.length; k++) ops[k] = new int[] {r.nextInt(OP_KINDS), r.nextInt(n + 5) - 2, r.nextInt(n + 5) - 2};
        return new SnapCase(slots, ops);
    }

    private interface Snapper { InventorySnapshot make(String type, ItemStack[] slots); }

    /** 결과 줄: 값은 문자열, 원소 객체는 "처음 받은 조작 번호", 예외는 종류·메시지. */
    private static List<String> runSnap(SnapCase in, Snapper snapper) {
        ItemStack[] slots = new ItemStack[in.slots().length];
        for (int i = 0; i < slots.length; i++) slots[i] = in.slots()[i].build();
        InventorySnapshot snap = snapper.make("minecraft:generic_9x6", slots);
        // 부른 쪽이 배열을 나중에 고쳐도 스냅샷은 그대로여야 한다(옛 구현은 바로 변환했으니 당연히 그대로)
        for (int i = 0; i < slots.length; i++) slots[i] = ItemStack.EMPTY;
        List<org.domi.furnace.api.item.ItemStack> c = snap.contents();
        // 비교용 값(옛 변환, 매번 새로) — 동등성 조작의 인자
        List<org.domi.furnace.api.item.ItemStack> probe = new ArrayList<>();
        for (SlotSpec s : in.slots()) probe.add(LegacyApiConvert.item(s.build(), items));
        IdentityHashMap<Object, Integer> seen = new IdentityHashMap<>();
        List<String> out = new ArrayList<>();
        for (int k = 0; k < in.ops().length; k++) {
            int[] op = in.ops()[k];
            final int step = k;
            int a = op[1], b = op[2];
            java.util.function.Function<Object, String> obj = o -> o == null ? "null" : "obj#" + seen.computeIfAbsent(o, x -> step) + "=" + o;
            java.util.function.Function<List<?>, String> objs = l -> {
                StringBuilder sb = new StringBuilder("[");
                for (Object o : l) sb.append(obj.apply(o)).append(';');
                return sb.append(']').toString();
            };
            org.domi.furnace.api.item.ItemStack x = probe.isEmpty() ? org.domi.furnace.api.item.ItemStack.EMPTY : probe.get(Math.floorMod(a, probe.size()));
            String line;
            try {
                line = switch (op[0]) {
                    case 0, 1, 2 -> obj.apply(c.get(a));
                    case 3 -> obj.apply(snap.get(a));
                    case 4 -> c.size() + "/" + c.isEmpty() + "/" + snap.size();
                    case 5 -> {
                        List<Object> it = new ArrayList<>();
                        for (var e : c) it.add(e);
                        yield objs.apply(it);
                    }
                    case 6 -> String.valueOf(c.contains(x));
                    case 7 -> String.valueOf(c.contains(null));
                    case 8 -> c.indexOf(x) + "/" + c.lastIndexOf(x);
                    case 9 -> objs.apply(c.subList(a, b));
                    case 10 -> objs.apply(java.util.Arrays.asList(c.toArray()));
                    case 11 -> objs.apply(java.util.Arrays.asList(c.toArray(new org.domi.furnace.api.item.ItemStack[Math.max(0, a)])));
                    case 12 -> String.valueOf(c.add(x));
                    case 13 -> obj.apply(c.set(a, x));
                    case 14 -> obj.apply(c.remove(a));
                    case 15 -> {
                        c.clear();
                        yield "cleared";
                    }
                    case 16 -> {
                        c.sort(null);
                        yield "sorted";
                    }
                    case 17 -> String.valueOf(c.removeIf(e -> true));
                    case 18 -> {
                        c.replaceAll(e -> e);
                        yield "replaced";
                    }
                    case 19 -> {
                        var it = c.iterator();
                        it.next();
                        it.remove();
                        yield "it.remove";
                    }
                    case 20 -> c.equals(List.copyOf(probe)) + "/" + List.copyOf(probe).equals(c) + "/" + c.equals(null) + "/" + c.equals("x")
                            + "/" + c.equals(c);
                    case 21 -> c.hashCode() + "/" + snap.hashCode() + "/" + snap.equals(new InventorySnapshot("minecraft:generic_9x6", probe));
                    case 22 -> c + " | " + snap;
                    case 23 -> objs.apply(c.stream().filter(e -> !e.isEmpty()).toList()) + c.stream().count();
                    case 24 -> obj.apply(c.getFirst()) + "/" + obj.apply(c.getLast());
                    case 25 -> objs.apply(c.reversed());
                    case 26 -> {
                        var li = c.listIterator(a);
                        yield li.hasPrevious() + "/" + (li.hasNext() ? obj.apply(li.next()) : "-");
                    }
                    case 27 -> String.valueOf(c.containsAll(probe));
                    case 28 -> String.valueOf(c.addAll(List.of()));
                    case 29 -> obj.apply(c.removeLast());
                    default -> throw new IllegalStateException();
                };
            } catch (RuntimeException e) {
                // JVM 이 스스로 던지는 예외(배열 범위 밖·NPE 등)는 같은 자리에서 많이 던지면 C2 가 메시지 없는 미리 만든 객체로 바꾼다
                // (-XX:+OmitStackTraceInFastThrow, 기본 켜짐) — 어느 쪽이 먼저 뜨거워지느냐에 따라 메시지가 null 이 되므로 종류만 비교한다
                boolean implicit = e instanceof ArrayIndexOutOfBoundsException || e instanceof NullPointerException || e instanceof ClassCastException
                        || e instanceof ArithmeticException || e instanceof ArrayStoreException;
                line = "throw " + e.getClass().getName() + (implicit ? "" : ": " + e.getMessage());
            }
            out.add(k + " " + line);
        }
        return out;
    }

    @Test
    void lazySnapshotMatchesEagerSnapshot() {
        var diff = Differential.<SnapCase, List<String>>of("t033-lazy-snapshot", LazySnapshotIdentityTest::snapCase)
                .seed(0x7033BL).iterations(3000);
        diff.check(in -> runSnap(in, (t, s) -> LegacyApiConvert.snapshot(t, s, items)), in -> runSnap(in, (t, s) -> ApiConvert.snapshot(t, s, items)));
        // 변이 점검: 배열을 복사하지 않고 원본 배열을 들면(부른 쪽이 고친 뒤 읽음) 잡아야 한다
        diff.assertDetects(in -> runSnap(in, (t, s) -> LegacyApiConvert.snapshot(t, s, items)),
                in -> runSnap(in, (t, s) -> new InventorySnapshot(t, LazyListNoCopy.of(s))));
    }

    /** 변이 점검용: 원본 배열을 복사하지 않는 게으른 목록(LazyList 와 같은 꼴, 배열만 공유). */
    private static final class LazyListNoCopy {
        static List<org.domi.furnace.api.item.ItemStack> of(ItemStack[] shared) {
            // LazyList.of 는 배열을 복사한다 — 공유를 흉내 내려고 읽을 때 원본 배열의 지금 값을 쓴다
            Integer[] idx = new Integer[shared.length];
            for (int i = 0; i < idx.length; i++) idx[i] = i;
            return LazyList.of(idx, i -> ApiConvert.item(shared[i], items));
        }
    }

    @Test
    void unreadSlotsAreNotConverted() {
        ItemStack[] slots = new ItemStack[54];
        for (int i = 0; i < slots.length; i++) slots[i] = new SlotSpec(2, i % ITEMS.length, 1 + i, new int[] {6, 3, 8}, new int[] {i, i, i}).build();
        InventorySnapshot snap = ApiConvert.snapshot("minecraft:generic_9x6", slots, items);
        LazyList<?> lazy = assertInstanceOf(LazyList.class, snap.contents());
        assertEquals(54, snap.size());
        assertEquals(0, lazy.resolvedCount(), "크기만 보면 변환하지 않는다");
        assertEquals(LegacyApiConvert.item(slots[7], items), snap.get(7));
        assertEquals(1, lazy.resolvedCount(), "읽은 칸만 변환한다");
        assertSame(snap.get(7), snap.contents().get(7));
        assertEquals(LegacyApiConvert.snapshot("minecraft:generic_9x6", slots, items), snap, "전체 비교는 다 변환한다");
        assertEquals(54, lazy.resolvedCount());
    }

    @Test
    void nullConversionIsRejectedOnRead() {
        LazyList<String> l = LazyList.of(new Integer[] {1, 2}, i -> i == 2 ? null : "a");
        assertEquals("a", l.get(0));
        assertThrows(NullPointerException.class, () -> l.get(1), "List.copyOf 처럼 null 원소는 받지 않는다");
    }

    @RepeatedTest(20)
    void concurrentFirstReadsSeeOneObjectPerSlot() throws Exception {
        int threads = 8, rounds = 50;
        ItemStack[] slots = new ItemStack[27];
        for (int i = 0; i < slots.length; i++) slots[i] = new SlotSpec(2, i % ITEMS.length, 1, new int[] {3, 6}, new int[] {i, i}).build();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int round = 0; round < rounds; round++) {
                InventorySnapshot snap = ApiConvert.snapshot("minecraft:generic_9x3", slots, items);
                CyclicBarrier go = new CyclicBarrier(threads);
                AtomicInteger order = new AtomicInteger(round);
                List<Future<Object[]>> fs = new ArrayList<>();
                for (int t = 0; t < threads; t++) {
                    fs.add(pool.submit(() -> {
                        int start = order.getAndIncrement();
                        go.await(10, TimeUnit.SECONDS);
                        Object[] got = new Object[slots.length];
                        for (int k = 0; k < slots.length; k++) {
                            int i = (start * 7 + k) % slots.length;
                            got[i] = snap.contents().get(i);
                        }
                        return got;
                    }));
                }
                Object[] first = fs.get(0).get(10, TimeUnit.SECONDS);
                for (var f : fs) {
                    Object[] got = f.get(10, TimeUnit.SECONDS);
                    for (int i = 0; i < got.length; i++) assertSame(first[i], got[i], "칸 " + i + " 은 모두 같은 객체를 받는다");
                }
                List<?> all = snap.contents().stream().toList();
                for (int i = 0; i < first.length; i++) {
                    assertNotNull(first[i]);
                    assertSame(first[i], all.get(i), "다 변환한 사본도 칸마다 처음 고정한 객체를 쓴다");
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
