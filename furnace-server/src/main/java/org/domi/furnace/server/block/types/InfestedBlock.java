// 바닐라 InfestedBlock: 좀벌레가 숨은 블록(host 블록 모양). 실크 터치 없이 부수면(doTileDrops) 좀벌레가 나온다.
package org.domi.furnace.server.block.types;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.Prop;

public class InfestedBlock extends BlockBehavior {

    /** host 블록 → 숨은 블록. */
    private static final Map<Integer, Integer> BLOCK_BY_HOST = new ConcurrentHashMap<>();

    protected final int host;

    public InfestedBlock(int block) {
        super(block);
        this.host = B.id(Blocks.get().params(block).get("host").getAsString());
        BLOCK_BY_HOST.put(host, block);
    }

    public int host() { return host; }

    public static boolean isCompatibleHostBlock(int state) { return BLOCK_BY_HOST.containsKey(B.block(state)); }

    @Override
    public void spawnAfterBreak(int state, org.domi.furnace.server.block.BlockLevel level, org.domi.furnace.server.block.Pos pos,
                                org.domi.furnace.server.entity.ItemStack tool, boolean dropExperience) {
        super.spawnAfterBreak(state, level, pos, tool, dropExperience);
        if (level.gameRule("doTileDrops") && !org.domi.furnace.server.item.ItemTools.hasEnchantmentTag(tool, "minecraft:prevents_infested_spawns")) {
            level.spawnSilverfishAt(pos);   // 칸 가운데에 좀벌레를 만들고 spawnAnim(엔티티 이벤트 20) — 레벨 난수는 쓰지 않는다
        }
    }

    /** infestedStateByHost: 같은 속성 값을 옮긴 숨은 블록 상태. */
    public static int infestedStateByHost(int hostState) { return copyProps(hostState, B.defaultState(BLOCK_BY_HOST.get(B.block(hostState)))); }

    /** hostStateByInfested: 같은 속성 값을 옮긴 host 상태. */
    public int hostStateByInfested(int infestedState) { return copyProps(infestedState, B.defaultState(host)); }

    private static int copyProps(int from, int to) {
        for (Prop p : B.BLOCKS.propsOf(B.block(from))) {
            if (p.has(to)) to = p.with(to, p.valueName(from));
        }
        return to;
    }
}
