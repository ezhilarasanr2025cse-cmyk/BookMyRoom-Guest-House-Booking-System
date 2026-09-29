
package com.bookmyroom.bookmyroom.controller;

import com.bookmyroom.bookmyroom.model.Guest;
import com.bookmyroom.bookmyroom.repository.GuestRepository;
import com.bookmyroom.bookmyroom.repository.BookingRepository;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/guests")
public class GuestController {

    private final GuestRepository guestRepository;
    private final BookingRepository bookingRepository;

    public GuestController(
            GuestRepository guestRepository,
            BookingRepository bookingRepository) {

        this.guestRepository = guestRepository;
        this.bookingRepository = bookingRepository;
    }

    // 1. REGISTER GUEST
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Guest addGuest(
            @Valid @RequestBody Guest guest) {

        if (guestRepository.existsByEmail(
                guest.getEmail())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email already registered");
        }

        guest.setId(null);

        return guestRepository.save(guest);
    }

    // 2. VIEW ALL GUESTS
    @GetMapping
    public List<Guest> getAllGuests() {

        return guestRepository.findAll();
    }

    // 3. VIEW GUEST BY ID
    @GetMapping("/{id}")
    public Guest getGuestById(
            @PathVariable Long id) {

        return guestRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Guest not found"));
    }

    // 4. UPDATE GUEST
    @PutMapping("/{id}")
    public Guest updateGuest(
            @PathVariable Long id,
            @Valid @RequestBody Guest updatedGuest) {

        Guest guest = getGuestById(id);

        if (!guest.getEmail().equals(
                updatedGuest.getEmail())
                && guestRepository.existsByEmail(
                updatedGuest.getEmail())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email already registered");
        }

        guest.setName(updatedGuest.getName());
        guest.setEmail(updatedGuest.getEmail());
        guest.setPhone(updatedGuest.getPhone());

        return guestRepository.save(guest);
    }

    // 5. DELETE GUEST
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGuest(
            @PathVariable Long id) {

        Guest guest = getGuestById(id);

        if (bookingRepository.existsByGuest_Id(id)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Guest has booking history and cannot be deleted");
        }

        guestRepository.delete(guest);
    }
}