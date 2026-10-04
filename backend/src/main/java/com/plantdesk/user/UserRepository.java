package com.plantdesk.user;

import com.plantdesk.security.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    List<User> findAllByOrderByFullNameAsc();

    List<User> findByRoleAndActiveTrueOrderByFullNameAsc(Role role);
}
