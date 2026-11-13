package bicycle_rental.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import bicycle_rental.model.User;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);
}