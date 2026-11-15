package bicycle_rental.controller;

import java.util.List;

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

    // Helper method to reliably detect Admin role
    private boolean checkIsAdmin(Authentication authentication, User currentUser) {
        if (authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().toUpperCase().contains("ADMIN"))) {
            return true;
        }
        return currentUser != null && currentUser.getRole() != null
                && currentUser.getRole().toUpperCase().contains("ADMIN");
    }

    // Automatically loads logged-in user & role for EVERY page
    @ModelAttribute
    public void addGlobalUserAttributes(Model model, Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            String email = authentication.getName();
            User currentUser = userRepository.findAll().stream()
                    .filter(u -> email.equalsIgnoreCase(u.getEmail()) || email.equalsIgnoreCase(u.getId()))
                    .findFirst()
                    .orElse(null);

            boolean isAdmin = checkIsAdmin(authentication, currentUser);

            model.addAttribute("username", (currentUser != null) ? currentUser.getName() : email);
            model.addAttribute("userEmail", email);
            model.addAttribute("userRole", isAdmin ? "Admin" : "Customer");
        }
    }

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
    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model, Authentication authentication) {
        if (authentication == null) return "redirect:/login";

        String email = authentication.getName();
        User currentUser = userRepository.findAll().stream()
                .filter(u -> email.equalsIgnoreCase(u.getEmail()) || email.equalsIgnoreCase(u.getId()))
                .findFirst()
                .orElse(null);

        boolean isAdmin = checkIsAdmin(authentication, currentUser);

        if (isAdmin) {
            List<Rental> allRentals = rentalRepository.findAll();
            long activeCount = allRentals.stream()
                    .filter(r -> "ACTIVE".equalsIgnoreCase(r.getStatus()) || "Ongoing".equalsIgnoreCase(r.getStatus()))
                    .count();

            double totalEarnings = allRentals.stream()
                    .filter(r -> !"ACTIVE".equalsIgnoreCase(r.getStatus()) && !"Ongoing".equalsIgnoreCase(r.getStatus()))
                    .mapToDouble(r -> {
                        Bike b = bikeRepository.findById(r.getBikeId()).orElse(null);
                        return (b != null && b.getRatePerHour() > 0) ? b.getRatePerHour() : 150.0;
                    })
                    .sum();

            model.addAttribute("totalBikes", bikeRepository.count());
            model.addAttribute("activeRentals", activeCount);
            model.addAttribute("totalUsers", userRepository.count());
            model.addAttribute("totalEarnings", totalEarnings);
            model.addAttribute("recentRentals", allRentals.stream().limit(5).toList());
            return "dashboard";
        } else {
            var sortedBikes = bikeRepository.findAll().stream()
                    .sorted((b1, b2) -> "Available".equalsIgnoreCase(b1.getStatus()) ? -1 : 1)
                    .toList();

            long freeBikesCount = sortedBikes.stream()
                    .filter(b -> "Available".equalsIgnoreCase(b.getStatus()))
                    .count();

            model.addAttribute("availableBikes", sortedBikes);
            model.addAttribute("availableFleetCount", freeBikesCount);

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

    // Rent Bike (Works for both Customer & Admin)
    @PostMapping("/rent-bike")
    public String processRental(@RequestParam("bikeId") String bikeId,
                                @RequestParam(value = "userId", required = false) String userId,
                                @RequestParam(value = "hours", defaultValue = "1") int hours,
                                @RequestParam(value = "paymentMethod", defaultValue = "GCash") String paymentMethod,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        try {
            String targetUserId = userId;
            if (targetUserId == null || targetUserId.isBlank()) {
                String email = authentication.getName();
                User currentUser = userRepository.findAll().stream()
                        .filter(u -> email.equalsIgnoreCase(u.getEmail()) || email.equalsIgnoreCase(u.getId()))
                        .findFirst()
                        .orElse(null);
                if (currentUser != null) targetUserId = currentUser.getId();
            }

            if (targetUserId != null) {
                rentalService.processBikeRental(bikeId, targetUserId);
                redirectAttributes.addFlashAttribute("message",
                        "Booked " + bikeId + " for " + hours + " hr(s) via " + paymentMethod + "!");
            } else {
                redirectAttributes.addFlashAttribute("message", "Error: User not found.");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("message", "Could not complete rental: " + e.getMessage());
        }
        return "redirect:/";
    }

    // Pay & Return Bike (Works smoothly for BOTH Customer and Admin)
    @PostMapping("/pay-return")
    public String payAndReturnBike(@RequestParam("rentalId") String rentalId,
                                   Authentication authentication,
                                   RedirectAttributes redirectAttributes) {
        Rental rental = rentalRepository.findById(rentalId).orElse(null);
        if (rental != null && ("ACTIVE".equalsIgnoreCase(rental.getStatus()) || "Ongoing".equalsIgnoreCase(rental.getStatus()))) {
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

        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().toUpperCase().contains("ADMIN"));

        return isAdmin ? "redirect:/transactions" : "redirect:/";
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

    // ================= ADMIN ROUTES =================

    @GetMapping("/rentals")
    public String rentalsPage(Model model) {
        model.addAttribute("bikes", bikeRepository.findAll());
        model.addAttribute("users", userRepository.findAll());
        return "rentals";
    }

    @GetMapping("/bicycles")
    public String bicyclesPage(Model model) {
        model.addAttribute("bikes", bikeRepository.findAll());
        return "bicycle";
    }

    @PostMapping("/bicycles/save")
    public String saveBike(@RequestParam(value = "id", required = false) String id,
                           @RequestParam("model") String bikeModel,
                           @RequestParam("category") String category,
                           @RequestParam("ratePerHour") double ratePerHour,
                           @RequestParam(value = "status", defaultValue = "Available") String status,
                           RedirectAttributes redirectAttributes) {
        String bikeId = (id != null && !id.isBlank()) ? id : "BIK" + (100 + bikeRepository.count() + 1);
        Bike bike = new Bike(bikeId, bikeModel, category, ratePerHour, status);
        bikeRepository.save(bike);
        redirectAttributes.addFlashAttribute("message", "Bicycle " + bikeId + " saved!");
        return "redirect:/bicycles";
    }

    @PostMapping("/bicycles/delete/{id}")
    public String deleteBike(@PathVariable String id, RedirectAttributes redirectAttributes) {
        bikeRepository.deleteById(id);
        redirectAttributes.addFlashAttribute("message", "Bicycle " + id + " deleted.");
        return "redirect:/bicycles";
    }

    @GetMapping("/users")
    public String usersPage(Model model) {
        model.addAttribute("users", userRepository.findAll());
        return "users";
    }

    @PostMapping("/users/delete/{id}")
    public String deleteUser(@PathVariable String id, RedirectAttributes redirectAttributes) {
        userRepository.deleteById(id);
        redirectAttributes.addFlashAttribute("message", "User removed.");
        return "redirect:/users";
    }

    @GetMapping("/transactions")
    public String transactionsPage(Model model) {
        model.addAttribute("rentals", rentalRepository.findAll());
        return "transactions";
    }

    @GetMapping("/receipt/{id}")
    public String receiptPage(@PathVariable String id, Model model) {
        Rental rental = rentalRepository.findById(id).orElse(null);
        Bike bike = (rental != null) ? bikeRepository.findById(rental.getBikeId()).orElse(null) : null;
        User customer = (rental != null) ? userRepository.findById(rental.getUserId()).orElse(null) : null;

        model.addAttribute("rental", rental);
        model.addAttribute("bike", bike);
        model.addAttribute("customer", customer);
        return "receipt";
    }
}