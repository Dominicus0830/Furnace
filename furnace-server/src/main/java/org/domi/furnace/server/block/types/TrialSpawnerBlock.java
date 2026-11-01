// 바닐라 TrialSpawnerBlock: 시련 생성기. 상태 전이·몹 생성·보상은 모두 블록 엔티티(TrialSpawnerBlockEntity) 동작이다.
// 블록 상태: trial_spawner_state(6종), ominous. 틱은 블록 엔티티 틱(서버)만 — 클라이언트 쪽 입자/소리는 클라이언트 몫.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.Pos;

public class TrialSpawnerBlock extends BaseEntityBlock {

    public TrialSpawnerBlock(int block) { super(block); }

    @Override
    public org.domi.furnace.server.block.entity.BlockEntity newBlockEntity(Pos pos, int state) {
        return new org.domi.furnace.server.block.entity.TrialSpawnerBlockEntity(pos, state);
    }
}
