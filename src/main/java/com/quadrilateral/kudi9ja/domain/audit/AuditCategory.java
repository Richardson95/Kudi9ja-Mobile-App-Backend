package com.quadrilateral.kudi9ja.domain.audit;

/** What part of the business an audit entry belongs to. */
public enum AuditCategory {

    GENERAL("General"),
    SETTINGS("Settings"),
    TEAM("Admin team"),
    CUSTOMER("Customer"),
    LOAN("Lending"),

    /** Every admin view of a customer's receipt is written here. */
    DATA_ACCESS("Data access"),

    /** Data-subject requests, complaints, breaches and collections contacts. */
    COMPLIANCE("Compliance");

    private final String label;

    AuditCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
