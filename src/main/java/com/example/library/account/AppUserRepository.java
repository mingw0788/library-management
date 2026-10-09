package com.example.library.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByLoginName(String loginName);

    boolean existsByRole(UserRole role);

    boolean existsByRoleAndStatus(UserRole role, UserStatus status);

    boolean existsByRoleAndStatusAndIdNot(UserRole role, UserStatus status, Long id);
}
