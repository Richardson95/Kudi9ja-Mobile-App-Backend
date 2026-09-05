package com.quadrilateral.kudi9ja.domain.thrift;

import com.quadrilateral.kudi9ja.common.util.Money;
import com.quadrilateral.kudi9ja.domain.savings.AutoFrequency;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A rotating savings circle: ajo, esusu, adashe.
 *
 * <p>Everyone contributes the same amount each cycle and one member collects
 * the whole pot each round, until everybody has had a turn.
 *
 * <p><b>Every member is a real Kudi9ja account.</b> The client asks for typed
 * names and cannot check them, which means a circle can be created around
 * people who do not exist and whose contributions will therefore never arrive.
 * Here each member is resolved to an account before the circle is created, and
 * a circle containing anyone who is not one is refused.
 */
@Entity
@Table(
        name = "thrift_circle",
        indexes = {
                @Index(name = "ix_circle_invite", columnList = "invite_code", unique = true),
                @Index(name = "ix_circle_creator", columnList = "created_by")
        })
@Getter
@Setter
@NoArgsConstructor
public class ThriftCircle {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    /** What each member pays in per cycle. */
    @Column(name = "contribution", nullable = false, precision = 19, scale = 2)
    private BigDecimal contribution;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 16)
    private AutoFrequency frequency;

    @Column(name = "start_date", nullable = false)
    private Instant startDate;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** 1-based index of the round currently being collected. */
    @Column(name = "current_round", nullable = false)
    private int currentRound = 1;

    @Column(name = "invite_code", nullable = false, length = 40)
    private String inviteCode;

    @Column(name = "emoji", length = 16)
    private String emoji = "🤝";

    /**
     * Whether the circle has been sealed and started.
     *
     * <p>Members can join an open circle. Once it starts, the rotation is
     * fixed: letting somebody join halfway would change every later member's
     * payout position after they had already paid in on the old one.
     */
    @Column(name = "started", nullable = false)
    private boolean started = false;

    @Column(name = "completed_at")
    private Instant completedAt;

    @OneToMany(mappedBy = "circle", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("position asc")
    private List<ThriftMember> members = new ArrayList<>();

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    // Derived ----------------------------------------------------------------

    public int size() {
        return members.size();
    }

    /** What the collector walks away with each round. */
    public BigDecimal potSize() {
        return Money.multiply(contribution, BigDecimal.valueOf(size()));
    }

    /** What each member pays across the whole circle: one round for each seat. */
    public BigDecimal totalCommitment() {
        return potSize();
    }

    public boolean isComplete() {
        return currentRound > size();
    }

    public Optional<ThriftMember> memberFor(UUID userId) {
        return members.stream().filter(m -> m.getUserId().equals(userId)).findFirst();
    }

    /** Whose turn it is to collect. */
    public Optional<ThriftMember> currentCollector() {
        return members.stream().filter(m -> m.getPosition() == currentRound).findFirst();
    }

    public Instant dateForRound(int round) {
        return startDate.plus(frequency.interval().multipliedBy(round - 1L));
    }

    public Instant nextCollectionDate() {
        return dateForRound(Math.min(currentRound, Math.max(size(), 1)));
    }

    public double progress() {
        return size() == 0 ? 0 : Math.max(0, Math.min(1, (double) (currentRound - 1) / size()));
    }
}
