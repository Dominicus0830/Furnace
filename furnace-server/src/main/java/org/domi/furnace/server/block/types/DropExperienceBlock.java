// 바닐라 DropExperienceBlock: 부수면(실크 터치가 아니면) 경험치를 내는 블록(광석 등). 경험치 범위는 생성 인자 experience.
package org.domi.furnace.server.block.types;

import com.google.gson.JsonElement;
import org.domi.furnace.server.block.Blocks;

public class DropExperienceBlock extends org.domi.furnace.server.block.BlockBehavior {

    /** IntProvider(상수 또는 uniform min..max) — 경험치. */
    protected final int xpMin, xpMax;

    public DropExperienceBlock(int block) {
        super(block);
        JsonElement e = Blocks.get().params(block).get("experience");
        if (e == null || e.isJsonPrimitive()) {
            xpMin = xpMax = e == null ? 0 : e.getAsInt();
        } else {
            xpMin = e.getAsJsonObject().get("min_inclusive").getAsInt();
            xpMax = e.getAsJsonObject().get("max_inclusive").getAsInt();
        }
    }

    /** 경험치를 코드에서 정하는 하위 종류(스컬크 = 1). */
    protected DropExperienceBlock(int block, int xpMin, int xpMax) {
        super(block);
        this.xpMin = xpMin;
        this.xpMax = xpMax;
    }

    // TODO(3-4b): spawnAfterBreak — dropExperience 이면 tryDropExperience(level, pos, tool, experience).
}
