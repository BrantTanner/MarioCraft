package dev.mariocraft.nativebridge;

import java.util.*;

/** Boundary of a union of axis-aligned collision boxes, in native units. */
public final class BoxSurfaces {
    private BoxSurfaces() {}
    private static long cell(int u,int v) { return ((long)u<<32)^(v&0xffffffffL); }
    private static List<int[]> merge(List<int[]> input,boolean vertical) {
        int a=vertical?1:0,b=vertical?0:1;
        input.sort((l,r)->{ int c=Integer.compare(l[b],r[b]); if(c==0)c=Integer.compare(l[b+2],r[b+2]); if(c==0)c=Integer.compare(l[a],r[a]); return c; });
        List<int[]> result=new ArrayList<>();
        for(int[] r:input) {
            int[] previous=result.isEmpty()?null:result.get(result.size()-1);
            if(previous!=null&&previous[b]==r[b]&&previous[b+2]==r[b+2]&&previous[a+2]>=r[a]) previous[a+2]=Math.max(previous[a+2],r[a+2]);
            else result.add(r.clone());
        }
        return result;
    }
    public static Sm64Library.Surface[] build(List<int[]> boxes) {
        List<int[]> triangles=new ArrayList<>();
        Map<String,List<int[]>> faces=new LinkedHashMap<>();
        List<Map<Integer,Map<Long,List<int[]>>>> planes=new ArrayList<>();
        for(int axis=0;axis<3;axis++) {
            int u=(axis+1)%3,v=(axis+2)%3;
            TreeMap<Integer,Map<Long,List<int[]>>> index=new TreeMap<>();
            for(int[] b:boxes) { index.put(b[axis],new HashMap<>()); index.put(b[axis+3],new HashMap<>()); }
            for(int[] b:boxes) for(Map<Long,List<int[]>> at:index.subMap(b[axis],true,b[axis+3],true).values())
                for(int i=Math.floorDiv(b[u],100);i<=Math.floorDiv(b[u+3]-1,100);i++) for(int j=Math.floorDiv(b[v],100);j<=Math.floorDiv(b[v+3]-1,100);j++)
                    at.computeIfAbsent(cell(i,j),k->new ArrayList<>()).add(b);
            planes.add(index);
        }
        // Index potential occluders by face plane. Only touching/overlapping boxes
        // can cover a face; subtract their rectangles rather than keeping voxel seams.
        for(int[] b:boxes) for(int axis=0;axis<3;axis++) for(int side=0;side<2;side++) {
            int u=(axis+1)%3,v=(axis+2)%3,plane=b[axis+side*3];
            List<int[]> pieces=new ArrayList<>(); pieces.add(new int[]{b[u],b[v],b[u+3],b[v+3]});
            Map<Long,List<int[]>> at=planes.get(axis).get(plane);
            Collection<int[]> candidates;
            int minU=Math.floorDiv(b[u],100),maxU=Math.floorDiv(b[u+3]-1,100),minV=Math.floorDiv(b[v],100),maxV=Math.floorDiv(b[v+3]-1,100);
            if(minU==maxU&&minV==maxV) candidates=at.get(cell(minU,minV));
            else {
                Set<int[]> gathered=new LinkedHashSet<>();
                for(int i=minU;i<=maxU;i++) for(int j=minV;j<=maxV;j++) { List<int[]> bucket=at.get(cell(i,j)); if(bucket!=null)gathered.addAll(bucket); }
                candidates=gathered;
            }
            if(candidates==null) candidates=Collections.emptyList();
            for(int[] other:candidates) {
                if(other==b || other[u]>=b[u+3] || other[u+3]<=b[u] || other[v]>=b[v+3] || other[v+3]<=b[v] || (side==0 ? other[axis]>=plane || other[axis+3]<plane : other[axis]>plane || other[axis+3]<=plane)) continue;
                List<int[]> next=new ArrayList<>();
                for(int[] r:pieces) {
                    int loU=Math.max(r[0],other[u]),loV=Math.max(r[1],other[v]);
                    int hiU=Math.min(r[2],other[u+3]),hiV=Math.min(r[3],other[v+3]);
                    if(loU>=hiU || loV>=hiV) { next.add(r); continue; }
                    if(r[0]<loU) next.add(new int[]{r[0],r[1],loU,r[3]});
                    if(hiU<r[2]) next.add(new int[]{hiU,r[1],r[2],r[3]});
                    if(r[1]<loV) next.add(new int[]{loU,r[1],hiU,loV});
                    if(hiV<r[3]) next.add(new int[]{loU,hiV,hiU,r[3]});
                }
                pieces=next; if(pieces.isEmpty()) break;
            }
            faces.computeIfAbsent(axis+":"+side+":"+plane,k->new ArrayList<>()).addAll(pieces);
        }
        for(Map.Entry<String,List<int[]>> face:faces.entrySet()) {
            String[] key=face.getKey().split(":"); int axis=Integer.parseInt(key[0]),side=Integer.parseInt(key[1]),plane=Integer.parseInt(key[2]);
            int u=(axis+1)%3,v=(axis+2)%3;
            List<int[]> rectangles=face.getValue(); int oldSize;
            do { oldSize=rectangles.size(); rectangles=merge(merge(rectangles,false),true); } while(rectangles.size()<oldSize);
            for(int[] r:rectangles) {
                int[][] points=new int[4][3];
                for(int i=0;i<4;i++) { points[i][axis]=plane; points[i][u]=(i==1||i==2)?r[2]:r[0]; points[i][v]=i>=2?r[3]:r[1]; }
                int[] order=side==1?new int[]{0,1,2,0,2,3}:new int[]{0,2,1,0,3,2};
                for(int t=0;t<2;t++) { int[] triangle=new int[9]; for(int i=0;i<3;i++) System.arraycopy(points[order[t*3+i]],0,triangle,i*3,3); triangles.add(triangle); }
            }
        }
        if(triangles.isEmpty()) return new Sm64Library.Surface[0];
        Sm64Library.Surface[] result=(Sm64Library.Surface[])new Sm64Library.Surface().toArray(triangles.size());
        for(int i=0;i<result.length;i++) result[i].vertices=triangles.get(i);
        return result;
    }
}
