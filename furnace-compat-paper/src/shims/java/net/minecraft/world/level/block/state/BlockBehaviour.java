package net.minecraft.world.level.block.state;

import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * 블록 동작의 바탕(Mojang 이름 shim, N0). 바닐라의 동작 메서드(모양·충돌·틱·상호작용)는 Furnace 블록 동작이 서버 안에 있어
 * 대부분 두지 않았다 — 부르면 H7 덫. 쪼갠 setBlock 조각(onPlace·handleNeighborChanged·updateNeighbourShapes·updateIndirectNeighbourShapes·triggerEvent,
 * T-204)은 서버 블록 동작을 그 칸의 주인 region 에서 부른다. 블록 속성 묶음({@code Properties}) 도 플러그인이 블록을 만들 수 없어 두지 않았다.
 */
class BlockBehaviour {

    /** 손 shim 시절의 기본 생성자(생성기가 ShimCtor 생성자만 더하면 사라지므로 명시한다). */
    public BlockBehaviour() {}

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

        @Override
        public BlockState furnaceDefaultState() { return owner.defaultBlockState(); }

        public Block getBlock() { return owner; }

        public Holder<Block> getBlockHolder() { return owner.builtInRegistryHolder(); }

        public boolean is(Block block) { return owner == block; }

        public boolean is(Holder<Block> block) { return block.value() == owner; }

        /** 바닐라: 그 묶음에 이 블록이 있는가. */
        public boolean is(net.minecraft.core.HolderSet<Block> set) { return set.contains(owner.builtInRegistryHolder()); }

        /** 바닐라: 블록의 레지스트리 손잡이가 태그에 들었는가(지금 걸린 태그). */
        public boolean is(net.minecraft.tags.TagKey<Block> tag) { return owner.builtInRegistryHolder().is(tag); }

        public boolean is(net.minecraft.tags.TagKey<Block> tag, java.util.function.Predicate<BlockStateBase> predicate) { return is(tag) && predicate.test(this); }

        public java.util.stream.Stream<net.minecraft.tags.TagKey<Block>> getTags() { return owner.builtInRegistryHolder().tags(); }

        /**
         * 바닐라 {@code BlockStateBase#tick(level, pos, random)}: 이 상태의 블록 예약 틱 동작(Block#tick — 서버 이식)을 그 칸에서 한 번 돈다. 칸의 지금 상태가 아니라
         * 이 상태를 쓴다. 그 칸의 주인 region 스레드에서만(Level#setBlock 과 같다).
         */
        public void tick(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.util.RandomSource random) {
            level.furnaceScheduledTick(pos, null, asState(), random);
        }

        /** 바닐라: 물·용암 블록은 level 에 맞는 유체, 잠긴 블록·켈프·해초·거품 기둥은 물 원천, 그 밖은 빈 유체. */
        public net.minecraft.world.level.material.FluidState getFluidState() {
            var f = fluidState;
            if (f == null) fluidState = f = org.domi.furnace.compat.nms.NmsFluids.ofBlockState(furnaceStateId);
            return f;
        }

