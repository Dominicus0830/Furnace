package net.minecraft.world.level.block.state;

import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * 블록 동작의 바탕(Mojang 이름 shim, N0). 바닐라의 동작 메서드(모양·충돌·틱·상호작용)는 Furnace 블록 동작이 서버 안에 있어
 * 두지 않았다 — 부르면 H7 덫. 블록 속성 묶음({@code Properties}) 도 플러그인이 블록을 만들 수 없어 두지 않았다.
 */
public abstract class BlockBehaviour {

    /** 블록 상태의 바탕. 값은 같은 블록의 상태 표에서 찾는다. */
    public abstract static class BlockStateBase extends StateHolder<Block, BlockState> {

        private final int furnaceStateId;

        protected BlockStateBase(Block owner, Map<Property<?>, Comparable<?>> values, int furnaceStateId) {
            super(owner, values);
            this.furnaceStateId = furnaceStateId;
        }

        /** 호환 모듈 전용 — 바닐라 상태 번호(= {@code Block.getId(this)}). */
        public final int furnaceStateId() { return furnaceStateId; }

        @Override
        protected BlockState withValue(Property<?> property, Comparable<?> value) { return owner.furnaceStateWith(asState(), property, value); }

        protected abstract BlockState asState();

        public Block getBlock() { return owner; }

        public Holder<Block> getBlockHolder() { return owner.builtInRegistryHolder(); }

        public boolean is(Block block) { return owner == block; }

        public boolean is(Holder<Block> block) { return block.value() == owner; }

        /** 바닐라 isAir: air·cave_air·void_air. */
        public boolean isAir() { return owner.furnaceIsAir(); }
    }
}
