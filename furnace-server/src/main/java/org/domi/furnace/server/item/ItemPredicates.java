// 바닐라 1.21.1 아이템 술어 공용 코덱: ItemPredicate(items·count·components·predicates)와 하위 술어 14종(ItemSubPredicates).
// 명령 인자(item_predicate 의 "종류~값")와 전리품·발전 과제(loot/ItemPredicate)가 같은 코드를 쓴다.
// 입력은 JSON 꼴(Map·List·수·문자열·불리언)이든 NBT 꼴(NbtCompound·NbtList·타입 있는 수)이든 받는다(JsonOps·NbtOps).
// 판정은 바이트코드 대조: cs(ItemPredicate), cu(레지스트리), cl·cn·cr·ck/dk·cj·ci·co·cp·cx·cy·ch·cv·cq(하위 술어),
// bd·bb·bc(CollectionPredicate·Contents·Counts), bq(EnchantmentPredicate), dh(MinMaxBounds — 1.21.1 코덱은 min>max 를 거절하지 않는다).
package org.domi.furnace.server.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtTags;

public final class ItemPredicates {
    private ItemPredicates() {}

    /** 바닐라 item_sub_predicate_type 레지스트리(1.21.1, 등록 순서). */
    public static final List<String> SUB_PREDICATE_TYPES = List.of("minecraft:damage", "minecraft:enchantments",
            "minecraft:stored_enchantments", "minecraft:potion_contents", "minecraft:custom_data", "minecraft:container",
            "minecraft:bundle_contents", "minecraft:firework_explosion", "minecraft:fireworks", "minecraft:writable_book_content",
            "minecraft:written_book_content", "minecraft:attribute_modifiers", "minecraft:trim", "minecraft:jukebox_playable");

    /** 아이템 술어 하나(하위 술어·전체 술어 공통). */
    public interface Test extends Predicate<ItemView> {}

    // ------------------------------------------------------------------ 전체 술어(ItemPredicate.CODEC)

    /** {items?, count?, components?, predicates?}. 모르는 컴포넌트·하위 술어 종류는 오류(바닐라 dispatchedMap). */
    public static Test parse(Object in, RegistryView regs) {
        Map<String, Object> m = Val.map(in);
        Holders items = m.get("items") != null ? Holders.parse("minecraft:item", m.get("items"), regs) : null;
        IntBounds count = m.get("count") != null ? IntBounds.parse(m.get("count")) : IntBounds.ANY;
        List<Test> comps = m.get("components") != null ? components(m.get("components")) : List.of();
        List<Test> subs = new ArrayList<>();
        if (m.get("predicates") != null) {
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (var e : Val.map(m.get("predicates")).entrySet()) {
                // DispatchedMapCodec: "damage" 와 "minecraft:damage" 를 같이 쓰면 같은 키 두 번
                if (!seen.add(resourceLocation(e.getKey()))) throw duplicate(e.getKey());
                subs.add(sub(e.getKey(), e.getValue(), regs));
            }
        }
        List<Test> fixedSubs = List.copyOf(subs);
        return s -> {
            if (items != null && !items.contains(s.item(), regs)) return false;
            if (!count.matches(s.count())) return false;
            for (Test t : comps) if (!t.test(s)) return false;
            for (Test t : fixedSubs) if (!t.test(s)) return false;
            return true;
        };
    }

    /** DataComponentPredicate: 컴포넌트마다 값이 같아야 한다(Objects.equals — 없는 컴포넌트는 null). */
    private static List<Test> components(Object in) {
        List<Test> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (var e : Val.map(in).entrySet()) {
            String id = resourceLocation(e.getKey());
            if (!seen.add(id)) throw duplicate(e.getKey());
            ComponentType t = DataComponents.byName(id);
            if (t == null) throw new CodecException("Unknown registry key in ResourceKey[minecraft:root / minecraft:data_component_type]: " + id);
            if (t.isTransient()) throw new CodecException("Encountered transient component " + id);
            Object raw = e.getValue();
            Object expected;
            try {
                expected = t.normalize(raw);
            } catch (CodecException ex) {
                // 우리 코덱이 못 읽는 값은 예전처럼 구조 비교로(바닐라라면 로드 오류 — 우리 코덱 결함을 데이터 팩 탓으로 돌리지 않는다)
                out.add(s -> {
                    Object actual = s.get(id);
                    return actual != null && org.domi.furnace.server.loot.Components.same(actual, raw);
                });
                continue;
            }
            out.add(s -> {
                Object actual = s.get(id);
                return actual != null && t.same(expected, actual);
            });
        }
        return List.copyOf(out);
    }

