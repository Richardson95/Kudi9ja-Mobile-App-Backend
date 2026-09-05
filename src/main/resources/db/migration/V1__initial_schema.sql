-- Kudi9ja — initial schema
--
-- Quadrilateral Technologies Limited (RC 1657731), Lagos, Nigeria.
--
-- Two things are worth knowing before reading this.
--
-- First, there are no account numbers in here. Kudi9ja issues none. What a
-- customer has is a customer reference (app_user.customer_ref, "K9-A1B2C3")
-- which exists to match a payment on a bank statement and is not payable into.
-- The only real account numbers stored are the customer's own payout account
-- and the company's collection account, and both are destinations rather than
-- identities.
--
-- Second, wallet_transaction is an append-only ledger and wallet.balance is its
-- running total. Nothing in this schema deletes a transaction: a reversal is a
-- status change on the same row. The balance must be reconstructible by
-- replaying the ledger, and any divergence is a defect.


-- ─────────────────────────────────────────────────────────────────────────────
-- People
-- ─────────────────────────────────────────────────────────────────────────────

create table app_user (
    id                       uuid         not null,
    customer_ref             varchar(16)  not null,
    full_name                varchar(160) not null,
    email                    varchar(190) not null,
    -- Required at sign-up and enforced there, but nullable here: an erasure has
    -- to be able to clear them once the retention period ends, and a not-null
    -- column would leave the choice between keeping a real phone number and a
    -- real date of birth for ever, or writing false ones. Both are worse.
    phone                    varchar(20),
    date_of_birth            date,
    gender                   varchar(32),

    -- Held because the AML rules require identity records for five years.
    -- Never returned by any endpoint: responses carry the last four digits.
    bvn                      varchar(11),
    nin                      varchar(11),
    bvn_verified_at          timestamp(6) with time zone,
    nin_verified_at          timestamp(6) with time zone,

    address                  varchar(400),
    state                    varchar(60),

    -- Where money leaves to. Resolved with the bank and name-matched when set,
    -- because the Terms promise payouts only to an account in the customer's
    -- own name.
    payout_bank              varchar(120),
    payout_account_number    varchar(10),
    payout_account_name      varchar(160),
    payout_verified_at       timestamp(6) with time zone,

    -- Slow KDF output with a per-user salt. The pepper lives in secret config
    -- and never in this database, so a dump of these columns alone is not
    -- enough to attack them offline.
    password_hash            varchar(300) not null,
    passcode_hash            varchar(300),
    pin_hash                 varchar(300),
    security_question        varchar(200),
    security_answer_hash     varchar(300),

    kyc_tier                 varchar(16)  not null,
    email_verified           boolean      not null default false,
    phone_verified           boolean      not null default false,
    biometrics_enabled       boolean      not null default false,

    -- Counted here rather than on the device: a client-side counter is reset
    -- by reinstalling the app, which makes it no counter at all.
    failed_passcode_attempts integer      not null default 0,
    locked_until             timestamp(6) with time zone,

    -- On the account rather than the device, so the choice follows the
    -- customer between phones.
    theme_mode               varchar(16)  not null,
    hide_balance             boolean      not null default false,
    auto_debit               boolean      not null default false,

    account_status           varchar(24)  not null,
    status_note              varchar(500),

    created_at               timestamp(6) with time zone not null,
    last_active_at           timestamp(6) with time zone,

    -- Soft close. A deletion request cannot override the five-year AML
    -- retention, so an account is closed and retained, never erased.
    closed_at                timestamp(6) with time zone,
    retain_until             timestamp(6) with time zone,

    row_version              bigint       not null default 0,

    constraint pk_app_user           primary key (id),
    constraint ix_user_email         unique (email),
    constraint ix_user_customer_ref  unique (customer_ref),
    constraint ck_user_kyc_tier      check (kyc_tier in ('TIER0', 'TIER1', 'TIER2')),
    constraint ck_user_theme         check (theme_mode in ('SYSTEM', 'LIGHT', 'DARK')),
    constraint ck_user_status
        check (account_status in ('ACTIVE', 'FLAGGED', 'FROZEN', 'DORMANT', 'CLOSED'))
);

create index ix_user_phone  on app_user (phone);
create index ix_user_status on app_user (account_status);

-- A BVN and an NIN each belong to one person, so they belong to one account.
-- Partial, because both are null until step three of the sign-up.
create unique index ix_user_bvn on app_user (bvn) where bvn is not null;
create unique index ix_user_nin on app_user (nin) where nin is not null;


