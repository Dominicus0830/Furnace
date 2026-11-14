package org.bukkit.craftbukkit.block;

import org.bukkit.DyeColor;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.craftbukkit.block.sign.CraftSignSide;

/** Paper CraftSign: 표지판(sign·hanging_sign 블록 엔티티)의 상태. 면 없는 메서드는 앞면, 밀랍은 is_waxed. */
public class CraftSign extends CraftBlockEntityState implements org.bukkit.block.Sign {

    private final CraftSignSide front;
    private final CraftSignSide back;

    public CraftSign(String dimension, org.domi.furnace.api.world.BlockPos pos, org.bukkit.block.data.BlockData data) {
        super(dimension, pos, data);
        this.front = new CraftSignSide(snapshotNbt(), "front_text");
        this.back = new CraftSignSide(snapshotNbt(), "back_text");
    }

    @Override
    public String[] getLines() { return front.getLines(); }

    @Override
    public String getLine(int index) { return front.getLine(index); }

    @Override
    public void setLine(int index, String line) { front.setLine(index, line); }

    @Override
    public boolean isEditable() { return !isWaxed(); }

    @Override
    public void setEditable(boolean editable) { setWaxed(!editable); }

    @Override
    public boolean isWaxed() { return snapshotNbt().getBoolean("is_waxed"); }

    @Override
    public void setWaxed(boolean waxed) { snapshotNbt().putBoolean("is_waxed", waxed); }

    @Override
    public boolean isGlowingText() { return front.isGlowingText(); }

    @Override
    public void setGlowingText(boolean glowing) { front.setGlowingText(glowing); }

    @Override
    public DyeColor getColor() { return front.getColor(); }

    @Override
    public void setColor(DyeColor color) { front.setColor(color); }

    @Override
    public SignSide getSide(Side side) {
        if (side == null) throw new IllegalArgumentException("side == null");
        return switch (side) {
            case FRONT -> front;
            case BACK -> back;
        };
    }

    @Override
    protected void applyToSnapshot() {
        front.applyTo();
        back.applyTo();
    }
}
