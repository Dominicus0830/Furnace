package net.minecraft.server.packs.repository;

import net.minecraft.network.chat.Component;

/** 팩이 이 판과 맞는가(바닐라 1.21.1 enum). forVersion 은 InclusiveRange shim 이 없어 두지 않았다. */
public enum PackCompatibility {
    TOO_OLD("old"),
    TOO_NEW("new"),
    COMPATIBLE("compatible");

    private final Component description;
    private final Component confirmation;

    PackCompatibility(String type) {
        this.description = Component.translatable("pack.incompatible." + type);
        this.confirmation = Component.translatable("pack.incompatible.confirm." + type);
    }

    public boolean isCompatible() { return this == COMPATIBLE; }

    public Component getDescription() { return this.description; }

    public Component getConfirmation() { return this.confirmation; }
}
