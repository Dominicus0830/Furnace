package net.minecraft.network.chat.contents;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

/** 키 바인딩 내용(Mojang 이름 shim). 서버의 기본 해석은 키 이름을 번역 키로 본다(en_us 의 key.jump → "Jump"). */
public class KeybindContents implements ComponentContents {

    private final String name;

    public KeybindContents(String name) { this.name = name; }

    public String getName() { return name; }

    private Component nested() { return Component.translatable(name); }

    @Override
    public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> consumer, Style style) { return nested().visit(consumer, style); }

    @Override
    public <T> Optional<T> visit(FormattedText.ContentConsumer<T> consumer) { return nested().visit(consumer); }

    @Override
    public boolean equals(Object o) { return this == o || o instanceof KeybindContents k && name.equals(k.name); }

    @Override
    public int hashCode() { return name.hashCode(); }

    @Override
    public String toString() { return "keybind{" + name + "}"; }
}
