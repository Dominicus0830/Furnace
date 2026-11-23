// 아이템 컴포넌트·영속 데이터를 읽고 쓰는 서버 쪽 구현의 입구. ItemStack 의 타입 있는 메서드가 이걸 거친다.
// 저장 꼴은 마인크래프트 버전마다 달라질 수 있어서(컴포넌트 필드 이름·기본값) 변환은 서버가 하고, API 타입은 버전 중립으로 둔다.
// 서버 구현 전용으로 한 번만 설치한다.
package org.domi.furnace.api.item;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.domi.furnace.api.nbt.NbtCompound;
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

    // ------------------------------------------------------------------ 패치 저장 꼴(바닐라 DataComponentPatch.CODEC 의 NBT 꼴)

    /**
     * 패치를 바닐라 {@code DataComponentPatch.CODEC} 을 NbtOps 로 쓴 꼴로: {@code {"minecraft:x": 값, "!minecraft:y": {}}}. 값은 컴포넌트 규칙으로
     * 정규화하고, 저장하지 않는(transient) 종류는 건너뛴다. 모르는 컴포넌트·규칙에 안 맞는 값이면 IllegalArgumentException.
     */
    NbtCompound encodeComponentPatch(Map<String, String> components, Set<String> removed);

    /**
     * {@link #encodeComponentPatch} 와 같은 규칙(정규화·transient 건너뜀·오류)으로 NBT 가 아닌 ops(JsonOps 등)가 쓸 값을 돌려준다. NBT 꼴은 참·거짓이
     * 바이트라서 그 ops 로는 0/1 이 되므로, 바닐라가 JSON 으로 쓰는 모양을 따로 준다: 맵은 {@code Map<String,Object>}, 리스트는 {@code List<Object>},
     * 잎은 {@code String}·{@code Boolean}·수({@code Byte}~{@code Double}, 필드 타입대로)이고, NBT 로 저장되는 값(custom_data 등)은 이 API 의
     * {@code NbtCompound}·{@code NbtList}·배열 그대로다. 키는 {@code {"minecraft:x": 값, "!minecraft:y": {}}} 꼴.
     */
    Map<String, Object> encodeComponentPatchValues(Map<String, String> components, Set<String> removed);

    /**
     * {@link #encodeComponentPatch} 의 반대(바닐라 CODEC 읽기)를 항목마다 따로 한다(바닐라 {@code DispatchedMapCodec.decode}): 좋은 항목은 패치에,
     * 모르는 컴포넌트·저장하지 않는 종류·규칙에 안 맞는 값은 바닐라와 같은 문구의 오류와 그 키로 모아 돌려준다(던지지 않는다).
     */
    ItemComponentPatchDecoding decodeComponentPatchPartial(NbtCompound nbt);

    /** {@link #decodeComponentPatchPartial} 의 엄격한 꼴: 나쁜 항목이 하나라도 있으면 첫 오류 문구로 IllegalArgumentException. */
    default ItemComponentPatch decodeComponentPatch(NbtCompound nbt) {
        ItemComponentPatchDecoding d = decodeComponentPatchPartial(nbt);
        if (!d.errors().isEmpty()) throw new IllegalArgumentException(d.errors().get(0));
        return d.patch();
    }

    /**
     * 패치를 스택에 얹은 새 스택(바닐라 {@code ItemStack.applyComponents(patch)} = PatchedDataComponentMap.applyPatch): 값은 {@code set}, 지움은 {@code remove}
     * 규칙(기본값과 같으면 패치에서 뗀다)으로 하나씩 적용한다.
     */
    ItemStack applyComponentPatch(ItemStack stack, ItemComponentPatch patch);

    /**
     * 값을 그 컴포넌트의 저장 꼴 SNBT 로(규칙·정규화는 {@code set} 과 같다). 아이템 기본값과 같아도 떼지 않는다 — 스택에 얹지 않고 값만 바꾼다.
     * 규칙에 맞지 않으면 IllegalArgumentException.
     */
    <T> String componentSnbt(ItemComponent<T> component, T value);

    /**
     * 컴포넌트 값 하나를 네트워크 꼴 바이트로(바닐라 {@code DataComponentType.streamCodec().encode}): 종류 id 는 붙이지 않는다.
     * 값은 SNBT(저장 꼴). 모르는 컴포넌트·규칙에 안 맞는 값이면 IllegalArgumentException.
     */
    byte[] encodeComponentValue(String component, String snbt);

    /** {@link #encodeComponentValue} 의 반대: {@code bytes} 의 위치부터 값 하나를 읽어(버퍼의 위치·한도는 바꾸지 않는다) 저장 꼴 SNBT 와 읽은 바이트 수를 준다. 잘못된 바이트면 IllegalArgumentException. */
    ItemComponentWireValue decodeComponentValue(String component, java.nio.ByteBuffer bytes);

    // ------------------------------------------------------------------ 설치

    static ItemSupport get() {
        ItemSupport s = Holder.support;
        if (s == null) throw new IllegalStateException("the server is not ready yet - item components can only be used inside the server");
        return s;
    }

    /** 서버 구현 전용. 한 번만 정할 수 있다(같은 객체를 다시 넣는 것은 괜찮다). */
    static void install(ItemSupport support) {
        synchronized (Holder.class) {
            if (Holder.support != null && Holder.support != support) throw new IllegalStateException("ItemSupport can only be set once");
            Holder.support = support;
        }
    }

    final class Holder {
        private static volatile ItemSupport support;

        private Holder() {}
    }
}
