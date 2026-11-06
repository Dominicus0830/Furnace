// 바닐라 ComparatorBlockEntity: 비교기 출력 세기(OutputSignal)를 들고 있다.
package org.domi.furnace.server.block.entity;

import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class ComparatorBlockEntity extends BlockEntity {

    private int output;

    public ComparatorBlockEntity(Pos pos, int state) { super("minecraft:comparator", pos, state); }

    public int getOutputSignal() { return output; }

    public void setOutputSignal(int output) { this.output = output; }

    @Override
    public boolean isValid(int state) { return org.domi.furnace.server.block.B.is(state, "comparator"); }

    @Override
    protected void saveAdditional(NbtCompound tag) { tag.putInt("OutputSignal", output); }

    @Override
    public void load(NbtCompound tag) { output = tag.getIntOr0("OutputSignal"); }
}
