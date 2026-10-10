// 리스너 메서드 표시 — 매개변수 하나(Event 하위 타입). Bukkit @EventHandler 와 같은 의미.
package org.domi.furnace.api.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Subscribe {

    EventPriority priority() default EventPriority.NORMAL;

    /** true 면 이미 취소된 이벤트는 받지 않는다. */
    boolean ignoreCancelled() default false;
}
