package net.minecraft.server.packs.repository;

import java.util.function.UnaryOperator;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** 팩의 출처(바닐라 interface): 제목 꾸밈과 "처음 목록에 자동으로 넣는가". */
interface PackSource {

    UnaryOperator<Component> NO_DECORATION = c -> c;
    PackSource DEFAULT = create(NO_DECORATION, true);
    PackSource BUILT_IN = create(decorateWithSource("pack.source.builtin"), true);
    PackSource FEATURE = create(decorateWithSource("pack.source.feature"), false);
    PackSource WORLD = create(decorateWithSource("pack.source.world"), true);
    PackSource SERVER = create(decorateWithSource("pack.source.server"), true);

    Component decorate(Component component);

    boolean shouldAddAutomatically();

    static PackSource create(UnaryOperator<Component> decorator, boolean addAutomatically) {
        return new PackSource() {
            @Override
            public Component decorate(Component component) { return decorator.apply(component); }

            @Override
            public boolean shouldAddAutomatically() { return addAutomatically; }
        };
    }

    private static UnaryOperator<Component> decorateWithSource(String source) {
        Component label = Component.translatable(source);
        return c -> Component.translatable("pack.nameAndSource", c, label).withStyle(ChatFormatting.GRAY);
    }
}