        /**
         * 바닐라 {@code BlockStateBase#useItemOn(stack, level, player, hand, hit)}(T-212): 이 상태의 블록이 그 아이템을 받는다(맞은 칸은 hit 의 칸).
         * 지금은 바닐라 생성자로 만든 월드에 없는 플레이어만 된다({@link org.domi.furnace.compat.nms.NmsItemUse}).
         */
        public net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, net.minecraft.world.level.Level level,
                                                                   net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
                                                                   net.minecraft.world.phys.BlockHitResult hit) {
            return org.domi.furnace.compat.nms.NmsItemUse.useItemOn(furnaceStateId, stack, level, player, hand, hit);
        }

        /** 바닐라 isAir: air·cave_air·void_air. */
        public boolean isAir() { return owner.furnaceIsAir(); }

        /** 바닐라: 블록이 EntityBlock 인가(움직이는 피스톤도 참). */
        /** 바닐라 getMapColor: 지도에서 이 상태의 색(위치와 무관 — 바닐라 1.21.1 은 상태마다 한 번 계산해 둔 값). 서버 블록 표의 지도 색이다. */
        public net.minecraft.world.level.material.MapColor getMapColor(net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos) {
            return net.minecraft.world.level.material.MapColor.furnaceByCol(org.domi.furnace.compat.nms.NmsBlockStates.table().mapColor()[furnaceStateId()]);
        }

        public boolean hasBlockEntity() { return owner instanceof net.minecraft.world.level.block.EntityBlock; }

        private byte requiresTool;   // 0 = 아직 모름, 1 = 아니오, 2 = 예(경합해도 결과가 같다)

        /** 바닐라 requiresCorrectToolForDrops: 맞는 도구가 아니면 드롭이 없는 블록인가(돌·광석 …). 고정 표 — 아무 스레드. */
        public boolean requiresCorrectToolForDrops() {
            byte t = requiresTool;
            if (t == 0) requiresTool = t = org.domi.furnace.compat.nms.NmsChunks.bridge().stateRequiresCorrectTool(furnaceStateId) ? (byte) 2 : (byte) 1;
            return t == 2;
        }

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

        /** 바닐라 isSolid(): 상태가 만들어질 때 정해진 legacySolid(힘줌 속성이나 충돌 모양 크기) — 서버 상태 표의 같은 값. 플러그인(Dynmap 등)이 부른다. */
        public boolean isSolid() { return (flagBits() & org.domi.furnace.api.internal.BlockStateInternals.LEGACY_SOLID) != 0; }

        public boolean canOcclude() { return (flagBits() & org.domi.furnace.api.internal.BlockStateInternals.CAN_OCCLUDE) != 0; }

        /** 바닐라 캐시 solidRender: 가리는 블록이고 가림 모양이 꽉 참. */
        public boolean isSolidRender(net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos) {
            return (flagBits() & org.domi.furnace.api.internal.BlockStateInternals.SOLID_RENDER) != 0;
        }

        /** 바닐라: 블록 클래스의 isSignalSource 재정의(레드스톤 가루·횃불·레버·단추 …). */
        public boolean isSignalSource() { return (flagBits() & org.domi.furnace.api.internal.BlockStateInternals.SIGNAL_SOURCE) != 0; }

        /** 바닐라 캐시값: 빛 가림에 모양을 쓰는가(반 블록·계단 등 — 서버 빛 표와 같은 값). */
        public boolean useShapeForLightOcclusion() { return (flagBits() & org.domi.furnace.api.internal.BlockStateInternals.USE_SHAPE_FOR_LIGHT) != 0; }

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

        /** 바닐라 canBeReplaced(ctx): 이 문맥으로 이 칸에 놓을 때 덮어쓸 수 있는가(서버 블록 동작 — {@code NmsPlacement}). */
        public boolean canBeReplaced(net.minecraft.world.item.context.BlockPlaceContext ctx) {
            return org.domi.furnace.compat.nms.NmsPlacement.canBeReplaced(asState(), ctx);
        }

        /** 바닐라 canSurvive(level, pos): 그 자리에서 버티는가(받침·벽 …). 레벨 객체의 가상 메서드로 읽는다({@code NmsPlacement}). */
        public boolean canSurvive(net.minecraft.world.level.LevelReader level, net.minecraft.core.BlockPos pos) {
            return org.domi.furnace.compat.nms.NmsPlacement.canSurvive(asState(), level, pos);
        }

        /** 바닐라 hasPostProcess(level, pos): 마그마 블록·갈색·빨간 버섯만 참(Properties.hasPostProcess(Blocks::always) — 위치를 보지 않는다). 고정 표 — 아무 스레드. */
        public boolean hasPostProcess(net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos) {
            return (flagBits() & org.domi.furnace.api.internal.BlockStateInternals.POST_PROCESS) != 0;
        }

        /** 바닐라: 비교기가 이 블록의 신호를 읽는가(상자·화로·책장 받침대 …). 고정 표 — 아무 스레드. */
        public boolean hasAnalogOutputSignal() { return (flagBits() & org.domi.furnace.api.internal.BlockStateInternals.ANALOG_OUTPUT) != 0; }

        // ---------------------------------------------------------- 쪼갠 setBlock 조각(T-204) — 서버 블록 동작을 그 칸의 주인 region 에서({@code NmsEffects})

        /** 바닐라 onPlace: 이 블록이 놓였을 때의 반응(레드스톤 선의 이웃 알림, 유체 틱 예약 …). 다른 스레드면 주인 작업으로. */
        public void onPlace(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, BlockState oldState, boolean moved) {
            org.domi.furnace.compat.nms.NmsEffects.run(level, pos, org.domi.furnace.compat.nms.NmsEffects.ON_PLACE, furnaceStateId, oldState.furnaceStateId(),
                    moved ? 1 : 0, 0L, "BlockState#onPlace");
        }

        /** 바닐라 handleNeighborChanged: 이 블록의 neighborChanged 를 직접(갱신기·BlockPhysicsEvent 를 거치지 않는다 — 바닐라와 같다). */
        public void handleNeighborChanged(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, Block sourceBlock,
                                          net.minecraft.core.BlockPos sourcePos, boolean moved) {
            org.domi.furnace.compat.nms.NmsEffects.run(level, pos, org.domi.furnace.compat.nms.NmsEffects.NEIGHBOR_CHANGED, furnaceStateId,
                    sourceBlock.defaultBlockState().furnaceStateId(), moved ? 1 : 0, sourcePos.asLong(), "BlockState#handleNeighborChanged");
        }

        /** 바닐라 updateNeighbourShapes(level, pos, flags): 남은 깊이 512. */
        public final void updateNeighbourShapes(net.minecraft.world.level.LevelAccessor level, net.minecraft.core.BlockPos pos, int flags) {
            updateNeighbourShapes(level, pos, flags, 512);
        }

        /** 바닐라 updateNeighbourShapes: 6방향(서·동·북·남·아래·위) 이웃에 모양 갱신(updateShape → 바뀌면 updateOrDestroy). */
        public final void updateNeighbourShapes(net.minecraft.world.level.LevelAccessor level, net.minecraft.core.BlockPos pos, int flags, int maxUpdateDepth) {
            org.domi.furnace.compat.nms.NmsEffects.run(level, pos, org.domi.furnace.compat.nms.NmsEffects.NEIGHBOUR_SHAPES, furnaceStateId, flags,
                    maxUpdateDepth, 0L, "BlockState#updateNeighbourShapes");
        }

        public final void updateIndirectNeighbourShapes(net.minecraft.world.level.LevelAccessor level, net.minecraft.core.BlockPos pos, int flags) {
            updateIndirectNeighbourShapes(level, pos, flags, 512);
        }

        /** 바닐라 updateIndirectNeighbourShapes: 레드스톤 선만 대각선 이웃 모양을 고친다(그 밖 블록은 아무것도 안 한다). */
        public void updateIndirectNeighbourShapes(net.minecraft.world.level.LevelAccessor level, net.minecraft.core.BlockPos pos, int flags, int maxUpdateDepth) {
            org.domi.furnace.compat.nms.NmsEffects.run(level, pos, org.domi.furnace.compat.nms.NmsEffects.INDIRECT_SHAPES, furnaceStateId, flags,
                    maxUpdateDepth, 0L, "BlockState#updateIndirectNeighbourShapes");
        }

        /**
         * 바닐라 triggerEvent(level, pos, 종류, 값): 블록 이벤트 처리(음 블록·피스톤·상자·종 …) — 처리했으면 true. 값이 있어 다른 스레드는 주인을 기다리거나
         * (틱 스레드가 아닐 때, 최대 10초) ThreadOwnershipException(region·전역 스레드, {@code NmsOwner}). 청크가 로드돼 있지 않으면 IllegalStateException.
         */
        public boolean triggerEvent(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, int type, int data) {
            return org.domi.furnace.compat.nms.NmsEffects.call(level, pos, org.domi.furnace.compat.nms.NmsEffects.TRIGGER_EVENT, furnaceStateId, type, data,
                    "BlockState#triggerEvent") != 0;
        }
    }
}
