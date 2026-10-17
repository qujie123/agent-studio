package com.agent.agentplatform.repository;

import com.agent.agentplatform.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.List;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    @Query("select u from User u where (:role = '' or u.role = :role) and (:enabled is null or u.enabled = :enabled) and (lower(u.username) like concat('%',:q,'%') or lower(coalesce(u.displayName,'')) like concat('%',:q,'%') or lower(u.email) like concat('%',:q,'%')) order by u.id desc")
    org.springframework.data.domain.Page<User> search(String q, String role, Boolean enabled, org.springframework.data.domain.Pageable pageable);
    long countByEnabled(boolean enabled);
    long countByRoleAndEnabled(String role, boolean enabled);
    Optional<User> findByUsername(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findForUpdate(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.username = :username")
    Optional<User> findByUsernameForUpdate(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
    boolean existsByRole(String role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.role = 'ADMIN' order by u.id")
    List<User> lockAdministrators();
}
