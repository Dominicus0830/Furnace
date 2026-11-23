package org.domi.furnace.compat.nms;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import org.domi.furnace.api.entity.LivingEntity;
import org.domi.furnace.api.entity.LivingEntityState;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.compat.paper.EntityStates;

/**
 * 엔티티의 상태 효과 NMS 보기({@code LivingEntity#getEffect·addEffect·removeEffect·getActiveEffects}). Bukkit 쪽({@code CraftLivingEntity})과 같은 서버 길을 쓴다.
 * <ul>
 *   <li>몹: 소유 스레드면 곧바로 읽고 쓴다(결과도 바닐라 값). 읽기는 소유 스레드가 아니면 ThreadOwnershipException, 쓰기는 소유 스레드로 예약하고
 *       바닐라가 알려 주는 "바뀌었는가" 대신 true 를 돌려준다(Bukkit {@code addPotionEffect} 와 같다).</li>
 *   <li>플레이어: 읽기는 어느 스레드나(잠금 안의 사본), 쓰기는 소유 스레드에서 — 지금 그 스레드면 바닐라 값, 아니면 예약하고 true/false 추정
 *       (지운 것은 지금 걸려 있는가, 비운 것은 하나라도 있는가).</li>
 * </ul>
 * 읽은 {@link MobEffectInstance} 는 사본이다.
 */
public final class NmsMobEffectOps {

    private NmsMobEffectOps() {}

    static org.domi.furnace.api.entity.MobEffect furnace(MobEffectInstance i) {
        return new org.domi.furnace.api.entity.MobEffect(key(i.getEffect()), i.getDuration(), i.getAmplifier(), i.isAmbient(), i.isVisible(), i.showIcon());
    }

    static MobEffectInstance nms(org.domi.furnace.api.entity.MobEffect m) {
        Holder<MobEffect> h = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(m.type()))
                .orElseThrow(() -> new IllegalArgumentException("unknown effect: " + m.type()));
        return new MobEffectInstance(h, m.duration(), m.amplifier(), m.ambient(), m.particles(), m.icon());
    }

    private static String key(Holder<MobEffect> h) {
        var k = h.unwrapKey();
        if (k.isEmpty()) throw new IllegalArgumentException("effect is not registered: " + h);
        return k.get().location().toString();
    }

    public static List<MobEffectInstance> list(LivingEntity e) {
        List<org.domi.furnace.api.entity.MobEffect> src = e instanceof Player p ? p.effects()
                : EntityStates.read(e, LivingEntityState.class, LivingEntityState::effects, List.of(), "LivingEntity#getActiveEffects");
        List<MobEffectInstance> out = new ArrayList<>(src.size());
        for (var m : src) out.add(nms(m));
        return out;
    }

    public static Map<Holder<MobEffect>, MobEffectInstance> map(LivingEntity e) {
        Map<Holder<MobEffect>, MobEffectInstance> out = new LinkedHashMap<>();
        for (var i : list(e)) out.put(i.getEffect(), i);
        return out;
    }

    public static MobEffectInstance get(LivingEntity e, Holder<MobEffect> effect) {
        String k = key(effect);
        java.util.Optional<org.domi.furnace.api.entity.MobEffect> m = e instanceof Player p
                ? p.effects().stream().filter(x -> x.type().equals(k)).findFirst()
                : EntityStates.read(e, LivingEntityState.class, s -> s.effect(k), java.util.Optional.empty(), "LivingEntity#getEffect");
        return m.map(NmsMobEffectOps::nms).orElse(null);
    }

    public static boolean add(LivingEntity e, MobEffectInstance effect) {
        var m = furnace(effect);
        if (e instanceof Player p) return await(p.addEffect(m), true);
        if (EntityStates.live(e)) return EntityStates.read(e, LivingEntityState.class, s -> s.addEffect(m), false, "LivingEntity#addEffect");
        EntityStates.write(e, LivingEntityState.class, s -> s.addEffect(m), "LivingEntity#addEffect");
        return true;
    }

    public static boolean remove(LivingEntity e, Holder<MobEffect> effect) {
        String k = key(effect);
        if (e instanceof Player p) {
            boolean had = p.effects().stream().anyMatch(x -> x.type().equals(k));
            return await(p.removeEffect(k), had);
        }
        if (EntityStates.live(e)) return EntityStates.read(e, LivingEntityState.class, s -> s.removeEffect(k), false, "LivingEntity#removeEffect");
        EntityStates.write(e, LivingEntityState.class, s -> s.removeEffect(k), "LivingEntity#removeEffect");
        return true;
    }

    public static boolean removeAll(LivingEntity e) {
        if (e instanceof Player p) {
            boolean had = !p.effects().isEmpty();
            var f = p.clearEffects();
            return f.isDone() ? await(f, 0) > 0 : had;
        }
        if (EntityStates.live(e)) return EntityStates.read(e, LivingEntityState.class, s -> s.clearEffects() > 0, false, "LivingEntity#removeAllEffects");
        EntityStates.write(e, LivingEntityState.class, LivingEntityState::clearEffects, "LivingEntity#removeAllEffects");
        return true;
    }

    /** 끝난 미래면 그 값(실패는 그대로 던짐), 넘겨진 것이면 추정값. */
    private static <T> T await(java.util.concurrent.CompletableFuture<T> f, T guess) {
        if (!f.isDone()) return guess;
        try {
            return f.join();
        } catch (java.util.concurrent.CompletionException ce) {
            if (ce.getCause() instanceof RuntimeException re) throw re;
            throw ce;
        }
    }
}
