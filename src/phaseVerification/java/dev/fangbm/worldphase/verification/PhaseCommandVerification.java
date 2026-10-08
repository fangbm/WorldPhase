package dev.fangbm.worldphase.verification;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.fangbm.worldphase.phase.*;
import dev.fangbm.worldphase.transfer.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.server.permissions.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Isolated actual command dispatcher probe and native-client fixtures/audit. Never packaged. */
final class PhaseCommandVerification {
    private static final Vec3 POINT=new Vec3(-16.125,220.5,15.9375);
    private static Pending pending;
    private record Pending(String command,ServerPlayer player,Vec3 position,ServerLevel level,float yaw,float pitch,Inventory inventory,List<ItemStack> items) {}

    static void smoke(MinecraftServer server) {
        try {
            var levels=levels(server); var cell=levels.get(PhaseId.CELL);
            var marker=server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("wph011-saved-probe.txt");
            var report=new StringBuilder("WPH011 seed123456789 actual registered dispatcher / real ServerPlayer / simulated outbound transport\n");
            if(Files.exists(marker)) {
                require(cell.getBlockState(new BlockPos(-17,209,15)).is(Blocks.GOLD_BLOCK),"Saved command fixture survives reload");
                report.append("RELOAD PASS persisted command fixture\n");
            }
            platforms(levels);
            var profile=new GameProfile(UUID.fromString("a3b83e90-28f9-4a94-b89c-8c46af811011"),"WPH011Probe");
            var player=new ServerPlayer(server,levels.get(PhaseId.SMOOTH),profile,ClientInformation.createDefault());
            player.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),player,CommonListenerCookie.createInitial(profile,false)) {
                @Override public void send(Packet<?> packet) {}
                @Override public void send(Packet<?> packet,io.netty.channel.ChannelFutureListener listener) {}
            };
            player.snapTo(POINT.x,POINT.y,POINT.z,123.25f,-37.5f); player.level().addNewPlayer(player);
            var inventory=player.getInventory(); inventory.setItem(0,new ItemStack(Items.DIAMOND,7)); inventory.setItem(10,new ItemStack(Items.IRON_INGOT,23));
            var items=copy(inventory);
            var messages=new ArrayList<String>();
            CommandSource sink=new CommandSource() {
                public void sendSystemMessage(Component c) { messages.add(c.getString()); }
                public boolean acceptsSuccess() { return true; }
                public boolean acceptsFailure() { return true; }
                public boolean shouldInformAdmins() { return false; }
            };
            var dispatcher=server.getCommands().getDispatcher();
            var ambiguities=new ArrayList<String>(); dispatcher.findAmbiguities((parent,child,sibling,inputs)-> {
                if(dispatcher.getPath(parent).stream().findFirst().orElse("").equals("wp")) ambiguities.add(inputs.toString());
            }); require(ambiguities.isEmpty(),"wp tree has no ambiguities");
            for(String command:List.of("wp phase list","wp phase current","wp seedcheck","wp compare all","wp sample ridge","wp compare smooth ridge","wp safepos cell")) {
                messages.clear(); int result=dispatcher.execute(command,source(server,player,sink,LevelBasedPermissionSet.GAMEMASTER));
                require(result>0,"Registered command: "+command); report.append(command).append('\n').append(String.join("\n",messages)).append('\n');
                require(player.position().equals(POINT),"Diagnostic/sampling never moves player"); identity(player,inventory,items);
            }
            var frame=source(server,player,sink,LevelBasedPermissionSet.GAMEMASTER);
            var suggestions=dispatcher.getCompletionSuggestions(dispatcher.parse("wp phase goto ",frame)).join();
            require(suggestions.getList().size()==4,"Native Phase suggestions");
            try { dispatcher.execute("wp phase list",frame.withPermission(PermissionSet.NO_PERMISSIONS)); throw new IllegalStateException("Non OP accepted"); }
            catch(CommandSyntaxException expected) { report.append("PASS non OP denied\n"); }
            try { dispatcher.execute("wp phase cycle",server.createCommandSourceStack()); throw new IllegalStateException("Console accepted player command"); }
            catch(CommandSyntaxException expected) { require(expected.getType()==CommandSourceStack.ERROR_NOT_PLAYER,"Typed console error"); }
            dispatcher.execute("wp phase current",server.createCommandSourceStack());
            for(String command:List.of("wp phase goto ridge","wp phase goto cell","wp phase cycle","wp phase cycle","wp phase cycle","wp phase cycle")) {
                messages.clear(); require(dispatcher.execute(command,source(server,player,sink,LevelBasedPermissionSet.GAMEMASTER))==1,"Real transfer: "+command);
                require(player.position().x==POINT.x && player.position().z==POINT.z,"Exact x/z"); identity(player,inventory,items);
                require(LandingSafety.isSafe(player,player.level(),player.position(),TransferPolicy.BOUNDED_LANDING),"Safe after actual command teleport");
                report.append(command).append(" => ").append(String.join("\n",messages)).append('\n');
            }
            var old=player.position(); var oldLevel=player.level();
            for(int y=215;y<=230;y++) layer(levels.get(PhaseId.FRACTURE),y,Blocks.STONE.defaultBlockState());
            messages.clear(); require(dispatcher.execute("wp phase cycle",source(server,player,sink,LevelBasedPermissionSet.GAMEMASTER))==0,"Unsafe cycle refused");
            require(player.position().equals(old) && player.level()==oldLevel,"Failed cycle unchanged"); identity(player,inventory,items);
            report.append("PASS unsafe cycle source unchanged: ").append(String.join("\n",messages)).append('\n');
            var ridge=levels.get(PhaseId.RIDGE); clear(ridge); layer(ridge,219,Blocks.STONE.defaultBlockState());
            messages.clear(); require(dispatcher.execute("wp phase goto ridge --surface",source(server,player,sink,LevelBasedPermissionSet.GAMEMASTER))==1,"Explicit surface uses actual support");
            require(player.position().equals(new Vec3(POINT.x,220,POINT.z)),"Surface actual full-block support"); identity(player,inventory,items);
            require(LandingSafety.isSafe(player,ridge,player.position(),TransferPolicy.DEV_SURFACE),"Safe actual surface");
            report.append("PASS surface => ").append(String.join("\n",messages)).append('\n');
            cell.setBlock(new BlockPos(-17,209,15),Blocks.GOLD_BLOCK.defaultBlockState(),3);
            player.discard(); server.saveEverything(false,true,true); Files.writeString(marker,"WPH011 saved\n");
            Files.writeString(Path.of("command-report.txt"),report); Files.writeString(Path.of("command-status.txt"),"PASS\n");
            System.out.print(report); System.out.println("WPH011 COMMAND SERVER PASS"); server.halt(false);
        } catch(Exception error) {
            try { Files.writeString(Path.of("command-status.txt"),"FAIL "+error); } catch(Exception ignored) {}
            server.halt(false); throw new IllegalStateException("WPH011 command probe failed",error);
        }
    }

    static void startNative(MinecraftServer server) {
        platforms(levels(server));
        NeoForge.EVENT_BUS.addListener(PhaseCommandVerification::login);
        NeoForge.EVENT_BUS.addListener(PhaseCommandVerification::command);
        NeoForge.EVENT_BUS.addListener(PhaseCommandVerification::tick);
        System.out.println("WPH011 NATIVE READY: loopback client WPH011Review only; actual wp chat commands audited after server tick");
    }
    private static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player) || !player.getGameProfile().name().equals("WPH011Review")) return;
        var server=player.level().getServer(); server.getPlayerList().op(player.nameAndId());
        player.setGameMode(GameType.CREATIVE); player.getAbilities().flying=true; player.onUpdateAbilities();
        // Fixture setup only. The production service still refuses Vanilla -> Phase bootstrap.
        server.getCommands().performPrefixedCommand(player.createCommandSourceStack(),"execute in minecraft:overworld run tp @s -511.875 220 768.25 123.25 -37.5");
        var inv=player.getInventory(); if(inv.getItem(0).isEmpty()) inv.setItem(0,new ItemStack(Items.DIAMOND,7));
        if(inv.getItem(10).isEmpty()) inv.setItem(10,new ItemStack(Items.IRON_INGOT,23));
        System.out.println("WPH011 NATIVE OP READY uuid="+player.getUUID()+" level="+player.level().dimension().identifier()+" at="+player.position());
    }
    private static void command(CommandEvent event) {
        var source=event.getParseResults().getContext().getSource(); var player=source.getPlayer();
        var input=event.getParseResults().getReader().getString();
        if(player==null || !player.getGameProfile().name().equals("WPH011Review") || !input.startsWith("wp ")) return;
        pending=new Pending(input,player,player.position(),player.level(),player.getYRot(),player.getXRot(),player.getInventory(),copy(player.getInventory()));
    }
    private static void tick(ServerTickEvent.Post event) {
        if(pending==null) return; var entry=pending; pending=null; var player=entry.player();
        identity(player,entry.inventory(),entry.items());
        require(player.position().x==entry.position().x && player.position().z==entry.position().z,"Native wp preserves x/z");
        require(player.getYRot()==entry.yaw() && player.getXRot()==entry.pitch(),"Native wp preserves yaw/pitch");
        var text="NATIVE "+entry.command()+" before="+entry.level().dimension().identifier()+entry.position()
                +" after="+player.level().dimension().identifier()+player.position()+" yaw="+player.getYRot()+" pitch="+player.getXRot()
                +" uuid="+player.getUUID()+" inventory/entity PASS\n";
        try { Files.writeString(Path.of("native-command-audit.txt"),text,StandardOpenOption.CREATE,StandardOpenOption.APPEND); }
        catch(Exception error) { throw new IllegalStateException(error); }
        System.out.print(text);
    }
    private static CommandSourceStack source(MinecraftServer server,ServerPlayer player,CommandSource sink,PermissionSet permissions) {
        return new CommandSourceStack(sink,player.position(),new Vec2(player.getXRot(),player.getYRot()),player.level(),permissions,"Probe",Component.literal("Probe"),server,player);
    }
    private static Map<PhaseId,ServerLevel> levels(MinecraftServer server) {
        var levels=new EnumMap<PhaseId,ServerLevel>(PhaseId.class);
        for(var phase:PhaseId.values()) { var level=server.getLevel(PhaseWorldResolver.levelKey(phase)); require(level!=null && level.getSeed()==123456789L,"Actual same-seed Levels"); levels.put(phase,level); }
        return levels;
    }
    private static void platforms(Map<PhaseId,ServerLevel> levels) {
        for(var entry:levels.entrySet()) { clear(entry.getValue()); layer(entry.getValue(),entry.getKey()==PhaseId.CELL?221:220,Blocks.STONE_SLAB.defaultBlockState()); }
    }
    private static void clear(ServerLevel level) { for(int y=214;y<=231;y++) layer(level,y,Blocks.AIR.defaultBlockState()); }
    private static void layer(ServerLevel level,int y,BlockState state) { for(int x=-18;x<=-15;x++) for(int z=14;z<=17;z++) level.setBlock(new BlockPos(x,y,z),state,3); }
    private static List<ItemStack> copy(Inventory inventory) { var items=new ArrayList<ItemStack>(); for(int i=0;i<inventory.getContainerSize();i++) items.add(inventory.getItem(i).copy()); return items; }
    private static void identity(ServerPlayer player,Inventory inventory,List<ItemStack> items) {
        require(player.getInventory()==inventory && player.getUUID().equals(player.getGameProfile().id()),"Inventory identity/UUID");
        for(int i=0;i<items.size();i++) require(ItemStack.matches(items.get(i),inventory.getItem(i)),"Inventory slot "+i);
        int count=0; for(var level:player.level().getServer().getAllLevels()) { var entity=level.getEntity(player.getUUID()); if(entity!=null) { require(entity==player && level==player.level(),"No duplicate player"); count++; }}
        require(count==1,"Exactly one UUID entity");
    }
    private static void require(boolean condition,String message) { if(!condition) throw new IllegalStateException(message); }
}
