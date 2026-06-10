// DensityFunction 비-노이즈 노드 단위 검증 테스트
package org.domi.furnace.server.world.gen.df;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class DensityFunctionsTest {
    static DensityFunction.FunctionContext at(int x,int y,int z){ return new DensityFunction.SinglePointContext(x,y,z); }
    @Test void constant(){ assertEquals(3.0, DensityFunctions.constant(3.0).compute(at(0,0,0)), 0.0); }
    @Test void addMul(){
        var f = DensityFunctions.mul(DensityFunctions.constant(2.0),
                  DensityFunctions.add(DensityFunctions.constant(1.0), DensityFunctions.constant(4.0)));
        assertEquals(10.0, f.compute(at(0,0,0)), 0.0);
    }
    @Test void minMaxClamp(){
        assertEquals(1.0, DensityFunctions.min(DensityFunctions.constant(1.0), DensityFunctions.constant(2.0)).compute(at(0,0,0)), 0.0);
        assertEquals(2.0, DensityFunctions.max(DensityFunctions.constant(1.0), DensityFunctions.constant(2.0)).compute(at(0,0,0)), 0.0);
        assertEquals(5.0, DensityFunctions.constant(9.0).clamp(0.0, 5.0).compute(at(0,0,0)), 0.0);
    }
    @Test void yGradient(){
        var f = DensityFunctions.yClampedGradient(0, 100, 0.0, 1.0);
        assertEquals(0.5, f.compute(at(0,50,0)), 1e-12);
        assertEquals(0.0, f.compute(at(0,-10,0)), 0.0);  // clamped below
        assertEquals(1.0, f.compute(at(0,200,0)), 0.0);  // clamped above
    }
    @Test void absSquare(){
        assertEquals(4.0, DensityFunctions.constant(-2.0).square().compute(at(0,0,0)), 0.0);
        assertEquals(2.0, DensityFunctions.constant(-2.0).abs().compute(at(0,0,0)), 0.0);
        assertEquals(9.0, DensityFunctions.constant(3.0).square().compute(at(0,0,0)), 0.0);
    }
}
