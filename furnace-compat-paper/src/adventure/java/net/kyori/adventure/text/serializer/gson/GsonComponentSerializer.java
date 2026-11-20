package net.kyori.adventure.text.serializer.gson;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.ComponentSerializer;

/** Adventure 4.17 대역(컴파일 전용): 진짜와 같은 모양(JSON 글 직렬화기). */
public interface GsonComponentSerializer extends ComponentSerializer<Component, Component, String> {
    static GsonComponentSerializer gson() { throw new UnsupportedOperationException("stub"); }
}
