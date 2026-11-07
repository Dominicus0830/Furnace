// 바닐라 Display 의 transformation NBT 해석(Transformation.EXTENDED_CODEC): 이동·왼쪽 회전·크기·오른쪽 회전 복합 태그
// (회전은 [x,y,z,w] 목록 또는 {angle, axis}) 또는 16개 숫자 행렬(행 우선).
// 행렬은 바닐라 MatrixUtil.svdDecompose(자코비 고유값 반복 5회 + 기븐스 QR)를 JOML 부동소수 순서 그대로 옮겨 이동·회전·크기·회전으로 쪼갠다.
package org.domi.furnace.server.entity;

import java.util.List;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;

public final class DisplayTransform {

    private DisplayTransform() {}

    /** 이동(3)·왼쪽 회전(4, x y z w)·크기(3)·오른쪽 회전(4). */
    public record Decomposed(float[] translation, float[] leftRotation, float[] scale, float[] rightRotation) {}

    /** NBT 값을 해석한다. 바닐라 코덱이 실패하면(필드 누락·길이·형식) null — 호출한 쪽은 transformation 을 건드리지 않는다. */
    public static Decomposed decode(Object tag) {
        if (tag instanceof NbtCompound c) {
            float[] t = vec(c.tags().get("translation"), 3);
            float[] l = rotation(c.tags().get("left_rotation"));
            float[] s = vec(c.tags().get("scale"), 3);
            float[] r = rotation(c.tags().get("right_rotation"));
            if (t == null || l == null || s == null || r == null) return null;
            return new Decomposed(t, l, s, r);
        }
        float[] m = vec(tag, 16);
        return m == null ? null : fromMatrix(m);
    }

    private static List<Object> items(Object o) {
        if (o instanceof NbtList l) return l.items();
        if (o instanceof int[] a) return java.util.Arrays.stream(a).boxed().map(i -> (Object) i).toList();
        if (o instanceof long[] a) return java.util.Arrays.stream(a).boxed().map(i -> (Object) i).toList();
        return null;
    }

    /** 숫자 n 개의 목록(NbtOps.getList: 리스트·정수/long 배열). */
    private static float[] vec(Object o, int n) {
        List<Object> l = items(o);
        if (l == null || l.size() != n) return null;
        float[] r = new float[n];
        for (int i = 0; i < n; i++) {
            if (!(l.get(i) instanceof Number x)) return null;
            r[i] = x.floatValue();
        }
        return r;
    }

    /** ExtraCodecs.QUATERNIONF: 성분 목록 [x,y,z,w] 먼저, 아니면 {angle, axis[3]}. */
    private static float[] rotation(Object o) {
        float[] q = vec(o, 4);
        if (q != null) return normalize(q);
        if (!(o instanceof NbtCompound c) || !(c.tags().get("angle") instanceof Number angle)) return null;
        float[] axis = vec(c.tags().get("axis"), 3);
        return axis == null ? null : fromAxisAngle(axis[0], axis[1], axis[2], angle.floatValue());
    }

    /** 바닐라 axw.e(List): new Quaternionf(x, y, z, w).normalize(). */
    private static float[] normalize(float[] q) {
        float inv = invsqrt(fma(q[0], q[0], fma(q[1], q[1], fma(q[2], q[2], q[3] * q[3]))));
        return new float[] {q[0] * inv, q[1] * inv, q[2] * inv, q[3] * inv};
    }

    /**
     * 바닐라는 new Quaternionf(new AxisAngle4f(angle, axis)): AxisAngle4f 가 각도를 [0, 2π) 로 정리하고,
     * Quaternionf(AxisAngle4f) 가 축을 정규화하지 않은 채 sin(각/2)를 곱한다(w 는 cosFromSin).
     */
    static float[] fromAxisAngle(float ax, float ay, float az, float angle) {
        double twoPi = Math.PI + Math.PI;
        float a = (float) ((angle < 0.0 ? twoPi + angle % twoPi : (double) angle) % twoPi);
        float half = a * 0.5F;
        float sin = (float) Math.sin(half);
        return new float[] {ax * sin, ay * sin, az * sin, (float) cosFromSin((double) sin, (double) half)};
    }

