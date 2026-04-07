
package com.chargelink.repository;

import com.chargelink.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByPhone(Long phone);
    boolean existsByEmail(String email);
    boolean existsByPhone(Long phone);
}
