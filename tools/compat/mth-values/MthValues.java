package org.domi.furnace.tools.mth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Mth 순수 계산의 기대값 줄을 만든다. 같은 입력을 바닐라 서버 jar 의 Mth 와 Furnace 호환 계층의 Mth 에 먹여 두 출력이 같아야 한다.
 * 입력만 담고 바닐라 코드·이름은 담지 않는다(Mojang 이름의 공개 메서드 호출뿐).
 *
 * 기대값 다시 만들기(판을 올릴 때): 바닐라 jar(Mojang 이름) + 라이브러리 클래스패스로 {@code java -cp <이 클래스>:<jar>:<libs> org.domi.furnace.tools.mth.MthValues > mth-vanilla-values.txt}
 * 이 파일은 시험 소스 폴더에 함께 걸려 있어 {@code MthVanillaValuesTest} 가 같은 줄을 호환 계층 쪽에서 만들어 견준다. 자세한 절차는 README.md.
 */
public final class MthValues {
    private MthValues() {}

    private static List<String> out;

    private static void p(String name, Object value) { out.add(name + "=" + value); }

    private static void guard(String name, java.util.function.Supplier<Object> f) {
        try { p(name, f.get()); } catch (RuntimeException e) { p(name, e.getClass().getSimpleName() + ":" + e.getMessage()); }
    }

    public static void main(String[] args) { lines().forEach(System.out::println); }

    public static List<String> lines() {
        out = new ArrayList<>();
        main0();
        edges();
        return out;
    }