    // ------------------------------------------------------------------ 하위 술어(ItemSubPredicates)

    private static CodecException duplicate(String key) { return new CodecException("Duplicate entry for key: '" + key + "'"); }

    /** 하위 술어 하나. 종류 이름은 네임스페이스 없이 와도 된다. 모르는 종류·잘못된 값은 CodecException. */
    public static Test sub(String type, Object value, RegistryView regs) {
        String id = resourceLocation(type);
        if (value == null) throw new CodecException("Missing value for " + id);
        return switch (id) {
            case "minecraft:damage" -> damage(value);
            case "minecraft:enchantments" -> enchantments("minecraft:enchantments", value, regs);
            case "minecraft:stored_enchantments" -> enchantments("minecraft:stored_enchantments", value, regs);
            case "minecraft:potion_contents" -> potionContents(value, regs);
            case "minecraft:custom_data" -> customData(value);
            case "minecraft:container" -> container(value, regs);
            case "minecraft:bundle_contents" -> bundleContents(value, regs);
            case "minecraft:firework_explosion" -> fireworkExplosion(value);
            case "minecraft:fireworks" -> fireworks(value);
            case "minecraft:writable_book_content" -> writableBook(value);
            case "minecraft:written_book_content" -> writtenBook(value);
            case "minecraft:attribute_modifiers" -> attributeModifiers(value, regs);
            case "minecraft:trim" -> trim(value, regs);
            case "minecraft:jukebox_playable" -> jukeboxPlayable(value, regs);
            default -> throw new CodecException("Unknown registry key in ResourceKey[minecraft:root / minecraft:item_sub_predicate_type]: " + id);
        };
    }

    /** SingleComponentItemPredicate(dw): 컴포넌트가 있어야 하고, 있으면 값으로 판정. */
    private static Test single(String component, Predicate<Object> test) {
        return s -> {
            Object v = s.get(component);
            return v != null && test.test(v);
        };
    }

    /** damage(cl): durability 는 getMaxDamage(없으면 0) - damage. isDamageableItem 은 보지 않는다. */
    private static Test damage(Object value) {
        Map<String, Object> m = Val.map(value);
        IntBounds durability = m.get("durability") != null ? IntBounds.parse(m.get("durability")) : IntBounds.ANY;
        IntBounds damage = m.get("damage") != null ? IntBounds.parse(m.get("damage")) : IntBounds.ANY;
        return s -> {
            if (!(s.get("minecraft:damage") instanceof Number d)) return false;
            int max = s.get("minecraft:max_damage") instanceof Number n ? n.intValue() : 0;
            return durability.matches(max - d.intValue()) && damage.matches(d.intValue());
        };
    }

    /** EnchantmentPredicate(bq): enchantments?(홀더 집합), levels?(기본 ANY — ANY 는 참조 비교). */
    private record EnchantmentTest(Holders enchantments, IntBounds levels) {
        boolean containedIn(Map<String, Object> levelsOnItem, RegistryView regs) {
            if (enchantments != null) {
                // 집합의 인챈트 중 하나라도: 아이템 레벨이 0 이 아니고 levels 를 만족
                for (var e : levelsOnItem.entrySet()) {
                    int lvl = Val.number(e.getValue()).intValue();
                    if (lvl == 0 || !enchantments.contains(e.getKey(), regs)) continue;
                    if (levels == IntBounds.ANY || levels.matches(lvl)) return true;
                }
                return false;
            }
            if (levels != IntBounds.ANY) {
                for (Object lvl : levelsOnItem.values()) if (levels.matches(Val.number(lvl).intValue())) return true;
                return false;
            }
            return !levelsOnItem.isEmpty();
        }
    }

    /** enchantments·stored_enchantments(cn): EnchantmentPredicate 리스트가 모두 만족. */
    private static Test enchantments(String component, Object value, RegistryView regs) {
        List<EnchantmentTest> list = new ArrayList<>();
        for (Object x : Val.list(value)) {
            Map<String, Object> m = Val.map(x);
            Holders set = m.get("enchantments") != null ? Holders.parse("minecraft:enchantment", m.get("enchantments"), regs) : null;
            IntBounds levels = m.get("levels") != null ? IntBounds.parse(m.get("levels")) : IntBounds.ANY;
            list.add(new EnchantmentTest(set, levels));
        }
        List<EnchantmentTest> fixed = List.copyOf(list);
        return single(component, v -> {
            Map<String, Object> levels = Val.map(PCodec.get(Val.map(v), "levels", Map.of()));
            for (EnchantmentTest t : fixed) if (!t.containedIn(levels, regs)) return false;
            return true;
        });
    }

