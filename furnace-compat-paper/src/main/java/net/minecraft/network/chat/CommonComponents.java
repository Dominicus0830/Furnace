package net.minecraft.network.chat;

/** 자주 쓰는 글(Mojang 이름 shim, 일부). */
public final class CommonComponents {

    public static final Component EMPTY = Component.empty();
    public static final Component SPACE = Component.literal(" ");
    public static final Component NEW_LINE = Component.literal("\n");
    public static final Component ELLIPSIS = Component.literal("...");

    private CommonComponents() {}
}