    /** JOML Math.fma: joml.useMathFma 가 꺼진 기본값에서는 두 번 반올림하는 a*b+c (진짜 FMA 가 아니다). */
    private static float fma(float a, float b, float c) { return a * b + c; }

    private static double invsqrt(double r) { return 1.0 / Math.sqrt(r); }

    private static float invsqrt(float r) { return 1.0F / (float) Math.sqrt(r); }

    /** JOML Math.cosFromSin(기본: fastmath 꺼짐). */
    private static double cosFromSin(double sin, double angle) {
        double cos = Math.sqrt(1.0 - sin * sin);
        double a = angle + Math.PI / 2.0;
        double b = a - (int) (a / (Math.PI * 2.0)) * (Math.PI * 2.0);
        if (b < 0.0) b = Math.PI * 2.0 + b;
        return b >= Math.PI ? -cos : cos;
    }

    // ---------------------------------------------------------------- 행렬 → 이동·회전·크기·회전

    /** m: 행 우선 16개(JOML setRowColumn(i>>2, i&3)). */
    static Decomposed fromMatrix(float[] m) {
        float f = 1.0F / m[15];
        // JOML 필드 mCR 은 열 C 행 R: 행 우선 목록의 [R*4 + C].
        M3 mat = new M3(m[0], m[4], m[8], m[1], m[5], m[9], m[2], m[6], m[10]);
        mat.scale(f);
        Q[] qs = new Q[2];
        float[] sigma = svd(mat, qs);
        return new Decomposed(new float[] {m[3] * f, m[7] * f, m[11] * f}, qs[0].toArray(), sigma, qs[1].toArray());
    }

    /** 바닐라 MatrixUtil.svdDecompose: 왼쪽 회전 u, 특이값, 오른쪽 회전(고유 벡터 쿼터니언의 켤레). mat 은 덮어쓴다. */
    static float[] svd(M3 mat, Q[] out) {
        M3 ata = new M3(mat);
        ata.transpose();
        ata.mul(mat);
        Q q = eigenvalueJacobi(ata, 5);
        float f = ata.m00, g = ata.m11;
        boolean bl = (double) f < 1.0E-6, bl2 = (double) g < 1.0E-6;
        M3 m7 = ata;
        M3 m8 = mat.rotate(q);
        Q u = new Q();
        Q tmp = new Q();
        Givens gp = bl ? qr(m8.m11, -m8.m10) : qr(m8.m00, m8.m01);
        Q q12 = gp.aroundZ(tmp);
        M3 m13 = gp.aroundZ(m7);
        u.mul(q12);
        m13.transpose();
        m13.mul(m8);
        m7 = m8;
        gp = (bl ? qr(m13.m22, -m13.m20) : qr(m13.m00, m13.m02)).inverse();
        Q q14 = gp.aroundY(tmp);
        M3 m15 = gp.aroundY(m7);
        u.mul(q14);
        m15.transpose();
        m15.mul(m13);
        m7 = m13;
        gp = bl2 ? qr(m15.m22, -m15.m21) : qr(m15.m11, m15.m12);
        Q q16 = gp.aroundX(tmp);
        M3 m17 = gp.aroundX(m7);
        u.mul(q16);
        m17.transpose();
        m17.mul(m15);
        out[0] = u;
        out[1] = q.conjugate();
        return new float[] {m17.m00, m17.m11, m17.m22};
    }

    private static final float G = 3.0F + 2.0F * (float) Math.sqrt(2.0F);
    private static final Givens PI_4 = Givens.fromPositiveAngle(0.7853982F);

    private static Givens approx(float a11, float a12, float a22) {
        float f = 2.0F * (a11 - a22);
        return G * a12 * a12 < f * f ? Givens.fromUnnormalized(a12, f) : PI_4;
    }

