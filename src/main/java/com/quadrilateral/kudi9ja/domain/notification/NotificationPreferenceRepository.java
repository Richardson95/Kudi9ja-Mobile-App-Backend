package com.quadrilateral.kudi9ja.domain.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository
        extends JpaRepository<NotificationPreference, UUID> {

    Optional<NotificationPreference> findByUserIdAndKind(UUID userId, NotifyKind kind);

    List<NotificationPreference> findByUserId(UUID userId);
}
