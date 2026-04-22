package com.tuservidor.cobblejobs.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import java.util.HashMap;
import java.util.Map;

public class MessageUtil {

    private static final Map<Character, Character> SMALL_CAPS = new HashMap<>();

    static {
        String normal = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        String small  = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"; 
        for (int i = 0; i < normal.length(); i++) {
            SMALL_CAPS.put(normal.charAt(i), small.charAt(i));
        }
    }

    public static MutableComponent literal(String text) {
        return Component.literal(text);
    }

    public static String toSmallCaps(String input) {
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            sb.append(SMALL_CAPS.getOrDefault(c, c));
        }
        return sb.toString();
    }

    // CORRECCIÓN 3: Método único centralizado para todo el mod
    public static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
