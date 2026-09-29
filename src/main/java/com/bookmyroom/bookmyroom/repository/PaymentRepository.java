
package com.bookmyroom.bookmyroom.repository;

import com.bookmyroom.bookmyroom.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentRepository
        extends JpaRepository<Payment, Long> {

    List<Payment> findByBooking_Id(Long bookingId);

    List<Payment> findByBooking_IdAndStatus(
            Long bookingId,
            String status
    );

    boolean existsByTransactionReference(
            String transactionReference
    );
}