-- A part-finished sign-up. Holds a BVN and an NIN before an account exists,
-- which is why it expires on a short clock and is purged nightly.
create table signup_draft (
    id                    uuid         not null,
    step                  varchar(24)  not null,
    full_name             varchar(160),
    email                 varchar(190) not null,
    phone                 varchar(20),
    date_of_birth         date,
    gender                varchar(32),
    email_verified_at     timestamp(6) with time zone,
    bvn                   varchar(11),
    nin                   varchar(11),
    address               varchar(400),
    state                 varchar(60),
    identity_verified_at  timestamp(6) with time zone,
    payout_bank           varchar(120),
    payout_account_number varchar(10),
    payout_account_name   varchar(160),
    password_hash         varchar(300),
    security_question     varchar(200),
    security_answer_hash  varchar(300),
    passcode_hash         varchar(300),
    pin_hash              varchar(300),
    created_at            timestamp(6) with time zone not null,
    updated_at            timestamp(6) with time zone not null,
    expires_at            timestamp(6) with time zone not null,
    completed_at          timestamp(6) with time zone,
    user_id               uuid,

    -- Recorded against the legal acceptance as evidence under the Evidence
    -- Act 2011, which the Terms rely on.
    device                varchar(300),
    ip_address            varchar(64),

    constraint pk_signup_draft primary key (id),
    constraint ck_draft_step check (step in (
        'PERSONAL', 'EMAIL_VERIFIED', 'IDENTITY', 'PAYOUT',
        'PASSWORD', 'PASSCODE', 'PIN', 'COMPLETE'))
);

create index ix_draft_email   on signup_draft (email);
create index ix_draft_expires on signup_draft (expires_at);


create table user_session (
    id                 uuid        not null,
    user_id            uuid        not null,

    -- Refresh tokens rotate: the id of the one currently valid. A spent token
    -- is refused a second time, so a stolen one is good for one exchange.
    refresh_token_id   varchar(64),
    refresh_expires_at timestamp(6) with time zone,

    created_at         timestamp(6) with time zone not null,
    last_seen_at       timestamp(6) with time zone,
    revoked_at         timestamp(6) with time zone,
    revoked_reason     varchar(200),

    -- What the customer sees on their own security screen.
    device_label       varchar(200),
    ip_address         varchar(64),

    constraint pk_user_session primary key (id),
    constraint fk_session_user foreign key (user_id) references app_user (id)
);

create index ix_session_user   on user_session (user_id);
create index ix_session_expiry on user_session (refresh_expires_at);


create table one_time_code (
    id          uuid         not null,
    target      varchar(190) not null,
    purpose     varchar(32)  not null,

    -- Hashed like every other secret. A code readable from the database is a
    -- code an operator can use.
    code_hash   varchar(300) not null,

    created_at  timestamp(6) with time zone not null,
    expires_at  timestamp(6) with time zone not null,
    consumed_at timestamp(6) with time zone,
    attempts    integer      not null default 0,
    user_id     uuid,

    constraint pk_one_time_code primary key (id),
    constraint ck_otp_purpose check (purpose in (
        'SIGNUP_EMAIL', 'PASSWORD_RESET', 'REACTIVATION',
        'PAYOUT_CHANGE', 'ACCOUNT_CLOSURE'))
);

create index ix_otp_target  on one_time_code (target, purpose);
create index ix_otp_expires on one_time_code (expires_at);


-- ─────────────────────────────────────────────────────────────────────────────
-- The wallet and its ledger
-- ─────────────────────────────────────────────────────────────────────────────

create table wallet (
    id            uuid          not null,
    user_id       uuid          not null,
    balance       numeric(19, 2) not null default 0,

    -- Monotonic per wallet. Gives the ledger a total order that survives two
    -- transactions landing in the same microsecond.
    next_sequence bigint        not null default 1,

    created_at    timestamp(6) with time zone not null,
    updated_at    timestamp(6) with time zone not null,
    row_version   bigint        not null default 0,

    constraint pk_wallet       primary key (id),
    constraint ix_wallet_user  unique (user_id),
    constraint fk_wallet_user  foreign key (user_id) references app_user (id),

    -- A wallet is not an overdraft. Nothing in this product lends by letting a
    -- balance go negative, so the database refuses one outright.
    constraint ck_wallet_balance_not_negative check (balance >= 0)
);


