package net.minecraft.world.entity.ai.attributes;

import com.google.common.collect.Multimap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import org.domi.furnace.compat.nms.NmsAttributes;

/**
 * 한 엔티티의 속성 모음(Mojang 이름 shim, 효과·프로필·속성 묶음) — {@code LivingEntity#getAttributes()} 가 준다. 따로 상태를 들지 않고 서버의 그 엔티티 속성을
 * 본다(/attribute 명령·장비·효과 수정자와 같은 상태). 각 속성은 {@link AttributeInstance} 의 주인 스레드 규칙을 그대로 따른다(주인 스레드면 곧바로,
 * 다른 스레드의 쓰기는 주인에게 넘김). 엔티티에 붙지 않은 모음({@code new AttributeMap(AttributeSupplier)})·저장({@code save}·{@code load})·
 * 값 복사({@code assignAllValues})·동기 대기 목록({@code getAttributesToSync}·{@code getAttributesToUpdate})은 두지 않았다(부르면 없는 멤버).
 */
class AttributeMap {

    private final org.domi.furnace.api.entity.LivingEntity entity;

    private AttributeMap(org.domi.furnace.api.entity.LivingEntity entity) { this.entity = entity; }

    /** 호환 모듈 전용: 그 엔티티의 속성 모음. */
    public static AttributeMap furnaceOf(org.domi.furnace.api.entity.LivingEntity entity) { return new AttributeMap(entity); }

    /** 바닐라: 그 엔티티가 가진 속성이면 그 속성, 아니면 null. */
    public AttributeInstance getInstance(Holder<Attribute> attribute) { return AttributeInstance.furnaceOf(entity, attribute); }

    public boolean hasAttribute(Holder<Attribute> attribute) { return getInstance(attribute) != null; }

    public boolean hasModifier(Holder<Attribute> attribute, ResourceLocation id) {
        AttributeInstance i = getInstance(attribute);
        return i != null && i.getModifier(id) != null;
    }

    private AttributeInstance need(Holder<Attribute> attribute) {
        AttributeInstance i = getInstance(attribute);
        if (i == null) throw new IllegalArgumentException("Can't find attribute " + attribute.getRegisteredName());
        return i;
    }

    public double getValue(Holder<Attribute> attribute) { return need(attribute).getValue(); }

    public double getBaseValue(Holder<Attribute> attribute) { return need(attribute).getBaseValue(); }

    /** 없는 속성·없는 수정자는 IllegalArgumentException(바닐라 문구). */
    public double getModifierValue(Holder<Attribute> attribute, ResourceLocation id) {
        AttributeModifier m = need(attribute).getModifier(id);
        if (m == null) throw new IllegalArgumentException("Can't find modifier " + id + " on attribute " + attribute.getRegisteredName());
        return m.amount();
    }

    /** 바닐라: 같은 id 가 있으면 바꿔 달고(저장되지 않는다), 엔티티가 그 속성을 안 가지면 건너뛴다. */
    public void addTransientAttributeModifiers(Multimap<Holder<Attribute>, AttributeModifier> modifiers) {
        modifiers.forEach((attribute, modifier) -> {
            AttributeInstance i = getInstance(attribute);
            if (i != null) {
                i.removeModifier(modifier.id());
                i.addTransientModifier(modifier);
            }
        });
    }

    public void removeAttributeModifiers(Multimap<Holder<Attribute>, AttributeModifier> modifiers) {
        modifiers.asMap().forEach((attribute, list) -> {
            AttributeInstance i = getInstance(attribute);
            if (i != null) list.forEach(m -> i.removeModifier(m.id()));
        });
    }

    /** 클라이언트에 동기하는 속성(공격력·공격 넉백 같은 것은 빠진다). */
    public Collection<AttributeInstance> getSyncableAttributes() {
        List<AttributeInstance> out = new ArrayList<>();
        for (var key : entity.attributes()) {
            Holder<Attribute> h = NmsAttributes.holder(key.key());
            if (h.value().isClientSyncable()) {
                AttributeInstance i = getInstance(h);
                if (i != null) out.add(i);
            }
        }
        return out;
    }
}
