package bicycle_rental.model;

import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "bikes")
public class Bike {

    @Id
    private String id;

    @Version
    private Long version; // Database-level optimistic locking

    private String model;
    private String category;
    private double ratePerHour;
    private String status;

    public Bike() {
        this.id = "BIK2026" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    public Bike(String id, String model, String category, double ratePerHour, String status) {
        this.id = (id != null && !id.isEmpty()) ? id : "BIK2026" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        this.model = model;
        this.category = category;
        this.ratePerHour = ratePerHour;
        this.status = status;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public double getRatePerHour() { return ratePerHour; }
    public void setRatePerHour(double ratePerHour) { this.ratePerHour = ratePerHour; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}