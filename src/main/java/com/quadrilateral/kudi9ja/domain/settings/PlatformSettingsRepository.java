package com.quadrilateral.kudi9ja.domain.settings;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformSettingsRepository extends JpaRepository<PlatformSettings, Long> {

    /** The version in force: settings are never edited, only superseded. */
    Optional<PlatformSettings> findFirstByOrderByVersionDesc();

    Page<PlatformSettings> findAllByOrderByVersionDesc(Pageable pageable);

    List<PlatformSettings> findTop2ByOrderByVersionDesc();
}
