package org.domi.furnace.compat.paper;

/**
 * Furnace 호환 어댑터가 아직 지원하지 않는 Bukkit 기능을 부를 때 던진다. 조용히 틀린 값을 주는 대신 무엇이 빠졌는지 알려 준다.
 * (이벤트 처리기 안에서 던져지면 그 처리기만 실패하고 로그에 이 메시지가 남는다.)
 */
public class UnsupportedFeature extends UnsupportedOperationException {

    public UnsupportedFeature(String feature, String why) {
        super("Furnace 호환 어댑터가 아직 지원하지 않는다: " + feature + " — " + why);
    }
}
