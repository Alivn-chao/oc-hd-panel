package dev.bec.hdpanel;

import java.util.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/** Bounded flood fill. Only complete, co-planar rectangles merge; no chunk is loaded by probing. */
public final class WallAssembly {
    private WallAssembly() { }
    public static int u(int meta,int x,int z) { return meta==0?x:meta==1?-z:meta==2?-x:z; }
    public static boolean rectangle(int width,int height,int count,int controllers) {
        return width>0&&width<=8&&height>0&&height<=6&&width*height==count&&controllers<=1;
    }
    public static void rebuild(PanelTile start,long now) {
        World world=start.getWorldObj();int meta=start.getBlockMetadata();
        int dx=meta==0?1:meta==2?-1:0,dz=meta==1?-1:meta==3?1:0;
        List<PanelTile> cells=new ArrayList<PanelTile>();Set<PanelTile> seen=new HashSet<PanelTile>();
        ArrayDeque<PanelTile> queue=new ArrayDeque<PanelTile>();queue.add(start);seen.add(start);
        while(!queue.isEmpty()&&cells.size()<49) {
            PanelTile cell=queue.remove();cells.add(cell);
            int[][] steps={{dx,0,dz},{-dx,0,-dz},{0,1,0},{0,-1,0}};
            for(int[] step:steps) {
                int x=cell.xCoord+step[0],y=cell.yCoord+step[1],z=cell.zCoord+step[2];
                if(!world.blockExists(x,y,z))continue;TileEntity tile=world.getTileEntity(x,y,z);
                if(tile instanceof PanelTile&&tile.getBlockMetadata()==meta&&seen.add((PanelTile)tile))queue.add((PanelTile)tile);
            }
        }
        int minU=Integer.MAX_VALUE,maxU=Integer.MIN_VALUE,minY=Integer.MAX_VALUE,maxY=Integer.MIN_VALUE,controllers=0;
        PanelTile owner=null,anchor=null;
        for(PanelTile cell:cells) {
            int u=u(meta,cell.xCoord,cell.zCoord);minU=Math.min(minU,u);maxU=Math.max(maxU,u);minY=Math.min(minY,cell.yCoord);maxY=Math.max(maxY,cell.yCoord);
            if(cell.bound()) {controllers++;owner=cell;}
        }
        for(PanelTile cell:cells)if(u(meta,cell.xCoord,cell.zCoord)==minU&&cell.yCoord==minY)anchor=cell;
        int width=maxU-minU+1,height=maxY-minY+1;
        boolean merge=queue.isEmpty()&&rectangle(width,height,cells.size(),controllers);
        if(owner==null)owner=anchor;
        for(PanelTile cell:cells) {
            cell.wallChecked=now;
            if(merge)cell.configureWall(owner,width,height,u(meta,cell.xCoord,cell.zCoord)-minU,cell.yCoord-minY);
            else cell.configureWall(cell,1,1,0,0);
        }
    }
}