    private static void edges() {
        double[] ds = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0, -0.0, Double.MIN_VALUE, Double.MAX_VALUE};
        for (double a : ds) {
            for (double b : ds) {
                p("e.atan2 " + a + "," + b, Mth.atan2(a, b));
                p("e.len " + a + "," + b, Mth.length(a, b));
                p("e.nd " + a + "," + b, Mth.rotLerp(0.5, a, b));
            }
            p("e.fis " + a, Mth.fastInvSqrt(a));
            p("e.sd " + a, Mth.smoothstepDerivative(a));
            p("e.invd " + a, Mth.invSqrt(a));
            p("e.wob " + a, Mth.wobble(a));
        }
        int[] is = {Integer.MIN_VALUE, Integer.MAX_VALUE, -1, 0, 1};
        for (int a : is) {
            p("e.mm " + a, Mth.murmurHash3Mixer(a));
            p("e.seed " + a, Mth.getSeed(a, a, -a));
            p("e.seed2 " + a, Mth.getSeed(a, 0, Integer.MAX_VALUE));
            for (int b : is) {
                if (b != 0) {
                    p("e.mult " + a + "," + b, Mth.isMultipleOf(a, b));
                    guard("e.fdiv " + a + "," + b, () -> Mth.floorDiv(a, b));
                    p("e.pcd " + a + "," + b, Mth.positiveCeilDiv(a, b));
                }
                p("e.li " + a + "," + b, Mth.lerpInt(0.5F, a, b));
                p("e.ld " + a + "," + b, Mth.lerpDiscrete(0.5F, a, b));
            }
        }
        float[] hues = {0.0F, 1.0F, 1.0F / 6.0F, 0.99999F, -0.5F, 2.0F, Float.NaN};
        // hsv 범위 밖 예외는 종류만 견준다(문구는 진단용이라 Furnace 가 자기 말로 쓴다)
        for (float h : hues) {
            try { p("e.hsv " + h, Mth.hsvToArgb(h, 1.0F, 1.0F, 255)); } catch (RuntimeException e) { p("e.hsv " + h, e.getClass().getSimpleName()); }
        }
        RandomSource r = RandomSource.create(7L);
        p("e.nf same", Mth.nextFloat(r, 2.0F, 2.0F));
        p("e.nf rev", Mth.nextFloat(r, 3.0F, 1.0F));
        p("e.nd same", Mth.nextDouble(r, 2.0, 2.0));
        p("e.nd rev", Mth.nextDouble(r, 3.0, 1.0));
        p("e.ni after", Mth.nextInt(r, 0, 1000));
        guard("e.ofo bounds", () -> Mth.outFromOrigin(5, 10, 0));
        guard("e.ofo steps", () -> Mth.outFromOrigin(5, 0, 10, 0));
        p("e.ofo small", Arrays.toString(Mth.outFromOrigin(0, 0, 0).toArray()));
        p("e.q", Mth.quantize(-7.5, 4));
        p("e.col", Mth.color(1.5F, -0.5F, Float.NaN));
        p("e.uuid", Mth.createInsecureUUID(RandomSource.create(1L)));
        guard("e.gi", () -> Mth.getInt("2147483648", -9));
        guard("e.gi2", () -> Mth.getInt("", -9));
    }

    private static void main0() {
    double[] ds={-7.5,-1,0,0.3,1,2.5,100.25};
    int[] is={-17,-5,0,3,7,64};
    for(int x:is)for(int y:is){ if(y==0)continue; p("isMult"+x+","+y,Mth.isMultipleOf(x,y)); p("floorDiv"+x+","+y,Mth.floorDiv(x,y)); p("pcd"+x+","+y,Mth.positiveCeilDiv(x,y)); p("rt"+x+","+y,Mth.roundToward(x,y)); p("q"+x+","+y,Mth.quantize(x*1.5,y)); p("seed"+x+","+y,Mth.getSeed(x,y,x+y)); }
    for(int x:is) p("mm"+x,Mth.murmurHash3Mixer(x));
    for(double x:ds)for(double y:ds){ p("l2"+x+","+y,Mth.length(x,y)); p("l3"+x+","+y,Mth.length(x,y,x-y)); p("ls"+x+","+y,Mth.lengthSquared(x,y)); p("atan2"+x+","+y,Mth.atan2(x,y)); p("rotd"+x+","+y,Mth.rotLerp(0.3,x,y)); p("lerp2"+x+","+y,Mth.lerp2(0.2,0.7,x,y,y,x*2)); }
    for(double x:ds){ p("fis"+x,Mth.fastInvSqrt(x)); p("sd"+x,Mth.smoothstepDerivative(x)); p("wob"+x,Mth.wobble(x)); p("fic"+x,Mth.fastInvCubeRoot((float)x));}
    float[] fs={-190f,-10f,0f,0.5f,45f,170f,359f};
    for(float x:fs)for(float y:fs){ p("ap"+x+","+y,Mth.approach(x,y,3.5f)); p("apd"+x+","+y,Mth.approachDegrees(x,y,3.5f)); p("dda"+x+","+y,Mth.degreesDifferenceAbs(x,y)); p("rl"+x+","+y,Mth.rotLerp(0.4f,x,y)); p("rin"+x+","+y,Mth.rotateIfNecessary(x,y,20f)); p("tw"+x+","+y,Mth.triangleWave(x,y==0?1:y)); p("cat"+x+","+y,Mth.catmullrom(0.3f,x,y,x*2,y*3)); p("li"+x+","+y,Mth.lerpInt(0.3f,(int)x,(int)y)); p("ld"+x+","+y,Mth.lerpDiscrete(0.3f,(int)x,(int)y)); p("ls3"+x+","+y,Mth.lengthSquared(x,y,1f)); p("col"+x+","+y,Mth.color(Math.abs(x%1),Math.abs(y%1),0.5f)); p("hsv"+x+","+y,Mth.hsvToArgb(Math.abs(x%1),Math.abs(y%1),0.8f,255)); }
    p("bs",Mth.binarySearch(0,1000,i->i*i>=5000));
    RandomSource r=RandomSource.create(42L);
    for(int i=0;i<5;i++){ p("ni",Mth.nextInt(r,3,9)); p("nf",Mth.nextFloat(r,1f,4f)); p("nd",Mth.nextDouble(r,-2,2)); p("rb",Mth.randomBetween(r,0f,2f)); p("rbi",Mth.randomBetweenInclusive(r,5,8)); p("norm",Mth.normal(r,1f,2f)); p("uuid",Mth.createInsecureUUID(r)); }
    p("ni-eq",Mth.nextInt(r,9,3));
    p("gi",Mth.getInt("12",3)); p("gi2",Mth.getInt("x",3)); p("gi3",Mth.getInt(null,3));
    p("ofo",Arrays.toString(Mth.outFromOrigin(10,0,20).toArray())); p("ofo2",Arrays.toString(Mth.outFromOrigin(2,0,20,3).toArray())); p("ofo3",Arrays.toString(Mth.outFromOrigin(19,0,20,4).toArray())); p("ofo4",Arrays.toString(Mth.outFromOrigin(30,0,20).toArray()));
    
  }
}
