package bicycle_rental.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;

import bicycle_rental.model.Rental;

public interface RentalRepository extends MongoRepository<Rental, String> {

    // MongoDB $lookup (Equivalent to SQL LEFT JOIN across rentals, bikes, and users)
    @Aggregation(pipeline = {
        "{ '$lookup': { 'from': 'bikes', 'localField': 'bikeId', 'foreignField': '_id', 'as': 'bikeDetails' } }",
        "{ '$unwind': { 'path': '$bikeDetails', 'preserveNullAndEmptyArrays': true } }",
        "{ '$lookup': { 'from': 'users', 'localField': 'userId', 'foreignField': '_id', 'as': 'userDetails' } }",
        "{ '$unwind': { 'path': '$userDetails', 'preserveNullAndEmptyArrays': true } }"
    })
    List<Rental> findAllWithJoinedDetails();
}