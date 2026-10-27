package dev.mariocraft.nativebridge;
import java.nio.file.*;
import java.util.*;

/** Synthetic boxes only, exercising the same union-boundary adapter as Minecraft. */
public final class TraversalSmoke {
    private static void require(boolean c,String m) { if(!c) throw new AssertionError(m); }
    public static void main(String[] args) throws Exception {
        for(String recipe:new String[]{"wall-kick","ledge","ceiling","crawl","seam","corner","partial","drop","update","insert","fast-wall-1","fast-wall-13","fast-wall-25"}) {
            List<int[]> boxes=new ArrayList<>(); boxes.add(new int[]{-2000,-100,-2000,2000,0,2000});
            if(recipe.startsWith("fast-wall")) boxes.add(new int[]{-1000,0,1000,1000,1000,1000+Integer.parseInt(recipe.substring(10))});
            if(recipe.equals("wall-kick")) boxes.add(new int[]{-1000,0,400,1000,1000,700});
            if(recipe.equals("ledge")) boxes.add(new int[]{-1000,0,400,1000,300,1000});
            if(recipe.equals("ceiling")) boxes.add(new int[]{-1000,250,-1000,1000,350,1000});
            if(recipe.equals("crawl")) boxes.add(new int[]{-1000,120,250,1000,220,1000});
            if(recipe.equals("corner")) { boxes.add(new int[]{-1000,0,400,1000,1000,700}); boxes.add(new int[]{100,0,-1000,400,1000,400}); }
            if(recipe.equals("partial")) boxes.add(new int[]{-1000,0,400,1000,50,1000});
            if(recipe.equals("drop")) boxes.add(new int[]{-1000,0,-1000,1000,200,400});
            if(recipe.equals("update")) boxes.add(new int[]{-1000,0,400,1000,1000,700});
            if(recipe.equals("seam")) { boxes.clear(); for(int z=-10;z<20;z++) boxes.add(new int[]{-1000,-100,z*100,1000,0,z*100+100}); }
            try(MarioSession session=new MarioSession(Paths.get(System.getProperty("mariocraft.native")),MarioSession.discoverRom())) {
                SurfaceBoundary boundary=new SurfaceBoundary(BoxSurfaces.build(boxes));
                Sm64Library.Surface[] submitted=boundary.at(0,10,0); session.surfaces(submitted); session.create(0,recipe.equals("drop")?210:10,0);
                Sm64Library.Inputs input=new Sm64Library.Inputs(); input.camLookZ=1;
                Set<Integer> actions=new LinkedHashSet<>(); int previous=0; float peak=0; boolean clear=true,walkOff=false;
                for(int t=0;t<180;t++) {
                    input.stickY=t>=40 && t<125?-1:0;
                    input.buttonA=(byte)((t>=55 && t<59 || recipe.equals("wall-kick") && (previous==0x000008A7 || previous==0x010208B6))?1:0);
                    if(recipe.equals("ledge")) input.buttonA=(byte)(t>=45 && t<65?1:0);
                    if(recipe.equals("crawl")) { input.buttonA=0; input.buttonZ=(byte)(t>=30&&t<125?1:0); }
                    if(recipe.equals("seam") || recipe.equals("corner") || recipe.equals("drop") || recipe.equals("update") || recipe.equals("insert")) input.buttonA=0;
                    if(recipe.equals("update") && t==100) { boxes.remove(boxes.size()-1); boundary=new SurfaceBoundary(BoxSurfaces.build(boxes)); }
                    if(recipe.startsWith("fast-wall")) { input.buttonZ=(byte)(t>=65&&t<69?1:0);input.buttonA=(byte)(t>=68&&t<78?1:0); }
                    float[] viewPosition=session.state.position;
                    if(recipe.equals("insert")&&t==65) {
                        int x=Math.round(session.state.position[0]),z=Math.round(session.state.position[2]);
                        boxes.add(new int[]{x-50,0,z-50,x+50,100,z+50});
                        float[] recovered=VoxelRecovery.find(session.state.position[0],session.state.position[1],session.state.position[2],boxes);
                        require(recovered!=null,"Inserted box has no bounded exit");
                        session.position(recovered[0],recovered[1],recovered[2]);
                        viewPosition=recovered;
                        boundary=new SurfaceBoundary(BoxSurfaces.build(boxes));
                    }
                    Sm64Library.Surface[] visible=boundary.at(viewPosition[0],viewPosition[1],viewPosition[2]);
                    if(visible!=submitted) {session.surfaces(visible);submitted=visible;}
                    session.tick(input);
                    for(float p:session.state.position) require(Float.isFinite(p),recipe+" finite pose");
                    if(t>=40 && recipe.equals("drop") && session.state.action==0x0100088C) walkOff=true;
                    if(t>=40 && recipe.equals("seam")) require(Math.abs(session.state.position[1])<.01, "Voxel seam caused vertical discontinuity");
                    if(recipe.startsWith("fast-wall")) require(session.state.position[2]<=950.01,"Fast long jump tunneled through thin wall");
                    if(recipe.equals("corner") || recipe.equals("wall-kick") || recipe.equals("update") && t<100) clear &=session.state.position[2]<=350.01;
                    previous=session.state.action; peak=Math.max(peak,session.state.position[1]);
                    if(actions.add(previous)) System.out.println(recipe+" t="+t+" action="+Integer.toHexString(previous)+" pos="+Arrays.toString(session.state.position)+" velocity="+Arrays.toString(session.state.velocity));
                }
                if(recipe.startsWith("fast-wall")) require(actions.contains(0x03000888),"Fast wall recipe missing long jump");
                if(recipe.equals("wall-kick")) require(actions.contains(0x03000886),"Fresh A wall kick missing");
                if(recipe.equals("ledge")) require(actions.contains(0x0800034B)&&actions.contains(0x54C)&&actions.contains(0x54D),"Ledge grab/climb missing");
                if(recipe.equals("ceiling")) require(peak<=90.01&&peak>80,"Ceiling response clearance");
                if(recipe.equals("crawl")) require(actions.contains(0x04008448)&&session.state.position[2]<250,"Native crouch clearance gate");
                if(recipe.equals("partial")) require(actions.contains(0x04000440)&&peak>=50,"Partial box floor");
                if(recipe.equals("drop")) require(walkOff&&Math.abs(session.state.position[1])<.01,"Ground to air and landing");
                if(recipe.equals("update")) require(session.state.position[2]>700,"Removed wall was not released");
                require(clear,"Wall/corner tunneling");
                System.out.println("TRAVERSAL "+recipe+" peak="+peak+" end="+Arrays.toString(session.state.position));
            }
        }
    }
}
