// 바닐라 JigsawBlockEntity: 직소 블록의 이름·대상·풀·연결 종류·최종 상태·선택/배치 우선순위. 앞(front) 방향 칸에서 "생성"으로 구조물을 펼친다.
// 저장 키: name, target, pool, final_state, joint, placement_priority, selection_priority. 클라이언트에는 saveCustomOnly 를 보낸다.
// Furnace 차이: 생성은 이 칸 region 의 로드된 청크와 노이즈 월드 생성기(구조물 엔진)가 있는 월드에서만 한다.
package org.domi.furnace.server.block.entity;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.LiveGrid;
import org.domi.furnace.server.world.RegionLevel;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.domi.furnace.server.world.gen.structure.JigsawGenerate;

public class JigsawBlockEntity extends BlockEntity {

    /** JigsawBlockEntity.JointType — 패킷에는 이름 문자열로 실린다. */
    public enum JointType {
        ROLLABLE("rollable"), ALIGNED("aligned");

        public final String serializedName;

        JointType(String n) { this.serializedName = n; }

        /** byName: 모르는 이름이면 null. */
        public static JointType byName(String s) {
            for (JointType j : values()) if (j.serializedName.equals(s)) return j;
            return null;
        }
    }

    private static final Prop ORIENTATION = B.prop("orientation");
    private static final int JIGSAW = B.id("jigsaw");
    private static final ResourceId EMPTY = new ResourceId("minecraft", "empty");

    private ResourceId name = EMPTY;
    private ResourceId target = EMPTY;
    private ResourceId pool = EMPTY;
    private JointType joint = JointType.ROLLABLE;
    private String finalState = "minecraft:air";
    private int placementPriority;
    private int selectionPriority;

    public JigsawBlockEntity(Pos pos, int state) { super("minecraft:jigsaw", pos, state); }

    @Override
    public boolean isValid(int state) { return B.block(state) == JIGSAW; }

    @Override
    public boolean hasUpdatePacket() { return true; }

    @Override
    public NbtCompound updateTag() { return saveCustomOnly(); }

    @Override
    protected void saveAdditional(NbtCompound tag) {
        tag.putString("name", name.toString());
        tag.putString("target", target.toString());
        tag.putString("pool", pool.toString());
        tag.putString("final_state", finalState);
        tag.putString("joint", joint.serializedName);
        tag.putInt("placement_priority", placementPriority);
        tag.putInt("selection_priority", selectionPriority);
    }

    @Override
    public void load(NbtCompound tag) {
        name = parse(tag.getString("name"));
        target = parse(tag.getString("target"));
        pool = parse(tag.getString("pool"));
        finalState = tag.getString("final_state");
        JointType j = JointType.byName(tag.getString("joint"));
        joint = j != null ? j : defaultJoint();
        placementPriority = tag.getInt("placement_priority");
        selectionPriority = tag.getInt("selection_priority");
    }

    /** ResourceLocation.parse — 바닐라는 잘못된 값이면 예외로 로드가 깨진다. 여기서는 비어 있는 이름으로 대신한다. */
    private static ResourceId parse(String s) {
        ResourceId id = ResourceId.tryParse(s);
        return id != null ? id : EMPTY;
    }

    /** 앞 방향이 수평이면 ALIGNED, 위·아래면 ROLLABLE. */
    private JointType defaultJoint() { return Dir.horizontal(frontOf(blockState)) ? JointType.ALIGNED : JointType.ROLLABLE; }

    /** orientation 의 앞 방향("north_up" → north). */
    public static int frontOf(int state) {
        String v = ORIENTATION.valueName(state);
        return Dir.byName(v.substring(0, v.indexOf('_')));
    }

    public ResourceId name() { return name; }

    public ResourceId target() { return target; }

    public ResourceId pool() { return pool; }

    public String finalState() { return finalState; }

    public JointType joint() { return joint; }

    public int placementPriority() { return placementPriority; }

    public int selectionPriority() { return selectionPriority; }

    public void setName(ResourceId n) { this.name = n; }

    public void setTarget(ResourceId t) { this.target = t; }

    public void setPool(ResourceId p) { this.pool = p; }

    public void setFinalState(String s) { this.finalState = s; }

    public void setJoint(JointType j) { this.joint = j; }

    public void setPlacementPriority(int p) { this.placementPriority = p; }

    public void setSelectionPriority(int p) { this.selectionPriority = p; }

    /**
     * generate(level, levels, keepJigsaws): 앞 방향 칸에서 풀·대상으로 구조물을 펼친다. 실패해도 바닐라처럼 아무 말이 없다.
     * 이 region 이 조각이 닿는 청크를 다 갖고 있지 않거나 노이즈 월드 생성기가 없으면 아무것도 놓지 않는다.
     */
    public boolean generate(BlockLevel level, int levels, boolean keepJigsaws) {
        if (!(level instanceof RegionLevel rl)) return false;
        NoiseWorldSource gen = NoiseWorldSource.of(rl.world().worldSource());
        if (gen == null) return false;
        Pos at = pos.relative(frontOf(level.getBlockState(pos)));
        try {
            return JigsawGenerate.generate(rl, gen, pool.toString(), target.toString(), levels, at.x(), at.y(), at.z(), keepJigsaws);
        } catch (LiveGrid.GridException e) {
            return false;
        }
    }
}
