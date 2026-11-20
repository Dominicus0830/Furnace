package net.kyori.adventure.text.serializer;

import net.kyori.adventure.text.Component;

/** Adventure 4.17 대역(컴파일 전용): 진짜와 같은 제네릭 모양(지움 뒤 서술자가 같다). */
public interface ComponentSerializer<I extends Component, O extends Component, R> {
    O deserialize(R input);

    R serialize(I component);
}
