package net.minecraft.world.entity.ai.attributes;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import org.domi.furnace.api.entity.LivingEntityState;
import org.domi.furnace.compat.nms.UnsupportedNmsCall;
import org.domi.furnace.compat.paper.EntityStates;

/**
 * 한 엔티티의 한 속성(Mojang 이름 shim, NMS shim 3번 묶음 — VeinMiner 채굴 속도, squaremap 방어 수치). {@code LivingEntity#getAttribute(Holder)} 가
 * 준다. 상태를 따로 들지 않고 서버의 그 엔티티 속성(Furnace {@link org.domi.furnace.api.entity.attribute.AttributeInstance})을 본다 — /attribute 명령·
 * 장비·효과 수정자와 같은 상태다.
 * <ul>
 *   <li>그 엔티티의 주인 스레드(플레이어는 그 플레이어의 region 스레드)면 바닐라처럼 곧바로 읽고 쓴다.</li>
 *   <li>다른 스레드에서 읽으면 그 순간 값(플레이어는 최근 값), 쓰면 주인 스레드로 넘긴다. 그때 돌려줄 값(뗐는가)은 부른 순간의 수정자 목록으로 답하고,
 *       같은 id 를 다시 다는 오류도 그 목록으로 판정한다. 넘긴 쓰기가 실패하면(엔티티가 사라짐 등) 플러그인마다 한 번 콘솔 경고.</li>
 * </ul>
 * 바닐라 생성자·{@code replaceFrom}·{@code save}·{@code load} 는 두지 않았다(엔티티에 붙지 않은 속성은 서버에 없다).
 */
public class AttributeInstance {

    private static final String WHAT = "AttributeInstance";

    private final Holder<Attribute> attribute;
    private final org.domi.furnace.api.entity.LivingEntity entity;
    private final org.domi.furnace.api.entity.attribute.Attribute furnace;

    private AttributeInstance(Holder<Attribute> attribute, org.domi.furnace.api.entity.LivingEntity entity, org.domi.furnace.api.entity.attribute.Attribute furnace) {
        this.attribute = attribute;
        this.entity = entity;
        this.furnace = furnace;
    }

    /** 호환 모듈 전용 — 그 엔티티가 이 속성을 가지면 그 보기, 아니면 null(바닐라 getAttribute). */
    public static AttributeInstance furnaceOf(org.domi.furnace.api.entity.LivingEntity entity, Holder<Attribute> attribute) {
        var a = org.domi.furnace.compat.nms.NmsAttributes.furnace(attribute);
        return entity.attribute(a).isPresent() ? new AttributeInstance(attribute, entity, a) : null;
    }

    public Holder<Attribute> getAttribute() { return attribute; }

    public double getBaseValue() { return read(org.domi.furnace.api.entity.attribute.AttributeInstance::baseValue, h -> h.baseValue()); }

    public void setBaseValue(double value) {
        write(i -> {
            i.setBaseValue(value);
            return null;
        }, () -> null);
    }

    /** 바닐라: 수정자까지 합쳐 범위로 자른 값. */
    public double getValue() { return read(org.domi.furnace.api.entity.attribute.AttributeInstance::value, h -> h.value()); }

    /** 바닐라: 달려 있는 수정자 전부(바꿀 수 없는 사본). */
    public Set<AttributeModifier> getModifiers() {
        var list = read(org.domi.furnace.api.entity.attribute.AttributeInstance::modifiers, h -> h.modifiers());
        Set<AttributeModifier> out = new LinkedHashSet<>();
        for (var m : list) out.add(nms(m));
        return java.util.Collections.unmodifiableSet(out);
    }

    public AttributeModifier getModifier(ResourceLocation id) {
        for (AttributeModifier m : getModifiers()) if (m.id().equals(id)) return m;
        return null;
    }

    public boolean hasModifier(ResourceLocation id) { return getModifier(id) != null; }

    /** 바닐라: 같은 id 가 있으면 IllegalArgumentException("Modifier is already applied on this attribute!"). 저장하지 않는다. */
    public void addTransientModifier(AttributeModifier modifier) {
        var m = furnace(modifier);
        write(i -> {
            i.addTransientModifier(m);
            return null;
        }, () -> {
            if (hasModifier(modifier.id())) throw new IllegalArgumentException("Modifier is already applied on this attribute!");
            return null;
        });
    }

    /** 바닐라: 같은 id 가 있으면 바꾼다(저장하지 않는다). */
    public void addOrUpdateTransientModifier(AttributeModifier modifier) {
        var m = furnace(modifier);
        write(i -> {
            i.removeModifier(m.id());
            i.addTransientModifier(m);
            return null;
        }, () -> null);
    }

    /** 바닐라: 같은 id 가 있으면 IllegalArgumentException. 엔티티와 함께 저장된다. */
    public void addPermanentModifier(AttributeModifier modifier) {
        var m = furnace(modifier);
        write(i -> {
            i.addModifier(m);
            return null;
        }, () -> {
            if (hasModifier(modifier.id())) throw new IllegalArgumentException("Modifier is already applied on this attribute!");
            return null;
        });
    }