    private static Givens qr(float f, float g) {
        float h = (float) Math.hypot(f, g);
        float i = h > 1.0E-6F ? g : 0.0F;
        float j = Math.abs(f) + Math.max(h, 1.0E-6F);
        if (f < 0.0F) {
            float k = i;
            i = j;
            j = k;
        }
        return Givens.fromUnnormalized(i, j);
    }

    private static void similarity(M3 mat, M3 temp) {
        mat.mul(temp);
        temp.transpose();
        temp.mul(mat);
        mat.set(temp);
    }

    private static void stepJacobi(M3 mat, M3 temp, Q q1, Q q2) {
        if (mat.m01 * mat.m01 + mat.m10 * mat.m10 > 1.0E-6F) {
            Givens g = approx(mat.m00, 0.5F * (mat.m01 + mat.m10), mat.m11);
            Q q = g.aroundZ(q1);
            q2.mul(q);
            g.aroundZ(temp);
            similarity(mat, temp);
        }
        if (mat.m02 * mat.m02 + mat.m20 * mat.m20 > 1.0E-6F) {
            Givens g = approx(mat.m00, 0.5F * (mat.m02 + mat.m20), mat.m22).inverse();
            Q q = g.aroundY(q1);
            q2.mul(q);
            g.aroundY(temp);
            similarity(mat, temp);
        }
        if (mat.m12 * mat.m12 + mat.m21 * mat.m21 > 1.0E-6F) {
            Givens g = approx(mat.m11, 0.5F * (mat.m12 + mat.m21), mat.m22);
            Q q = g.aroundX(q1);
            q2.mul(q);
            g.aroundX(temp);
            similarity(mat, temp);
        }
    }

    private static Q eigenvalueJacobi(M3 mat, int iterations) {
        Q q = new Q();
        M3 temp = new M3();
        Q q1 = new Q();
        for (int i = 0; i < iterations; i++) stepJacobi(mat, temp, q1, q);
        q.normalize();
        return q;
    }

    /** 바닐라 GivensParameters(sin, cos). */
    record Givens(float sin, float cos) {
        static Givens fromUnnormalized(float sin, float cos) {
            float n = invsqrt(sin * sin + cos * cos);
            return new Givens(n * sin, n * cos);
        }

        static Givens fromPositiveAngle(float angle) {
            float s = (float) Math.sin(angle / 2.0F);
            float c = (float) cosFromSin((double) s, (double) (angle / 2.0F));
            return new Givens(s, c);
        }

        Givens inverse() { return new Givens(-sin, cos); }

        Q aroundX(Q q) { return q.set(sin, 0, 0, cos); }

        Q aroundY(Q q) { return q.set(0, sin, 0, cos); }

        Q aroundZ(Q q) { return q.set(0, 0, sin, cos); }

        private float c2() { return cos * cos - sin * sin; }

        private float s2() { return 2.0F * sin * cos; }

        M3 aroundX(M3 m) {
            m.m01 = 0; m.m02 = 0; m.m10 = 0; m.m20 = 0;
            float f = c2(), g = s2();
            m.m11 = f; m.m22 = f; m.m12 = g; m.m21 = -g; m.m00 = 1;
            return m;
        }

        M3 aroundY(M3 m) {
            m.m01 = 0; m.m10 = 0; m.m12 = 0; m.m21 = 0;
            float f = c2(), g = s2();
            m.m00 = f; m.m22 = f; m.m02 = -g; m.m20 = g; m.m11 = 1;
            return m;
        }

        M3 aroundZ(M3 m) {
            m.m02 = 0; m.m12 = 0; m.m20 = 0; m.m21 = 0;
            float f = c2(), g = s2();
            m.m00 = f; m.m11 = f; m.m01 = g; m.m10 = -g; m.m22 = 1;
            return m;
        }
    }

    /** JOML Quaternionf 의 필요한 연산. */
    static final class Q {
        float x, y, z, w = 1;

        Q set(float x, float y, float z, float w) {
            this.x = x; this.y = y; this.z = z; this.w = w;
            return this;
        }