create table wallet_transaction (
    id            uuid           not null,
    wallet_id     uuid           not null,
    user_id       uuid           not null,
    kind          varchar(32)    not null,

    -- Always positive. Whether a row adds or subtracts is decided by `kind`,
    -- not by a sign, so a mistyped minus cannot invent money.
    amount        numeric(19, 2) not null,

    -- The balance this row left behind. What makes the ledger auditable
    -- without replaying it.
    balance_after numeric(19, 2) not null,

    description   varchar(300)   not null,
    counterparty  varchar(160),
    reference     varchar(64)    not null,
    status        varchar(16)    not null,
    occurred_at   timestamp(6) with time zone not null,
    sequence      bigint         not null,

    -- What this movement was about: a plan, a loan, a claim, a circle.
    related_type  varchar(32),
    related_id    uuid,

    constraint pk_wallet_transaction primary key (id),
    constraint ix_txn_sequence       unique (wallet_id, sequence),
    constraint fk_txn_wallet         foreign key (wallet_id) references wallet (id),
    constraint fk_txn_user           foreign key (user_id) references app_user (id),
    constraint ck_txn_amount_positive check (amount > 0),
    constraint ck_txn_status check (status in ('PENDING', 'SUCCESSFUL', 'REVERSED')),
    constraint ck_txn_kind check (kind in (
        'DEPOSIT', 'WITHDRAWAL',
        'SAVINGS_LOCK', 'INTEREST_PAYOUT', 'SAVINGS_RELEASE',
        'LOAN_DISBURSEMENT', 'LOAN_REPAYMENT', 'FEE'))
);

create index ix_txn_wallet_date on wallet_transaction (wallet_id, occurred_at);
create index ix_txn_user_date   on wallet_transaction (user_id, occurred_at);
create index ix_txn_reference   on wallet_transaction (reference);
create index ix_txn_kind        on wallet_transaction (kind);


-- ─────────────────────────────────────────────────────────────────────────────
-- Money in
-- ─────────────────────────────────────────────────────────────────────────────

create table pay_in_claim (
    id                   uuid           not null,
    user_id              uuid           not null,
    customer_name        varchar(160)   not null,
    customer_ref         varchar(16)    not null,
    amount               numeric(19, 2) not null,

    -- Unique to this payment, not to this customer. Two transfers of the same
    -- amount on the same day are otherwise indistinguishable on a statement,
    -- which is the one thing the reference exists to prevent.
    reference            varchar(64)    not null,

    purpose              varchar(24)    not null,
    loan_id              uuid,
    loan_purpose         varchar(200),

    -- Mandatory. A claim cannot be submitted without one, and the object is
    -- kept five years as part of the transaction record.
    receipt_key          varchar(300)   not null,
    receipt_content_type varchar(120),
    receipt_size_bytes   bigint,

    sender_name          varchar(160),
    sender_bank          varchar(120),

    status               varchar(16)    not null,
    claimed_at           timestamp(6) with time zone not null,
    reviewed_at          timestamp(6) with time zone,
    reviewed_by          varchar(200),
    note                 varchar(1000),
    row_version          bigint         not null default 0,

    constraint pk_pay_in_claim   primary key (id),
    constraint ix_payin_reference unique (reference),
    constraint fk_payin_user     foreign key (user_id) references app_user (id),
    constraint ck_payin_amount   check (amount > 0),
    constraint ck_payin_status   check (status in ('PENDING', 'CONFIRMED', 'REJECTED')),
    constraint ck_payin_purpose  check (purpose in ('WALLET', 'LOAN_REPAYMENT')),

    -- A rejection has to say why. The customer is told the reason, and a claim
    -- that merely stopped being pending is how somebody comes to believe their
    -- money was taken.
    constraint ck_payin_rejection_has_reason
        check (status <> 'REJECTED' or note is not null)
);

create index ix_payin_user   on pay_in_claim (user_id, claimed_at);
create index ix_payin_status on pay_in_claim (status, claimed_at);


-- Money that arrived without an owner. Held while it is traced and returned to
-- source after thirty days, which the Terms commit to; return_due_at is that
-- deadline made explicit rather than recomputed.
create table unmatched_pay_in (
    id               uuid           not null,
    amount           numeric(19, 2) not null,
    narration        varchar(500),
    sender_name      varchar(160),
    sender_bank      varchar(120),
    sender_account   varchar(32),
    bank_reference   varchar(120),
    received_at      timestamp(6) with time zone not null,
    recorded_at      timestamp(6) with time zone not null,
    recorded_by      varchar(200),
    status           varchar(16)    not null,
    return_due_at    timestamp(6) with time zone not null,
    resolved_at      timestamp(6) with time zone,
    resolved_by      varchar(200),
    matched_user_id  uuid,
    matched_claim_id uuid,
    trace_notes      varchar(2000),

    constraint pk_unmatched_pay_in primary key (id),
    constraint ck_unmatched_amount check (amount > 0),
    constraint ck_unmatched_status
        check (status in ('HELD', 'MATCHED', 'RETURNED', 'UNRETURNABLE'))
);

create index ix_unmatched_status    on unmatched_pay_in (status, received_at);
create index ix_unmatched_narration on unmatched_pay_in (narration);

