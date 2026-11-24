package net.minecraft.network.chat;

/**
 * 채팅 글 도우미(Mojang 이름 shim) — 지금은 {@code fromMessage} 만 있다. 나머지 멤버는 없다(없는 멤버 — 호출 위치에서 실패).
 */
class ComponentUtils {

    private ComponentUtils() {}

    /**
     * 바닐라 fromMessage: Brigadier 메시지를 글로. 바닐라는 메시지가 이미 글이면 그대로 주지만 이 shim 의 {@link Component} 는 Brigadier
     * {@code Message} 를 잇지 않으므로(Brigadier 없이도 글을 쓸 수 있게) 늘 글자 그대로의 글로 만든다 — 바닐라가 글이 아닌 메시지에 하는 일과 같다.
     */
    public static Component fromMessage(com.mojang.brigadier.Message message) { return Component.literal(message.getString()); }
}
