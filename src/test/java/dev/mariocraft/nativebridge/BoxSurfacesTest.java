package dev.mariocraft.nativebridge;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public final class BoxSurfacesTest {
    private static double area(Sm64Library.Surface[] mesh) {
        double total=0;
        for(Sm64Library.Surface s:mesh) { int[] p=s.vertices; double ax=p[3]-p[0],ay=p[4]-p[1],az=p[5]-p[2],bx=p[6]-p[0],by=p[7]-p[1],bz=p[8]-p[2]; total+=Math.sqrt(Math.pow(ay*bz-az*by,2)+Math.pow(az*bx-ax*bz,2)+Math.pow(ax*by-ay*bx,2))/2; }
        return total;
    }
    @Test public void adjacentVoxelsHaveNoInternalFace() {
        Sm64Library.Surface[] mesh=BoxSurfaces.build(Arrays.asList(new int[]{0,0,0,100,100,100},new int[]{100,0,0,200,100,100}));
        assertEquals(100000,area(mesh),.001);
        for(Sm64Library.Surface s:mesh) assertFalse(s.vertices[0]==100&&s.vertices[3]==100&&s.vertices[6]==100);
    }
    @Test public void partialContactSubtractsOnlyCoveredRectangle() {
        assertEquals(75000,area(BoxSurfaces.build(Arrays.asList(new int[]{0,0,0,100,100,100},new int[]{100,0,0,150,50,100}))),.001);
    }
    @Test public void normalsPointOutward() {
        for(Sm64Library.Surface s:BoxSurfaces.build(Collections.singletonList(new int[]{-100,-100,-100,100,100,100}))) {
            int[] p=s.vertices;
            double nx=(p[4]-p[1])*(p[8]-p[2])-(p[5]-p[2])*(p[7]-p[1]);
            double ny=(p[5]-p[2])*(p[6]-p[0])-(p[3]-p[0])*(p[8]-p[2]);
            double nz=(p[3]-p[0])*(p[7]-p[1])-(p[4]-p[1])*(p[6]-p[0]);
            assertTrue(nx*p[0]+ny*p[1]+nz*p[2]>0);
        }
    }
    @Test public void emptyMeshIsValid() { assertEquals(0,BoxSurfaces.build(Collections.emptyList()).length); }
}
