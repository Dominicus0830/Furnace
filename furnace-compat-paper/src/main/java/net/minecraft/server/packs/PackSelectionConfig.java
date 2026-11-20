package net.minecraft.server.packs;

import net.minecraft.server.packs.repository.Pack;

/** 팩을 고르는 규칙(바닐라 record). */
public record PackSelectionConfig(boolean required, Pack.Position defaultPosition, boolean fixedPosition) {}