        /** this = this × q. */
        Q mul(Q q) {
            return set(fma(w, q.x, fma(x, q.w, fma(y, q.z, -z * q.y))),
                    fma(w, q.y, fma(-x, q.z, fma(y, q.w, z * q.x))),
                    fma(w, q.z, fma(x, q.y, fma(-y, q.x, z * q.w))),
                    fma(w, q.w, fma(-x, q.x, fma(-y, q.y, -z * q.z))));
        }

        Q normalize() {
            float inv = invsqrt(fma(x, x, fma(y, y, fma(z, z, w * w))));
            return set(x * inv, y * inv, z * inv, w * inv);
        }

        Q conjugate() { return set(-x, -y, -z, w); }

        float[] toArray() { return new float[] {x, y, z, w}; }
    }

    /** JOML Matrix3f(필드 mCR = 열 C 행 R). */
    static final class M3 {
        float m00 = 1, m01, m02, m10, m11 = 1, m12, m20, m21, m22 = 1;

        M3() {}

        M3(float m00, float m01, float m02, float m10, float m11, float m12, float m20, float m21, float m22) {
            this.m00 = m00; this.m01 = m01; this.m02 = m02;
            this.m10 = m10; this.m11 = m11; this.m12 = m12;
            this.m20 = m20; this.m21 = m21; this.m22 = m22;
        }

        M3(M3 o) { this(o.m00, o.m01, o.m02, o.m10, o.m11, o.m12, o.m20, o.m21, o.m22); }

        void set(M3 o) {
            m00 = o.m00; m01 = o.m01; m02 = o.m02;
            m10 = o.m10; m11 = o.m11; m12 = o.m12;
            m20 = o.m20; m21 = o.m21; m22 = o.m22;
        }

        M3 scale(float s) {
            m00 *= s; m01 *= s; m02 *= s; m10 *= s; m11 *= s; m12 *= s; m20 *= s; m21 *= s; m22 *= s;
            return this;
        }

        M3 transpose() {
            float t = m01; m01 = m10; m10 = t;
            t = m02; m02 = m20; m20 = t;
            t = m12; m12 = m21; m21 = t;
            return this;
        }

        /** this = this × r. */
        M3 mul(M3 r) {
            float nm00 = fma(m00, r.m00, fma(m10, r.m01, m20 * r.m02));
            float nm01 = fma(m01, r.m00, fma(m11, r.m01, m21 * r.m02));
            float nm02 = fma(m02, r.m00, fma(m12, r.m01, m22 * r.m02));
            float nm10 = fma(m00, r.m10, fma(m10, r.m11, m20 * r.m12));
            float nm11 = fma(m01, r.m10, fma(m11, r.m11, m21 * r.m12));
            float nm12 = fma(m02, r.m10, fma(m12, r.m11, m22 * r.m12));
            float nm20 = fma(m00, r.m20, fma(m10, r.m21, m20 * r.m22));
            float nm21 = fma(m01, r.m20, fma(m11, r.m21, m21 * r.m22));
            float nm22 = fma(m02, r.m20, fma(m12, r.m21, m22 * r.m22));
            m00 = nm00; m01 = nm01; m02 = nm02; m10 = nm10; m11 = nm11; m12 = nm12; m20 = nm20; m21 = nm21; m22 = nm22;
            return this;
        }

        /** this = this × R(q). */
        M3 rotate(Q q) {
            float w2 = q.w * q.w, x2 = q.x * q.x, y2 = q.y * q.y, z2 = q.z * q.z;
            float zw = q.z * q.w, dzw = zw + zw, xy = q.x * q.y, dxy = xy + xy, xz = q.x * q.z, dxz = xz + xz;
            float yw = q.y * q.w, dyw = yw + yw, yz = q.y * q.z, dyz = yz + yz, xw = q.x * q.w, dxw = xw + xw;
            M3 r = new M3(w2 + x2 - z2 - y2, dxy + dzw, dxz - dyw,
                    -dzw + dxy, y2 - z2 + w2 - x2, dyz + dxw,
                    dyw + dxz, dyz - dxw, z2 - y2 - x2 + w2);
            return mul(r);
        }
    }
}
