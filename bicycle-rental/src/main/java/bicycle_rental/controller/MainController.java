package bicycle_rental.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import bicycle_rental.model.Bike;
import bicycle_rental.model.Rental;
import bicycle_rental.model.User;
import bicycle_rental.repository.BikeRepository;
import bicycle_rental.repository.RentalRepository;
import bicycle_rental.repository.UserRepository;
import bicycle_rental.service.RentalService;

@Controller
public class MainController {

    @Autowired private BikeRepository bikeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RentalRepository rentalRepository;
    @Autowired private RentalService rentalService;
    @Autowired private PasswordEncoder passwordEncoder;

    // Show Login View
    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    // Show Registration View
    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("user", new User());
        return "register";
    }

    // Process New User Registration
    @PostMapping("/register")
    public String registerUser(@ModelAttribute("user") User user) {
        user.setId("USR" + System.currentTimeMillis());
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setRole("ROLE_CUSTOMER");
        userRepository.save(user);
        return "redirect:/login?registered";
    }

    // Main Role-Based Dashboard Router
    @GetMapping("/")
    public String dashboard(Model model, Authentication authentication) {
        if (authentication == null) return "redirect:/login";

        String email = authentication.getName();
        User currentUser = userRepository.findAll().stream()
                .filter(u -> email.equalsIgnoreCase(u.getEmail()) || email.equalsIgnoreCase(u.getId()))
                .findFirst()
                .orElse(null);

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        model.addAttribute("username", (currentUser != null) ? currentUser.getName() : email);
        model.addAttribute("userEmail", email);
        model.addAttribute("userRole", isAdmin ? "Administrator" : "Customer");

        if (isAdmin) {
            model.addAttribute("totalBikes", bikeRepository.count());
            model.addAttribute("activeRentals", rentalRepository.count());
            model.addAttribute("totalUsers", userRepository.count());
            model.addAttribute("recentRentals", rentalRepository.findAll().stream().limit(5).toList());
            return "dashboard";
        } else {
            var sortedBikes = bikeRepository.findAll().stream()
                    .sorted((b1, b2) -> "Available".equalsIgnoreCase(b1.getStatus()) ? -1 : 1)
                    .toList();
            model.addAttribute("availableBikes", sortedBikes);

            if (currentUser != null) {
                var userRentals = rentalRepository.findAll().stream()
                        .filter(r -> currentUser.getId().equals(r.getUserId()))
                        .toList();

                var activeList = userRentals.stream()
                        .filter(r -> "ACTIVE".equalsIgnoreCase(r.getStatus()))
                        .toList();

                var pastList = userRentals.stream()
                        .filter(r -> !"ACTIVE".equalsIgnoreCase(r.getStatus()))
                        .toList();

                model.addAttribute("activeRentals", activeList);
                model.addAttribute("pastRentals", pastList);
                model.addAttribute("activeCount", activeList.size());
                model.addAttribute("completedCount", pastList.size());
            }
            return "customer-dashboard";
        }
    }

    // Rent Bike with Hours, Payment Method & Error Handling
    @PostMapping("/rent-bike")
    public String processRental(@RequestParam("bikeId") String bikeId,
                                @RequestParam(value = "hours", defaultValue = "1") int hours,
                                @RequestParam(value = "paymentMethod", defaultValue = "GCash") String paymentMethod,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        try {
            if (authentication != null) {
                String email = authentication.getName();
                User currentUser = userRepository.findAll().stream()
                        .filter(u -> email.equalsIgnoreCase(u.getEmail()) || email.equalsIgnoreCase(u.getId()))
                        .findFirst()
                        .orElse(null);

                if (currentUser != null) {
                    rentalService.processBikeRental(bikeId, currentUser.getId());
                    redirectAttributes.addFlashAttribute("message",
                        "Booked " + bikeId + " for " + hours + " hr(s) via " + paymentMethod + "!");
                } else {
                    redirectAttributes.addFlashAttribute("message", "Error: Logged-in user not found in database.");
                }
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("message", "Could not complete rental: " + e.getMessage());
        }
        return "redirect:/";
    }

    // Pay & Return Bike
    @PostMapping("/pay-return")
    public String payAndReturnBike(@RequestParam("rentalId") String rentalId,
                                   RedirectAttributes redirectAttributes) {
        Rental rental = rentalRepository.findById(rentalId).orElse(null);
        if (rental != null && "ACTIVE".equalsIgnoreCase(rental.getStatus())) {
            rental.setStatus("PAID & COMPLETED");
            rentalRepository.save(rental);

            Bike bike = bikeRepository.findById(rental.getBikeId()).orElse(null);
            if (bike != null) {
                bike.setStatus("Available");
                bikeRepository.save(bike);
            }
            redirectAttributes.addFlashAttribute("message",
                "Payment settled! Bike " + rental.getBikeId() + " has been returned.");
        }
        return "redirect:/";
    }

    // Extend Active Rental (+1 Hour)
    @PostMapping("/extend-rental")
    public String extendRental(@RequestParam("rentalId") String rentalId,
                               RedirectAttributes redirectAttributes) {
        Rental rental = rentalRepository.findById(rentalId).orElse(null);
        if (rental != null) {
            redirectAttributes.addFlashAttribute("message",
                "Rental " + rentalId + " extended by +1 hour!");
        }
        return "redirect:/";
    }

    // Report Bike Maintenance Issue
    @PostMapping("/report-issue")
    public String reportIssue(@RequestParam("bikeId") String bikeId,
                              @RequestParam("issueNote") String issueNote,
                              RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("message",
            "Issue reported for " + bikeId + ": \"" + issueNote + "\". Our mechanics have been notified.");
        return "redirect:/";
    }

    // Update Customer Profile Name / Password
    @PostMapping("/update-profile")
    public String updateProfile(@RequestParam("newName") String newName,
                                @RequestParam(value = "newPassword", required = false) String newPassword,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        if (authentication != null) {
            String email = authentication.getName();
            User currentUser = userRepository.findAll().stream()
                    .filter(u -> email.equalsIgnoreCase(u.getEmail()) || email.equalsIgnoreCase(u.getId()))
                    .findFirst()
                    .orElse(null);

            if (currentUser != null) {
                if (newName != null && !newName.isBlank()) {
                    currentUser.setName(newName.trim());
                }
                if (newPassword != null && !newPassword.isBlank()) {
                    currentUser.setPassword(passwordEncoder.encode(newPassword));
                }
                userRepository.save(currentUser);
                redirectAttributes.addFlashAttribute("message", "Profile updated successfully!");
            }
        }
        return "redirect:/";
    }

    // Admin Page Routes
    @GetMapping("/rentals")
    public String rentalsPage(Model model) {
        model.addAttribute("bikes", bikeRepository.findAll());
        return "rentals";
    }

    @GetMapping("/bicycles")
    public String bicyclesPage(Model model) {
        model.addAttribute("bikes", bikeRepository.findAll());
        return "bicycle";
    }

    @GetMapping("/users")
    public String usersPage(Model model) {
        model.addAttribute("users", userRepository.findAll());
        return "users";
    }

    @GetMapping("/transactions")
    public String transactionsPage(Model model) {
        model.addAttribute("rentals", rentalRepository.findAll());
        return "transactions";
    }

    @GetMapping("/receipt/{id}")
    public String receiptPage(@PathVariable String id, Model model) {
        Rental rental = rentalRepository.findById(id).orElse(null);
        model.addAttribute("rental", rental);
        return "receipt";
    }
}