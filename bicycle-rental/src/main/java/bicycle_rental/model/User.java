package bicycle_rental.model;

import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "users")
public class User {

    @Id
    private String id;

    @Version
    private Long version; // Database-level optimistic locking

    private String name;
    private String email;
    private String password;
    private String role;
    private String phoneNumber;
    private String validIdNumber;

    public User() {
        this.id = "USR2026" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    public User(String id, String name, String email, String password, String role) {
        this.id = (id != null && !id.isEmpty()) ? id : "USR2026" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        this.name = name;
        this.email = email;
        this.password = password;
        this.role = role;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getValidIdNumber() { return validIdNumber; }
    public void setValidIdNumber(String validIdNumber) { this.validIdNumber = validIdNumber; }
}