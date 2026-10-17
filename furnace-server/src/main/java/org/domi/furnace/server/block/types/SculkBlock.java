// 바닐라 SculkBlock: 스컬크(경험치 1). 스컬크 촉매의 퍼짐(SculkSpreader)이 이 블록 위에서 전하를 써서 감지체·비명체를 키운다.
package org.domi.furnace.server.block.types;

public class SculkBlock extends DropExperienceBlock {

    public SculkBlock(int block) { super(block, 1, 1); }

    // TODO(블록 엔티티): SculkBehaviour.attemptUseCharge — 스컬크 촉매(SculkSpreader) 이식 때 함께(전하가 있고 1/chargeDecayRate 로,
    //   멀리 있고 위가 비었으면 1/11 비명체(can_summon)·아니면 감지체를 위에 키운다; worldgen 근사는 SculkPatchFeature).
}
