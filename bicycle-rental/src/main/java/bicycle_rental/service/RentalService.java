package bicycle_rental.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
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

    // Overloaded helper for backward compatibility
    @Transactional(rollbackFor = Exception.class)
    public synchronized void processBikeRental(String bikeId, String userId) {
        processBikeRental(bikeId, userId, 1, "GCash");
    }

    // Combines Java thread lock + @Transactional rollback + MongoDB @Version optimistic locking
    @Transactional(rollbackFor = Exception.class)
    public synchronized Rental processBikeRental(String bikeId, String userId, int hours, String paymentMethod) {
        try {
            Bike bike = bikeRepository.findById(bikeId)
                    .orElseThrow(() -> new RuntimeException("Bike not found: " + bikeId));

            // Double-check lock state inside critical section
            if (!"Available".equalsIgnoreCase(bike.getStatus())) {
                throw new RuntimeException("Transaction Conflict Prevented: Bike " + bikeId + " is currently " + bike.getStatus() + ".");
            }

            int validHours = Math.max(1, hours);
            double rate = (bike.getRatePerHour() > 0) ? bike.getRatePerHour() : 150.0;
            LocalDateTime now = LocalDateTime.now();

            // 1. Lock & Update Bike Status to Rented (increments @Version in MongoDB)
            bike.setStatus("Rented");
            bikeRepository.save(bike);

            // 2. Create New Active Rental with guaranteed unique alphanumeric Primary Key
            String uniqueRentalId = "RNT2026" + UUID.randomUUID().toString().substring(0, 5).toUpperCase();
            Rental rental = new Rental(
                    uniqueRentalId,
                    bikeId,
                    userId,
                    now,
                    "ACTIVE"
            );
            rental.setBookedHours(validHours);
            rental.setDueTime(now.plusHours(validHours));
            rental.setPaymentMethod(paymentMethod != null ? paymentMethod : "GCash");
            rental.setBaseAmount(rate * validHours);
            rental.setLateFee(0.0);
            rental.setTotalPaid(rate * validHours);

            return rentalRepository.save(rental);

        } catch (OptimisticLockingFailureException e) {
            throw new RuntimeException("Database Lock Conflict: Another user just booked this bike. Transaction rolled back.");
        }
    }

    // Settles rental, computes overdue late fees if past dueTime, and releases bike back to Available
    @Transactional(rollbackFor = Exception.class)
    public synchronized Rental settleAndReturnBike(String rentalId) {
        Rental rental = rentalRepository.findById(rentalId)
                .orElseThrow(() -> new RuntimeException("Rental not found: " + rentalId));

        if (!"ACTIVE".equalsIgnoreCase(rental.getStatus()) && !"Ongoing".equalsIgnoreCase(rental.getStatus())) {
            return rental;
        }

        Bike bike = bikeRepository.findById(rental.getBikeId()).orElse(null);
        double hourlyRate = (bike != null && bike.getRatePerHour() > 0) ? bike.getRatePerHour() : 150.0;

        double base = (rental.getBaseAmount() > 0)
                ? rental.getBaseAmount()
                : hourlyRate * Math.max(1, rental.getBookedHours());
        double penalty = rental.calculateCurrentLateFee(hourlyRate);

        rental.setBaseAmount(base);
        rental.setLateFee(penalty);
        rental.setTotalPaid(base + penalty);
        rental.setReturnTime(LocalDateTime.now());
        rental.setStatus("PAID & COMPLETED");
        rentalRepository.save(rental);

        if (bike != null) {
            bike.setStatus("Available");
            bikeRepository.save(bike);
        }

        return rental;
    }

    // Retrieves joined Rental + Bike + User records using MongoDB $lookup
    public List<Rental> getAllJoinedRentals() {
        return rentalRepository.findAllWithJoinedDetails();
    }
}