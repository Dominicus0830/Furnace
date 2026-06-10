// 바닐라 net.minecraft.util.CubicSpline 비트정확 포팅: float 기반 큐빅 스플라인 보간
package org.domi.furnace.server.world.gen.df;

import java.util.ArrayList;
import java.util.List;

/**
 * Cubic spline over a generic coordinate type C.
 * I is a ToFloatFunction<C> that extracts a float coordinate from C.
 * Float arithmetic is kept to match vanilla bit-exactly.
 */
public interface CubicSpline<C, I extends ToFloatFunction<C>> extends ToFloatFunction<C> {

    String parityString();

    CubicSpline<C, I> mapAll(CoordinateVisitor<I> visitor);

    static <C, I extends ToFloatFunction<C>> CubicSpline<C, I> constant(float value) {
        return new Constant<>(value);
    }

    static <C, I extends ToFloatFunction<C>> Builder<C, I> builder(I coordinate) {
        return new Builder<>(coordinate);
    }

    // ------------------------------------------------------------------
    // CoordinateVisitor
    // ------------------------------------------------------------------

    interface CoordinateVisitor<I> {
        I visit(I coordinate);
    }

    // ------------------------------------------------------------------
    // Constant spline: always returns the same float value
    // ------------------------------------------------------------------

    record Constant<C, I extends ToFloatFunction<C>>(float value) implements CubicSpline<C, I> {
        @Override public float apply(C object) { return value; }
        @Override public String parityString() { return String.format("k=%.3f", value); }
        @Override public float minValue() { return value; }
        @Override public float maxValue() { return value; }
        @Override public CubicSpline<C, I> mapAll(CoordinateVisitor<I> visitor) { return this; }
    }

    // ------------------------------------------------------------------
    // Multipoint spline: piecewise cubic Hermite interpolation
    // ------------------------------------------------------------------

    final class Multipoint<C, I extends ToFloatFunction<C>> implements CubicSpline<C, I> {
        private final I coordinate;
        private final float[] locations;
        private final List<CubicSpline<C, I>> values;
        private final float[] derivatives;
        private final float minValue;
        private final float maxValue;

        public Multipoint(
                I coordinate,
                float[] locations,
                List<CubicSpline<C, I>> values,
                float[] derivatives,
                float minValue,
                float maxValue) {
            validateSizes(locations, values, derivatives);
            this.coordinate = coordinate;
            this.locations = locations;
            this.values = values;
            this.derivatives = derivatives;
            this.minValue = minValue;
            this.maxValue = maxValue;
        }

        /** Factory that computes min/max bounds, matching vanilla Multipoint.create(). */
        public static <C, I extends ToFloatFunction<C>> Multipoint<C, I> create(
                I coordinate,
                float[] locations,
                List<CubicSpline<C, I>> values,
                float[] derivatives) {
            validateSizes(locations, values, derivatives);
            int last = locations.length - 1;
            float f = Float.POSITIVE_INFINITY;
            float f1 = Float.NEGATIVE_INFINITY;
            float f2 = coordinate.minValue();
            float f3 = coordinate.maxValue();

            if (f2 < locations[0]) {
                float f4 = linearExtend(f2, locations, values.get(0).minValue(), derivatives, 0);
                float f5 = linearExtend(f2, locations, values.get(0).maxValue(), derivatives, 0);
                f = Math.min(f, Math.min(f4, f5));
                f1 = Math.max(f1, Math.max(f4, f5));
            }
            if (f3 > locations[last]) {
                float f4 = linearExtend(f3, locations, values.get(last).minValue(), derivatives, last);
                float f5 = linearExtend(f3, locations, values.get(last).maxValue(), derivatives, last);
                f = Math.min(f, Math.min(f4, f5));
                f1 = Math.max(f1, Math.max(f4, f5));
            }
            for (CubicSpline<C, I> s : values) {
                f = Math.min(f, s.minValue());
                f1 = Math.max(f1, s.maxValue());
            }
            for (int i1 = 0; i1 < last; i1++) {
                float f5 = locations[i1];
                float f6 = locations[i1 + 1];
                float f7 = f6 - f5;
                CubicSpline<C, I> s1 = values.get(i1);
                CubicSpline<C, I> s2 = values.get(i1 + 1);
                float f8 = s1.minValue();
                float f9 = s1.maxValue();
                float f10 = s2.minValue();
                float f11 = s2.maxValue();
                float f12 = derivatives[i1];
                float f13 = derivatives[i1 + 1];
                if (f12 != 0.0F || f13 != 0.0F) {
                    float f14 = f12 * f7;
                    float f15 = f13 * f7;
                    float min  = Math.min(f8, f10);
                    float max  = Math.max(f9, f11);
                    float f16 = f14 - f11 + f8;
                    float f17 = f14 - f10 + f9;
                    float f18 = -f15 + f10 - f9;
                    float f19 = -f15 + f11 - f8;
                    float min1 = Math.min(f16, f18);
                    float max1 = Math.max(f17, f19);
                    f = Math.min(f, min + 0.25F * min1);
                    f1 = Math.max(f1, max + 0.25F * max1);
                }
            }
            return new Multipoint<>(coordinate, locations, values, derivatives, f, f1);
        }