-- The same bank credit must not be recorded twice.
create unique index ix_unmatched_bank_reference
    on unmatched_pay_in (bank_reference) where bank_reference is not null;


-- ─────────────────────────────────────────────────────────────────────────────
-- Money out
-- ─────────────────────────────────────────────────────────────────────────────

-- The id is deliberately the same as the pending ledger transaction's. The
-- wallet is debited at request, not at approval, so the same money cannot be
-- spent twice while an admin is looking at it; approving settles that row and
-- declining reverses it and refunds in full.
create table withdrawal_request (
    id                  uuid           not null,
    user_id             uuid           not null,
    customer_name       varchar(160)   not null,
    customer_ref        varchar(16)    not null,
    amount              numeric(19, 2) not null,
    bank                varchar(120)   not null,
    destination_account varchar(10)    not null,
    destination_name    varchar(160),
    reference           varchar(64)    not null,
    status              varchar(16)    not null,
    requested_at        timestamp(6) with time zone not null,
    reviewed_at         timestamp(6) with time zone,
    reviewed_by         varchar(200),

    -- The reference of the transfer that actually paid it, so a question six
    -- months later has a factual answer.
    payout_reference    varchar(120),
    note                varchar(1000),
    row_version         bigint         not null default 0,

    constraint pk_withdrawal_request primary key (id),
    constraint fk_withdrawal_user    foreign key (user_id) references app_user (id),
    constraint fk_withdrawal_txn     foreign key (id) references wallet_transaction (id),
    constraint ck_withdrawal_amount  check (amount > 0),
    constraint ck_withdrawal_status  check (status in ('PENDING', 'APPROVED', 'DECLINED')),
    constraint ck_withdrawal_decline_has_reason
        check (status <> 'DECLINED' or note is not null)
);

create index ix_withdrawal_user      on withdrawal_request (user_id, requested_at);
create index ix_withdrawal_status    on withdrawal_request (status, requested_at);
create index ix_withdrawal_reference on withdrawal_request (reference);


-- ─────────────────────────────────────────────────────────────────────────────
-- Savings
-- ─────────────────────────────────────────────────────────────────────────────

-- annual_rate and bonus_rate are stored on the row, not read from settings.
-- A running plan keeps the terms it was opened on: changing the rate card
-- tomorrow must not rewrite what a customer was promised today.
create table savings_plan (
    id                   uuid           not null,
    user_id              uuid           not null,
    title                varchar(120)   not null,
    type                 varchar(16)    not null,
    principal            numeric(19, 2) not null default 0,
    lock_days            integer        not null,

    -- Paid into the wallet the moment a Fixed plan opens, which is only sound
    -- because the principal cannot be taken back out.
    interest_paid        numeric(19, 2) not null default 0,
    annual_rate          numeric(12, 6),

    start_date           timestamp(6) with time zone not null,
    maturity_date        timestamp(6) with time zone not null,
    status               varchar(40)    not null,
    emoji                varchar(16),

    target_amount        numeric(19, 2),
    target_months        integer,
    auto_frequency       varchar(16),
    auto_amount          numeric(19, 2),
    next_auto_run        timestamp(6) with time zone,
    auto_enabled         boolean        not null default false,

    bonus_rate           numeric(12, 6) not null default 0,
    bonus_paid           boolean        not null default false,
    contributions        integer        not null default 0,

    -- A missed auto-save is skipped and retried, never failed and never
    -- charged for. Counted so the bonus is paid on what was actually saved.
    missed_contributions integer        not null default 0,

    closed_at            timestamp(6) with time zone,
    closure_note         varchar(500),
    row_version          bigint         not null default 0,

    constraint pk_savings_plan primary key (id),
    constraint fk_plan_user    foreign key (user_id) references app_user (id),
    constraint ck_plan_type    check (type in ('FIXED', 'TARGET')),
    constraint ck_plan_status  check (status in (
        'ACTIVE', 'MATURED', 'WITHDRAWN', 'BROKEN',
        'RELEASED_ON_COMPASSIONATE_GROUNDS')),
    constraint ck_plan_frequency
        check (auto_frequency is null or auto_frequency in ('DAILY', 'WEEKLY', 'MONTHLY')),
    constraint ck_plan_principal check (principal >= 0),
    constraint ck_plan_matures_after_it_starts check (maturity_date > start_date)
);

create index ix_plan_user     on savings_plan (user_id, start_date);
create index ix_plan_status   on savings_plan (status);
create index ix_plan_maturity on savings_plan (status, maturity_date);
create index ix_plan_auto     on savings_plan (auto_enabled, next_auto_run);


