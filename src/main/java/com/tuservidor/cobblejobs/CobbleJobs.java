package com.tuservidor.cobblejobs;

import com.tuservidor.cobblejobs.command.FisherCommand;
import com.tuservidor.cobblejobs.config.FisherConfig;
import com.tuservidor.cobblejobs.economy.EconomyBridge;
import com.tuservidor.cobblejobs.event.FishingHandler;
import com.tuservidor.cobblejobs.fishing.leaderboard.LeaderboardManager;
import com.tuservidor.cobblejobs.fishing.zone.FishingZoneManager;
import com.tuservidor.cobblejobs.job.PlayerFisherData;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CobbleJobs implements DedicatedServerModInitializer {

    public static final String MOD_ID = "cobblejobs";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static MinecraftServer SERVER;

    @Override
    public void onInitializeServer() {
        LOGGER.info("[CobbleJobs] Cargando sistema de Pescador avanzado...");

        // Config
        FisherConfig.load();

        // Economy bridge (Impactor)
        EconomyBridge.init();

        // Fishing zone manager (particles, holograms)
        FishingZoneManager.init();

        // Fishing event handler (core loop)
        FishingHandler.register();

        // Leaderboard
        LeaderboardManager.init();

        // Commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) ->
            FisherCommand.register(dispatcher));

        // Player data lifecycle
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            PlayerFisherData.evict(handler.player.getUUID()));

        ServerLifecycleEvents.SERVER_STARTED.register(srv -> {
            SERVER = srv;
            LOGGER.info("[CobbleJobs] ¡Listo! Pescador v2 activo.");
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(srv -> {
            LeaderboardManager.saveAll();
            SERVER = null;
        });

        LOGGER.info("[CobbleJobs] Inicializado correctamente.");
    }
}
