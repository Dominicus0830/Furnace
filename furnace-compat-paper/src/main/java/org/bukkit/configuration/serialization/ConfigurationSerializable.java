package org.bukkit.configuration.serialization;

import java.util.Map;

/**
 * 설정·객체 스트림에 저장할 수 있는 객체. 되읽기는 {@link ConfigurationSerialization} 이 맡는다: 클래스에
 * {@code static T deserialize(Map<String, Object>)}, {@code static T valueOf(Map<String, Object>)}, 공개 생성자
 * {@code (Map<String, Object>)} 중 하나가 있어야 하고(이 순서로 찾는다), {@link ConfigurationSerialization#registerClass} 로 등록해야 한다.
 */
public interface ConfigurationSerializable {

    /** 저장할 값의 맵. 값은 문자열·수·불리언·리스트·맵·다른 ConfigurationSerializable 만 쓴다. */
    Map<String, Object> serialize();
}
