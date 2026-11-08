package org.bukkit.configuration.serialization;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 클래스의 객체를 되읽을 때는 다른 클래스의 deserialize 를 쓴다(별칭도 그 클래스의 것). 구현 클래스가 여럿인 인터페이스
 * (예: ItemMeta 의 Furnace 구현)를 한 별칭으로 묶을 때 쓴다.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DelegateDeserialization {

    /** 되읽기를 맡을 클래스. */
    Class<? extends ConfigurationSerializable> value();
}
