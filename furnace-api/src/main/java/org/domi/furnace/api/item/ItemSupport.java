// 아이템 컴포넌트·영속 데이터를 읽고 쓰는 서버 쪽 구현의 입구. ItemStack 의 타입 있는 메서드가 이걸 거친다.
// 저장 꼴은 마인크래프트 버전마다 달라질 수 있어서(컴포넌트 필드 이름·기본값) 변환은 서버가 하고, API 타입은 버전 중립으로 둔다.
// 서버 구현 전용으로 한 번만 설치한다.
package org.domi.furnace.api.item;

import java.util.function.Consumer;
import org.domi.furnace.api.persistence.PersistentDataContainer;
import org.domi.furnace.api.persistence.PersistentDataView;

public interface ItemSupport {

    /**
     * 컴포넌트의 실제 값: 스택에 붙은 것, 없으면 그 아이템의 기본값(바닐라 ItemStack.get). 지워졌거나 기본값도 없으면 null.
     * 값이 그 컴포넌트 규칙에 맞지 않게 저장돼 있으면 IllegalArgumentException.
     */
    <T> T get(ItemStack stack, ItemComponent<T> component);

    /** 값을 넣은 새 스택. 값이 그 아이템 기본값과 같으면 붙이지 않고 기본으로 되돌린다(바닐라 PatchedDataComponentMap.set). 규칙에 맞지 않으면 IllegalArgumentException. */
    <T> ItemStack set(ItemStack stack, ItemComponent<T> component, T value);

    /** 컴포넌트를 없앤 새 스택. 그 아이템에 기본값이 있으면 "지움" 표시를, 없으면 그냥 뗀다(바닐라 remove). */
    ItemStack remove(ItemStack stack, ItemComponent<?> component);

    /** 스택에 든 영속 데이터(custom_data 의 PublicBukkitValues)의 읽기 전용 사본. */
    PersistentDataView persistentData(ItemStack stack);

    /** 영속 데이터를 고친 새 스택. 바뀐 것이 없으면 같은 스택. 다 비면 PublicBukkitValues, 그래서 custom_data 가 비면 그것도 뗀다. */
    ItemStack editPersistentData(ItemStack stack, Consumer<PersistentDataContainer> edit);

    // ------------------------------------------------------------------ 설치

    static ItemSupport get() {
        ItemSupport s = Holder.support;
        if (s == null) throw new IllegalStateException("서버가 아직 준비되지 않았다 — 아이템 컴포넌트는 서버 안에서만 쓸 수 있다");
        return s;
    }

    /** 서버 구현 전용 — 한 번만 정할 수 있다(같은 객체를 다시 넣는 것은 괜찮다). */
    static void install(ItemSupport support) {
        synchronized (Holder.class) {
            if (Holder.support != null && Holder.support != support) throw new IllegalStateException("ItemSupport 는 한 번만 정할 수 있다");
            Holder.support = support;
        }
    }

    final class Holder {
        private static volatile ItemSupport support;

        private Holder() {}
    }
}
