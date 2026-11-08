package org.bukkit.util.io;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;

/**
 * Serializable 이 아닌 ConfigurationSerializable 을 객체 스트림에 싣는 껍데기: {@code ==} 별칭과 serialize() 맵을 담는다.
 * 클래스 이름·serialVersionUID·필드(map: Map)는 Paper 와 같아 스트림이 서로 읽힌다.
 * <p>
 * Paper 는 맵을 Guava ImmutableMap 으로 담는다. 호환 모듈은 Guava 가 있다고 가정할 수 없어서(서버 제공 라이브러리는 플러그인이 선언했을 때만 올라온다)
 * 삽입 순서를 지키는 LinkedHashMap 을 쓴다. Paper 가 우리 스트림을 읽는 데는 문제가 없다(필드 선언 타입이 Map 이다).
 * 반대로 Paper 가 쓴 스트림을 읽으려면 ImmutableMap 의 직렬화 형태를 풀 Guava 가 플러그인 클래스 로더에 보여야 한다.
 */
final class Wrapper<T extends Map<String, ?> & Serializable> implements Serializable {

    private static final long serialVersionUID = -986209235411767547L;

    final T map;

    static Wrapper<LinkedHashMap<String, ?>> newWrapper(ConfigurationSerializable obj) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put(ConfigurationSerialization.SERIALIZED_TYPE_KEY, ConfigurationSerialization.getAlias(obj.getClass()));
        map.putAll(obj.serialize());
        return new Wrapper<LinkedHashMap<String, ?>>(map);
    }

    private Wrapper(T map) { this.map = map; }
}
