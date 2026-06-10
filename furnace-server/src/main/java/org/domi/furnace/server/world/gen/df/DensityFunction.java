// 밀도 함수 인터페이스: 바닐라 DensityFunction 비트정확 포팅 (Codec/registry 제외)
package org.domi.furnace.server.world.gen.df;

public interface DensityFunction {

    double compute(FunctionContext context);

    default void fillArray(double[] array, ContextProvider provider) {
        provider.fillAllDirectly(array, this);
    }

    double minValue();

    double maxValue();

    // ---------------------------------------------------------------
    // Convenience instance methods matching vanilla DensityFunction
    // ---------------------------------------------------------------

    default DensityFunction clamp(double lo, double hi) {
        return new DensityFunctions.Clamp(this, lo, hi);
    }

    default DensityFunction abs() {
        return DensityFunctions.map(this, DensityFunctions.Mapped.Type.ABS);
    }

    default DensityFunction square() {
        return DensityFunctions.map(this, DensityFunctions.Mapped.Type.SQUARE);
    }

    default DensityFunction cube() {
        return DensityFunctions.map(this, DensityFunctions.Mapped.Type.CUBE);
    }

    default DensityFunction halfNegative() {
        return DensityFunctions.map(this, DensityFunctions.Mapped.Type.HALF_NEGATIVE);
    }

    default DensityFunction quarterNegative() {
        return DensityFunctions.map(this, DensityFunctions.Mapped.Type.QUARTER_NEGATIVE);
    }

    default DensityFunction squeeze() {
        return DensityFunctions.map(this, DensityFunctions.Mapped.Type.SQUEEZE);
    }

    // ---------------------------------------------------------------
    // Nested types
    // ---------------------------------------------------------------

    interface FunctionContext {
        int blockX();
        int blockY();
        int blockZ();
    }

    record SinglePointContext(int blockX, int blockY, int blockZ) implements FunctionContext {}

    interface ContextProvider {
        FunctionContext forIndex(int arrayIndex);
        void fillAllDirectly(double[] values, DensityFunction fn);
    }

    /** Simple function: fillArray loops compute, mapAll visits self. */
    interface SimpleFunction extends DensityFunction {
        @Override
        default void fillArray(double[] array, ContextProvider provider) {
            provider.fillAllDirectly(array, this);
        }
    }

    // ---------------------------------------------------------------
    // mapAll: 트리 전체를 방문자로 재변환 (NoiseChunk wrap 용)
    // 기본 구현은 자기 자신을 visitor 에 그대로 통과 (leaf 노드).
    // 복합 노드는 자식을 먼저 재귀 변환한 뒤 visitor 를 적용한다.
    // ---------------------------------------------------------------

    @FunctionalInterface
    interface Visitor {
        DensityFunction apply(DensityFunction df);
    }

    /** 기본 구현: 리프 노드 — 자기 자신을 visitor 에 전달한다. */
    default DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(this);
    }
}
