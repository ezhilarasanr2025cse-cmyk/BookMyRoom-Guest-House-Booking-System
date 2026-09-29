
package com.bookmyroom.bookmyroom.repository;

import com.bookmyroom.bookmyroom.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface BookingRepository
        extends JpaRepository<Booking, Long> {

    List<Booking> findByRoom_IdAndStatusInAndCheckInLessThanAndCheckOutGreaterThan(
            Long roomId,
            Collection<String> statuses,
            LocalDate checkOut,
            LocalDate checkIn
    );

    List<Booking> findByRoom_IdAndStatusAndCheckInLessThanAndCheckOutGreaterThan(
            Long roomId,
            String status,
            LocalDate checkOut,
            LocalDate checkIn
    );

    boolean existsByRoom_Id(Long roomId);

    boolean existsByGuest_Id(Long guestId);

    List<Booking> findByStatus(String status);
}