-- Kudi9ja — the payment reference a customer quotes on a bank transfer
--
-- This table belongs with the pay-in tables in V1, and it is here instead
-- because V1 had already run against the live database by the time it was
-- needed. Flyway records a checksum of every migration it applies and refuses
-- to run when one changes underneath it — which is the behaviour you want. A
-- schema file that quietly stops matching the database it built is a far worse
-- problem than a deploy that stops.

-- A reference handed to a customer for one payment.
--
-- Written down when it is COPIED, not when it is displayed: a customer who
-- looked at the pay-in screen three times has not made three payments, and
-- three references on their record would be three things for an admin to rule
-- out. One row is active (copied_at null) at a time — the one on their screen.
--
-- This is what an admin compares a bank narration against, and it exists
-- because the alternative is having nothing to compare until a claim arrives.
create table payment_reference (
    id        uuid        not null,
    user_id   uuid        not null,
    reference varchar(64) not null,
    issued_at timestamp(6) with time zone not null,

    -- Null while it is the one on the customer's screen. Set the moment they
    -- take it away, which is when it starts mattering.
    copied_at timestamp(6) with time zone,

    -- Set when a claim quotes it, so a reference taken and never used stands
    -- out from one that was actually paid.
    claim_id  uuid,

    constraint pk_payment_reference primary key (id),
    constraint ix_payref_value      unique (reference),
    constraint fk_payref_user       foreign key (user_id) references app_user (id)
        on delete cascade,
    constraint fk_payref_claim      foreign key (claim_id) references pay_in_claim (id)
);

create index ix_payref_user on payment_reference (user_id, issued_at);
