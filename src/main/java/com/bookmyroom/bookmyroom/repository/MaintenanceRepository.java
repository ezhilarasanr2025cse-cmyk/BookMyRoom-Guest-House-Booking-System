
package com.bookmyroom.bookmyroom.repository;

import com.bookmyroom.bookmyroom.model.Maintenance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface MaintenanceRepository
        extends JpaRepository<Maintenance, Long> {

    List<Maintenance> findByRoom_Id(Long roomId);

    List<Maintenance>
    findByRoom_IdAndStatusInAndStartDateLessThanAndEndDateGreaterThanEqual(
            Long roomId,
            Collection<String> statuses,
            LocalDate exclusiveEnd,
            LocalDate startDate
    );
}