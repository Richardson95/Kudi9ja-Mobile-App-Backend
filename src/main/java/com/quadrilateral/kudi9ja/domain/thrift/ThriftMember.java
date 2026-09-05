package com.quadrilateral.kudi9ja.domain.thrift;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One seat in a circle, held by a real Kudi9ja account.
 *
 * <p>{@link #position} is both the seat and the round at which this member
 * collects the pot. It is fixed when the circle starts and never moves — a
 * rotation whose order can change after people have paid in is not a rotation.
 */
@Entity
@Table(
        name = "thrift_member",
        indexes = {
                @Index(name = "ix_member_circle", columnList = "circle_id, position"),
                @Index(name = "ix_member_user", columnList = "user_id"),
                @Index(name = "ix_member_unique", columnList = "circle_id, user_id", unique = true)
        })
@Getter
@Setter
@NoArgsConstructor
public class ThriftMember {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "circle_id", nullable = false)
    private ThriftCircle circle;

    /** Always a real account. A circle cannot contain anyone who is not one. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Copied at join time so the circle renders without a join. */
    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;

    @Column(name = "initials", length = 8)
    private String initials;

    @Column(name = "customer_ref", nullable = false, length = 16)
    private String customerRef;

    /** The seat, and the round at which this member collects. */
    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt = Instant.now();

    @Column(name = "left_at")
    private Instant leftAt;

    public boolean hasCollected(int currentRound) {
        return currentRound > position;
    }
}
