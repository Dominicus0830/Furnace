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
