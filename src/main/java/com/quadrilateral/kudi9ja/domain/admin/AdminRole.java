package com.quadrilateral.kudi9ja.domain.admin;

/**
 * What an admin is allowed to do.
 *
 * <p>Every one of these is enforced server-side on every request. The client's
 * role check is a convenience for hiding buttons and nothing more.
 */
public enum AdminRole {

    OWNER("Owner", "Full control, including rates and managing the admin team"),
    ADMIN("Administrator", "Everything except adding or removing other admins"),
    SUPPORT("Support", "View customers and act on loans; cannot change rates"),
    VIEWER("Viewer", "Read-only access to the panel");

    private final String label;
    private final String blurb;

    AdminRole(String label, String blurb) {
        this.label = label;
        this.blurb = blurb;
    }

    public String label() {
        return label;
    }

    public String blurb() {
        return blurb;
    }

    /** Can change platform rates, limits and feature switches. */
    public boolean canEditSettings() {
        return this == OWNER || this == ADMIN;
    }

    /** Can add, promote, suspend or remove other admins. */
    public boolean canManageTeam() {
        return this == OWNER;
    }

    /** Can act on a loan: remind, write off, decide. */
    public boolean canActOnLoans() {
        return this != VIEWER;
    }

    /**
     * Can confirm or reject a pay-in, and approve or decline a withdrawal.
     * Money only moves on this permission.
     */
    public boolean canApprovePayments() {
        return this != VIEWER;
    }

    /** Can flag or freeze a customer. */
    public boolean canManageCustomers() {
        return this != VIEWER;
    }

    /** Can open a customer's full record. Every admin can. */
    public boolean canViewCustomers() {
        return true;
    }

    /**
     * Can read the audit log. Everyone can: being watched is the point, and
     * hiding the record from part of the team would defeat it.
     */
    public boolean canViewAudit() {
        return true;
    }
}
