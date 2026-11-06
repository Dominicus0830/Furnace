// 플러그인 레지스트리 하나(PluginRegistries 에서 얻은, 한 플러그인에 묶인 보기). 항목 순서는 넣은 순서다.
package org.domi.furnace.api.registry;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface PluginRegistry<T> {

    String key();

    Class<T> type();

    /** 만든 플러그인 이름. */
    String owner();

    /**
     * 항목을 넣는다. key 의 namespace 는 이 보기 플러그인의 namespace 여야 한다.
     * 이미 있는 키·고정된 레지스트리·사라진 레지스트리면 예외(IllegalArgumentException / IllegalStateException).
     */
    T register(String key, T value);

    /** 이 보기 플러그인이 넣은 항목을 뺀다. 없으면 false, 남의 항목이면 IllegalArgumentException, 고정됐으면 IllegalStateException. */
    boolean unregister(String key);

    /** 없으면 null. */
    T get(String key);

    /** 없으면 IllegalArgumentException. */
    default T getOrThrow(String key) {
        T v = get(key);
        if (v == null) throw new IllegalArgumentException(key() + " 에 없는 항목: " + key);
        return v;
    }

    boolean contains(String key);

    /** 그 항목을 넣은 플러그인 이름(없으면 null). */
    String entryOwner(String key);

    /** 키(넣은 순서, 사본). */
    List<String> keys();

    /** 값(넣은 순서, 사본). */
    Collection<T> values();

    /** 키 → 값(넣은 순서, 바꿀 수 없는 사본). */
    Map<String, T> entries();

    int size();

    /** 만든 플러그인만 부를 수 있다. 고정 뒤에는 넣기·빼기가 안 된다(플러그인이 꺼질 때의 정리는 예외). */
    void freeze();

    boolean isFrozen();

    /** 만든 플러그인이 꺼져 레지스트리가 사라졌는가. */
    boolean isRemoved();

    /** 항목 변화 알림을 단다(이 보기 플러그인이 꺼지면 함께 떨어진다). 알림은 바꾼 스레드에서 바로 불린다. */
    void addListener(RegistryListener<? super T> listener);
}
