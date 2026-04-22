package com.tuservidor.cobblejobs.item;

import com.cobblemon.mod.common.CobblemonItems;
import com.tuservidor.cobblejobs.CobbleJobs;
import com.tuservidor.cobblejobs.config.FisherConfig;
import com.tuservidor.cobblejobs.fishing.rarity.FishRarity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import com.tuservidor.cobblejobs.util.MessageUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

public class FishItem {

    public static final String ROD_NBT_KEY = "cobblejobs_rod";

    public static ItemStack create(String species, double weight, int length,
                                   boolean shiny, FishRarity rarity, double minigameMult) {
        // CORRECCIÓN 2: Por defecto creamos 1, pero read/write ahora soportan stacks
        return create(species, weight, length, shiny, rarity, minigameMult, 1);
    }

    public static ItemStack create(String species, double weight, int length,
                                   boolean shiny, FishRarity rarity, double minigameMult, int count) {
        ItemStack stack = buildPokemonModelItem(species, shiny);
        stack.setCount(count); // Aplicar cantidad

        String cleanName = capitalize(species.replace("cobblemon:", ""));
        String namePrefix = rarity.color + (shiny ? "✦ Shiny " : "") + cleanName;
        stack.set(DataComponents.CUSTOM_NAME, MessageUtil.literal(namePrefix));

        FishData data = new FishData(species, weight, length, shiny, rarity, minigameMult, count);
        
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtil.literal(rarity.color + "▶ " + rarity.displayName));
        lore.add(MessageUtil.literal("§7Especie: §f" + cleanName));
        lore.add(MessageUtil.literal("§7Peso:    §f" + String.format("%.2f", weight) + " kg"));
        lore.add(MessageUtil.literal("§7Talla:   §f" + length + " cm"));
        if (shiny) lore.add(MessageUtil.literal("§e§l✦ SHINY ✦"));
        if (minigameMult < 1.0) lore.add(MessageUtil.literal("§c⚠️ Pez dañado (Penalización)"));
        
        lore.add(MessageUtil.literal("§7Valor unitario: §a$" + String.format("%.2f", calculatePrice(data))));
        lore.add(MessageUtil.literal(" "));
        lore.add(MessageUtil.literal("§8[CobbleJobs] /job sell para vender"));
        stack.set(DataComponents.LORE, new ItemLore(lore));

        CompoundTag meta = new CompoundTag();
        meta.putString("species", species);
        meta.putDouble("weight", weight);
        meta.putInt("length", length);
        meta.putBoolean("shiny", shiny);
        meta.putString("rarity", rarity.name());
        meta.putDouble("minigame_mult", minigameMult);
        
        stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(meta));
        return stack;
    }

    public static FishData read(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        var customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return null;
        CompoundTag tag = customData.copyTag();
        if (!tag.contains("species")) return null;

        FishRarity rarity = FishRarity.COMMON;
        try { rarity = FishRarity.valueOf(tag.getString("rarity")); } catch (Exception ignored) {}
        
        double mult = tag.contains("minigame_mult") ? tag.getDouble("minigame_mult") : 1.0;

        // CORRECCIÓN 2: Ahora leemos y guardamos el count real del stack
        return new FishData(
            tag.getString("species"),
            tag.getDouble("weight"),
            tag.getInt("length"),
            tag.getBoolean("shiny"),
            rarity,
            mult,
            stack.getCount()
        );
    }

    public static double calculatePrice(FishData fish) {
        FisherConfig cfg = FisherConfig.get();
        double base  = fish.weight() * cfg.getPricePerKg();
        double price = base * fish.rarity().priceMultiplier;
        if (fish.shiny()) price *= cfg.getShinyMultiplier();
        return Math.round(price * fish.minigameMult() * 100.0) / 100.0;
    }

    public record FishData(String species, double weight, int length,
                           boolean shiny, FishRarity rarity, double minigameMult, int count) {}

    // ... Resto de métodos de la caña permanecen igual ...
    public static ItemStack createCustomRod() {
        ItemStack stack = new ItemStack(Items.FISHING_ROD);
        stack.set(DataComponents.CUSTOM_NAME, MessageUtil.literal("§b§lCaña Pokémon §7✦"));
        List<Component> lore = List.of(MessageUtil.literal("§7Una caña especial para Pokémon."));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        CompoundTag tag = new CompoundTag(); tag.putBoolean(ROD_NBT_KEY, true);
        stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
        return stack;
    }

    public static boolean isCustomRod(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean(ROD_NBT_KEY);
    }

    private static ItemStack buildPokemonModelItem(String speciesId, boolean shiny) {
        try {
            ItemStack stack = new ItemStack(CobblemonItems.POKEMON_MODEL);
            CompoundTag pokemonItemTag = new CompoundTag();
            pokemonItemTag.putString("species", speciesId.toLowerCase());
            ListTag aspects = new ListTag(); aspects.add(StringTag.valueOf(shiny ? "shiny" : ""));
            pokemonItemTag.put("aspects", aspects);
            setPokeballComponent(stack, pokemonItemTag);
            return stack;
        } catch (Exception e) { return new ItemStack(Items.TROPICAL_FISH); }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setPokeballComponent(ItemStack stack, CompoundTag tag) {
        try {
            var registry = net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE;
            var key = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblemon", "pokemon_item");
            var componentType = registry.get(key);
            if (componentType == null) return;
            var codec = componentType.codec();
            if (codec == null) return;
            codec.decode(net.minecraft.nbt.NbtOps.INSTANCE, tag).result().ifPresent(pair -> stack.set((net.minecraft.core.component.DataComponentType) componentType, pair.getFirst()));
        } catch (Exception ignored) {}
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
