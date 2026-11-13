package bicycle_rental.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import bicycle_rental.model.Rental;

public interface RentalRepository extends MongoRepository<Rental, String> {
}