    /** potion_contents(cr): 물약 홀더 집합. 물약이 없으면 거짓. */
    private static Test potionContents(Object value, RegistryView regs) {
        Holders potions = Holders.parse("minecraft:potion", value, regs);
        return single("minecraft:potion_contents", v -> Val.map(v).get("potion") instanceof String p && potions.contains(p, regs));
    }

    /** custom_data(ck → dk): getOrDefault(CUSTOM_DATA, 빈 태그)에 NbtUtils.compareNbt(술어, 값, 부분 리스트). 컴포넌트가 없어도 본다. */
    private static Test customData(Object value) {
        NbtCompound expected = (NbtCompound) PCodec.COMPOUND.normalize(value);   // TagParser.LENIENT_CODEC: SNBT 문자열 또는 compound
        return s -> {
            Object actual = s.get("minecraft:custom_data");
            return NbtTags.compareNbt(expected, actual instanceof NbtCompound c ? c : new NbtCompound(), true);
        };
    }

    /** container(cj): items?(아이템 술어 컬렉션)을 빈 칸 뺀 아이템들(칸 순서)에. */
    private static Test container(Object value, RegistryView regs) {
        Map<String, Object> m = Val.map(value);
        Predicate<List<ItemView>> items = m.get("items") != null ? collection(m.get("items"), x -> parse(x, regs)) : null;
        return single("minecraft:container", v -> {
            if (items == null) return true;
            List<ItemView> views = new ArrayList<>();
            for (Object slot : Val.list(v)) {
                ItemView iv = ItemView.ofCanonical(Val.map(slot).get("item"));
                if (iv.count() > 0) views.add(iv);
            }
            return items.test(views);
        });
    }

    /** bundle_contents(ci): items?(아이템 술어 컬렉션)을 묶음 속 아이템들에. */
    private static Test bundleContents(Object value, RegistryView regs) {
        Map<String, Object> m = Val.map(value);
        Predicate<List<ItemView>> items = m.get("items") != null ? collection(m.get("items"), x -> parse(x, regs)) : null;
        return single("minecraft:bundle_contents", v -> {
            if (items == null) return true;
            List<ItemView> views = new ArrayList<>();
            for (Object stack : Val.list(v)) views.add(ItemView.ofCanonical(stack));
            return items.test(views);
        });
    }

    private static final List<String> FIREWORK_SHAPES = List.of("small_ball", "large_ball", "star", "creeper", "burst");

    /** FireworkExplosionPredicate(co$a): shape?·has_twinkle?·has_trail?. */
    private static Predicate<Object> explosionTest(Object value) {
        Map<String, Object> m = Val.map(value);
        String shape = m.get("shape") != null ? (String) PCodec.enumByName(FIREWORK_SHAPES).normalize(m.get("shape")) : null;
        Boolean twinkle = m.get("has_twinkle") != null ? Val.bool(m.get("has_twinkle")) : null;
        Boolean trail = m.get("has_trail") != null ? Val.bool(m.get("has_trail")) : null;
        return v -> {
            Map<String, Object> e = Val.map(v);
            if (shape != null && !shape.equals(e.get("shape"))) return false;
            if (twinkle != null && twinkle != Val.bool(PCodec.get(e, "has_twinkle", false))) return false;
            return trail == null || trail == Val.bool(PCodec.get(e, "has_trail", false));
        };
    }

    /** firework_explosion(co). */
    private static Test fireworkExplosion(Object value) {
        Predicate<Object> t = explosionTest(value);
        return single("minecraft:firework_explosion", t);
    }

    /** fireworks(cp): explosions?(폭발 술어 컬렉션), flight_duration(기본 ANY). */
    private static Test fireworks(Object value) {
        Map<String, Object> m = Val.map(value);
        Predicate<List<Object>> explosions = m.get("explosions") != null ? collection(m.get("explosions"), ItemPredicates::explosionTest) : null;
        IntBounds flight = m.get("flight_duration") != null ? IntBounds.parse(m.get("flight_duration")) : IntBounds.ANY;
        return single("minecraft:fireworks", v -> {
            Map<String, Object> f = Val.map(v);
            if (explosions != null && !explosions.test(Val.list(PCodec.get(f, "explosions", List.of())))) return false;
            return flight.matches(Val.number(PCodec.get(f, "flight_duration", 0)).intValue());
        });
    }

