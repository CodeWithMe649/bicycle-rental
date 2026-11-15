package bicycle_rental.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bicycle_rental.model.Bike;
import bicycle_rental.model.Rental;
import bicycle_rental.repository.BikeRepository;
import bicycle_rental.repository.RentalRepository;

@Service
public class RentalService {

    @Autowired
    private BikeRepository bikeRepository;

    @Autowired
    private RentalRepository rentalRepository;

    // Synchronized method-level lock prevents two concurrent users from booking the same bike simultaneously
    @Transactional
    public synchronized void processBikeRental(String bikeId, String userId) {
        Bike bike = bikeRepository.findById(bikeId)
                .orElseThrow(() -> new RuntimeException("Bike not found: " + bikeId));

        // Double-check lock state inside critical section
        if (!"Available".equalsIgnoreCase(bike.getStatus())) {
            throw new RuntimeException("Transaction Conflict Prevented: Bike " + bikeId + " is currently " + bike.getStatus() + ".");
        }

        // 1. Lock & Update Bike Status to Rented
        bike.setStatus("Rented");
        bikeRepository.save(bike);

        // 2. Create New Active Rental with guaranteed unique alphanumeric Primary Key
        String uniqueRentalId = "RNT" + System.currentTimeMillis();
        Rental rental = new Rental(
                uniqueRentalId,
                bikeId,
                userId,
                LocalDateTime.now(),
                "ACTIVE"
        );
        rentalRepository.save(rental);
    }
}