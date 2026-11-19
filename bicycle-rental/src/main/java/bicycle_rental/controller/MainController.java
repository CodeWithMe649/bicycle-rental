package bicycle_rental.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

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

    private boolean checkIsAdmin(Authentication authentication, User currentUser) {
        if (authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().toUpperCase().contains("ADMIN"))) {
            return true;
        }
        return currentUser != null && currentUser.getRole() != null
                && currentUser.getRole().toUpperCase().contains("ADMIN");
    }

    private double computeRentalRevenue(Rental r, Map<String, Bike> bikeMap) {
        if (r.getTotalPaid() > 0) {
            return r.getTotalPaid();
        }
        Bike b = (r.getBikeDetails() != null) ? r.getBikeDetails() : bikeMap.get(r.getBikeId());
        double rate = (b != null && b.getRatePerHour() > 0) ? b.getRatePerHour() : 150.0;
        return rate * Math.max(1, r.getBookedHours());
    }

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

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("user", new User());
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(@ModelAttribute("user") User user, RedirectAttributes redirectAttributes) {
        if (user.getName() == null || user.getName().trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Full name is required.");
            return "redirect:/register";
        }
        if (user.getEmail() == null || !user.getEmail().matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            redirectAttributes.addFlashAttribute("error", "Please enter a valid email address.");
            return "redirect:/register";
        }
        if (user.getPassword() == null || user.getPassword().length() < 6) {
            redirectAttributes.addFlashAttribute("error", "Password must be at least 6 characters long.");
            return "redirect:/register";
        }

        boolean emailExists = userRepository.findAll().stream()
                .anyMatch(u -> user.getEmail().trim().equalsIgnoreCase(u.getEmail()));
        if (emailExists) {
            redirectAttributes.addFlashAttribute("error", "An account with this email already exists.");
            return "redirect:/register";
        }

        user.setId("USR2026" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        user.setName(user.getName().trim());
        user.setEmail(user.getEmail().trim().toLowerCase());
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setRole("ROLE_CUSTOMER");
        userRepository.save(user);
        return "redirect:/login?registered";
    }

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
            List<Bike> allBikes = bikeRepository.findAll();
            long availableCount = allBikes.stream()
                    .filter(b -> "Available".equalsIgnoreCase(b.getStatus()))
                    .count();
            long rentedCount = allBikes.stream()
                    .filter(b -> "Rented".equalsIgnoreCase(b.getStatus()) || "In Use".equalsIgnoreCase(b.getStatus()))
                    .count();
            long maintenanceCount = allBikes.stream()
                    .filter(b -> "Maintenance".equalsIgnoreCase(b.getStatus()))
                    .count();

            List<Rental> allRentals = rentalRepository.findAll();
            List<Rental> activeRentalsList = allRentals.stream()
                    .filter(r -> "ACTIVE".equalsIgnoreCase(r.getStatus()) || "Ongoing".equalsIgnoreCase(r.getStatus()))
                    .toList();

            Map<String, User> userMap = userRepository.findAll().stream()
                    .filter(u -> u.getId() != null)
                    .collect(Collectors.toMap(u -> Objects.requireNonNull(u.getId()), u -> u, (a, b) -> a));

            Map<String, Bike> bikeMap = allBikes.stream()
                    .filter(b -> b.getId() != null)
                    .collect(Collectors.toMap(b -> Objects.requireNonNull(b.getId()), b -> b, (a, b) -> a));

            double totalEarnings = allRentals.stream()
                    .filter(r -> !"ACTIVE".equalsIgnoreCase(r.getStatus()) && !"Ongoing".equalsIgnoreCase(r.getStatus()))
                    .mapToDouble(r -> computeRentalRevenue(r, bikeMap))
                    .sum();

            model.addAttribute("totalBikes", allBikes.size());
            model.addAttribute("availableBikesCount", availableCount);
            model.addAttribute("rentedBikesCount", rentedCount);
            model.addAttribute("maintenanceBikesCount", maintenanceCount);
            model.addAttribute("activeRentals", activeRentalsList.size());
            model.addAttribute("activeRentalsList", activeRentalsList);
            model.addAttribute("userMap", userMap);
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

            if (targetUserId == null) {
                redirectAttributes.addFlashAttribute("message", "Error: User not found.");
                return "redirect:/";
            }

            final String finalUserId = targetUserId;
            boolean hasUnreturnedBike = rentalRepository.findAll().stream()
                    .anyMatch(r -> finalUserId.equals(r.getUserId())
                            && ("ACTIVE".equalsIgnoreCase(r.getStatus()) || "Ongoing".equalsIgnoreCase(r.getStatus())));

            if (hasUnreturnedBike) {
                redirectAttributes.addFlashAttribute("message",
                        "Rental blocked: You already have an active unreturned bicycle. Please return it first.");
                return "redirect:/";
            }

            Rental booked = rentalService.processBikeRental(bikeId, targetUserId, hours, paymentMethod);

            redirectAttributes.addFlashAttribute("message",
                    String.format("Booked %s for %d hr(s) via %s (₱%.2f)!",
                            bikeId, hours, paymentMethod, booked.getTotalPaid()));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("message", "Could not complete rental: " + e.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/pay-return")
    public String payAndReturnBike(@RequestParam("rentalId") String rentalId,
                                   Authentication authentication,
                                   RedirectAttributes redirectAttributes) {
        try {
            Rental settled = rentalService.settleAndReturnBike(rentalId);
            if (settled.getLateFee() > 0) {
                redirectAttributes.addFlashAttribute("message",
                        String.format("Settled %s! Base: ₱%.2f + Overdue Late Fee: ₱%.2f = Total: ₱%.2f.",
                                settled.getBikeId(), settled.getBaseAmount(), settled.getLateFee(), settled.getTotalPaid()));
            } else {
                redirectAttributes.addFlashAttribute("message",
                        String.format("On-time return settled! Bike %s returned (Total: ₱%.2f).",
                                settled.getBikeId(), settled.getTotalPaid()));
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("message", "Return error: " + e.getMessage());
        }

        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().toUpperCase().contains("ADMIN"));

        return isAdmin ? "redirect:/dashboard" : "redirect:/";
    }

    @PostMapping("/extend-rental")
    public String extendRental(@RequestParam("rentalId") String rentalId,
                               RedirectAttributes redirectAttributes) {
        Rental rental = rentalRepository.findById(rentalId).orElse(null);
        if (rental != null) {
            Bike bike = bikeRepository.findById(rental.getBikeId()).orElse(null);
            double rate = (bike != null && bike.getRatePerHour() > 0) ? bike.getRatePerHour() : 150.0;

            LocalDateTime base = (rental.getDueTime() != null) ? rental.getDueTime() : LocalDateTime.now();
            rental.setDueTime(base.plusHours(1));
            rental.setBookedHours(Math.max(1, rental.getBookedHours()) + 1);
            rental.setBaseAmount(rental.getBookedHours() * rate);
            rental.setTotalPaid(rental.getBaseAmount());
            rentalRepository.save(rental);

            redirectAttributes.addFlashAttribute("message",
                    String.format("Rental %s extended by +1 hour (+₱%.2f)!", rentalId, rate));
        }
        return "redirect:/";
    }

    @PostMapping("/report-issue")
    public String reportIssue(@RequestParam(value = "bikeId", required = false) String bikeId,
                              @RequestParam("issueNote") String issueNote,
                              RedirectAttributes redirectAttributes) {
        if (bikeId != null && !bikeId.isBlank()) {
            Bike bike = bikeRepository.findById(bikeId.trim().toUpperCase()).orElse(null);
            if (bike != null) {
                if ("Available".equalsIgnoreCase(bike.getStatus())) {
                    bike.setStatus("Maintenance");
                    bikeRepository.save(bike);
                }
                redirectAttributes.addFlashAttribute("message",
                        "Issue logged for " + bike.getId() + " (\"" + issueNote + "\"). Fleet status updated.");
                return "redirect:/";
            }
        }
        redirectAttributes.addFlashAttribute("message",
                "Maintenance report logged: \"" + issueNote + "\". Our mechanics have been notified.");
        return "redirect:/";
    }

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
                    if (newPassword.length() < 6) {
                        redirectAttributes.addFlashAttribute("message", "Error: New password must be at least 6 characters.");
                        return "redirect:/";
                    }
                    currentUser.setPassword(passwordEncoder.encode(newPassword));
                }
                userRepository.save(currentUser);
                redirectAttributes.addFlashAttribute("message", "Profile updated successfully!");
            }
        }
        return "redirect:/";
    }

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
        if (bikeModel == null || bikeModel.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("message", "Validation Error: Bicycle model cannot be empty.");
            return "redirect:/bicycles";
        }
        if (ratePerHour <= 0) {
            redirectAttributes.addFlashAttribute("message", "Validation Error: Hourly rate must be greater than ₱0.");
            return "redirect:/bicycles";
        }

        String bikeId = (id != null && !id.isBlank())
                ? id
                : "BIK2026" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        Bike bike = (id != null && !id.isBlank())
                ? bikeRepository.findById(id).orElse(new Bike(bikeId, bikeModel.trim(), category, ratePerHour, status))
                : new Bike(bikeId, bikeModel.trim(), category, ratePerHour, status);
        bike.setModel(bikeModel.trim());
        bike.setCategory(category);
        bike.setRatePerHour(ratePerHour);
        bike.setStatus(status);
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
        List<Rental> allRentals = rentalService.getAllJoinedRentals();
        Map<String, User> userMap = userRepository.findAll().stream()
                .filter(u -> u.getId() != null)
                .collect(Collectors.toMap(u -> Objects.requireNonNull(u.getId()), u -> u, (a, b) -> a));
        Map<String, Bike> bikeMap = bikeRepository.findAll().stream()
                .filter(b -> b.getId() != null)
                .collect(Collectors.toMap(b -> Objects.requireNonNull(b.getId()), b -> b, (a, b) -> a));

        double totalRevenue = allRentals.stream()
                .filter(r -> !"ACTIVE".equalsIgnoreCase(r.getStatus()) && !"Ongoing".equalsIgnoreCase(r.getStatus()))
                .mapToDouble(r -> computeRentalRevenue(r, bikeMap))
                .sum();

        model.addAttribute("rentals", allRentals);
        model.addAttribute("userMap", userMap);
        model.addAttribute("bikeMap", bikeMap);
        model.addAttribute("totalRevenue", totalRevenue);
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