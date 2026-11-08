package org.domi.furnace.compat.nms;

/**
 * 덫({@link NmsTrap})이 던지는 링크 오류(H7a 검토 반영). JVM 이 shim 없이 냈을 종류와 같은 클래스의 하위라서
 * {@code catch (NoSuchMethodError | LinkageError e)} 로 하는 기능 감지가 바닐라 Paper 에서처럼 대체 경로로 간다.
 * 메시지에는 대상·이유·플러그인·호출 위치를 싣는다. JVM 대응물이 없는 거절(정의 거부·strict·{@code connection.send})은 {@link UnsupportedNmsCall}.
 */
public sealed interface UnsupportedNmsLink permits UnsupportedNmsLink.Method, UnsupportedNmsLink.Field, UnsupportedNmsLink.Klass {

    /** 덫 종류: 클래스 없음 → {@link NoClassDefFoundError}. */
    int CLASS = 0;
    /** 메서드·생성자 없음 → {@link NoSuchMethodError}. */
    int METHOD = 1;
    /** 필드 없음 → {@link NoSuchFieldError}. */
    int FIELD = 2;

    /** 대상 심볼(점 이름). */
    String target();

    static Error of(int kind, String target, String message) {
        return switch (kind) {
            case CLASS -> new Klass(target, message);
            case FIELD -> new Field(target, message);
            default -> new Method(target, message);
        };
    }

    final class Method extends NoSuchMethodError implements UnsupportedNmsLink {
        private final String target;

        Method(String target, String message) {
            super(message);
            this.target = target;
        }

        @Override
        public String target() { return target; }
    }

    final class Field extends NoSuchFieldError implements UnsupportedNmsLink {
        private final String target;

        Field(String target, String message) {
            super(message);
            this.target = target;
        }

        @Override
        public String target() { return target; }
    }

    final class Klass extends NoClassDefFoundError implements UnsupportedNmsLink {
        private final String target;

        Klass(String target, String message) {
            super(message);
            this.target = target;
        }

        @Override
        public String target() { return target; }
    }
}
