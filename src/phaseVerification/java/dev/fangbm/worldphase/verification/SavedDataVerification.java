package dev.fangbm.worldphase.verification;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import dev.fangbm.worldphase.phase.*;
import dev.fangbm.worldphase.tutorial.*;
import dev.fangbm.worldphase.tutorial.TutorialOpeningState.*;
import dev.fangbm.worldphase.worldgen.sample.TerrainSampleService;
import java.nio.file.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.ModList;

/** Two independent JVMs; actual global DataStorage, compressed NBT and a fixed-seed diagnostic. */
final class SavedDataVerification {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    static void verify(MinecraftServer server) {
        String stage = System.getProperty("worldphase.savedData.stage", "invalid");
        var report = new JsonObject();
        Path output = Path.of("saved-data-" + stage + ".json");
        try {
            require(stage.equals("cold") || stage.equals("reload"), "run.stage");
            require(server.isSameThread(), "server.thread");
            report.addProperty("stage", stage);
            report.addProperty("minecraft", SharedConstants.getCurrentVersion().name());
            report.addProperty("neoforge", ModList.get().getModContainerById("neoforge").orElseThrow().getModInfo().getVersion().toString());
            report.addProperty("java", System.getProperty("java.runtime.version"));
            var levels = new ArrayList<ServerLevel>(); levels.add(server.overworld());
            for (var phase : PhaseId.values()) {
                var level = server.getLevel(PhaseWorldResolver.levelKey(phase));
                require(level != null && level.getSeed() == 123456789L, "world.phase_seed." + phase);
                levels.add(level);
            }
            var spawn = server.overworld().getRespawnData();
            var beforeChunks = levels.stream().map(l -> l.getChunkSource().getLoadedChunksCount()).toList();
            Path file = WorldPhaseSavedData.dataFile(server);
            Path checkpoint = server.getWorldPath(LevelResource.ROOT).resolve("saved-data-checkpoint.json");
            require(stage.equals("cold") ? Files.notExists(file) : Files.isRegularFile(file), "storage." + stage + "_file");
            var data = WorldPhaseSavedData.get(server);
            var provenance = WorldPhaseSavedData.currentProvenance(server, 0, 0, TutorialSearchConfig.defaults());
            if (stage.equals("cold")) {
                require(data.state().equals(TutorialOpeningState.initial()), "state.default");
                var sampler = new TerrainSampleService(server);
                var diagnostic = new CandidateMetadata(-16, 24, sampler.sampleHeight(PhaseId.FRACTURE, -16, 24),
                        sampler.sampleHeight(PhaseId.SMOOTH, -16, 24), Evidence.UNVERIFIED);
                require(data.completeSearch(provenance, Optional.of(diagnostic)), "state.search_update");
                require(data.state().openingChoice() == OpeningChoice.UNDECIDED, "search.not_selection");
                require(data.requireControlledOpening(), "state.explicit_controlled_choice");
            } else {
                var expected = JsonParser.parseString(Files.readString(checkpoint));
                var actual = TutorialOpeningState.CODEC.encodeStart(JsonOps.INSTANCE, data.state()).getOrThrow();
                require(expected.equals(actual), "reload.exact_candidate_choice_provenance");
                require(!data.isDirty(), "reload.clean");
                report.addProperty("reload", "PASS: exact snapshot restored; no search or candidate replacement");
            }
            for (var level : levels) require(WorldPhaseSavedData.get(level) == data, "storage.single_overworld_instance");
            require(!data.canUseNaturalOpening(), "evidence.not_route_or_selection");
            require(data.state().openingChoice() == OpeningChoice.CONTROLLED_OPENING_REQUIRED, "choice.controlled");
            server.overworld().getDataStorage().saveAndJoin();
            require(Files.isRegularFile(file) && !data.isDirty(), "storage.actual_save");
            byte[] before = Files.readAllBytes(file);
            require(!data.completeSearch(provenance, data.state().candidate()), "update.identical_search_noop");
            require(!data.requireControlledOpening() && !data.isDirty(), "update.identical_choice_noop");
            server.overworld().getDataStorage().saveAndJoin();
            require(Arrays.equals(before, Files.readAllBytes(file)), "update.no_file_change");
            require(beforeChunks.equals(levels.stream().map(l -> l.getChunkSource().getLoadedChunksCount()).toList()), "storage.no_full_chunks");
            require(spawn.equals(server.overworld().getRespawnData()) && server.getPlayerCount() == 0, "storage.no_spawn_or_player_change");
            report.add("snapshot", TutorialOpeningState.CODEC.encodeStart(JsonOps.INSTANCE, data.state()).getOrThrow());
            report.addProperty("seed", server.overworld().getSeed());
            report.addProperty("additionalLoadedFullChunks", 0);
            report.addProperty("globalLevelAccesses", levels.size());
            report.addProperty("naturalOpeningUsable", false);
            report.add("validationIssues", JSON.toJsonTree(data.validationIssues(provenance)));
            report.addProperty("dataFile", file.toString());
            if (stage.equals("cold")) Files.writeString(checkpoint, JSON.toJson(report.get("snapshot")));
            require(server.saveEverything(false, true, true), "server.save");
            report.addProperty("status", "PASS");
            Files.writeString(output, JSON.toJson(report));
            System.out.println("WPH014 " + stage + " PASS: explicit controlled choice, diagnostic and provenance persisted; no selection/chunks/players");
        } catch (Exception failure) {
            report.addProperty("status", "FAIL"); report.addProperty("failure", failure.toString());
            try { Files.writeString(output, JSON.toJson(report)); } catch (Exception write) { failure.addSuppressed(write); }
            throw new IllegalStateException("WPH014 " + stage + " failed; inspect " + output, failure);
        } finally { server.halt(false); }
    }

    private static void require(boolean condition, String invariant) {
        if (!condition) throw new IllegalStateException("WPH014 invariant=" + invariant);
    }
}