-- ─────────────────────────────────────────────────────────────────────────────
-- Lending
-- ─────────────────────────────────────────────────────────────────────────────

-- flat_rate is frozen onto the loan at disbursement. Interest is flat: computed
-- once on the principal, never compounding and never growing. There is no late
-- fee and no penalty interest column anywhere in this table, because there is
-- no late fee and no penalty interest in this product — the Lending Agreement
-- commits to that in writing.
create table loan (
    id                uuid           not null,
    user_id           uuid           not null,
    principal         numeric(19, 2) not null,
    tenure_months     integer        not null,
    flat_rate         numeric(12, 6) not null,

    -- Deducted from the disbursement, never added to the debt.
    processing_fee    numeric(19, 2) not null default 0,

    purpose           varchar(200)   not null,
    requested_at      timestamp(6) with time zone not null,
    disbursed_at      timestamp(6) with time zone,
    due_date          timestamp(6) with time zone,
    amount_repaid     numeric(19, 2) not null default 0,

    -- Interest given back on early settlement. Recorded rather than netted off
    -- amount_repaid, so a rebate is never mistaken for a payment.
    rebate_granted    numeric(19, 2) not null default 0,

    status            varchar(24)    not null,
    settled_at        timestamp(6) with time zone,

    -- A declined or reduced decision records its reasons, because the Privacy
    -- Policy gives the customer the right to a human review of it.
    decision_reasons  varchar(2000),
    score_at_decision integer,
    decided_by        varchar(200),
    write_off_note    varchar(1000),
    row_version       bigint         not null default 0,

    constraint pk_loan          primary key (id),
    constraint fk_loan_user     foreign key (user_id) references app_user (id),
    constraint ck_loan_status   check (status in (
        'PENDING', 'ACTIVE', 'REPAID', 'OVERDUE',
        'REJECTED', 'CANCELLED', 'WRITTEN_OFF')),
    constraint ck_loan_principal check (principal > 0),
    constraint ck_loan_tenure    check (tenure_months >= 1),
    constraint ck_loan_repaid_not_negative check (amount_repaid >= 0),
    constraint ck_loan_writeoff_has_reason
        check (status <> 'WRITTEN_OFF' or write_off_note is not null)
);

create index ix_loan_user   on loan (user_id, disbursed_at);
create index ix_loan_status on loan (status);
create index ix_loan_due    on loan (status, due_date);

-- The repayment schedule is derived from what has been repaid, never stored.
-- A stored schedule drifts from the ledger; a derived one cannot.


-- ─────────────────────────────────────────────────────────────────────────────
-- Thrift circles (ajo / esusu / adashe)
-- ─────────────────────────────────────────────────────────────────────────────

create table thrift_circle (
    id            uuid           not null,
    name          varchar(120)   not null,
    contribution  numeric(19, 2) not null,
    frequency     varchar(16)    not null,
    start_date    timestamp(6) with time zone not null,
    created_by    uuid           not null,
    current_round integer        not null default 1,
    invite_code   varchar(40)    not null,
    emoji         varchar(16),
    started       boolean        not null default false,
    created_at    timestamp(6) with time zone not null,
    completed_at  timestamp(6) with time zone,
    row_version   bigint         not null default 0,

    constraint pk_thrift_circle  primary key (id),
    constraint ix_circle_invite  unique (invite_code),
    constraint fk_circle_creator foreign key (created_by) references app_user (id),
    constraint ck_circle_frequency check (frequency in ('DAILY', 'WEEKLY', 'MONTHLY')),
    constraint ck_circle_contribution check (contribution > 0),
    constraint ck_circle_round check (current_round >= 1)
);

create index ix_circle_creator on thrift_circle (created_by);


-- Every member resolves to a real account. The client took typed names and
-- asked that they be existing customers, which is a request rather than a rule:
-- a circle built around people who do not exist collects nothing from them and
-- pays the pot out anyway. The foreign key is what makes it a rule.
create table thrift_member (
    id           uuid         not null,
    circle_id    uuid         not null,
    user_id      uuid         not null,
    customer_ref varchar(16)  not null,
    display_name varchar(160) not null,
    initials     varchar(8),
    position     integer      not null,
    joined_at    timestamp(6) with time zone not null,
    left_at      timestamp(6) with time zone,

    constraint pk_thrift_member primary key (id),
    constraint ix_member_unique unique (circle_id, user_id),
    constraint fk_member_circle foreign key (circle_id) references thrift_circle (id)
        on delete cascade,
    constraint fk_member_user   foreign key (user_id) references app_user (id)
);

create index ix_member_circle on thrift_member (circle_id, position);
create index ix_member_user   on thrift_member (user_id);


