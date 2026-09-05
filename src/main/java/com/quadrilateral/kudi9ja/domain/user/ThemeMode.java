package com.quadrilateral.kudi9ja.domain.user;

/**
 * Which palette the customer has chosen, in Profile then Appearance.
 *
 * <p>It lives on the account rather than on the device so the choice follows
 * the customer between phones. Kudi9ja is gold on black, so the default is
 * dark: light is a deliberate choice, not something a phone setting turns on
 * for a customer who never asked.
 */
public enum ThemeMode {

    SYSTEM("Match my phone"),
    LIGHT("Light"),
    DARK("Dark");

    private final String label;

    ThemeMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
