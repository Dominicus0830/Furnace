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

    /** registries/builtin-1.21.1.txt(바닐라 reports/registries.json 의 protocol_id 순). 처음 쓸 때 한 번 읽는다. */
    private static final class BuiltIn {
        static final java.util.Map<String, List<String>> TABLE = load();

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
