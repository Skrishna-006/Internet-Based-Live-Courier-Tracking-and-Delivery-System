package com.courier.tracking.repository;

import com.courier.tracking.entity.Role;
import com.courier.tracking.entity.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Integer> {

    Optional<Role> findByName(RoleName name);

    boolean existsByName(RoleName name);
}
