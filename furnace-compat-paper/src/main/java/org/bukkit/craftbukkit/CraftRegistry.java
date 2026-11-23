package org.bukkit.craftbukkit;

import java.util.Iterator;
import java.util.stream.Stream;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

/**
 * Paper CraftRegistry — 서버 레지스트리에서 오는 Bukkit 레지스트리(관측표 registry.*: 상태 효과·현수막 무늬·장식 재료·무늬 등, T-264 P2d).
 * Furnace 는 값 목록을 따로 들고 있으므로(고정 목록·데이터 팩 장식 등) 그 몸에 그대로 맡긴다 — 몸을 하위 클래스로 두면 getClass() 가 Paper 와
 * 달라진다. Paper 생성자는 NMS 레지스트리를 받아 Furnace 생성자는 하위(WritableCraftRegistry)가 부를 수 있게 protected 이고 호환 계층은 CompatRegistries 로 만든다.
 */
public class CraftRegistry<B extends Keyed, M> implements Registry<B> {

    private final Registry<B> body;

    protected CraftRegistry(Registry<B> body) { this.body = body; }

    @Override
    public B get(NamespacedKey key) { return body.get(key); }

    @Override
    public NamespacedKey getKey(B value) { return body.getKey(value); }

    @Override
    public Stream<B> stream() { return body.stream(); }

    @Override
    public Iterator<B> iterator() { return body.iterator(); }

    @Override
    public String toString() { return body.toString(); }

    /** Paper CraftRegistry.getMinecraftRegistry() — 서버의 레지스트리 묶음(T-264, 덫 계측 11-23 에서 6개 플러그인이 리플렉션으로 부름). */
    public static net.minecraft.core.RegistryAccess getMinecraftRegistry() { return net.minecraft.core.RegistryAccess.SERVER; }

    /** Paper 와 같다: 묶음에 없는 레지스트리는 {@code registryOrThrow} 의 "Missing registry". */
    public static <E> net.minecraft.core.Registry<E> getMinecraftRegistry(net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<E>> key) {
        return getMinecraftRegistry().registryOrThrow(key);
    }
}
