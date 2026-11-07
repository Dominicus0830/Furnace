package org.bukkit.configuration.serialization;

import java.util.Map;

/** 설정에 저장할 수 있는 객체. 1차 호환에서는 저장만 되고(Map 으로) 되읽기 등록은 없다. */
public interface ConfigurationSerializable {

    Map<String, Object> serialize();
}
