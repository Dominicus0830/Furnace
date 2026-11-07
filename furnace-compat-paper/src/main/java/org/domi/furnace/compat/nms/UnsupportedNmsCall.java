package org.domi.furnace.compat.nms;

import org.domi.furnace.compat.paper.UnsupportedFeature;

/**
 * NMS·CraftBukkit 호환 표면에 모양은 있지만 Furnace 가 같은 결과를 낼 수 없는 호출(설계 H5 의 등급 B 밖·C).
 * 값을 지어내거나 조용히 무시하지 않고 던진다. 메시지는 대상과 이유, 있으면 대체 Furnace API 를 알려 준다.
 */
public class UnsupportedNmsCall extends UnsupportedFeature {

    public UnsupportedNmsCall(String target, String why) { super("NMS " + target, why); }
}
