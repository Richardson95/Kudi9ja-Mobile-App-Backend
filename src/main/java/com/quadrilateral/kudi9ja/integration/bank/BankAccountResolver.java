package com.quadrilateral.kudi9ja.integration.bank;

/**
 * Name enquiry: who owns a given bank account.
 *
 * <p>The client cannot check that a payout account belongs to the customer, so
 * the server must. The Terms promise payouts only to the customer, and this is
 * how that promise is kept: resolve the account name and refuse a mismatch.
 */
public interface BankAccountResolver {

    /**
     * @param bankCode      the NIP code from {@link BankDirectory}
     * @param accountNumber ten digits
     */
    Resolution resolve(String bankCode, String accountNumber);

    /**
     * @param resolved   whether the account exists
     * @param accountName the name the bank holds on it
     * @param reason     why it could not be resolved, when it could not
     */
    record Resolution(boolean resolved, String accountName, String bankCode, String reason) {

        public static Resolution failed(String reason) {
            return new Resolution(false, null, null, reason);
        }
    }
}