    /** writable_book_content(cx): pages?(쪽 술어 컬렉션 — 쪽의 raw 문자열이 같은가). */
    private static Test writableBook(Object value) {
        Map<String, Object> m = Val.map(value);
        Predicate<List<Object>> pages = m.get("pages") != null ? collection(m.get("pages"), x -> {
            String contents = Val.string(x);
            return page -> contents.equals(Val.map(page).get("raw"));
        }) : null;
        return single("minecraft:writable_book_content", v -> pages == null || pages.test(Val.list(PCodec.get(Val.map(v), "pages", List.of()))));
    }

    /** written_book_content(cy): author?·title?(raw)·generation(기본 ANY)·resolved?·pages?(쪽 raw 텍스트가 같은가). */
    private static Test writtenBook(Object value) {
        Map<String, Object> m = Val.map(value);
        Predicate<List<Object>> pages = m.get("pages") != null ? collection(m.get("pages"), x -> {
            String contents = textKey(x);   // ComponentSerialization.CODEC — 저장된 쪽(정규 평면 문자열)과 같은 꼴로
            return page -> contents.equals(Val.map(page).get("raw"));
        }) : null;
        String author = m.get("author") != null ? Val.string(m.get("author")) : null;
        String title = m.get("title") != null ? Val.string(m.get("title")) : null;
        IntBounds generation = m.get("generation") != null ? IntBounds.parse(m.get("generation")) : IntBounds.ANY;
        Boolean resolved = m.get("resolved") != null ? Val.bool(m.get("resolved")) : null;
        return single("minecraft:written_book_content", v -> {
            Map<String, Object> b = Val.map(v);
            if (author != null && !author.equals(b.get("author"))) return false;
            if (title != null && !title.equals(Val.map(b.get("title")).get("raw"))) return false;
            if (!generation.matches(Val.number(PCodec.get(b, "generation", 0)).intValue())) return false;
            if (resolved != null && resolved != Val.bool(PCodec.get(b, "resolved", false))) return false;
            return pages == null || pages.test(Val.list(PCodec.get(b, "pages", List.of())));
        });
    }

    /** 텍스트 컴포넌트(트리 꼴) → 쓴 책 쪽의 정규 문자열(FlatText). 같은 텍스트면 같은 문자열 — Component.equals 대응. */
    private static String textKey(Object tree) {
        return FlatText.canonical(Codecs.toJsonString(styleBooleans(tree)));
    }

    private static final java.util.Set<String> STYLE_BOOLEANS = java.util.Set.of("bold", "italic", "underlined", "strikethrough", "obfuscated", "interpret");

    /**
     * NBT 꼴 텍스트(명령 SNBT)의 스타일 불리언은 바이트(bold:true = 1b)다. 바닐라 NbtOps 의 Codec.BOOL 은 수를 != 0 으로 읽으므로
     * JSON 으로 옮기기 전에 그 키들의 수 값을 불리언으로 바꾼다(그대로 두면 JSON 수가 되어 스타일이 버려진다).
     */
    private static Object styleBooleans(Object v) {
        if (Val.isMap(v)) {
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            for (var e : Val.map(v).entrySet()) {
                Object x = e.getValue();
                out.put(e.getKey(), STYLE_BOOLEANS.contains(e.getKey()) && x instanceof Number n ? (Object) (n.doubleValue() != 0) : styleBooleans(x));
            }
            return out;
        }
        if (Val.isList(v) && !(v instanceof int[] || v instanceof byte[] || v instanceof long[])) {
            List<Object> out = new ArrayList<>();
            for (Object x : Val.list(v)) out.add(styleBooleans(x));
            return out;
        }
        return v;
    }

    private static final List<String> OPERATIONS = List.of("add_value", "add_multiplied_base", "add_multiplied_total");
    private static final List<String> SLOT_GROUPS = List.of("any", "mainhand", "offhand", "hand", "feet", "legs", "chest", "head", "armor", "body");

