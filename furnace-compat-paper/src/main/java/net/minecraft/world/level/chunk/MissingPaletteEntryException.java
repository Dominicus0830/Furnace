package net.minecraft.world.level.chunk;

/** 팔레트에 없는 번호(Mojang 이름 shim, N2b-1) — 문구는 바닐라와 같다. */
public class MissingPaletteEntryException extends RuntimeException {

    public MissingPaletteEntryException(int id) { super("Missing Palette entry for index " + id + "."); }
}
