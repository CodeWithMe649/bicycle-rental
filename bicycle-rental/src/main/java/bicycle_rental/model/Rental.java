package bicycle_rental.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "rentals")
public class Rental {

    @Id
    private String id; // e.g. "RNT20260001"
    private String bikeId;
    private String userId;
    private LocalDateTime startTime;
    private String status; // "ACTIVE", "COMPLETED"

    public Rental() {}

    public Rental(String id, String bikeId, String userId, LocalDateTime startTime, String status) {
        this.id = id;
        this.bikeId = bikeId;
        this.userId = userId;
        this.startTime = startTime;
        this.status = status;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getBikeId() { return bikeId; }
    public void setBikeId(String bikeId) { this.bikeId = bikeId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}