create table thrift_contribution (
    id             uuid           not null,
    circle_id      uuid           not null,
    user_id        uuid           not null,
    round          integer        not null,
    amount         numeric(19, 2) not null,
    paid_at        timestamp(6) with time zone not null,
    transaction_id uuid,

    constraint pk_thrift_contribution primary key (id),

    -- One contribution per member per round. Paying a round twice would fund
    -- somebody else's pot out of the payer's pocket.
    constraint ix_contribution_unique unique (circle_id, user_id, round),
    constraint fk_contribution_circle foreign key (circle_id) references thrift_circle (id)
        on delete cascade,
    constraint fk_contribution_user   foreign key (user_id) references app_user (id),
    constraint fk_contribution_txn    foreign key (transaction_id)
        references wallet_transaction (id),
    constraint ck_contribution_amount check (amount > 0)
);

create index ix_contribution_circle_round on thrift_contribution (circle_id, round);


-- ─────────────────────────────────────────────────────────────────────────────
-- Platform settings
-- ─────────────────────────────────────────────────────────────────────────────

-- Versioned, never edited in place. Each save writes a new row and every row is
-- kept, so the terms a plan or loan was opened under can always be produced.
create table platform_settings (
    version                        bigint         not null,
    created_at                     timestamp(6) with time zone not null,
    created_by                     varchar(160)   not null,

    savings_annual_rate            numeric(12, 6) not null,
    min_lock_days                  integer        not null,
    max_lock_days                  integer        not null,
    days_per_year                  integer        not null,
    min_savings_amount             numeric(19, 2) not null,
    max_savings_amount             numeric(19, 2) not null,
    target_rate_short              numeric(12, 6) not null,
    target_rate_medium             numeric(12, 6) not null,
    target_rate_long               numeric(12, 6) not null,
    target_tier_medium             integer        not null,
    target_tier_long               integer        not null,
    min_target_months              integer        not null,
    days_per_savings_month         integer        not null,

    max_loan_tenure_months         integer        not null,
    min_loan_amount                numeric(19, 2) not null,
    max_loan_amount                numeric(19, 2) not null,
    early_payoff_rebate_share      numeric(12, 6) not null,
    loan_cancellation_hours        integer        not null,

    flat_processing_fee            numeric(19, 2) not null,
    processing_fee_threshold       numeric(19, 2) not null,
    loan_processing_fee_rate       numeric(12, 6) not null,

    loan_base_cap                  numeric(19, 2) not null,
    loan_savings_multiple          numeric(12, 4) not null,
    loan_score_baseline            integer        not null,
    loan_score_per_point           numeric(19, 2) not null,
    loan_offer_rounding            numeric(19, 2) not null,

    credit_base_score              integer        not null,
    credit_points_per_plan         integer        not null,
    credit_plan_points_cap         integer        not null,
    credit_naira_per_savings_point numeric(19, 2) not null,
    credit_savings_points_cap      integer        not null,
    credit_points_per_repaid_loan  integer        not null,
    credit_repaid_points_cap       integer        not null,
    credit_overdue_penalty         integer        not null,
    credit_verified_bonus          integer        not null,
    credit_score_floor             integer        not null,
    credit_score_ceiling           integer        not null,

    max_passcode_attempts          integer        not null,
    lock_timeout_minutes           integer        not null,
    otp_resend_seconds             integer        not null,

    min_deposit_amount             numeric(19, 2) not null,
    min_withdrawal_amount          numeric(19, 2) not null,

    min_circle_contribution        numeric(19, 2) not null,
    min_circle_members             integer        not null,
    max_circle_members             integer        not null,

    -- The company's collection account. The same for everybody, and not a
    -- customer account: Kudi9ja issues none.
    company_account_name           varchar(160)   not null,
    company_account_number         varchar(32)    not null,
    company_bank                   varchar(120)   not null,

    savings_enabled                boolean        not null default true,
    lending_enabled                boolean        not null default true,
    thrift_enabled                 boolean        not null default true,
    maintenance_mode               boolean        not null default false,

    constraint pk_platform_settings primary key (version),

    -- Validated in the service too, and again here: a settings document that
    -- got past the service with min above max would price every plan after it
    -- wrongly, and nothing would notice.
    constraint ck_settings_lock_days   check (min_lock_days < max_lock_days),
    constraint ck_settings_loan_amount check (min_loan_amount < max_loan_amount),
    constraint ck_settings_savings_rate check (savings_annual_rate > 0),
    constraint ck_settings_score_band  check (credit_score_floor < credit_score_ceiling),
    constraint ck_settings_target_tiers check (target_tier_medium < target_tier_long),
    constraint ck_settings_days_per_year check (days_per_year > 0)
);


