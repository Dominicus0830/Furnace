// 한 엔티티의 한 속성 핸들. 읽기는 어느 스레드에서나 되며 "핸들을 만든 시점" 값이다(플레이어는 최근 값).
// 쓰기는 그 엔티티를 가진 region 스레드로 넘겨 실행하고, 끝나면 Future 로 알린다.
// 얻는 곳은 {@code LivingEntity#attribute} 다. 지금 값을 읽거나 여러 번 쓰기를 한꺼번에 하려면 {@link #edit} 를 쓴다.
package org.domi.furnace.api.entity.attribute;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public interface AttributeHandle {

    Attribute attribute();

    /** 이 속성의 기본값({@link Attribute#defaultValue}, Bukkit getDefaultValue 와 같다). */
    double defaultValue();

    /** 핸들 시점의 기본값(수정자를 뺀 값). */
    double baseValue();

    /** 핸들 시점의 값(수정자를 합쳐 범위로 자른 값). */
    double value();

    /** 핸들 시점에 달려 있던 수정자 전부. */
    List<AttributeModifier> modifiers();

    default Optional<AttributeModifier> modifier(String id) {
        String key = new AttributeModifier(id, 0, org.domi.furnace.api.item.AttributeModifiers.Operation.ADD_VALUE).id();   // 네임스페이스를 맞춘다("boost" = "minecraft:boost")
        for (AttributeModifier m : modifiers()) if (m.id().equals(key)) return Optional.of(m);
        return Optional.empty();
    }

    /**
     * 소유 region 스레드에서 action 을 돌린다. 받은 {@link AttributeInstance} 는 지금 값이라, 읽은 뒤 쓰는 동안 값이 끼어들어 바뀌지 않는다.
     *
     * <p>엔티티가 사라졌거나 접속이 끊겼으면 IllegalStateException 으로 완료한다.
     * <p>그 엔티티가 이 속성을 가지지 않을 때(그사이 없어질 일은 없지만 종류가 달라진 경우)도 IllegalStateException 으로 완료한다.
     * <p>action 의 예외는 그대로 Future 의 실패가 된다. 콜백 밖으로 인스턴스를 들고 나가지 않는다.
     */
    <T> CompletableFuture<T> edit(Function<? super AttributeInstance, ? extends T> action);

    /** {@link AttributeInstance#setBaseValue}. */
    default CompletableFuture<Void> setBaseValue(double value) {
        return edit(i -> {
            i.setBaseValue(value);
            return null;
        });
    }

    /** {@link AttributeInstance#addModifier}. 같은 id 가 있으면 IllegalArgumentException 으로 완료한다. */
    default CompletableFuture<Void> addModifier(AttributeModifier modifier) {
        return edit(i -> {
            i.addModifier(modifier);
            return null;
        });
    }

    /** {@link AttributeInstance#addTransientModifier}. */
    default CompletableFuture<Void> addTransientModifier(AttributeModifier modifier) {
        return edit(i -> {
            i.addTransientModifier(modifier);
            return null;
        });
    }

    /** {@link AttributeInstance#setModifier}. */
    default CompletableFuture<Void> setModifier(AttributeModifier modifier) {
        return edit(i -> {
            i.setModifier(modifier);
            return null;
        });
    }

    /** {@link AttributeInstance#removeModifier}. 있었으면 true 로 완료한다. */
    default CompletableFuture<Boolean> removeModifier(String id) { return edit(i -> i.removeModifier(id)); }

    /** 소유 스레드에서 지금 값을 읽는다({@code edit(AttributeInstance::value)} 와 같다). */
    default CompletableFuture<Double> currentValue() { return edit(AttributeInstance::value); }
}
