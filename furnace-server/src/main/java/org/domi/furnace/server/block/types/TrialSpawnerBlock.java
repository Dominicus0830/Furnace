// 바닐라 TrialSpawnerBlock: 시련 생성기. 상태 전이·몹 생성·보상은 모두 블록 엔티티(TrialSpawner) 동작이다.
package org.domi.furnace.server.block.types;

public class TrialSpawnerBlock extends BaseEntityBlock {

    public TrialSpawnerBlock(int block) { super(block); }

    // TODO(블록 엔티티): TrialSpawnerBlockEntity 틱(trial_spawner_state 전이). TODO(아이템): 툴팁(spawn_data)은 클라 쪽.
}
