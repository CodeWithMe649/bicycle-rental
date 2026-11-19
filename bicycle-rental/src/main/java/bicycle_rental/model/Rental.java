package bicycle_rental.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "rentals")
public class Rental {

    @Id
    private String id;

    @Version
    private Long version; // Database-level optimistic locking

    private String bikeId;
    private String userId;
    private LocalDateTime rentTime;
    private LocalDateTime dueTime;
    private LocalDateTime returnTime;
    private String status;

    // Billing & Penalty Fields
    private int bookedHours = 1;
    private String paymentMethod = "GCash";
    private double baseAmount;
    private double lateFee;
    private double totalPaid;

    // Populated via MongoDB $lookup (JOIN equivalent)
    private Bike bikeDetails;
    private User userDetails;

    public Rental() {
        this.id = "RNT2026" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    public Rental(String id, String bikeId, String userId, LocalDateTime rentTime, String status) {
        this.id = (id != null && !id.isEmpty()) ? id : "RNT2026" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        this.bikeId = bikeId;
        this.userId = userId;
        this.rentTime = rentTime;
        this.dueTime = (rentTime != null) ? rentTime.plusHours(1) : LocalDateTime.now().plusHours(1);
        this.status = status;
    }

    public LocalDateTime getEffectiveDueTime() {
        if (this.dueTime != null) {
            return this.dueTime;
        }
        return (this.rentTime != null) ? this.rentTime.plusHours(1) : null;
    }

    public boolean isOverdue() {
        if (!"ACTIVE".equalsIgnoreCase(this.status) && !"Ongoing".equalsIgnoreCase(this.status)) {
            return false;
        }
        LocalDateTime deadline = getEffectiveDueTime();
        return deadline != null && LocalDateTime.now().isAfter(deadline);
    }

    // Calculates real-time overdue penalty (charges 1 full hourly rate per started overdue hour)
    public double calculateCurrentLateFee(double hourlyRate) {
        LocalDateTime deadline = getEffectiveDueTime();
        if (deadline == null || !LocalDateTime.now().isAfter(deadline)) {
            return 0.0;
        }
        long overdueMinutes = Duration.between(deadline, LocalDateTime.now()).toMinutes();
        if (overdueMinutes <= 0) {
            return 0.0;
        }
        long overdueHoursRoundedUp = (long) Math.ceil(overdueMinutes / 60.0);
        return overdueHoursRoundedUp * (hourlyRate > 0 ? hourlyRate : 150.0);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public String getBikeId() { return bikeId; }
    public void setBikeId(String bikeId) { this.bikeId = bikeId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public LocalDateTime getRentTime() { return rentTime; }
    public void setRentTime(LocalDateTime rentTime) { this.rentTime = rentTime; }

    public LocalDateTime getDueTime() { return dueTime; }
    public void setDueTime(LocalDateTime dueTime) { this.dueTime = dueTime; }

    public LocalDateTime getReturnTime() { return returnTime; }
    public void setReturnTime(LocalDateTime returnTime) { this.returnTime = returnTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getBookedHours() { return bookedHours; }
    public void setBookedHours(int bookedHours) { this.bookedHours = bookedHours; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public double getBaseAmount() { return baseAmount; }
    public void setBaseAmount(double baseAmount) { this.baseAmount = baseAmount; }

    public double getLateFee() { return lateFee; }
    public void setLateFee(double lateFee) { this.lateFee = lateFee; }

    public double getTotalPaid() { return totalPaid; }
    public void setTotalPaid(double totalPaid) { this.totalPaid = totalPaid; }

    public Bike getBikeDetails() { return bikeDetails; }
    public void setBikeDetails(Bike bikeDetails) { this.bikeDetails = bikeDetails; }

    public User getUserDetails() { return userDetails; }
    public void setUserDetails(User userDetails) { this.userDetails = userDetails; }
}