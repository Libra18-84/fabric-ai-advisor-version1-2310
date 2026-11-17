package com.fabric.fabricaiadvisor.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Shared normalization for every catalog search and validation path. */
public final class FabricText {
    private FabricText() {}

    public static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('đ', 'd').replace('_', ' ')
                .replaceAll("\\s+", " ").trim();
    }

    public static boolean contains(String text, String phrase) {
        return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(phrase) + "(?![a-z0-9])")
                .matcher(text).find();
    }

    public static String garment(String value) {
        String text = normalize(value);
        return switch (text) {
            case "ao so mi", "so mi" -> "so mi";
            case "ao thun", "ao phong", "thun" -> "thun";
            case "dam", "vay", "vay / dam" -> "vay";
            default -> text;
        };
    }

    public static boolean matchesGarment(String description, String requested) {
        String keyword = garment(requested);
        return keyword.isEmpty() || contains(normalize(description), keyword);
    }
}
