package bicycle_rental.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

    public void processBikeRental(String bikeId, String userId) {
        Bike bike = bikeRepository.findById(bikeId)
                .orElseThrow(() -> new RuntimeException("Bike not found: " + bikeId));

        if (!"Available".equalsIgnoreCase(bike.getStatus())) {
            throw new RuntimeException("Bike is already rented!");
        }

        // 1. Update Bike Status to Rented
        bike.setStatus("Rented");
        bikeRepository.save(bike);

        // 2. Create New Active Rental with guaranteed unique ID
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