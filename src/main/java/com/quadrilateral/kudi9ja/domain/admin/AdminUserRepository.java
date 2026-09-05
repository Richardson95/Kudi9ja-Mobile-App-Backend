package com.quadrilateral.kudi9ja.domain.admin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminUserRepository extends JpaRepository<AdminUser, UUID> {

    /** The grant that decides whether the panel appears. Email is the key. */
    Optional<AdminUser> findByEmailIgnoreCaseAndActiveTrue(String email);

    Optional<AdminUser> findByEmailIgnoreCase(String email);

    Optional<AdminUser> findByUserId(UUID userId);

    boolean existsByEmailIgnoreCase(String email);

    List<AdminUser> findAllByOrderByRoleAscNameAsc();

    /** Guards the last-owner rule: an owner may not be the last one standing. */
    long countByRoleAndActiveTrue(AdminRole role);
}
