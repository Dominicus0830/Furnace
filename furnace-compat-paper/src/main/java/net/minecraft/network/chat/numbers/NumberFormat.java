package net.minecraft.network.chat.numbers;

import net.minecraft.network.chat.MutableComponent;

/**
 * 점수 숫자 형식(Mojang 이름 shim, easy-nms E4c-2). 바닐라 구현은 {@link BlankFormat}·{@link StyledFormat}·{@link FixedFormat} 셋이다.
 * 형식 종류(type()·NumberFormatType)와 코덱은 DataFixerUpper·네트워크 코덱 shim 이 없어 두지 않았다 — 패킷으로 보낼 때는 이 셋만 쓴다.
 */
public interface NumberFormat {

    MutableComponent format(int number);
}
