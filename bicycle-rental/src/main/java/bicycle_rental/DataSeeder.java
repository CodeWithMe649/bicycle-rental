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
        // 1. Seed 105 Bikes (Meets 100+ minimum requirement)
        if (bikeRepository.count() < 100) {
            bikeRepository.deleteAll();
            String[] models = {"Trek Marlin 5", "Giant Escape 3", "Specialized Sirrus", "Cannondale Trail 8", "Merida Crossway"};
            String[] categories = {"Mountain", "City", "Hybrid", "Mountain", "Hybrid"};
            double[] rates = {180.0, 120.0, 150.0, 200.0, 160.0};

            List<Bike> bikes = new ArrayList<>();
            for (int i = 1; i <= 105; i++) {
                int idx = i % models.length;
                String status = (i <= 5) ? "Rented" : (i == 6 ? "Maintenance" : "Available");
                bikes.add(new Bike(
                    String.format("BIK2026%04d", i),
                    models[idx] + " #" + i,
                    categories[idx],
                    rates[idx],
                    status
                ));
            }
            bikeRepository.saveAll(bikes);
        }

        // 2. Seed 105 Users (Meets 100+ minimum requirement)
        if (userRepository.count() < 100) {
            userRepository.deleteAll();
            List<User> users = new ArrayList<>();
            String customPassword = passwordEncoder.encode("christian123");

            for (int i = 1; i <= 105; i++) {
                String name = (i == 1) ? "Christian" : "Customer " + i;
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
        }

        // 3. Seed 210 Rentals (Meets 200+ minimum transaction requirement)
        if (rentalRepository.count() < 200) {
            rentalRepository.deleteAll();
            List<Rental> rentals = new ArrayList<>();
            for (int i = 1; i <= 210; i++) {
                String status = (i <= 5) ? "ACTIVE" : "COMPLETED";
                rentals.add(new Rental(
                    String.format("RNT2026%04d", i),
                    String.format("BIK2026%04d", (i % 100) + 1),
                    String.format("USR2026%04d", (i % 100) + 2),
                    LocalDateTime.now().minusHours(i * 2),
                    status
                ));
            }
            rentalRepository.saveAll(rentals);
        }
    }
}