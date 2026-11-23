package org.domi.furnace.compat.nms;

import java.util.NoSuchElementException;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;

/**
 * T-264 P4 변환 가족 W/U(설계 3.5): 껍데기 생성기(shimgen {@code CraftConversions})가 Paper CraftBukkit 의 레지스트리 값 변환 정적 메서드
 * ({@code minecraftToBukkit}·{@code bukkitToMinecraft}·{@code minecraftHolderToBukkit}·{@code bukkitToMinecraftHolder}) 몸통으로 부른다.
 * Paper 의 CraftRegistry 길과 결과가 같다 — 같은 키의 값을 반대쪽 레지스트리에서 찾는다(Paper 는 Bukkit → NMS 를 Craft 객체의 핸들로 가지만
 * Furnace 의 Bukkit 값은 핸들이 없어 같은 키의 NMS 값을 찾는다. 등록된 값이면 같은 객체다).
 * 예외는 Paper 1.21.1 과 같다(11-23 Paper 1.21.1 서버 바이트코드 대조 — 클래스마다 길이 둘이라 생성기가 고른다):
 * <ul>
 *   <li>null 인자: 메시지 없는 {@link IllegalArgumentException}({@code Preconditions.checkArgument}) — 레지스트리를 찾기 전에 본다.</li>
 *   <li>등록 안 된 NMS 값: 클래스가 제 몸통으로 바꾸면(direct) {@link NoSuchElementException}({@code orElseThrow()}), {@code CraftRegistry} 로
 *       넘기면 {@link IllegalStateException}("Cannot convert '…' to bukkit representation, since it is not registered."). 같은 키의 Bukkit 값이
 *       없으면 메시지 없는 {@link IllegalArgumentException}.</li>
 *   <li>같은 키의 NMS 값이 없는 Bukkit 값: {@link NoSuchElementException}(Paper 는 direct 면 {@code orElseThrow()}, 아니면 핸들이라 일어나지 않는다).</li>
 *   <li>참조 홀더가 아니면 {@link IllegalArgumentException}("No Reference holder found for " + 값 + 클래스마다 다른 꼬리 — 생성기가 넘긴다).</li>
 *   <li>Furnace 가 아직 그 레지스트리를 주지 않으면(서버 레지스트리 묶음·{@code Bukkit.getRegistry} 에 없음) 껍데기와 같은 {@link UnsupportedNmsCall}
 *       (계측 대상) — 레지스트리를 채우는 영역 작업이 끝나면 저절로 산다.</li>
 * </ul>
 */
public final class CraftConvert {

    private CraftConvert() {}

    /**
     * NMS 값 → 같은 키의 Bukkit 값. registry 는 생성기가 넣은 {@code Registries} 필드(아직 없는 필드면 null), viaRegistry 는 Paper 가 이 클래스에서
     * {@code CraftRegistry.minecraftToBukkit} 로 넘기는지(등록 안 된 값의 예외 종류가 갈린다).
     */
    public static Object minecraftToBukkit(Object minecraft, ResourceKey<?> registry, Class<?> bukkitType, boolean viaRegistry, String where) {
        if (minecraft == null) throw new IllegalArgumentException();
        Registry<Object> nms = nms(registry, where);
        java.util.Optional<ResourceKey<Object>> found = nms.getResourceKey(minecraft);
        if (found.isEmpty()) {
            if (viaRegistry) throw new IllegalStateException(String.format("Cannot convert '%s' to bukkit representation, since it is not registered.", minecraft));
            throw new NoSuchElementException("No value present");
        }
        ResourceLocation loc = found.get().location();
        Keyed bukkit = bukkit(bukkitType, where).get(new NamespacedKey(loc.getNamespace(), loc.getPath()));
        if (bukkit == null) throw new IllegalArgumentException();
        return bukkit;
    }

    public static Object minecraftHolderToBukkit(Holder<?> minecraft, ResourceKey<?> registry, Class<?> bukkitType, boolean viaRegistry, String where) {
        return minecraftToBukkit(minecraft.value(), registry, bukkitType, viaRegistry, where);
    }

    /** Bukkit 값 → 같은 키의 NMS 값. */
    public static Object bukkitToMinecraft(Object bukkit, ResourceKey<?> registry, String where) {
        if (bukkit == null) throw new IllegalArgumentException();
        return bukkitToMinecraft(bukkit, nms(registry, where), where);
    }

    /** Bukkit 값 → NMS 레지스트리의 참조 홀더(Paper 처럼 참조 홀더가 아니면 던진다). holderTail 은 그 클래스의 Paper 문구 꼬리. */
    public static Holder<?> bukkitToMinecraftHolder(Object bukkit, ResourceKey<?> registry, String holderTail, String where) {
        if (bukkit == null) throw new IllegalArgumentException();
        Registry<Object> nms = nms(registry, where);
        if (nms.wrapAsHolder(bukkitToMinecraft(bukkit, nms, where)) instanceof Holder.Reference<?> holder) return holder;
        throw new IllegalArgumentException("No Reference holder found for " + bukkit + holderTail);
    }

    private static Object bukkitToMinecraft(Object bukkit, Registry<Object> nms, String where) {
        if (!(bukkit instanceof Keyed keyed)) throw NmsShell.unsupported(where);
        NamespacedKey k = keyed.getKey();
        return nms.getOptional(ResourceLocation.fromNamespaceAndPath(k.getNamespace(), k.getKey())).orElseThrow();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Registry<Object> nms(ResourceKey<?> registry, String where) {
        if (registry == null) throw NmsShell.unsupported(where);
        java.util.Optional<Registry<Object>> r = RegistryAccess.SERVER.registry((ResourceKey) registry);
        if (r.isEmpty()) throw NmsShell.unsupported(where);
        return r.get();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static org.bukkit.Registry<? extends Keyed> bukkit(Class<?> type, String where) {
        org.bukkit.Registry<? extends Keyed> r = Keyed.class.isAssignableFrom(type) ? Bukkit.getRegistry((Class) type) : null;
        if (r == null) throw NmsShell.unsupported(where);
        return r;
    }
}
