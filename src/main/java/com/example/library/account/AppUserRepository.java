package com.example.library.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByLoginName(String loginName);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.id = :id")
    Optional<AppUser> findByIdForUpdate(@Param("id") Long id);

    boolean existsByRole(UserRole role);

    boolean existsByRoleAndStatus(UserRole role, UserStatus status);

    boolean existsByRoleAndStatusAndIdNot(UserRole role, UserStatus status, Long id);
}
