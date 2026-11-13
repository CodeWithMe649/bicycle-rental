package bicycle_rental;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import bicycle_rental.model.Bike;
import bicycle_rental.model.Rental;
import bicycle_rental.model.User;
import bicycle_rental.repository.BikeRepository;
import bicycle_rental.repository.RentalRepository;
import bicycle_rental.repository.UserRepository;

@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired private BikeRepository bikeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RentalRepository rentalRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        // 1. Seed 105 Bikes
        if (bikeRepository.count() < 100) {
            List<Bike> bikes = new ArrayList<>();
            for (int i = 1; i <= 105; i++) {
                bikes.add(new Bike(
                    String.format("BIK2026%04d", i),
                    "Model-" + i,
                    i % 2 == 0 ? "Mountain" : "City",
                    10.0 + (i % 5),
                    "Available"
                ));
            }
            bikeRepository.saveAll(bikes);
        }

        // 2. Clear old users and seed new users with christian123 password
        userRepository.deleteAll();
        
        List<User> users = new ArrayList<>();
        String customPassword = passwordEncoder.encode("christian123");

        for (int i = 1; i <= 105; i++) {
            String name = (i == 1) ? "Christian" : "User " + i;
            String email = (i == 1) ? "christian@example.com" : "user" + i + "@example.com";

            users.add(new User(
                String.format("USR2026%04d", i),
                name,
                email,
                customPassword,
                i == 1 ? "ROLE_ADMIN" : "ROLE_CUSTOMER"
            ));
        }
        userRepository.saveAll(users);

        // 3. Seed 210 Rental Transactions
        if (rentalRepository.count() < 200) {
            List<Rental> rentals = new ArrayList<>();
            for (int i = 1; i <= 210; i++) {
                rentals.add(new Rental(
                    String.format("RNT2026%04d", i),
                    String.format("BIK2026%04d", (i % 100) + 1),
                    String.format("USR2026%04d", (i % 100) + 1),
                    LocalDateTime.now().minusDays(i),
                    "COMPLETED"
                ));
            }
            rentalRepository.saveAll(rentals);
        }
    }
}