package com.quadrilateral.kudi9ja.integration.bank;

import java.util.List;

/**
 * The banks a customer may nominate for payouts, with their NIP codes.
 *
 * <p>Served to the app so the payout step offers a list rather than a free-text
 * field, and so a name enquiry has a code to send.
 */
public final class BankDirectory {

    /** A bank, as the customer picks it and as the rails address it. */
    public record Bank(String code, String name) {
    }

    private static final List<Bank> BANKS = List.of(
            new Bank("044", "Access Bank"),
            new Bank("063", "Access Bank (Diamond)"),
            new Bank("035A", "ALAT by Wema"),
            new Bank("401", "ASO Savings and Loans"),
            new Bank("023", "Citibank Nigeria"),
            new Bank("050", "Ecobank Nigeria"),
            new Bank("562", "Ekondo Microfinance Bank"),
            new Bank("070", "Fidelity Bank"),
            new Bank("011", "First Bank of Nigeria"),
            new Bank("214", "First City Monument Bank"),
            new Bank("501", "FSDH Merchant Bank"),
            new Bank("00103", "Globus Bank"),
            new Bank("058", "Guaranty Trust Bank"),
            new Bank("030", "Heritage Bank"),
            new Bank("301", "Jaiz Bank"),
            new Bank("082", "Keystone Bank"),
            new Bank("50211", "Kuda Microfinance Bank"),
            new Bank("565", "Carbon"),
            new Bank("526", "Parallex Bank"),
            new Bank("076", "Polaris Bank"),
            new Bank("101", "Providus Bank"),
            new Bank("221", "Stanbic IBTC Bank"),
            new Bank("068", "Standard Chartered Bank"),
            new Bank("232", "Sterling Bank"),
            new Bank("100", "Suntrust Bank"),
            new Bank("102", "Titan Trust Bank"),
            new Bank("032", "Union Bank of Nigeria"),
            new Bank("033", "United Bank for Africa"),
            new Bank("215", "Unity Bank"),
            new Bank("035", "Wema Bank"),
            new Bank("057", "Zenith Bank"),
            new Bank("50515", "Moniepoint Microfinance Bank"),
            new Bank("50823", "CEMCS Microfinance Bank"),
            new Bank("999991", "PalmPay"),
            new Bank("999992", "OPay Digital Services"));

    private BankDirectory() {
    }

    public static List<Bank> all() {
        return BANKS;
    }

    public static java.util.Optional<Bank> byName(String name) {
        if (name == null) {
            return java.util.Optional.empty();
        }
        return BANKS.stream()
                .filter(bank -> bank.name().equalsIgnoreCase(name.trim()))
                .findFirst();
    }

    public static java.util.Optional<Bank> byCode(String code) {
        if (code == null) {
            return java.util.Optional.empty();
        }
        return BANKS.stream()
                .filter(bank -> bank.code().equalsIgnoreCase(code.trim()))
                .findFirst();
    }

    public static boolean isKnown(String name) {
        return byName(name).isPresent();
    }
}
