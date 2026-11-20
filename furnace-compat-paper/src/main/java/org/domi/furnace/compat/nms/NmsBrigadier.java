package org.domi.furnace.compat.nms;

import com.mojang.brigadier.Message;
import net.minecraft.network.chat.Component;

/**
 * NMS shim 이 Brigadier 오류를 만드는 곳(5e-3c). 바닐라 오류 메시지는 Brigadier {@code Message} 를 잇는 {@code Component} 지만, 이 shim 의
 * {@link Component} 는 Brigadier 없이도 쓰이게 {@code Message} 를 잇지 않는다 — 그래서 글을 감싼 메시지로 준다. {@code getString()}(서버 언어로 번역한 글)과
 * 예외 문구는 바닐라와 같고, {@code getRawMessage()} 가 {@code Component} 가 아닌 점만 다르다({@link ComponentMessage#component()} 로 글을 꺼낸다).
 * 이 클래스는 Brigadier 를 부르는 shim 만 부르므로 Brigadier 묶음이 붙은 뒤에만 읽힌다.
 */
public final class NmsBrigadier {

    private NmsBrigadier() {}

    /** 글을 Brigadier 메시지로. */
    public static Message message(Component component) { return new ComponentMessage(component); }

    /** 바닐라 {@code Component.translatableEscape(key, args)} 를 메시지로(인자가 글자뿐이라 translatable 과 같다). */
    public static Message translatable(String key, Object... args) {
        Object[] a = new Object[args.length];
        for (int i = 0; i < args.length; i++) a[i] = args[i] instanceof Component || args[i] instanceof Number || args[i] instanceof Boolean ? args[i] : String.valueOf(args[i]);
        return message(Component.translatable(key, a));
    }

    /** 글을 감싼 Brigadier 메시지. */
    public record ComponentMessage(Component component) implements Message {
        @Override
        public String getString() { return component.getString(); }

        @Override
        public String toString() { return component.getString(); }
    }
}
