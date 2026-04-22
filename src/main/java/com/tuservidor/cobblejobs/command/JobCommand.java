package com.tuservidor.cobblejobs.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.tuservidor.cobblejobs.config.JobsConfig;
import com.tuservidor.cobblejobs.gui.SellGui;
import com.tuservidor.cobblejobs.item.FishItem;
import com.tuservidor.cobblejobs.job.PlayerJobData;
import com.tuservidor.cobblejobs.economy.EconomyBridge;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import com.tuservidor.cobblejobs.util.MessageUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * /job — all player and admin commands.
 *
 *  Player:
 *   /job join <butcher|fisher>   — Take a job
 *   /job leave                   — Leave current job
 *   /job info                    — Show current job
 *   /job sell                    — Sell fish (fisher only)
 *   /job shop                    — Buy the custom fishing rod
 *
 *  Admin (OP level 2):
 *   /job setzone <job> pos1|pos2 — Define zone corners
 *   /job stopzone <job>          — Disable a zone (no new spawns/fishing)
 *   /job startzone <job>         — Re-enable a zone
 *   /job reload                  — Reload config from disk
 */
public class JobCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var base = Commands.literal("job");

        // ── Player commands ────────────────────────────────────────────────
        base.then(Commands.literal("join")
            .requires(CommandSourceStack::isPlayer)
            .then(Commands.argument("job", StringArgumentType.word())
                .suggests((ctx, b) -> { b.suggest("butcher"); b.suggest("fisher"); return b.buildFuture(); })
                .executes(ctx -> joinJob(ctx.getSource(), StringArgumentType.getString(ctx, "job")))));

        base.then(Commands.literal("leave")
            .requires(CommandSourceStack::isPlayer)
            .executes(ctx -> leaveJob(ctx.getSource())));

        base.then(Commands.literal("info")
            .requires(CommandSourceStack::isPlayer)
            .executes(ctx -> showInfo(ctx.getSource())));

        base.then(Commands.literal("sell")
            .requires(CommandSourceStack::isPlayer)
            .executes(ctx -> openSell(ctx.getSource())));

        base.then(Commands.literal("shop")
            .requires(CommandSourceStack::isPlayer)
            .executes(ctx -> openShop(ctx.getSource())));

        base.then(Commands.literal("buy")
            .requires(CommandSourceStack::isPlayer)
            .then(Commands.literal("rod")
                .executes(ctx -> buyRod(ctx.getSource()))));

        // ── Admin commands ─────────────────────────────────────────────────
        base.then(Commands.literal("setzone")
            .requires(src -> src.hasPermission(2))
            .then(Commands.argument("job", StringArgumentType.word())
                .suggests((ctx, b) -> { b.suggest("butcher"); b.suggest("fisher"); return b.buildFuture(); })
                .then(Commands.literal("pos1").executes(ctx ->
                    setZone(ctx.getSource(), StringArgumentType.getString(ctx, "job"), true)))
                .then(Commands.literal("pos2").executes(ctx ->
                    setZone(ctx.getSource(), StringArgumentType.getString(ctx, "job"), false)))));

        base.then(Commands.literal("stopzone")
            .requires(src -> src.hasPermission(2))
            .then(Commands.argument("job", StringArgumentType.word())
                .suggests((ctx, b) -> { b.suggest("butcher"); b.suggest("fisher"); return b.buildFuture(); })
                .executes(ctx -> toggleZone(ctx.getSource(),
                    StringArgumentType.getString(ctx, "job"), false))));

        base.then(Commands.literal("startzone")
            .requires(src -> src.hasPermission(2))
            .then(Commands.argument("job", StringArgumentType.word())
                .suggests((ctx, b) -> { b.suggest("butcher"); b.suggest("fisher"); return b.buildFuture(); })
                .executes(ctx -> toggleZone(ctx.getSource(),
                    StringArgumentType.getString(ctx, "job"), true))));

        base.then(Commands.literal("reload")
            .requires(src -> src.hasPermission(2))
            .executes(ctx -> {
                JobsConfig.load();
                ctx.getSource().sendSystemMessage(MessageUtil.literal("§a[CobbleJobs] Config recargada."));
                return 1;
            }));

        dispatcher.register(base);
    }

    // ── Player handlers ────────────────────────────────────────────────────

    private static int joinJob(CommandSourceStack src, String jobName) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            PlayerJobData.Job job = switch (jobName.toLowerCase()) {
                case "butcher" -> PlayerJobData.Job.BUTCHER;
                case "fisher"  -> PlayerJobData.Job.FISHER;
                default -> null;
            };
            if (job == null) {
                player.sendSystemMessage(MessageUtil.literal("§c[CobbleJobs] Trabajo desconocido. Usa: butcher, fisher"));
                return 0;
            }
            PlayerJobData data = PlayerJobData.get(player.getUUID());
            if (data.getActiveJob() == job) {
                player.sendSystemMessage(MessageUtil.literal("§e[CobbleJobs] Ya tienes ese trabajo."));
                return 0;
            }
            data.setActiveJob(job);
            PlayerJobData.save(player.getUUID());

            String label = job == PlayerJobData.Job.BUTCHER ? "§c🗡 Carnicero" : "§b🎣 Pescador";
            player.sendSystemMessage(MessageUtil.literal("§a[CobbleJobs] Ahora eres: " + label));
            if (job == PlayerJobData.Job.FISHER) {
                player.sendSystemMessage(MessageUtil.literal(
                    "§7Compra la §bCaña Pokémon §7con §f/job shop §7para empezar a pescar."));
            }
            return 1;
        } catch (Exception e) { return 0; }
    }

    private static int leaveJob(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            PlayerJobData.get(player.getUUID()).setActiveJob(PlayerJobData.Job.NONE);
            PlayerJobData.save(player.getUUID());
            player.sendSystemMessage(MessageUtil.literal("§7[CobbleJobs] Has dejado tu trabajo."));
            return 1;
        } catch (Exception e) { return 0; }
    }

    private static int showInfo(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            PlayerJobData.Job job = PlayerJobData.get(player.getUUID()).getActiveJob();
            String label = switch (job) {
                case BUTCHER -> "§c🗡 Carnicero";
                case FISHER  -> "§b🎣 Pescador";
                case NONE    -> "§7Ninguno";
            };
            JobsConfig cfg = JobsConfig.get();
            String butcherStatus = cfg.isButcherEnabled() ? "§aActiva" : "§cDetenida";
            String fisherStatus  = cfg.isFisherEnabled()  ? "§aActiva" : "§cDetenida";
            player.sendSystemMessage(MessageUtil.literal(
                "§6§l[CobbleJobs] §r§fTrabajo: " + label + "\n" +
                "§7Zona Carnicero: " + butcherStatus + " §7| Zona Pesca: " + fisherStatus));
            return 1;
        } catch (Exception e) { return 0; }
    }

    private static int openSell(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            PlayerJobData.Job job = PlayerJobData.get(player.getUUID()).getActiveJob();
            if (job == PlayerJobData.Job.NONE) {
                player.sendSystemMessage(MessageUtil.literal("§c[CobbleJobs] No tienes ningún trabajo activo."));
                return 0;
            }
            if (job == PlayerJobData.Job.BUTCHER) {
                player.sendSystemMessage(MessageUtil.literal(
                    "§c[Carnicero] Los carniceros cobran al matar — no hay nada que vender manualmente."));
                return 0;
            }
            SellGui.open(player);
            return 1;
        } catch (Exception e) { return 0; }
    }

    /** /job shop — buy the custom fishing rod */
    private static int openShop(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            PlayerJobData.Job job = PlayerJobData.get(player.getUUID()).getActiveJob();

            player.sendSystemMessage(MessageUtil.literal("§6§l[CobbleJobs — Tienda] ═══════════"));

            if (job == PlayerJobData.Job.FISHER || job == PlayerJobData.Job.NONE) {
                player.sendSystemMessage(MessageUtil.literal(
                    "§b🎣 Caña Pokémon §7— §a$" + String.format("%.0f", com.tuservidor.cobblejobs.config.FisherConfig.get().getRodPrice())));
                player.sendSystemMessage(MessageUtil.literal(
                    "§7  Necesaria para pescar en la zona."));
                player.sendSystemMessage(MessageUtil.literal(
                    "§7  Escribe §f/job buy rod §7para comprarla."));
            }

            if (job == PlayerJobData.Job.NONE) {
                player.sendSystemMessage(MessageUtil.literal(
                    "§7Únete a un trabajo con §f/job join <trabajo> §7para acceder a la tienda."));
            }

            player.sendSystemMessage(MessageUtil.literal("§6§l═══════════════════════════"));
            return 1;
        } catch (Exception e) { return 0; }
    }

    private static int buyRod(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            PlayerJobData.Job job = PlayerJobData.get(player.getUUID()).getActiveJob();
            if (job != PlayerJobData.Job.FISHER) {
                player.sendSystemMessage(MessageUtil.literal("§c[CobbleJobs] Solo los §bPescadores §cpueden comprar la caña."));
                return 0;
            }
            // Check if player already has one
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (FishItem.isCustomRod(player.getInventory().getItem(i))) {
                    player.sendSystemMessage(MessageUtil.literal("§e[CobbleJobs] Ya tienes una Caña Pokémon."));
                    return 0;
                }
            }
            if (!EconomyBridge.isAvailable()) {
                player.sendSystemMessage(MessageUtil.literal("§c[CobbleJobs] Economía no disponible."));
                return 0;
            }
            // Deduct price and give rod
            EconomyBridge.withdraw(player, com.tuservidor.cobblejobs.config.FisherConfig.get().getRodPrice());
            ItemStack rod = FishItem.createCustomRod();
            if (!player.addItem(rod)) player.drop(rod, false);
            player.sendSystemMessage(MessageUtil.literal(
                "§a[CobbleJobs] §f¡Compraste la §bCaña Pokémon §fpor §a$" +
                String.format("%.0f", com.tuservidor.cobblejobs.config.FisherConfig.get().getRodPrice()) + "§f!"));
            return 1;
        } catch (Exception e) { return 0; }
    }

    // ── Admin handlers ─────────────────────────────────────────────────────

    private static int setZone(CommandSourceStack src, String jobName, boolean isPos1) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            JobsConfig cfg = JobsConfig.get();
            double x = player.getX(), y = player.getY(), z = player.getZ();

            JobsConfig.ZoneConfig zone = switch (jobName.toLowerCase()) {
                case "butcher" -> cfg.getButcherZone();
                case "fisher"  -> cfg.getFisherZone();
                default -> null;
            };
            if (zone == null) {
                player.sendSystemMessage(MessageUtil.literal("§cTrabajo desconocido: " + jobName));
                return 0;
            }

            if (isPos1) zone.set(x, y, z, zone.getX2(), zone.getY2(), zone.getZ2());
            else {
                zone.set(zone.getX1(), zone.getY1(), zone.getZ1(), x, y, z);
                zone.setConfigured(true); // Mark ready when pos2 is set
            }

            JobsConfig.save();
            String corner = isPos1 ? "pos1" : "pos2";
            player.sendSystemMessage(MessageUtil.literal(
                "§a[CobbleJobs] Zona §e" + jobName + " §a— " + corner +
                " §fguardada en §e" + String.format("%.1f, %.1f, %.1f", x, y, z)));
            if (!isPos1) player.sendSystemMessage(MessageUtil.literal(
                "§a✔ Zona configurada y lista."));
            return 1;
        } catch (Exception e) { return 0; }
    }

    private static int toggleZone(CommandSourceStack src, String jobName, boolean enable) {
        try {
            JobsConfig cfg = JobsConfig.get();
            switch (jobName.toLowerCase()) {
                case "butcher" -> cfg.setButcherEnabled(enable);
                case "fisher"  -> cfg.setFisherEnabled(enable);
                default -> {
                    src.sendSystemMessage(MessageUtil.literal("§cTrabajo desconocido: " + jobName));
                    return 0;
                }
            }
            JobsConfig.save();
            String action = enable ? "§aactivada" : "§cdetenida";
            src.sendSystemMessage(MessageUtil.literal(
                "§6[CobbleJobs] §fZona §e" + jobName + " " + action + "§f."));
            return 1;
        } catch (Exception e) { return 0; }
    }
}
