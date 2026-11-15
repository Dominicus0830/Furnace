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
        private net.minecraft.world.phys.shapes.VoxelShape shape;   // 처음 물을 때 채운다(경합해도 값이 같다)

        /** 바닐라 private final 이지만 Paper 가 열어 FAWE 가 필드로 읽는다. 블록 파괴 시간(-1 = 못 부순다). */
        public final float destroySpeed;

        protected BlockStateBase(Block owner, Map<Property<?>, Comparable<?>> values, int furnaceStateId) {
            super(owner, values);
            this.furnaceStateId = furnaceStateId;
            this.destroySpeed = org.domi.furnace.compat.nms.NmsBlockStates.table().destroySpeed()[furnaceStateId];
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

        private int flagBits() { return org.domi.furnace.compat.nms.NmsBlockStates.table().flags()[furnaceStateId]; }

        /** 바닐라 캐시값: 빛이 지나갈 때 줄어드는 정도(꽉 찬 가림 15, 하늘빛 통과 0, 아니면 1 …). 위치·세계는 보지 않는다(바닐라도 캐시). */
        public int getLightBlock(net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos) {
            return org.domi.furnace.compat.nms.NmsBlockStates.table().lightBlock()[furnaceStateId];
        }

        /** 바닐라 getShape(level, pos) 의 빈 세계 값(바깥 모양, 서버가 바닐라에서 뽑은 상자들). */
        public net.minecraft.world.phys.shapes.VoxelShape getShape(net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos) {
            var s = shape;
            if (s == null) shape = s = net.minecraft.world.phys.shapes.VoxelShape.furnaceOf(org.domi.furnace.compat.nms.NmsBlockStates.bridge().outlineBoxes(furnaceStateId));
            return s;
        }

        public boolean canOcclude() { return (flagBits() & org.domi.furnace.api.internal.BlockStateInternals.CAN_OCCLUDE) != 0; }

        /** 바닐라 캐시 solidRender: 가리는 블록이고 가림 모양이 꽉 참. */
        public boolean isSolidRender(net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos) {
            return (flagBits() & org.domi.furnace.api.internal.BlockStateInternals.SOLID_RENDER) != 0;
        }

        /** 바닐라: 블록 클래스의 isSignalSource 재정의(레드스톤 가루·횃불·레버·단추 …). */
        public boolean isSignalSource() { return (flagBits() & org.domi.furnace.api.internal.BlockStateInternals.SIGNAL_SOURCE) != 0; }

        public int getLightEmission() { return org.domi.furnace.compat.nms.NmsBlockStates.table().lightEmission()[furnaceStateId]; }

        /**
         * 피스톤 반응. Paper 1.21.1 은 부술 수 없는 블록(Block#isDestroyable 이 거짓 — 기반암·차단벽·엔드 차원문과 그 틀·엔드 관문·명령 블록 3종·구조물 블록·
         * 직소, 기본 설정)을 늘 BLOCK 으로 준다(N2b-6 — Paper 와 대조). 나머지는 블록 속성 그대로(바닐라).
         */
        public net.minecraft.world.level.material.PushReaction getPistonPushReaction() {
            if (!owner.isDestroyable()) return net.minecraft.world.level.material.PushReaction.BLOCK;
            return net.minecraft.world.level.material.PushReaction.values()[org.domi.furnace.compat.nms.NmsBlockStates.table().pushReaction()[furnaceStateId]];
        }

        /** Paper: 블록이 부술 수 있는 것인가(Block#isDestroyable). */
        public boolean isDestroyable() { return owner.isDestroyable(); }

        public boolean blocksMotion() { return (flagBits() & org.domi.furnace.api.internal.BlockStateInternals.BLOCKS_MOTION) != 0; }

        public boolean canBeReplaced() { return (flagBits() & org.domi.furnace.api.internal.BlockStateInternals.REPLACEABLE) != 0; }
    }
}
