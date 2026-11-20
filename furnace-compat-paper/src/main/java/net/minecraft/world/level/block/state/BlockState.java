package net.minecraft.world.level.block.state;

import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * 블록 상태 하나(Mojang 이름 shim, N0). 상태 번호마다 객체가 하나라 바닐라처럼 {@code ==} 로 비교된다
 * ({@code Block.stateById}·{@code setValue}·{@code defaultBlockState} 모두 같은 표에서 꺼낸다).
 */
public class BlockState extends BlockBehaviour.BlockStateBase {

    /**
     * 바닐라: {@code {Name: "minecraft:…", Properties: {…}}} 꼴 코덱({@link org.domi.furnace.compat.nms.NmsCodecs#blockState}). 서버 제공 라이브러리 묶음
     * datafixerupper 가 이 클래스가 처음 쓰일 때 보이지 않으면 null 이다(그 묶음을 쓰는 플러그인이 없는 서버 — 부를 플러그인도 없다).
     */
    public static final com.mojang.serialization.Codec<BlockState> CODEC = createCodec();

    private static com.mojang.serialization.Codec<BlockState> createCodec() {
        if (!org.domi.furnace.compat.nms.NmsDfu.visible()) return null;
        try {
            return org.domi.furnace.compat.nms.NmsCodecs.blockState();
        } catch (LinkageError e) {
            org.domi.furnace.compat.nms.NmsDfu.markBroken(e);
            return null;
        }
    }

    /** 호환 모듈 전용 — 블록 shim 이 상태 표를 만들 때만 쓴다. */
    public BlockState(Block owner, Map<Property<?>, Comparable<?>> values, int furnaceStateId) { super(owner, values, furnaceStateId); }

    @Override
    protected BlockState asState() { return this; }
}