    /** ItemAttributeModifiersPredicate.EntryPredicate(ch$a): attribute?·id?·amount(기본 ANY)·operation?·slot?. */
    private static Predicate<Object> modifierTest(Object value, RegistryView regs) {
        Map<String, Object> m = Val.map(value);
        Holders attribute = m.get("attribute") != null ? Holders.parse("minecraft:attribute", m.get("attribute"), regs) : null;
        String id = m.get("id") != null ? (String) PCodec.RESOURCE_LOCATION.normalize(m.get("id")) : null;
        DoubleBounds amount = m.get("amount") != null ? DoubleBounds.parse(m.get("amount")) : DoubleBounds.ANY;
        String operation = m.get("operation") != null ? (String) PCodec.enumByName(OPERATIONS).normalize(m.get("operation")) : null;
        String slot = m.get("slot") != null ? (String) PCodec.enumByName(SLOT_GROUPS).normalize(m.get("slot")) : null;
        return v -> {
            Map<String, Object> e = Val.map(v);
            if (attribute != null && !attribute.contains((String) e.get("type"), regs)) return false;
            if (id != null && !id.equals(e.get("id"))) return false;
            if (!amount.matches(Val.number(e.get("amount")).doubleValue())) return false;
            if (operation != null && !operation.equals(e.get("operation"))) return false;
            return slot == null || slot.equals(PCodec.get(e, "slot", "any"));
        };
    }

    /** attribute_modifiers(ch): modifiers?(변경치 술어 컬렉션). */
    private static Test attributeModifiers(Object value, RegistryView regs) {
        Map<String, Object> m = Val.map(value);
        Predicate<List<Object>> modifiers = m.get("modifiers") != null ? collection(m.get("modifiers"), x -> modifierTest(x, regs)) : null;
        return single("minecraft:attribute_modifiers",
                v -> modifiers == null || modifiers.test(Val.list(PCodec.get(Val.map(v), "modifiers", List.of()))));
    }

    /** trim(cv): material?·pattern?(홀더 집합). 직접 정의(인라인) 홀더는 어떤 집합에도 들지 않는다. */
    private static Test trim(Object value, RegistryView regs) {
        Map<String, Object> m = Val.map(value);
        Holders material = m.get("material") != null ? Holders.parse("minecraft:trim_material", m.get("material"), regs) : null;
        Holders pattern = m.get("pattern") != null ? Holders.parse("minecraft:trim_pattern", m.get("pattern"), regs) : null;
        return single("minecraft:trim", v -> {
            Map<String, Object> t = Val.map(v);
            if (material != null && !(t.get("material") instanceof String id && material.contains(id, regs))) return false;
            return pattern == null || t.get("pattern") instanceof String id && pattern.contains(id, regs);
        });
    }

    /** jukebox_playable(cq): song?(홀더 집합) — 집합의 키 중 음반 키와 같은 것이 있는가. */
    private static Test jukeboxPlayable(Object value, RegistryView regs) {
        Map<String, Object> m = Val.map(value);
        Holders song = m.get("song") != null ? Holders.parse("minecraft:jukebox_song", m.get("song"), regs) : null;
        return single("minecraft:jukebox_playable", v -> song == null || Val.map(v).get("song") instanceof String id && song.contains(id, regs));
    }

    // ------------------------------------------------------------------ CollectionPredicate(bd)

    /**
     * {contains?, count?, size?}. contains: 술어 0개=참, 1개=하나라도, 여럿=원소마다 맞는 술어를 지워 다 지워지면 참(bb).
     * count: [{test, count}] 마다 맞는 원소 수가 범위 안(bc). size: 원소 수.
     */
    private static <T> Predicate<List<T>> collection(Object value, Function<Object, Predicate<? super T>> element) {
        Map<String, Object> m = Val.map(value);
        Predicate<List<T>> contains = null;
        if (m.get("contains") != null) {
            List<Predicate<? super T>> tests = new ArrayList<>();
            for (Object x : Val.list(m.get("contains"))) tests.add(element.apply(x));
            List<Predicate<? super T>> fixed = List.copyOf(tests);
            if (fixed.isEmpty()) {
                contains = l -> true;
            } else if (fixed.size() == 1) {
                Predicate<? super T> only = fixed.get(0);
                contains = l -> {
                    for (T t : l) if (only.test(t)) return true;
                    return false;
                };
            } else {
                contains = l -> {
                    List<Predicate<? super T>> left = new ArrayList<>(fixed);
                    for (T t : l) {
                        left.removeIf(p -> p.test(t));
                        if (left.isEmpty()) return true;
                    }
                    return false;
                };
            }
        }
        List<Predicate<List<T>>> counts = new ArrayList<>();
        if (m.get("count") != null) {
            for (Object x : Val.list(m.get("count"))) {
                Map<String, Object> e = Val.map(x);
                if (e.get("test") == null) throw new CodecException("No key test in MapLike[" + Val.describe(e) + "]");
                if (e.get("count") == null) throw new CodecException("No key count in MapLike[" + Val.describe(e) + "]");
                Predicate<? super T> test = element.apply(e.get("test"));
                IntBounds n = IntBounds.parse(e.get("count"));
                counts.add(l -> {
                    int k = 0;
                    for (T t : l) if (test.test(t)) k++;
                    return n.matches(k);
                });
            }
        }
        IntBounds size = m.get("size") != null ? IntBounds.parse(m.get("size")) : null;
        Predicate<List<T>> fContains = contains;
        List<Predicate<List<T>>> fCounts = List.copyOf(counts);
        return l -> {
            if (fContains != null && !fContains.test(l)) return false;
            for (Predicate<List<T>> c : fCounts) if (!c.test(l)) return false;
            return size == null || size.matches(l.size());
        };
    }

