
package com.bookmyroom.bookmyroom.repository;

import com.bookmyroom.bookmyroom.model.Guest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GuestRepository
        extends JpaRepository<Guest, Long> {

    boolean existsByEmail(String email);
}