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
        private net.minecraft.world.level.material.FluidState fluidState;   // 처음 물을 때 채운다(같은 객체라 경합해도 결과가 같다)
        private byte randomlyTicking;   // 0 = 아직 모름, 1 = 아니오, 2 = 예(경합해도 결과가 같다)

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

        /** 바닐라: 블록의 레지스트리 손잡이가 태그에 들었는가(지금 걸린 태그). */
        public boolean is(net.minecraft.tags.TagKey<Block> tag) { return owner.builtInRegistryHolder().is(tag); }

        public boolean is(net.minecraft.tags.TagKey<Block> tag, java.util.function.Predicate<BlockStateBase> predicate) { return is(tag) && predicate.test(this); }

        public java.util.stream.Stream<net.minecraft.tags.TagKey<Block>> getTags() { return owner.builtInRegistryHolder().tags(); }

        /** 바닐라: 물·용암 블록은 level 에 맞는 유체, 잠긴 블록·켈프·해초·거품 기둥은 물 원천, 그 밖은 빈 유체. */
        public net.minecraft.world.level.material.FluidState getFluidState() {
            var f = fluidState;
            if (f == null) fluidState = f = org.domi.furnace.compat.nms.NmsFluids.ofBlockState(furnaceStateId);
            return f;
        }

        /** 바닐라 isAir: air·cave_air·void_air. */
        public boolean isAir() { return owner.furnaceIsAir(); }

        /** 바닐라: 블록 자체가 무작위 틱을 받는가(유체 쪽은 {@code getFluidState().isRandomlyTicking()}). 서버 블록 동작 표에서 처음 물을 때 채운다. */
        public boolean isRandomlyTicking() {
            byte t = randomlyTicking;
            if (t == 0) randomlyTicking = t = org.domi.furnace.compat.nms.NmsChunks.bridge().stateRandomlyTicking(furnaceStateId) ? (byte) 2 : (byte) 1;
            return t == 2;
        }
    }
}
