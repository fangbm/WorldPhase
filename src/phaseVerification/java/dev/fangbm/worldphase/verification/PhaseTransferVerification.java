package dev.fangbm.worldphase.verification;

import com.mojang.authlib.GameProfile;
import dev.fangbm.worldphase.phase.*;
import dev.fangbm.worldphase.transfer.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;

/** Real ServerPlayer and Vanilla teleport handler; only outbound networking is discarded. */
final class PhaseTransferVerification {
    static void verify(MinecraftServer server) {
        try {
            var levels = new EnumMap<PhaseId, ServerLevel>(PhaseId.class);
            for (PhaseId phase : PhaseId.values()) {
                var level = server.getLevel(PhaseWorldResolver.levelKey(phase));
                require(level != null && level.getSeed() == 123456789L, "Four live same-seed Phase worlds");
                levels.put(phase, level);
            }
            var smooth = levels.get(PhaseId.SMOOTH);
            var ridge = levels.get(PhaseId.RIDGE);
            var cell = levels.get(PhaseId.CELL);
            var fracture = levels.get(PhaseId.FRACTURE);
            // Controlled dev-only body space high above terrain, straddling negative chunk edges.
            var position = new Vec3(-16.125, 220.375, 15.9375);
            for (int x = -18; x <= -15; x++) for (int z = 14; z <= 17; z++) {
                for (var level : levels.values()) {
                    level.setBlock(new BlockPos(x,219,z), Blocks.STONE.defaultBlockState(), 3);
                    for (int y = 220; y <= 223; y++) level.setBlock(new BlockPos(x,y,z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
            var profile = new GameProfile(UUID.fromString("a3b83e90-28f9-4a94-b89c-8c46af811009"), "WPH009Probe");
            var player = createPlayer(server, smooth, profile, position);
            smooth.addNewPlayer(player);
            var inventory = player.getInventory();
            inventory.setItem(0, new ItemStack(Items.DIAMOND, 7));
            inventory.setItem(10, new ItemStack(Items.IRON_INGOT, 23));
            var originalItems = new ArrayList<ItemStack>();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) originalItems.add(inventory.getItem(slot).copy());
            var service = new PhaseTransferService(server);
            var report = new StringBuilder("WPH009: seed=123456789; real ServerPlayer/Vanilla teleport; simulated outbound transport\n");
            var savedMarker = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("wph010-saved-probe.txt");
            var markerBlock = new BlockPos(-17,209,15);
            if (Files.exists(savedMarker)) {
                require(cell.getBlockState(markerBlock).is(Blocks.GOLD_BLOCK), "Saved target collision fixture survives restart");
                report.append("WPH010 RELOAD PASS persisted GOLD_BLOCK target fixture\n");
            } else report.append("WPH010 fresh world; restart this saved world to verify persistence\n");
            for (PhaseId phase : new PhaseId[]{PhaseId.RIDGE, PhaseId.CELL}) {
                int before = levels.get(phase).getChunkSource().getLoadedChunksCount();
                require(service.transfer(player,phase,TransferPolicy.EXACT_POSITION) == TransferResult.SUCCESS, "Smooth -> Ridge -> Cell");
                assertPlayer(player, levels.get(phase), position, profile, inventory, originalItems);
                int after = levels.get(phase).getChunkSource().getLoadedChunksCount();
                require(after > 0, "Service acquired real destination FULL chunks");
                report.append("SUCCESS ").append(phase).append(" exact x/y/z/yaw/pitch, UUID/object/inventory retained; loaded chunk holders ")
                        .append(before).append(" -> ").append(after).append('\n');
            }
            require(service.transfer(player,PhaseId.CELL,TransferPolicy.EXACT_POSITION) == TransferResult.SAME_PHASE, "Same Phase no-op");
            // Block the original y in the destination, while y+3 stays empty. No correction in #9.
            fracture.setBlock(BlockPos.containing(position), Blocks.STONE.defaultBlockState(), 3);
            require(service.transfer(player,PhaseId.FRACTURE,TransferPolicy.EXACT_POSITION) == TransferResult.NO_SAFE_POSITION, "Blocked exact y rejection");
            assertPlayer(player, cell, position, profile, inventory, originalItems);
            fracture.setBlock(BlockPos.containing(position), Blocks.AIR.defaultBlockState(), 3);
            // An actual overlapping entity must also reject; do not just validate stone/air.
            // Armor stands can overlap players under Vanilla's collision policy. A boat has
            // an actual blocking entity shape, so it exercises noCollision's entity branch.
            var blocker = net.minecraft.world.entity.EntityType.OAK_BOAT.create(fracture, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            require(blocker != null, "Boat fixture creation");
            blocker.snapTo(position.x,position.y,position.z,0,0);
            require(fracture.addFreshEntity(blocker), "Boat fixture insertion");
            require(!fracture.noCollision(player,player.getBoundingBox()), "Boat fixture must actually collide");
            require(service.transfer(player,PhaseId.FRACTURE,TransferPolicy.EXACT_POSITION) == TransferResult.NO_SAFE_POSITION, "Occupied entity rejection");
            assertPlayer(player, cell, position, profile, inventory, originalItems);
            blocker.discard();
            double oldSize = ridge.getWorldBorder().getSize();
            ridge.getWorldBorder().setSize(4);
            try {
                require(service.transfer(player,PhaseId.RIDGE,TransferPolicy.EXACT_POSITION) == TransferResult.OUT_OF_WORLD, "World border rejection");
                assertPlayer(player, cell, position, profile, inventory, originalItems);
            } finally { ridge.getWorldBorder().setSize(oldSize); }
            // Exercise the actual NeoForge travel veto, which returns null before native mutation.
            Consumer<EntityTravelToDimensionEvent> veto = event -> {
                if (event.getEntity() == player && event.getDimension() == PhaseWorldResolver.levelKey(PhaseId.RIDGE)) event.setCanceled(true);
            };
            NeoForge.EVENT_BUS.addListener(veto);
            try {
                require(service.transfer(player,PhaseId.RIDGE,TransferPolicy.EXACT_POSITION) == TransferResult.ERROR, "Native travel veto");
                assertPlayer(player, cell, position, profile, inventory, originalItems);
            } finally { NeoForge.EVENT_BUS.unregister(veto); }
            report.append("PASS same Phase, solid block, entity occupancy, border, actual NeoForge travel veto; source state/items unchanged\n");
            for (int i = 0; i < 12; i++) {
                PhaseId target = i % 2 == 0 ? PhaseId.RIDGE : PhaseId.CELL;
                require(service.transfer(player,target,TransferPolicy.EXACT_POSITION) == TransferResult.SUCCESS, "Repeated transfer " + i);
                assertPlayer(player, levels.get(target), position, profile, inventory, originalItems);
            }
            report.append("PASS 12 repeated transfers, exactly one UUID/entity and unchanged inventory contents\n");
            verifyBounded(player, service, levels, profile, inventory, originalItems, report);
            cell.setBlock(markerBlock,Blocks.GOLD_BLOCK.defaultBlockState(),3);
            player.discard();
            require(service.transfer(player,PhaseId.RIDGE,TransferPolicy.EXACT_POSITION) == TransferResult.INVALID_SOURCE, "Removed player rejection");
            server.saveEverything(false,true,true);
            Files.writeString(savedMarker,"WPH010 saved collision fixture\n");
            Files.writeString(Path.of("phase-transfer-report.txt"),report);
            Files.writeString(Path.of("phase-transfer-status.txt"),"PASS\n");
            System.out.print(report); System.out.println("WPH009 REGRESSION / WPH010 SAFE LANDING PASS");
            server.halt(false);
        } catch (Exception error) {
            try { Files.writeString(Path.of("phase-transfer-status.txt"),"FAIL " + error); } catch (Exception ignored) {}
            server.halt(false); throw new IllegalStateException("WPH009 transfer verification failed",error);
        }
    }

    private static void verifyBounded(ServerPlayer player, PhaseTransferService service, Map<PhaseId,ServerLevel> levels,
                                      GameProfile profile, net.minecraft.world.entity.player.Inventory inventory,
                                      List<ItemStack> items, StringBuilder report) {
        var smooth=levels.get(PhaseId.SMOOTH); var ridge=levels.get(PhaseId.RIDGE);
        var cell=levels.get(PhaseId.CELL); var fracture=levels.get(PhaseId.FRACTURE);
        var base=new Vec3(-16.125,220.5,15.9375);
        // All fixtures are dev-only, actual blocks in the same small chunk footprint.
        for(var level:levels.values()) clear(level);
        slab(smooth,220); slab(ridge,220); slab(cell,221);
        player.snapTo(base.x,base.y,base.z,123.25f,-37.5f);
        require(service.transfer(player,PhaseId.SMOOTH,TransferPolicy.EXACT_POSITION)==TransferResult.SUCCESS,"Prepare Smooth source");
        require(service.transfer(player,PhaseId.RIDGE,TransferPolicy.BOUNDED_LANDING)==TransferResult.SUCCESS,"Smooth -> Ridge exact supported y");
        arrived(player,ridge,base,profile,inventory,items);
        require(service.transfer(player,PhaseId.CELL,TransferPolicy.BOUNDED_LANDING)==TransferResult.SUCCESS,"Ridge -> Cell blocked exact y corrected +1");
        var raised=base.add(0,1,0); arrived(player,cell,raised,profile,inventory,items);
        report.append("WPH010 PASS Smooth -> Ridge exact220.5 -> Cell +1=221.5; actual slab support/body/entity/fluid checks\n");

        for(int y=215;y<=230;y++) layer(fracture,y,Blocks.STONE.defaultBlockState());
        rejected(player,service,PhaseId.FRACTURE,cell,raised,profile,inventory,items,"All nine blocked");
        clear(fracture);
        require(fracture.noCollision(player,player.getBoundingBox()),"Unsupported fixture body actually empty");
        rejected(player,service,PhaseId.FRACTURE,cell,raised,profile,inventory,items,"All nine unsupported");
        slab(fracture,226); // Source221.5 -> floor top226.5 is +5, forbidden.
        rejected(player,service,PhaseId.FRACTURE,cell,raised,profile,inventory,items,"Safe +5 outside bound");
        require(LandingSafety.isSafe(player,fracture,raised.add(0,5,0),TransferPolicy.BOUNDED_LANDING),"+5 truly safe but unsearched");
        clear(fracture); slab(fracture,221); layer(fracture,223,Blocks.STONE.defaultBlockState());
        rejected(player,service,PhaseId.FRACTURE,cell,raised,profile,inventory,items,"Narrow cave head collision");

        for(var hazard:new net.minecraft.world.level.block.Block[]{Blocks.WATER,Blocks.LAVA,Blocks.FIRE,Blocks.SOUL_FIRE,
                Blocks.MAGMA_BLOCK,Blocks.CACTUS,Blocks.CAMPFIRE,Blocks.SWEET_BERRY_BUSH,Blocks.WITHER_ROSE,Blocks.POWDER_SNOW,Blocks.POINTED_DRIPSTONE}) {
            clear(fracture); layer(fracture,220,Blocks.STONE.defaultBlockState());
            if(hazard==Blocks.MAGMA_BLOCK) layer(fracture,220,hazard.defaultBlockState());
            else {
                if(hazard==Blocks.SOUL_FIRE) layer(fracture,220,Blocks.SOUL_SOIL.defaultBlockState());
                if(hazard==Blocks.CACTUS) layer(fracture,220,Blocks.SAND.defaultBlockState());
                if(hazard==Blocks.SWEET_BERRY_BUSH || hazard==Blocks.WITHER_ROSE) layer(fracture,220,Blocks.DIRT.defaultBlockState());
                if(hazard==Blocks.WATER || hazard==Blocks.LAVA) layer(fracture,221,hazard.defaultBlockState());
                else fracture.setBlock(new BlockPos(-17,221,15),hazard.defaultBlockState(),3);
                require(fracture.getBlockState(new BlockPos(-17,221,15)).is(hazard),"Hazard fixture survived placement: "+hazard);
            }
            var integral=new Vec3(base.x,221,base.z); player.snapTo(integral.x,integral.y,integral.z,123.25f,-37.5f);
            rejected(player,service,PhaseId.FRACTURE,cell,integral,profile,inventory,items,"Hazard "+hazard);
        }

        clear(fracture); slab(fracture,220);
        player.snapTo(base.x,base.y,base.z,123.25f,-37.5f);
        var boat=net.minecraft.world.entity.EntityType.OAK_BOAT.create(fracture,net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        require(boat!=null,"Bounded boat creation"); boat.snapTo(base.x,base.y,base.z,0,0); fracture.addFreshEntity(boat);
        require(!fracture.noCollision(player,player.getBoundingBox()),"Real boat actually blocks body");
        rejected(player,service,PhaseId.FRACTURE,cell,base,profile,inventory,items,"Entity occupies only supported landing"); boat.discard();

        clear(ridge); slab(ridge,224);
        require(service.transfer(player,PhaseId.RIDGE,TransferPolicy.BOUNDED_LANDING)==TransferResult.SUCCESS,"Only +4 supported");
        arrived(player,ridge,base.add(0,4,0),profile,inventory,items);
        clear(cell); slab(cell,216); player.snapTo(base.x,base.y,base.z,123.25f,-37.5f);
        require(service.transfer(player,PhaseId.CELL,TransferPolicy.BOUNDED_LANDING)==TransferResult.SUCCESS,"Only -4 supported");
        arrived(player,cell,base.add(0,-4,0),profile,inventory,items);
        clear(ridge); slab(ridge,215);
        require(service.transfer(player,PhaseId.RIDGE,TransferPolicy.BOUNDED_LANDING)==TransferResult.SUCCESS,"Downward -1 supported");
        arrived(player,ridge,base.add(0,-5,0),profile,inventory,items); // Relative to previous216.5: -1.

        clear(fracture); layer(fracture,220,Blocks.STONE_STAIRS.defaultBlockState()
                .setValue(net.minecraft.world.level.block.StairBlock.FACING,net.minecraft.core.Direction.EAST));
        var stairTop=new Vec3(base.x,221,base.z); player.snapTo(stairTop.x,stairTop.y,stairTop.z,123.25f,-37.5f);
        require(service.transfer(player,PhaseId.FRACTURE,TransferPolicy.BOUNDED_LANDING)==TransferResult.SUCCESS,"Actual stair support geometry");
        arrived(player,fracture,stairTop,profile,inventory,items);

        clear(ridge); clear(cell); slab(ridge,220); slab(cell,221);
        player.snapTo(base.x,base.y,base.z,123.25f,-37.5f);
        for(int i=0;i<12;i++) {
            var phase=i%2==0?PhaseId.RIDGE:PhaseId.CELL;
            require(service.transfer(player,phase,TransferPolicy.BOUNDED_LANDING)==TransferResult.SUCCESS,"Repeated bounded shift "+i);
            arrived(player,levels.get(phase),phase==PhaseId.RIDGE?base:raised,profile,inventory,items);
        }
        var border=ridge.getWorldBorder(); double size=border.getSize(); border.setSize(4);
        try {
            require(service.transfer(player,PhaseId.RIDGE,TransferPolicy.BOUNDED_LANDING)==TransferResult.OUT_OF_WORLD,"Bounded WorldBorder rejection");
            assertPlayer(player,cell,raised,profile,inventory,items);
        } finally { border.setSize(size); }
        Consumer<EntityTravelToDimensionEvent> veto=event -> {
            if(event.getEntity()==player && event.getDimension()==PhaseWorldResolver.levelKey(PhaseId.RIDGE)) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(veto);
        try {
            require(service.transfer(player,PhaseId.RIDGE,TransferPolicy.BOUNDED_LANDING)==TransferResult.ERROR,"Veto adjusted -1 native transfer");
            assertPlayer(player,cell,raised,profile,inventory,items);
        } finally { NeoForge.EVENT_BUS.unregister(veto); }
        report.append("WPH010 PASS all blocked/unsupported/+5/headroom, real water/lava/fire/magma/cactus/campfire/berry/rose/powder/dripstone, boat, +4/-4/-1, stairs\n");
        report.append("WPH010 PASS 12 repeated bounded shifts alternating220.5/221.5, bounded border/native veto; x/z/yaw/pitch/UUID/entity/inventory unchanged; save/stop\n");
    }

    static ServerPlayer createPlayer(MinecraftServer server, ServerLevel level, GameProfile profile, Vec3 position) {
        var player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(Packet<?> packet) {}
            @Override public void send(Packet<?> packet, io.netty.channel.ChannelFutureListener listener) {}
        };
        player.snapTo(position.x, position.y, position.z, 123.25f, -37.5f);
        return player; // Caller registers after fixture setup or Vanilla saved-player loading.
    }

    static void clear(ServerLevel level) {
        for(int y=214;y<=231;y++) layer(level,y,Blocks.AIR.defaultBlockState());
    }
    static void slab(ServerLevel level,int y) { layer(level,y,Blocks.STONE_SLAB.defaultBlockState()); }
    static void layer(ServerLevel level,int y,net.minecraft.world.level.block.state.BlockState state) {
        for(int x=-18;x<=-15;x++) for(int z=14;z<=17;z++) level.setBlock(new BlockPos(x,y,z),state,3);
    }
    static void arrived(ServerPlayer player,ServerLevel target,Vec3 point,GameProfile profile,
                                net.minecraft.world.entity.player.Inventory inventory,List<ItemStack> items) {
        assertPlayer(player,target,point,profile,inventory,items);
        require(LandingSafety.isSafe(player,target,point,TransferPolicy.BOUNDED_LANDING),"Postarrival actual safe environment");
        require(target.noCollision(player,player.getBoundingBox()) && !target.containsAnyLiquid(player.getBoundingBox()),"Vanilla postarrival collision/fluid check");
        var body=player.getBoundingBox();
        require(target.findSupportingBlock(player,new net.minecraft.world.phys.AABB(body.minX,body.minY-.0001,body.minZ,body.maxX,body.minY,body.maxZ)).isPresent(),
                "Vanilla actual foot support after arrival");
    }
    private static void rejected(ServerPlayer player,PhaseTransferService service,PhaseId target,ServerLevel source,Vec3 point,
                                 GameProfile profile,net.minecraft.world.entity.player.Inventory inventory,List<ItemStack> items,String message) {
        require(service.transfer(player,target,TransferPolicy.BOUNDED_LANDING)==TransferResult.NO_SAFE_POSITION,message);
        assertPlayer(player,source,point,profile,inventory,items);
    }

    static void assertPlayer(ServerPlayer player, ServerLevel target, Vec3 position, GameProfile profile,
                                     net.minecraft.world.entity.player.Inventory inventory, List<ItemStack> items) {
        require(player.level() == target && player.position().equals(position), "Target Level and exact coordinates");
        require(player.getYRot() == 123.25f && player.getXRot() == -37.5f, "Exact yaw/pitch");
        require(player.getUUID().equals(profile.id()) && player.getInventory() == inventory, "UUID/inventory identity");
        for (int slot = 0; slot < items.size(); slot++) require(ItemStack.matches(items.get(slot),inventory.getItem(slot)), "Inventory slot " + slot);
        int found = 0;
        for (ServerLevel level : target.getServer().getAllLevels()) {
            var entity = level.getEntity(profile.id());
            if (entity != null) { require(entity == player && level == target, "No replacement or duplicated entity"); found++; }
        }
        require(found == 1, "Exactly one registered player entity");
    }

    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