        private static float linearExtend(float coordinate, float[] locations, float value, float[] derivatives, int index) {
            float d = derivatives[index];
            return d == 0.0F ? value : value + d * (coordinate - locations[index]);
        }

        private static <C, I extends ToFloatFunction<C>> void validateSizes(
                float[] locations, List<CubicSpline<C, I>> values, float[] derivatives) {
            if (locations.length != values.size() || locations.length != derivatives.length) {
                throw new IllegalArgumentException(
                    "All lengths must be equal, got: " + locations.length + " " + values.size() + " " + derivatives.length);
            }
            if (locations.length == 0) {
                throw new IllegalArgumentException("Cannot create a multipoint spline with no points");
            }
        }

        @Override
        public float apply(C object) {
            float f = coordinate.apply(object);
            int i = findIntervalStart(locations, f);
            int last = locations.length - 1;
            if (i < 0) {
                return linearExtend(f, locations, values.get(0).apply(object), derivatives, 0);
            } else if (i == last) {
                return linearExtend(f, locations, values.get(last).apply(object), derivatives, last);
            } else {
                float f1 = locations[i];
                float f2 = locations[i + 1];
                float f3 = (f - f1) / (f2 - f1);
                ToFloatFunction<C> lo = (ToFloatFunction<C>) values.get(i);
                ToFloatFunction<C> hi = (ToFloatFunction<C>) values.get(i + 1);
                float f4 = derivatives[i];
                float f5 = derivatives[i + 1];
                float f6 = lo.apply(object);
                float f7 = hi.apply(object);
                float f8 = f4 * (f2 - f1) - (f7 - f6);
                float f9 = -f5 * (f2 - f1) + (f7 - f6);
                // vanilla: Mth.lerp(f3, f6, f7) + f3*(1-f3)*Mth.lerp(f3, f8, f9)
                float base = f6 + f3 * (f7 - f6);  // lerp(f3, f6, f7)
                float tang = f8 + f3 * (f9 - f8);  // lerp(f3, f8, f9)
                return base + f3 * (1.0F - f3) * tang;
            }
        }

        private static int findIntervalStart(float[] locations, float start) {
            // vanilla: Mth.binarySearch(0, locations.length, i -> start < locations[i]) - 1
            int lo = 0, hi = locations.length;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (start < locations[mid]) hi = mid;
                else lo = mid + 1;
            }
            return lo - 1;
        }

        @Override public float minValue() { return minValue; }
        @Override public float maxValue() { return maxValue; }

        @Override
        public String parityString() {
            return "Spline{coordinate=" + coordinate + ", locations=" + locationsStr() + ", values=[...]}";
        }

        private String locationsStr() {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < locations.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(String.format("%.3f", locations[i]));
            }
            return sb.append("]").toString();
        }

        @Override
        public CubicSpline<C, I> mapAll(CoordinateVisitor<I> visitor) {
            List<CubicSpline<C, I>> mapped = new ArrayList<>(values.size());
            for (CubicSpline<C, I> v : values) mapped.add(v.mapAll(visitor));
            return create(visitor.visit(coordinate), locations, mapped, derivatives);
        }

        public I coordinate() { return coordinate; }
        public float[] locations() { return locations; }
        public List<CubicSpline<C, I>> values() { return values; }
        public float[] derivatives() { return derivatives; }
    }

    // ------------------------------------------------------------------
    // Builder
    // ------------------------------------------------------------------

    final class Builder<C, I extends ToFloatFunction<C>> {
        private final I coordinate;
        private final List<Float> locations = new ArrayList<>();
        private final List<CubicSpline<C, I>> values = new ArrayList<>();
        private final List<Float> derivatives = new ArrayList<>();

        public Builder(I coordinate) {
            this.coordinate = coordinate;
        }

        public Builder<C, I> addPoint(float location, float value) {
            return addPoint(location, new Constant<>(value), 0.0F);
        }

        public Builder<C, I> addPoint(float location, float value, float derivative) {
            return addPoint(location, new Constant<>(value), derivative);
        }

        public Builder<C, I> addPoint(float location, CubicSpline<C, I> value) {
            return addPoint(location, value, 0.0F);
        }

        private Builder<C, I> addPoint(float location, CubicSpline<C, I> value, float derivative) {
            if (!locations.isEmpty() && location <= locations.get(locations.size() - 1)) {
                throw new IllegalArgumentException("Please register points in ascending order");
            }
            locations.add(location);
            values.add(value);
            derivatives.add(derivative);
            return this;
        }

        public CubicSpline<C, I> build() {
            if (locations.isEmpty()) throw new IllegalStateException("No elements added");
            float[] locs = new float[locations.size()];
            float[] ders = new float[derivatives.size()];
            for (int i = 0; i < locs.length; i++) { locs[i] = locations.get(i); ders[i] = derivatives.get(i); }
            return Multipoint.create(coordinate, locs, List.copyOf(values), ders);
        }
    }
}
