package dev.fangbm.worldphase.verification;

import dev.fangbm.worldphase.transfer.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Bounded local test setup only. Uses real blocks and LandingSafety, never changes terrain. */
final class MvpLocalWalkSetup {
    record Plan(List<Vec3> points, int inspected, int fullChunks, String reason) {}
    private final ServerPlayer player;
    private final ServerLevel level;
    private final int budget, minX, maxX, minZ, maxZ;
    private final Map<BlockPos, Boolean> safety = new HashMap<>();
    private final Set<Long> chunks = new HashSet<>();
    private int inspected;
    private final Set<BlockPos> blocked;
    private final boolean adaptive=Boolean.getBoolean("worldphase.mvp.adaptiveWalk");
    MvpLocalWalkSetup(ServerPlayer player, ServerLevel level, int budget, int minX, int maxX, int minZ, int maxZ) {
        this(player,level,budget,minX,maxX,minZ,maxZ,Set.of());
    }
    MvpLocalWalkSetup(ServerPlayer player, ServerLevel level, int budget, int minX, int maxX, int minZ, int maxZ, Set<BlockPos> blocked) {
        this.blocked=Set.copyOf(blocked);
        this.player=player;this.level=level;this.budget=budget;this.minX=minX;this.maxX=maxX;this.minZ=minZ;this.maxZ=maxZ;
    }
    private boolean safe(BlockPos p) {
        if(blocked.contains(p))return false;
        if(p.getX()<minX || p.getX()>maxX || p.getZ()<minZ || p.getZ()>maxZ || inspected>=budget) return false;
        Boolean prior=safety.get(p);if(prior!=null)return prior;
        inspected++;chunk(p.getX(),p.getZ());
        boolean value=LandingSafety.isSafe(player,level,Vec3.atBottomCenterOf(p),TransferPolicy.BOUNDED_LANDING);
        safety.put(p.immutable(),value);return value;
    }
    private void chunk(int x,int z) {
        long key=net.minecraft.world.level.ChunkPos.pack(x>>4,z>>4);
        if(chunks.add(key)) level.getChunk(x>>4,z>>4);
    }
    private int height(int x,int z) {if(x<minX || x>maxX || z<minZ || z>maxZ)return Integer.MIN_VALUE;chunk(x,z);return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);}
    private List<Vec3> path(Map<BlockPos,BlockPos> previous,BlockPos end) {
        var result=new ArrayList<Vec3>();
        for(BlockPos at=end;at!=null;at=previous.get(at))result.add(Vec3.atBottomCenterOf(at));
        Collections.reverse(result);return result;
    }
    Plan cave() {
        // Start at an actual outdoor/covered boundary, rather than an isolated sealed air pocket.
        var mouths=new ArrayList<BlockPos>();
        for(int x=minX+2;x<=maxX-2;x++) for(int z=minZ+2;z<=maxZ-2;z++) {
            int y=height(x,z);var outside=new BlockPos(x,y,z);
            for(int[] d:new int[][]{{0,-1},{1,0},{0,1},{-1,0}}) {
                var inside=outside.offset(d[0],0,d[1]);
                if(height(inside.getX(),inside.getZ())<y+3 || !safe(outside) || !safe(inside))continue;
                boolean nearby=false;for(var m:mouths)if(Math.abs(m.getX()-x)<8 && Math.abs(m.getZ()-z)<8){nearby=true;break;}
                if(nearby)break;
                mouths.add(outside);break;
            }
        }
        System.out.println("WPH015 natural mouth boundaries "+level.dimension().identifier()+" count="+mouths.size()+" first="+mouths.subList(0,Math.min(16,mouths.size())));
        for(var start:mouths.subList(0,Math.min(16,mouths.size()))) {
            var queue=new ArrayDeque<BlockPos>();var previous=new LinkedHashMap<BlockPos,BlockPos>();queue.add(start);previous.put(start,null);int visited=0;
            while(!queue.isEmpty() && visited++<500 && inspected<budget) {
                var p=queue.remove();int depth=height(p.getX(),p.getZ())-p.getY();boolean roof=false;
                for(int dy=2;dy<=depth;dy++)if(level.getBlockState(p.above(dy)).is(BlockTags.BASE_STONE_OVERWORLD)){roof=true;break;}
                var route=path(previous,p);
                if(depth>=6 && roof && route.size()>=6 && route.size()<=96)return new Plan(route,inspected,chunks.size(),"NATURAL_MOUTH_TO_COVERED_PASSAGE");
                for(int[] d:new int[][]{{0,-1},{1,0},{0,1},{-1,0}})for(int dy:new int[]{0,1,-1}){
                    var next=p.offset(d[0],dy,d[1]);
                    if(height(next.getX(),next.getZ())>next.getY()+1 && !previous.containsKey(next) && safe(next)){previous.put(next,p);queue.add(next);break;}
                }
            }
        }

        // Shallow covered air pockets first; a stone roof excludes tree-canopy false caves.
        var starts=new ArrayList<BlockPos>();
        outer:for(int depth=6;depth<=32;depth+=2) for(int x=minX+2;x<=maxX-2;x+=8) for(int z=minZ+2;z<=maxZ-2;z+=8) {
            int top=height(x,z),y=top-depth;
            var p=new BlockPos(x,y,z);
            if(!level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir())continue;
            boolean roof=false;
            for(int dy=2;dy<=depth;dy++) if(level.getBlockState(p.above(dy)).is(BlockTags.BASE_STONE_OVERWORLD)){roof=true;break;}
            if(roof && safe(p)) {starts.add(p);if(starts.size()>=16)break outer;}
        }
        System.out.println("WPH015 cave discovery "+level.dimension().identifier()+" coveredSeeds="+starts+" inspected="+inspected);
        for(var start:starts) {
            var queue=new ArrayDeque<BlockPos>();var previous=new LinkedHashMap<BlockPos,BlockPos>();
            queue.add(start);previous.put(start,null);int visited=0;
            while(!queue.isEmpty() && visited++<1000 && inspected<budget) {
                var p=queue.remove();
                if(height(p.getX(),p.getZ())<=p.getY()) {
                    var route=path(previous,p);Collections.reverse(route);
                    if(route.size()>=6 && route.size()<=96) return new Plan(route,inspected,chunks.size(),"NATURAL_MOUTH_TO_COVERED_PASSAGE");
                }
                for(int[] d:new int[][]{{0,-1},{1,0},{0,1},{-1,0}}) for(int dy:new int[]{0,1,-1}) {
                    var next=p.offset(d[0],dy,d[1]);
                    if(!previous.containsKey(next) && safe(next)) {previous.put(next,p);queue.add(next);break;}
                }
            }
        }
        return new Plan(List.of(),inspected,chunks.size(),"NO_CONNECTED_CAVE_WITHIN_FROZEN_BUDGET");
    }
    Plan north(Vec3 from,int goalZ) {
        var start=BlockPos.containing(from);var queue=new ArrayDeque<BlockPos>();
        var previous=new LinkedHashMap<BlockPos,BlockPos>();queue.add(start);previous.put(start,null);
        BlockPos best=start;
        while(!queue.isEmpty() && inspected<budget) {
            var p=adaptive?queue.removeLast():queue.removeFirst();if(p.getZ()<best.getZ())best=p;
            if(p.getZ()<=goalZ) {best=p;break;}
            for(int[] d:adaptive?new int[][]{{0,1},{-1,0},{1,0},{0,-1}}:new int[][]{{0,-1},{1,0},{-1,0},{0,1}}) for(int dy:new int[]{0,1,-1}) {
                var next=p.offset(d[0],dy,d[1]);
                if(!previous.containsKey(next) && safe(next)) {previous.put(next,p);queue.add(next);break;}
            }
        }
        var route=path(previous,best);
        if(adaptive && route.size()>2){
            var compressed=new ArrayList<Vec3>();compressed.add(route.getFirst());
            for(int i=1;i<route.size()-1;i++){
                var a=route.get(i-1);var b=route.get(i);var c=route.get(i+1);
                if(a.y!=b.y || b.y!=c.y || b.x-a.x!=c.x-b.x || b.z-a.z!=c.z-b.z)compressed.add(b);
            }
            compressed.add(route.getLast());route=compressed;
        }
        if(route.size()>256)route=new ArrayList<>(route.subList(0,256));
        return new Plan(route,inspected,chunks.size(),best.getZ()<=goalZ?"SEGMENT_CONNECTED":"SEGMENT_OBSTRUCTED");
    }
}
