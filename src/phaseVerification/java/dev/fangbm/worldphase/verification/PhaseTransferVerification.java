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
                var level = smooth;
                level.setBlock(new BlockPos(x,219,z), Blocks.STONE.defaultBlockState(), 3);
                for (int y = 220; y <= 223; y++) level.setBlock(new BlockPos(x,y,z), Blocks.AIR.defaultBlockState(), 3);
            }
            var profile = new GameProfile(UUID.fromString("a3b83e90-28f9-4a94-b89c-8c46af811009"), "WPH009Probe");
            var player = new ServerPlayer(server, smooth, profile, ClientInformation.createDefault());
            player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player,
                    CommonListenerCookie.createInitial(profile, false)) {
                @Override public void send(Packet<?> packet) {}
                @Override public void send(Packet<?> packet, io.netty.channel.ChannelFutureListener listener) {}
            };
            player.snapTo(position.x, position.y, position.z, 123.25f, -37.5f);
            smooth.addNewPlayer(player);
            var inventory = player.getInventory();
            inventory.setItem(0, new ItemStack(Items.DIAMOND, 7));
            inventory.setItem(10, new ItemStack(Items.IRON_INGOT, 23));
            var originalItems = new ArrayList<ItemStack>();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) originalItems.add(inventory.getItem(slot).copy());
            var service = new PhaseTransferService(server);
            var report = new StringBuilder("WPH009: seed=123456789; real ServerPlayer/Vanilla teleport; simulated outbound transport\n");
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
            player.discard();
            require(service.transfer(player,PhaseId.RIDGE,TransferPolicy.EXACT_POSITION) == TransferResult.INVALID_SOURCE, "Removed player rejection");
            server.saveEverything(false,true,true);
            Files.writeString(Path.of("phase-transfer-report.txt"),report);
            Files.writeString(Path.of("phase-transfer-status.txt"),"PASS\n");
            System.out.print(report); System.out.println("WPH009 PHASE TRANSFER PASS");
            server.halt(false);
        } catch (Exception error) {
            try { Files.writeString(Path.of("phase-transfer-status.txt"),"FAIL " + error); } catch (Exception ignored) {}
            server.halt(false); throw new IllegalStateException("WPH009 transfer verification failed",error);
        }
    }

    private static void assertPlayer(ServerPlayer player, ServerLevel target, Vec3 position, GameProfile profile,
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
