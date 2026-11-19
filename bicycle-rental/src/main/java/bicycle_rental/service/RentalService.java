package bicycle_rental.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.LookupOperation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bicycle_rental.model.Bike;
import bicycle_rental.model.Rental;
import bicycle_rental.repository.BikeRepository;
import bicycle_rental.repository.RentalRepository;

@Service
public class RentalService {

    @Autowired private BikeRepository bikeRepository;
    @Autowired private RentalRepository rentalRepository;
    @Autowired private MongoTemplate mongoTemplate;

    @Transactional(rollbackFor = Exception.class)
    public Rental processBikeRental(String bikeId, String userId, int hours, String paymentMethod) {
        if (hours < 1 || hours > 24) {
            throw new IllegalArgumentException("Rental duration must be between 1 and 24 hours.");
        }

        Bike bike = bikeRepository.findById(bikeId)
                .orElseThrow(() -> new RuntimeException("Bicycle not found: " + bikeId));

        if (!"Available".equalsIgnoreCase(bike.getStatus())) {
            throw new IllegalStateException("Bicycle " + bikeId + " is currently unavailable (" + bike.getStatus() + ").");
        }

        try {
            bike.setStatus("Rented");
            bikeRepository.save(bike);

            LocalDateTime now = LocalDateTime.now();
            double rate = (bike.getRatePerHour() > 0) ? bike.getRatePerHour() : 150.0;
            double baseCost = rate * hours;

            Rental rental = new Rental(null, bikeId, userId, now, "ACTIVE");
            rental.setBookedHours(hours);
            rental.setDueTime(now.plusHours(hours));
            rental.setPaymentMethod((paymentMethod != null && !paymentMethod.isBlank()) ? paymentMethod : "GCash");
            rental.setBaseAmount(baseCost);
            rental.setLateFee(0.0);
            rental.setTotalPaid(baseCost);

            return rentalRepository.save(rental);

        } catch (OptimisticLockingFailureException ex) {
            throw new RuntimeException("Concurrency lock triggered: Another rider just booked this bicycle. Please choose another unit.");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Rental settleAndReturnBike(String rentalId) {
        Rental rental = rentalRepository.findById(rentalId)
                .orElseThrow(() -> new RuntimeException("Rental record not found: " + rentalId));

        if (!"ACTIVE".equalsIgnoreCase(rental.getStatus()) && !"Ongoing".equalsIgnoreCase(rental.getStatus())) {
            throw new IllegalStateException("This rental is already settled.");
        }

        Bike bike = bikeRepository.findById(rental.getBikeId())
                .orElseThrow(() -> new RuntimeException("Associated bicycle not found."));

        try {
            double hourlyRate = (bike.getRatePerHour() > 0) ? bike.getRatePerHour() : 150.0;
            double base = (rental.getBaseAmount() > 0)
                    ? rental.getBaseAmount()
                    : hourlyRate * Math.max(1, rental.getBookedHours());
            double penalty = rental.calculateCurrentLateFee(hourlyRate);

            rental.setReturnTime(LocalDateTime.now());
            rental.setBaseAmount(base);
            rental.setLateFee(penalty);
            rental.setTotalPaid(base + penalty);
            rental.setStatus("COMPLETED");
            Rental savedRental = rentalRepository.save(rental);

            bike.setStatus("Available");
            bikeRepository.save(bike);

            return savedRental;

        } catch (OptimisticLockingFailureException ex) {
            throw new RuntimeException("Concurrency lock triggered: Return is already being processed.");
        }
    }

    public List<Rental> getAllJoinedRentals() {
        LookupOperation lookupBike = LookupOperation.newLookup()
                .from("bikes")
                .localField("bikeId")
                .foreignField("_id")
                .as("bikeDetails");

        LookupOperation lookupUser = LookupOperation.newLookup()
                .from("users")
                .localField("userId")
                .foreignField("_id")
                .as("userDetails");

        Aggregation aggregation = Aggregation.newAggregation(
                lookupBike,
                Aggregation.unwind("bikeDetails", true),
                lookupUser,
                Aggregation.unwind("userDetails", true)
        );

        return mongoTemplate.aggregate(aggregation, "rentals", Rental.class).getMappedResults();
    }
}