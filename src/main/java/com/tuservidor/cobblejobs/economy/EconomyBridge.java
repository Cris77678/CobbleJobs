package com.tuservidor.cobblejobs.economy;

import com.tuservidor.cobblejobs.CobbleJobs;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Puente a Impactor Economy API (sin cambios respecto a v1).
 * Usa reflexión para carga lazy de Impactor.
 */
public class EconomyBridge {

    private static boolean available = false;

    public static void init() {
        try {
            Class.forName("net.impactdev.impactor.api.economy.EconomyService");
            available = true;
            CobbleJobs.LOGGER.info("[CobbleJobs] Impactor Economy detectado.");
        } catch (ClassNotFoundException e) {
            available = false;
            CobbleJobs.LOGGER.warn("[CobbleJobs] Impactor no encontrado — pagos desactivados.");
        }
    }

    public static void pay(ServerPlayer player, double amount) {
        if (!available) {
            CobbleJobs.LOGGER.warn("[CobbleJobs] No se puede pagar a {} — Impactor no disponible",
                player.getName().getString());
            return;
        }
        try {
            Class<?> svcClass = Class.forName("net.impactdev.impactor.api.economy.EconomyService");
            Class<?> curClass = Class.forName("net.impactdev.impactor.api.economy.currency.Currency");
            Object service    = svcClass.getMethod("instance").invoke(null);
            Object currencies = svcClass.getMethod("currencies").invoke(service);
            Object currency   = currencies.getClass().getMethod("primary").invoke(currencies);
            java.util.concurrent.CompletableFuture<?> future =
                (java.util.concurrent.CompletableFuture<?>)
                svcClass.getMethod("account", curClass, UUID.class)
                        .invoke(service, currency, player.getUUID());
            future.thenAccept(account -> {
                try {
                    account.getClass().getMethod("deposit", BigDecimal.class)
                           .invoke(account, BigDecimal.valueOf(amount));
                } catch (Exception ex) {
                    CobbleJobs.LOGGER.error("[CobbleJobs] Depósito fallido para {}",
                        player.getName().getString(), ex);
                }
            });
        } catch (Exception e) {
            CobbleJobs.LOGGER.error("[CobbleJobs] Error pago Impactor", e);
        }
    }

    public static boolean withdraw(ServerPlayer player, double amount) {
        if (!available) return false;
        try {
            Class<?> svcClass = Class.forName("net.impactdev.impactor.api.economy.EconomyService");
            Class<?> curClass = Class.forName("net.impactdev.impactor.api.economy.currency.Currency");
            Object service    = svcClass.getMethod("instance").invoke(null);
            Object currencies = svcClass.getMethod("currencies").invoke(service);
            Object currency   = currencies.getClass().getMethod("primary").invoke(currencies);
            java.util.concurrent.CompletableFuture<?> future =
                (java.util.concurrent.CompletableFuture<?>)
                svcClass.getMethod("account", curClass, UUID.class)
                        .invoke(service, currency, player.getUUID());
            future.thenAccept(account -> {
                try {
                    account.getClass().getMethod("withdraw", BigDecimal.class)
                           .invoke(account, BigDecimal.valueOf(amount));
                } catch (Exception ex) {
                    CobbleJobs.LOGGER.error("[CobbleJobs] Retiro fallido", ex);
                }
            });
            return true;
        } catch (Exception e) {
            CobbleJobs.LOGGER.error("[CobbleJobs] Error retiro Impactor", e);
            return false;
        }
    }

    public static boolean isAvailable() { return available; }
}
