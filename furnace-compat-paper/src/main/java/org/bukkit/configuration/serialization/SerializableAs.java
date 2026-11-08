package org.bukkit.configuration.serialization;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 직렬화할 때 클래스 이름 대신 쓸 별칭(설정 파일의 {@code ==} 값). {@link ConfigurationSerialization#registerClass} 가 이 별칭으로 등록한다. */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SerializableAs {

    /** 별칭. 같은 별칭을 다른 클래스가 쓰면 나중에 등록한 쪽이 이긴다. */
    String value();
}