-- Flat interest by tenure. Every selectable tenure has a row.
create table platform_loan_rate (
    settings_version bigint         not null,
    tenure_months    integer        not null,
    flat_rate        numeric(12, 6) not null,

    constraint pk_platform_loan_rate primary key (settings_version, tenure_months),
    constraint fk_loan_rate_settings foreign key (settings_version)
        references platform_settings (version) on delete cascade,
    constraint ck_loan_rate_tenure check (tenure_months >= 1),
    constraint ck_loan_rate_positive check (flat_rate > 0)
);


-- ─────────────────────────────────────────────────────────────────────────────
-- Legal documents
-- ─────────────────────────────────────────────────────────────────────────────

-- Every published version is kept, permanently. The record of which version a
-- customer accepted is worth little if the document it names can no longer be
-- produced.
create table legal_document (
    id             uuid         not null,
    kind           varchar(16)  not null,
    version        varchar(24)  not null,
    title          varchar(200) not null,
    short_title    varchar(120) not null,
    summary        varchar(1000) not null,
    read_minutes   integer      not null,

    -- text, not a large object. On PostgreSQL a @Lob String becomes an oid,
    -- which needs the LOB API to read and fails through a plain accessor.
    body_json      text         not null,

    effective_from timestamp(6) with time zone not null,

    -- When customers were told it was coming. The company gives thirty days'
    -- notice before a material change takes effect, and this is what makes
    -- that measurable rather than merely intended.
    announced_at   timestamp(6) with time zone,

    published_at   timestamp(6) with time zone not null,
    published_by   varchar(200),
    change_summary varchar(2000),

    constraint pk_legal_document      primary key (id),
    constraint ix_legal_kind_version  unique (kind, version),
    constraint ck_legal_kind check (kind in ('TERMS', 'PRIVACY', 'LENDING'))
);

create index ix_legal_effective on legal_document (kind, effective_from);


-- Which version of each document a customer accepted, when, and on what
-- device. The Terms rely on this as evidence under the Evidence Act 2011.
create table legal_acceptance (
    id               uuid        not null,
    user_id          uuid        not null,
    kind             varchar(16) not null,
    document_id      uuid        not null,
    document_version varchar(24) not null,
    accepted_at      timestamp(6) with time zone not null,
    device           varchar(300),
    ip_address       varchar(64),

    constraint pk_legal_acceptance primary key (id),
    constraint ix_acceptance_doc   unique (user_id, kind, document_version),
    constraint fk_acceptance_user  foreign key (user_id) references app_user (id),
    constraint fk_acceptance_doc   foreign key (document_id) references legal_document (id),
    constraint ck_acceptance_kind  check (kind in ('TERMS', 'PRIVACY', 'LENDING'))
);

create index ix_acceptance_user on legal_acceptance (user_id);


-- ─────────────────────────────────────────────────────────────────────────────
-- The admin panel
-- ─────────────────────────────────────────────────────────────────────────────

-- A grant creates no account and no password. It means: when somebody signs in
-- with that email, the panel appears. The foreign key is what enforces that
-- access can only be given to an email that already belongs to an account.
create table admin_user (
    id             uuid         not null,
    user_id        uuid         not null,
    email          varchar(190) not null,
    name           varchar(160) not null,
    phone          varchar(20),
    role           varchar(16)  not null,
    added_at       timestamp(6) with time zone not null,
    added_by       varchar(200),

    -- Suspended rather than removed, so the record of what they did survives.
    active         boolean      not null default true,
    last_active_at timestamp(6) with time zone,

    constraint pk_admin_user primary key (id),
    constraint ix_admin_email unique (email),
    constraint fk_admin_user  foreign key (user_id) references app_user (id),
    constraint ck_admin_role  check (role in ('OWNER', 'ADMIN', 'SUPPORT', 'VIEWER'))
);

create index ix_admin_user on admin_user (user_id);


-- Append-only. No edit, no delete, ever — there is no endpoint for either, and
-- nothing in this schema cascades into it.
create table audit_entry (
    id            uuid          not null,
    actor         varchar(200)  not null,
    actor_id      uuid,
    category      varchar(24)   not null,
    action        varchar(120)  not null,

    -- Human-readable, with before → after. A log that records that something
    -- changed without saying what is not a record of anything.
    detail        varchar(4000) not null,

    subject_id    uuid,
    subject_label varchar(200),
    ip_address    varchar(64),
    user_agent    varchar(400),
    occurred_at   timestamp(6) with time zone not null,

    constraint pk_audit_entry primary key (id),
    constraint ck_audit_category check (category in (
        'GENERAL', 'SETTINGS', 'TEAM', 'CUSTOMER',
        'LOAN', 'DATA_ACCESS', 'COMPLIANCE'))
);