    // ------------------------------------------------------------------ 조각

    /** ResourceLocation.CODEC: 네임스페이스 없으면 minecraft. */
    private static String resourceLocation(String s) {
        ResourceId id = ResourceId.tryParse(s);
        if (id == null) throw new CodecException("Not a valid resource location: " + s);
        return id.toString();
    }

    /** HolderSet(RegistryCodecs.homogeneousList): "#태그" | "id" | ["id", …]. */
    public record Holders(String registry, String tag, List<String> direct) {

        public static Holders parse(String registry, Object in, RegistryView regs) {
            if (in instanceof String s) {
                if (s.startsWith("#")) {
                    String tag = resourceLocation(s.substring(1));
                    if (!regs.hasTag(registry, tag)) throw new CodecException("Missing tag: '" + tag + "' in '" + registry + "'");
                    return new Holders(registry, tag, null);
                }
                return new Holders(registry, null, List.of(known(registry, s, regs)));
            }
            List<String> ids = new ArrayList<>();
            for (Object x : Val.list(in)) ids.add(known(registry, Val.string(x), regs));
            return new Holders(registry, null, List.copyOf(ids));
        }

        private static String known(String registry, String s, RegistryView regs) {
            String id = resourceLocation(s);
            if (!regs.exists(registry, id)) throw new CodecException("Unknown registry key in ResourceKey[minecraft:root / " + registry + "]: " + id);
            return id;
        }

        public boolean contains(String id, RegistryView regs) {
            if (id == null) return false;
            return tag != null ? regs.inTag(registry, tag, id) : direct.contains(id);
        }
    }

    /** MinMaxBounds.Ints: 수 하나 또는 {min?, max?}. ANY 는 필드가 없을 때만(참조 비교하는 곳이 있다). */
    public record IntBounds(Integer min, Integer max) {
        public static final IntBounds ANY = new IntBounds(null, null);

        public static IntBounds parse(Object in) {
            if (in instanceof Number || in instanceof Boolean) {
                int v = Val.number(in).intValue();
                return new IntBounds(v, v);
            }
            if (!Val.isMap(in)) throw new CodecException("Not a number or range: " + Val.describe(in));
            Map<String, Object> m = Val.map(in);
            Integer min = m.get("min") != null ? Val.number(m.get("min")).intValue() : null;
            Integer max = m.get("max") != null ? Val.number(m.get("max")).intValue() : null;
            return new IntBounds(min, max);
        }

        public boolean matches(int v) { return (min == null || min <= v) && (max == null || v <= max); }
    }

    /** MinMaxBounds.Doubles. */
    public record DoubleBounds(Double min, Double max) {
        public static final DoubleBounds ANY = new DoubleBounds(null, null);

        public static DoubleBounds parse(Object in) {
            if (in instanceof Number || in instanceof Boolean) {
                double v = Val.number(in).doubleValue();
                return new DoubleBounds(v, v);
            }
            if (!Val.isMap(in)) throw new CodecException("Not a number or range: " + Val.describe(in));
            Map<String, Object> m = Val.map(in);
            Double min = m.get("min") != null ? Val.number(m.get("min")).doubleValue() : null;
            Double max = m.get("max") != null ? Val.number(m.get("max")).doubleValue() : null;
            return new DoubleBounds(min, max);
        }

        public boolean matches(double v) { return (min == null || min <= v) && (max == null || v <= max); }
    }
}
