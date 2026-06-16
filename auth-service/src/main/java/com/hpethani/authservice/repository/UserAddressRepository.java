package com.hpethani.authservice.repository;

import com.hpethani.authservice.entity.UserAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {

    List<UserAddress> findByUserEmail(String email);

    Optional<UserAddress> findByIdAndUserEmail(Long id, String email);

    // Unset all defaults for a user before setting a new one
    @Modifying
    @Query("UPDATE UserAddress a SET a.isDefault = false WHERE a.user.email = :email")
    void clearDefaultForUser(@Param("email") String email);
}

