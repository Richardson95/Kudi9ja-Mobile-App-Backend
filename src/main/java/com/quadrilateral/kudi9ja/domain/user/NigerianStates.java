package com.quadrilateral.kudi9ja.domain.user;

import java.util.List;
import java.util.Locale;

/** The thirty-six states and the Federal Capital Territory. */
public final class NigerianStates {

    private static final List<String> STATES = List.of(
            "Abia", "Adamawa", "Akwa Ibom", "Anambra", "Bauchi", "Bayelsa", "Benue", "Borno",
            "Cross River", "Delta", "Ebonyi", "Edo", "Ekiti", "Enugu", "FCT - Abuja", "Gombe",
            "Imo", "Jigawa", "Kaduna", "Kano", "Katsina", "Kebbi", "Kogi", "Kwara", "Lagos",
            "Nasarawa", "Niger", "Ogun", "Ondo", "Osun", "Oyo", "Plateau", "Rivers", "Sokoto",
            "Taraba", "Yobe", "Zamfara");

    private NigerianStates() {
    }

    public static List<String> all() {
        return STATES;
    }

    public static boolean isKnown(String state) {
        if (state == null) {
            return false;
        }
        String wanted = state.trim().toLowerCase(Locale.ROOT);
        return STATES.stream().anyMatch(known -> known.toLowerCase(Locale.ROOT).equals(wanted));
    }
}
