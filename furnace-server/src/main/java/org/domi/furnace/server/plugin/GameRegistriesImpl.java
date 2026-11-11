// Furnace.gameRegistries(): 시작 때 고정된 동기화 레지스트리 보기 + 마법부여 끄기(EnchantmentToggles, 플러그인 이름 몫).
package org.domi.furnace.server.plugin;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.registry.GameRegistries;
import org.domi.furnace.server.data.DynamicRegistries;
import org.domi.furnace.server.loot.EnchantmentToggles;
import org.domi.furnace.server.net.registry.RegistrySet;

final class GameRegistriesImpl implements GameRegistries {

    @Override
    public List<String> entries(String registry) { return List.copyOf(RegistrySet.current().entries(normalize(registry))); }

    @Override
    public List<String> builtIn(String registry) { return BuiltIn.TABLE.getOrDefault(normalize(registry), List.of()); }

    @Override
    public Set<String> tags(String registry) {
        var byTag = TagView.current().names.get(normalize(registry));
        return byTag == null ? Set.of() : byTag.keySet();
    }

    @Override
    public Optional<List<String>> tag(String registry, String tag) {
        var byTag = TagView.current().names.get(normalize(registry));
        return Optional.ofNullable(byTag == null ? null : byTag.get(normalize(tag)));
    }

    @Override
    public boolean isTagged(String registry, String tag, String entry) {
        String reg = normalize(registry);
        var byTag = TagView.current().members.get(reg);
        if (byTag == null) return false;
        var members = byTag.get(normalize(tag));
        if (members == null) return false;
        int id = idOf(reg, normalize(entry));
        return id >= 0 && members.get(id);
    }

    /**
     * 태그 표 하나에서 한 번 만든 보기: 레지스트리 → 태그 → 항목 이름 목록(같은 표면 같은 목록 객체 — 호환 shim 이 이것으로 다시 만들지 판단)과 번호 집합.
     * 데이터 팩 리로드로 표가 바뀌면 다음 물음에서 다시 만든다.
     */
    private static final class TagView {
        private static volatile TagView last;

        final org.domi.furnace.server.net.registry.TagSet set;
        final java.util.Map<String, java.util.Map<String, List<String>>> names = new java.util.HashMap<>();
        final java.util.Map<String, java.util.Map<String, java.util.BitSet>> members = new java.util.HashMap<>();

        private TagView(org.domi.furnace.server.net.registry.TagSet set) {
            this.set = set;
            for (String reg : set.registries()) {
                List<String> entryNames = names(reg);
                var n = new java.util.LinkedHashMap<String, List<String>>();
                var m = new java.util.HashMap<String, java.util.BitSet>();
                for (var e : set.tagsOf(reg).entrySet()) {
                    List<String> out = new java.util.ArrayList<>(e.getValue().length);
                    java.util.BitSet bits = new java.util.BitSet();
                    for (int id : e.getValue()) {
                        if (id >= 0 && id < entryNames.size()) out.add(entryNames.get(id));
                        if (id >= 0) bits.set(id);
                    }
                    n.put(e.getKey(), List.copyOf(out));
                    m.put(e.getKey(), bits);
                }
                names.put(reg, java.util.Collections.unmodifiableMap(n));
                members.put(reg, m);
            }
        }

        static TagView current() {
            var t = currentTags();
            TagView v = last;
            if (v == null || v.set != t) last = v = new TagView(t);
            return v;
        }
    }

    /** 데이터 팩 관리자가 건 태그, 없으면(시험 서버처럼 데이터 팩을 안 거는 경우) 바닐라 1.21.1 태그 — 다른 서버 부품(ItemTags·EntityTypeTags)과 같은 바탕. */
    private static org.domi.furnace.server.net.registry.TagSet currentTags() {
        var t = org.domi.furnace.server.item.ItemContext.tagSet();
        return t != null ? t : VanillaTags.SET;
    }

    private static final class VanillaTags {
        static final org.domi.furnace.server.net.registry.TagSet SET = org.domi.furnace.server.net.registry.TagSet.load("registries/tags-1.21.1.txt");
    }

    /** 태그 숫자 id 의 이름표: 내장 레지스트리면 내장 목록, 아니면 동기화 레지스트리(태그 번호는 TagRegistries 와 같은 순서). */
    private static List<String> names(String reg) {
        List<String> b = BuiltIn.TABLE.get(reg);
        return b != null ? b : RegistrySet.current().entries(reg);
    }

    private static int idOf(String reg, String entry) {
        var idx = BuiltIn.INDEX.get(reg);
        if (idx != null) return idx.getOrDefault(entry, -1);
        return RegistrySet.current().entries(reg).indexOf(entry);
    }

    /** registries/builtin-1.21.1.txt(바닐라 reports/registries.json 의 protocol_id 순). 처음 쓸 때 한 번 읽는다. */
    private static final class BuiltIn {
        static final java.util.Map<String, List<String>> TABLE = load();
        static final java.util.Map<String, java.util.Map<String, Integer>> INDEX = index();

        private static java.util.Map<String, java.util.Map<String, Integer>> index() {
            var out = new java.util.HashMap<String, java.util.Map<String, Integer>>();
            for (var e : TABLE.entrySet()) {
                var m = new java.util.HashMap<String, Integer>();
                for (int i = 0; i < e.getValue().size(); i++) m.put(e.getValue().get(i), i);
                out.put(e.getKey(), java.util.Map.copyOf(m));
            }
            return java.util.Map.copyOf(out);
        }

        private static java.util.Map<String, List<String>> load() {
            var out = new java.util.LinkedHashMap<String, java.util.List<String>>();
            try (var in = GameRegistriesImpl.class.getClassLoader().getResourceAsStream("registries/builtin-1.21.1.txt")) {
                if (in == null) throw new IllegalStateException("registries/builtin-1.21.1.txt 없음");
                var r = new java.io.BufferedReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    int sp = line.indexOf(' ');
                    out.computeIfAbsent(line.substring(0, sp), k -> new java.util.ArrayList<>()).add(line.substring(sp + 1));
                }
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            out.replaceAll((k, v) -> List.copyOf(v));
            return java.util.Map.copyOf(out);
        }
    }

    @Override
    public Optional<String> source(String registry, String key) {
        DynamicRegistries d = DynamicRegistries.current();
        return Optional.ofNullable(d == null ? null : d.source(normalize(registry), normalize(key)));
    }

    @Override
    public void disableEnchantment(FurnacePlugin owner, String key) {
        java.util.Objects.requireNonNull(owner, "owner");
        String k = normalize(key);
        if (!contains(ENCHANTMENT, k)) throw new IllegalArgumentException("모르는 마법부여: " + key);
        EnchantmentToggles.disable(owner.name(), k);
    }

    @Override
    public boolean enableEnchantment(FurnacePlugin owner, String key) {
        java.util.Objects.requireNonNull(owner, "owner");
        return EnchantmentToggles.enable(owner.name(), normalize(key));
    }

    @Override
    public boolean isEnchantmentDisabled(String key) { return EnchantmentToggles.isDisabled(normalize(key)); }

    @Override
    public Set<String> disabledEnchantments() { return EnchantmentToggles.disabled(); }

    private static String normalize(String id) { return id.indexOf(':') < 0 ? "minecraft:" + id : id; }
}
