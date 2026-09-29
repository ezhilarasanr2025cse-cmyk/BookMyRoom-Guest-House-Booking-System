
package com.bookmyroom.bookmyroom.repository;

import com.bookmyroom.bookmyroom.model.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomRepository
        extends JpaRepository<Room, Long> {

    // Check whether a room number already exists
    boolean existsByRoomNumber(String roomNumber);
}