package com.tuservidor.cobblejobs.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.tuservidor.cobblejobs.fishing.leaderboard.LeaderboardManager;
import com.tuservidor.cobblejobs.job.PlayerFisherData;
import com.tuservidor.cobblejobs.util.MessageUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public class FisherCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var base = Commands.literal("job");

        base.then(Commands.literal("info").requires(CommandSourceStack::isPlayer).executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayer();
            PlayerFisherData d = PlayerFisherData.get(p.getUUID());
            if (!d.isFisher()) { 
                p.sendSystemMessage(MessageUtil.literal("§c[CobbleJobs] No eres Pescador.")); 
                return 0; 
            }

            double nextXp = d.xpForNextLevel();
            String xpBar = buildXpBar(d.getXp(), nextXp, 20, d.getLevel());

            p.sendSystemMessage(MessageUtil.literal(
                "§6§l══ 🎣 Pescador ══\n" +
                "§fNivel: §e" + d.getLevel() + (d.getLevel() >= 50 ? " §6§l[MAX]" : "") + "\n" +
                "§fXP: §7" + xpBar + " §8(" + (int)d.getXp() + "/" + (int)nextXp + ")\n" +
                "§fPeces: §7" + d.getTotalFishCaught() + "\n" +
                "§6§l════════════════"
            ));
            return 1;
        }));
        
        dispatcher.register(base);
    }

    // CORRECCIÓN 3: Barra de XP inteligente para niveles máximos
    private static String buildXpBar(double current, double max, int length, int level) {
        if (level >= 50) {
            // Estética dorada para jugadores que ya alcanzaron el tope (Prestigio)
            return "§6" + "█".repeat(length);
        }
        int filled = (int) Math.round((current / max) * length);
        filled = Math.max(0, Math.min(filled, length));
        return "§a" + "█".repeat(filled) + "§8" + "░".repeat(length - filled);
    }
}
