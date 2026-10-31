package com.courier.tracking.repository;

import com.courier.tracking.entity.RoleName;
import com.courier.tracking.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"role"})
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByRoleName(RoleName roleName);

    List<User> findByRoleNameAndActiveTrue(RoleName roleName);

    long countByRoleNameAndActiveTrue(RoleName roleName);
}