create index ix_audit_date     on audit_entry (occurred_at);
create index ix_audit_category on audit_entry (category);
create index ix_audit_subject  on audit_entry (subject_id);


-- ─────────────────────────────────────────────────────────────────────────────
-- Idempotency
-- ─────────────────────────────────────────────────────────────────────────────

-- Every money-moving endpoint takes a key. The unique index is what makes it
-- work: a second request arriving while the first is still running collides
-- here rather than doing the work twice.
create table idempotency_record (
    id                  uuid          not null,
    user_id             uuid          not null,
    idempotency_key     varchar(120)  not null,
    operation           varchar(120)  not null,

    -- The same key with a different body is refused rather than replayed.
    -- Answering with the first result would be worse than doubling, because
    -- the caller would believe something happened that did not.
    request_fingerprint varchar(128)  not null,

    response_status     integer       not null,
    response_body       varchar(8000),
    created_at          timestamp(6) with time zone not null,

    constraint pk_idempotency_record primary key (id),
    constraint ix_idem_key unique (user_id, idempotency_key)
);

create index ix_idem_created on idempotency_record (created_at);


-- ─────────────────────────────────────────────────────────────────────────────
-- Notifications
-- ─────────────────────────────────────────────────────────────────────────────

create table notification (
    id         uuid         not null,
    user_id    uuid         not null,
    kind       varchar(24)  not null,
    title      varchar(160) not null,
    body       varchar(1000) not null,
    amount     numeric(19, 2),
    read_flag  boolean      not null default false,
    created_at timestamp(6) with time zone not null,

    -- Cleared, not deleted. Some of these are a customer's record of a
    -- security event, and a feed that can be emptied is a feed an intruder
    -- empties first.
    cleared_at timestamp(6) with time zone,

    constraint pk_notification primary key (id),
    constraint fk_notification_user foreign key (user_id) references app_user (id),
    constraint ck_notification_kind check (kind in (
        'INTEREST', 'MATURITY', 'REPAYMENT_DUE', 'REPAYMENT_PAID',
        'AUTO_SAVE', 'THRIFT', 'SECURITY', 'GENERAL'))
);

create index ix_notification_user   on notification (user_id, created_at);
create index ix_notification_unread on notification (user_id, read_flag);


-- ─────────────────────────────────────────────────────────────────────────────
-- Push notifications
-- ─────────────────────────────────────────────────────────────────────────────

-- One row per phone a customer has the app installed on.
--
-- The token identifies an app installation, not a person: it is not a
-- credential and grants nothing. It rotates on its own, which is why the app
-- re-registers on every sign-in and why the unique key is the token rather
-- than the customer -- a handset that changed hands arrives carrying a token
-- already on file against whoever had it before, and the row moves to whoever
-- signed in last rather than leaving two accounts pointing at one phone.
create table device_token (
    id            uuid         not null,
    user_id       uuid         not null,
    token         varchar(512) not null,
    platform      varchar(16)  not null,
    device_label  varchar(200),
    registered_at timestamp(6) with time zone not null,

    -- Touched on every sign-in, so handsets nobody has opened in months can be
    -- dropped rather than sent to for ever.
    last_seen_at  timestamp(6) with time zone not null,

    constraint pk_device_token primary key (id),
    constraint ix_device_token unique (token),
    constraint fk_device_user  foreign key (user_id) references app_user (id)
        on delete cascade,
    constraint ck_device_platform check (platform in ('ANDROID', 'IOS', 'WEB'))
);

create index ix_device_user on device_token (user_id);
create index ix_device_seen on device_token (last_seen_at);


-- Only the groups a customer has actually switched off.
--
-- Absence means on, so somebody who has never opened the settings screen still
-- hears about their money. Security alerts and money movements are refused by
-- the service before they reach this table -- switching those off is
-- indistinguishable from not being told.
create table notification_preference (
    id      uuid        not null,
    user_id uuid        not null,
    kind    varchar(24) not null,
    enabled boolean     not null default true,

    constraint pk_notification_preference primary key (id),
    constraint ix_pref_user_kind unique (user_id, kind),
    constraint fk_pref_user foreign key (user_id) references app_user (id)
        on delete cascade,
    constraint ck_pref_kind check (kind in (
        'INTEREST', 'MATURITY', 'REPAYMENT_DUE', 'REPAYMENT_PAID',
        'AUTO_SAVE', 'THRIFT', 'SECURITY', 'GENERAL')),

    -- The two that cannot be silenced, enforced here as well as in the service.
    constraint ck_pref_not_silenceable
        check (enabled or kind not in ('SECURITY', 'GENERAL'))
);
