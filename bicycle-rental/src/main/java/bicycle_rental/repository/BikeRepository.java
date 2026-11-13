package bicycle_rental.repository;

import bicycle_rental.model.Bike;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BikeRepository extends MongoRepository<Bike, String> {
}