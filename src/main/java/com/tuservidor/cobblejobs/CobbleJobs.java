package com.tuservidor.cobblejobs;

import com.tuservidor.cobblejobs.command.FisherCommand;
import com.tuservidor.cobblejobs.command.JobCommand;
import com.tuservidor.cobblejobs.config.FisherConfig;
import com.tuservidor.cobblejobs.config.JobsConfig;
import com.tuservidor.cobblejobs.economy.EconomyBridge;
import com.tuservidor.cobblejobs.event.FishingHandler;
import com.tuservidor.cobblejobs.event.KillHandler;
import com.tuservidor.cobblejobs.fishing.leaderboard.LeaderboardManager;
import com.tuservidor.cobblejobs.fishing.zone.FishingZoneManager;
import com.tuservidor.cobblejobs.job.PlayerFisherData;
import com.tuservidor.cobblejobs.zone.ButcherZoneManager;
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
        LOGGER.info("[CobbleJobs] Cargando sistema de Trabajos...");

        // Configs
        FisherConfig.load();
        JobsConfig.load(); // <-- AÑADIDO: Configuración general y de zonas

        // Economy bridge (Impactor)
        EconomyBridge.init();

        // Managers y Eventos de Pesca
        FishingZoneManager.init();
        FishingHandler.register();

        // Managers y Eventos de Carnicero
        ButcherZoneManager.register(); // <-- AÑADIDO: Spawneo de presas
        KillHandler.register();        // <-- AÑADIDO: Drop de loot

        // Leaderboard
        LeaderboardManager.init();

        // Commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> {
            JobCommand.register(dispatcher);    // <-- AÑADIDO: Comandos vitales (/job join, shop, sell)
            FisherCommand.register(dispatcher); 
        });

        // Player data lifecycle
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            PlayerFisherData.evict(handler.player.getUUID()));

        ServerLifecycleEvents.SERVER_STARTED.register(srv -> {
            SERVER = srv;
            LOGGER.info("[CobbleJobs] ¡Listo! Trabajos v2 activos.");
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(srv -> {
            LeaderboardManager.saveAll();
            SERVER = null;
        });

        LOGGER.info("[CobbleJobs] Inicializado correctamente.");
    }
}
