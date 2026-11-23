package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

/**
 * Mth 순수 계산(2026-11-24 영역 9)을 바닐라 1.21.1 서버 jar 가 낸 값과 줄 단위로 견준다.
 * 기대값 {@code mth-vanilla-values.txt} 는 바닐라 jar 에 같은 입력을 먹여 얻은 숫자뿐이다(코드·이름 없음). 난수는 시드 42 의 호출 순서까지 같다.
 */
class MthVanillaValuesTest {

  private final List<String> out = new ArrayList<>();

  private void p(String n, Object o) { out.add(n + "=" + o); }

  @Test
  void valuesMatchVanilla() throws IOException {
    run();
    List<String> expected;
    try (var in = MthVanillaValuesTest.class.getResourceAsStream("/mth-vanilla-values.txt")) {
      expected = Arrays.asList(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R"));
    }
    assertEquals(expected.size(), out.size());
    for (int i = 0; i < expected.size(); i++) assertEquals(expected.get(i), out.get(i), "줄 " + (i + 1));
  }

  private void run() {
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