    /** 바닐라: 같은 id 가 있으면 바꾼다(저장된다). */
    public void addOrReplacePermanentModifier(AttributeModifier modifier) {
        var m = furnace(modifier);
        write(i -> {
            i.setModifier(m);
            return null;
        }, () -> null);
    }

    public void removeModifier(AttributeModifier modifier) { removeModifier(modifier.id()); }

    /** 바닐라: 그 id 의 수정자를 뗀다. 있었으면 true. */
    public boolean removeModifier(ResourceLocation id) {
        String key = id.toString();
        return write(i -> i.removeModifier(key), () -> hasModifier(id));
    }

    /** 바닐라: 모든 수정자를 뗀다. */
    public void removeModifiers() {
        write(i -> {
            i.clearModifiers();
            return null;
        }, () -> null);
    }

    // ---------------------------------------------------------------- 주인 스레드 규칙

    private <T> T read(Function<org.domi.furnace.api.entity.attribute.AttributeInstance, T> live, Function<org.domi.furnace.api.entity.attribute.AttributeHandle, T> snapshot) {
        if (EntityStates.live(entity)) {   // 몹의 주인 스레드: 지금 값
            T v = EntityStates.read(entity, LivingEntityState.class, s -> s.attribute(furnace).map(live).orElse(null), null, WHAT);
            if (v != null) return v;
        } else if (entity instanceof org.domi.furnace.api.entity.Player
                && (org.domi.furnace.api.Furnace.server().scheduler().isTickThread() || entity instanceof org.domi.furnace.api.entity.Player pl && !pl.isOnline())) {
            // 플레이어의 주인 스레드(또는 월드에 없는 플레이어)면 그 자리에서 읽는다 — 방금 쓴 값이 보인다. 다른 region 의 틱 스레드면 그 읽기는
            // 주인 스레드에서 값을 바꾸지 않고 돌고, 여기서는 최근 값으로 답한다. 틱 스레드가 아니면(squaremap 의 비동기 작업 등) 곧바로 최근 값
            var f = handle().edit(live);
            if (f.isDone() && !f.isCompletedExceptionally()) return f.join();
        }
        return snapshot.apply(handle());
    }

    /**
     * 주인 스레드면 곧바로(값·예외 그대로), 아니면 넘기고 guess 로 답한다. guess 는 넘기기 전에 계산한다 — 부른 순간의 목록으로 답하고,
     * guess 가 예외(같은 id 가 이미 있음)면 바닐라처럼 아무것도 넘기지 않는다. 주인 스레드에서는 guess 도 지금 값을 보므로 op 와 같은 판정이다.
     */
    private <T> T write(Function<org.domi.furnace.api.entity.attribute.AttributeInstance, T> op, Supplier<T> guess) {
        if (EntityStates.live(entity)) {
            Object[] out = new Object[1];
            boolean[] had = new boolean[1];
            EntityStates.read(entity, LivingEntityState.class, s -> {
                s.attribute(furnace).ifPresent(i -> {
                    out[0] = op.apply(i);
                    had[0] = true;
                });
                return null;
            }, null, WHAT);
            if (!had[0]) throw new UnsupportedNmsCall(WHAT, "the entity is gone or no longer has " + furnace.key());
            @SuppressWarnings("unchecked") T t = (T) out[0];
            return t;
        }
        T answer = guess.get();
        var f = handle().edit(op);
        if (f.isDone()) {   // 플레이어의 주인 스레드(또는 월드에 없는 플레이어): 그 자리에서 했다
            try {
                return f.join();
            } catch (java.util.concurrent.CompletionException e) {
                if (e.getCause() instanceof RuntimeException re) throw re;
                throw e;
            }
        }
        String who = org.domi.furnace.compat.nms.NmsCaller.pluginName();
        f.whenComplete((v, e) -> {
            if (e == null) return;
            Throwable cause = e instanceof java.util.concurrent.CompletionException && e.getCause() != null ? e.getCause() : e;
            org.domi.furnace.compat.nms.NmsCaller.warnOnceAs(who, WHAT, "compat.nms.effect-failed", WHAT, cause.toString());
        });
        return answer;
    }

    private org.domi.furnace.api.entity.attribute.AttributeHandle handle() {
        return entity.attribute(furnace).orElseThrow(() -> new UnsupportedNmsCall(WHAT, "the entity no longer has " + furnace.key()));
    }

    private static org.domi.furnace.api.entity.attribute.AttributeModifier furnace(AttributeModifier m) {
        return new org.domi.furnace.api.entity.attribute.AttributeModifier(m.id().toString(), m.amount(),
                org.domi.furnace.api.item.AttributeModifiers.Operation.valueOf(m.operation().name()));
    }

    private static AttributeModifier nms(org.domi.furnace.api.entity.attribute.AttributeModifier m) {
        return new AttributeModifier(ResourceLocation.parse(m.id()), m.amount(), AttributeModifier.Operation.valueOf(m.operation().name()));
    }
}
