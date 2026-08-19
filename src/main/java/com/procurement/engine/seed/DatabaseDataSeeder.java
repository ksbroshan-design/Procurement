package com.procurement.engine.seed;

import com.procurement.engine.product.entity.Product;
import com.procurement.engine.product.entity.ReliabilityHistory;
import com.procurement.engine.product.repository.ProductRepository;
import com.procurement.engine.product.repository.ReliabilityHistoryRepository;
import com.procurement.engine.user.entity.Role;
import com.procurement.engine.user.entity.User;
import com.procurement.engine.user.repository.UserRepository;
import com.procurement.engine.vendor.entity.Vendor;
import com.procurement.engine.vendor.entity.VendorStatus;
import com.procurement.engine.vendor.repository.VendorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
public class DatabaseDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseDataSeeder.class);

    private final UserRepository userRepository;
    private final VendorRepository vendorRepository;
    private final ProductRepository productRepository;
    private final ReliabilityHistoryRepository reliabilityHistoryRepository;
    private final PasswordEncoder passwordEncoder;

    public DatabaseDataSeeder(UserRepository userRepository,
                              VendorRepository vendorRepository,
                              ProductRepository productRepository,
                              ReliabilityHistoryRepository reliabilityHistoryRepository,
                              PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.vendorRepository = vendorRepository;
        this.productRepository = productRepository;
        this.reliabilityHistoryRepository = reliabilityHistoryRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("Database already seeded. Skipping initial seeding.");
            return;
        }

        log.info("Starting database seeding for Autonomous Procurement Engine...");

        // 1. Seed Users
        seedUsers();

        // 2. Seed Vendors
        Map<String, Vendor> vendors = seedVendors();

        // 3. Seed Products and Reliability Data
        seedProducts(vendors);

        log.info("Database seeding completed successfully! Seeded {} users, {} vendors, {} products.",
                userRepository.count(), vendorRepository.count(), productRepository.count());
    }

    private void seedUsers() {
        User manager = User.builder()
                .name("Alex Hunter (Procurement Manager)")
                .email("manager@procurement.com")
                .password(passwordEncoder.encode("password123"))
                .role(Role.PROCUREMENT_MANAGER)
                .authorizationLimit(new BigDecimal("450000.00"))
                .build();
        userRepository.save(manager);

        User admin = User.builder()
                .name("Sarah Connor (Admin / Approver)")
                .email("admin@procurement.com")
                .password(passwordEncoder.encode("password123"))
                .role(Role.ADMIN)
                .authorizationLimit(new BigDecimal("5000000.00"))
                .build();
        userRepository.save(admin);
    }

    private Map<String, Vendor> seedVendors() {
        Map<String, Vendor> vendors = new HashMap<>();

        Vendor techDirect = Vendor.builder()
                .name("TechDirect Enterprises")
                .source("Enterprise Direct")
                .sellerRating(new BigDecimal("4.85"))
                .reliabilityScore(new BigDecimal("0.96"))
                .returnPolicy("30-day no-questions-asked enterprise replacement")
                .status(VendorStatus.ACTIVE)
                .build();
        vendors.put("TechDirect", vendorRepository.save(techDirect));

        Vendor megaRetail = Vendor.builder()
                .name("MegaRetail Supplies")
                .source("Wholesale B2B")
                .sellerRating(new BigDecimal("4.20"))
                .reliabilityScore(new BigDecimal("0.84"))
                .returnPolicy("15-day return with 10% restocking fee")
                .status(VendorStatus.ACTIVE)
                .build();
        vendors.put("MegaRetail", vendorRepository.save(megaRetail));

        Vendor globalEquip = Vendor.builder()
                .name("GlobalEquip Solutions")
                .source("Global Authorized Partner")
                .sellerRating(new BigDecimal("4.90"))
                .reliabilityScore(new BigDecimal("0.98"))
                .returnPolicy("45-day complete money back guarantee")
                .status(VendorStatus.ACTIVE)
                .build();
        vendors.put("GlobalEquip", vendorRepository.save(globalEquip));

        Vendor primeGoods = Vendor.builder()
                .name("PrimeGoods Distribution (Suspended)")
                .source("Third Party Marketplace")
                .sellerRating(new BigDecimal("3.10"))
                .reliabilityScore(new BigDecimal("0.60"))
                .returnPolicy("No returns once package unsealed")
                .status(VendorStatus.UNAVAILABLE)
                .build();
        vendors.put("PrimeGoods", vendorRepository.save(primeGoods));

        return vendors;
    }

    private void seedProducts(Map<String, Vendor> vendors) {
        Vendor techDirect = vendors.get("TechDirect");
        Vendor megaRetail = vendors.get("MegaRetail");
        Vendor globalEquip = vendors.get("GlobalEquip");
        Vendor primeGoods = vendors.get("PrimeGoods");

        // ==================== 1. TVs (6 items) ====================
        createProductWithReliability(
                techDirect, "LG C3 55-Inch 4K OLED evo Smart TV", "TV", "LG", "OLED55C3PSA",
                new BigDecimal("56000.00"), true, 25, 3,
                new BigDecimal("4.85"), new BigDecimal("0.95"), 3, "ONSITE", 30,
                Map.of("screenSize", 55, "resolution", "4K", "panelType", "OLED", "refreshRate", 120, "smartTv", true, "hdmiPorts", 4),
                new BigDecimal("0.0250"), new BigDecimal("1200.00"), new BigDecimal("400.00"), 850
        );

        // Cheaper upfront, but lower reliability, higher repair cost -> False Economy vs LG C3!
        createProductWithReliability(
                megaRetail, "VisionMax 55-Inch 4K Budget LED TV", "TV", "VisionMax", "VM55-4K-ECO",
                new BigDecimal("42000.00"), true, 40, 5,
                new BigDecimal("3.80"), new BigDecimal("0.72"), 1, "STANDARD", 15,
                Map.of("screenSize", 55, "resolution", "4K", "panelType", "LED", "refreshRate", 60, "smartTv", true, "hdmiPorts", 2),
                new BigDecimal("0.1400"), new BigDecimal("8500.00"), new BigDecimal("3500.00"), 320
        );

        createProductWithReliability(
                globalEquip, "Samsung 55-Inch Neo QLED 4K Smart TV", "TV", "Samsung", "QA55QN90CA",
                new BigDecimal("58000.00"), true, 18, 4,
                new BigDecimal("4.80"), new BigDecimal("0.94"), 3, "ONSITE", 30,
                Map.of("screenSize", 55, "resolution", "4K", "panelType", "QLED", "refreshRate", 120, "smartTv", true, "hdmiPorts", 4),
                new BigDecimal("0.0300"), new BigDecimal("1500.00"), new BigDecimal("600.00"), 620
        );

        createProductWithReliability(
                techDirect, "Sony BRAVIA XR 55-Inch 4K OLED TV", "TV", "Sony", "XR-55A80L",
                new BigDecimal("68000.00"), true, 12, 2,
                new BigDecimal("4.90"), new BigDecimal("0.97"), 3, "EXTENDED", 30,
                Map.of("screenSize", 55, "resolution", "4K", "panelType", "OLED", "refreshRate", 120, "smartTv", true, "hdmiPorts", 4),
                new BigDecimal("0.0180"), new BigDecimal("2000.00"), new BigDecimal("500.00"), 450
        );

        // Fails hard constraint (Screen size < 55)
        createProductWithReliability(
                megaRetail, "Xiaomi 43-Inch 4K Dolby Vision TV", "TV", "Xiaomi", "X-Pro-43",
                new BigDecimal("28000.00"), true, 30, 4,
                new BigDecimal("4.10"), new BigDecimal("0.80"), 1, "STANDARD", 15,
                Map.of("screenSize", 43, "resolution", "4K", "panelType", "LED", "refreshRate", 60, "smartTv", true, "hdmiPorts", 3),
                new BigDecimal("0.0800"), new BigDecimal("3000.00"), new BigDecimal("1000.00"), 500
        );

        // Unavailable Vendor
        createProductWithReliability(
                primeGoods, "Hisense 55-Inch ULED 4K TV", "TV", "Hisense", "55U8K",
                new BigDecimal("49000.00"), false, 0, 7,
                new BigDecimal("3.50"), new BigDecimal("0.65"), 1, "STANDARD", 7,
                Map.of("screenSize", 55, "resolution", "4K", "panelType", "ULED", "refreshRate", 144, "smartTv", true, "hdmiPorts", 4),
                new BigDecimal("0.1100"), new BigDecimal("4500.00"), new BigDecimal("1800.00"), 180
        );

        // ==================== 2. Tablets (6 items) ====================
        createProductWithReliability(
                techDirect, "Lenovo Tab P12 Pro 12.6-Inch", "Tablet", "Lenovo", "Tab-P12-Pro",
                new BigDecimal("36000.00"), true, 35, 3,
                new BigDecimal("4.70"), new BigDecimal("0.92"), 2, "STANDARD", 30,
                Map.of("ram", 8, "storage", 256, "screenSize", 12.6, "processor", "Snapdragon 870", "batteryCapacity", 10200, "stylusIncluded", true),
                new BigDecimal("0.0350"), new BigDecimal("1800.00"), new BigDecimal("500.00"), 420
        );

        createProductWithReliability(
                globalEquip, "Xiaomi Pad 6 Max 14-Inch", "Tablet", "Xiaomi", "Pad6-Max-256",
                new BigDecimal("38500.00"), true, 20, 2,
                new BigDecimal("4.65"), new BigDecimal("0.90"), 2, "STANDARD", 30,
                Map.of("ram", 8, "storage", 256, "screenSize", 14.0, "processor", "Snapdragon 8+ Gen 1", "batteryCapacity", 10000, "stylusIncluded", false),
                new BigDecimal("0.0400"), new BigDecimal("2200.00"), new BigDecimal("700.00"), 350
        );

        createProductWithReliability(
                techDirect, "Samsung Galaxy Tab S9 FE+ 12.4-Inch", "Tablet", "Samsung", "Tab-S9-FE-Plus",
                new BigDecimal("44000.00"), true, 15, 3,
                new BigDecimal("4.85"), new BigDecimal("0.96"), 3, "EXTENDED", 30,
                Map.of("ram", 12, "storage", 256, "screenSize", 12.4, "processor", "Exynos 1380", "batteryCapacity", 10090, "stylusIncluded", true),
                new BigDecimal("0.0200"), new BigDecimal("1500.00"), new BigDecimal("400.00"), 580
        );

        // Fails hard constraint (RAM = 4GB, Storage = 64GB)
        createProductWithReliability(
                megaRetail, "Samsung Galaxy Tab A9+ 11-Inch", "Tablet", "Samsung", "Tab-A9-Plus",
                new BigDecimal("19000.00"), true, 50, 4,
                new BigDecimal("4.20"), new BigDecimal("0.85"), 1, "STANDARD", 15,
                Map.of("ram", 4, "storage", 64, "screenSize", 11.0, "processor", "Snapdragon 695", "batteryCapacity", 7040, "stylusIncluded", false),
                new BigDecimal("0.0600"), new BigDecimal("1200.00"), new BigDecimal("400.00"), 600
        );

        createProductWithReliability(
                globalEquip, "Apple iPad Air 11-Inch M2", "Tablet", "Apple", "iPadAir-M2-128",
                new BigDecimal("59000.00"), true, 25, 2,
                new BigDecimal("4.95"), new BigDecimal("0.98"), 2, "EXTENDED", 30,
                Map.of("ram", 8, "storage", 128, "screenSize", 11.0, "processor", "Apple M2", "batteryCapacity", 7606, "stylusIncluded", false),
                new BigDecimal("0.0150"), new BigDecimal("3500.00"), new BigDecimal("800.00"), 900
        );

        // Out of stock
        createProductWithReliability(
                megaRetail, "OnePlus Pad 11.6-Inch", "Tablet", "OnePlus", "OP-Pad-1",
                new BigDecimal("34000.00"), false, 0, 5,
                new BigDecimal("4.40"), new BigDecimal("0.88"), 1, "STANDARD", 15,
                Map.of("ram", 8, "storage", 128, "screenSize", 11.6, "processor", "Dimensity 9000", "batteryCapacity", 9510, "stylusIncluded", false),
                new BigDecimal("0.0450"), new BigDecimal("2000.00"), new BigDecimal("600.00"), 220
        );

        // ==================== 3. Laptops (6 items) ====================
        createProductWithReliability(
                techDirect, "Dell Latitude 5540 Business Laptop", "Laptop", "Dell", "LAT-5540-I7",
                new BigDecimal("78000.00"), true, 30, 3,
                new BigDecimal("4.80"), new BigDecimal("0.95"), 3, "ONSITE", 30,
                Map.of("ram", 16, "storage", 512, "screenSize", 15.6, "processor", "Intel Core i7-1365U", "batteryHours", 11, "weightKg", 1.61),
                new BigDecimal("0.0280"), new BigDecimal("3000.00"), new BigDecimal("1200.00"), 780
        );

        createProductWithReliability(
                globalEquip, "Lenovo ThinkPad T14s Gen 4", "Laptop", "Lenovo", "TP-T14S-G4",
                new BigDecimal("82000.00"), true, 22, 2,
                new BigDecimal("4.90"), new BigDecimal("0.97"), 3, "ONSITE", 45,
                Map.of("ram", 16, "storage", 512, "screenSize", 14.0, "processor", "AMD Ryzen 7 PRO 7840U", "batteryHours", 14, "weightKg", 1.25),
                new BigDecimal("0.0190"), new BigDecimal("2500.00"), new BigDecimal("800.00"), 890
        );

        createProductWithReliability(
                megaRetail, "HP Pavilion 15 Core i5 Budget", "Laptop", "HP", "PAV-15-EG30",
                new BigDecimal("52000.00"), true, 40, 5,
                new BigDecimal("4.10"), new BigDecimal("0.82"), 1, "STANDARD", 15,
                Map.of("ram", 16, "storage", 512, "screenSize", 15.6, "processor", "Intel Core i5-1335U", "batteryHours", 7, "weightKg", 1.75),
                new BigDecimal("0.0750"), new BigDecimal("4500.00"), new BigDecimal("2000.00"), 410
        );

        createProductWithReliability(
                techDirect, "Apple MacBook Air 15-Inch M3", "Laptop", "Apple", "MBA-15-M3-16",
                new BigDecimal("125000.00"), true, 18, 2,
                new BigDecimal("4.95"), new BigDecimal("0.98"), 2, "EXTENDED", 30,
                Map.of("ram", 16, "storage", 512, "screenSize", 15.3, "processor", "Apple M3", "batteryHours", 18, "weightKg", 1.51),
                new BigDecimal("0.0120"), new BigDecimal("6000.00"), new BigDecimal("1000.00"), 950
        );

        createProductWithReliability(
                megaRetail, "Acer Aspire 3 Entry Laptop", "Laptop", "Acer", "A315-59",
                new BigDecimal("35000.00"), true, 15, 6,
                new BigDecimal("3.90"), new BigDecimal("0.75"), 1, "STANDARD", 15,
                Map.of("ram", 8, "storage", 256, "screenSize", 15.6, "processor", "Intel Core i3-1215U", "batteryHours", 6, "weightKg", 1.78),
                new BigDecimal("0.0950"), new BigDecimal("4000.00"), new BigDecimal("1800.00"), 300
        );

        createProductWithReliability(
                primeGoods, "ASUS Zenbook 14 OLED (Suspended Vendor)", "Laptop", "ASUS", "UM3402YA",
                new BigDecimal("72000.00"), false, 0, 7,
                new BigDecimal("3.60"), new BigDecimal("0.70"), 1, "STANDARD", 7,
                Map.of("ram", 16, "storage", 512, "screenSize", 14.0, "processor", "AMD Ryzen 5 7530U", "batteryHours", 10, "weightKg", 1.39),
                new BigDecimal("0.0800"), new BigDecimal("5000.00"), new BigDecimal("2200.00"), 150
        );

        // ==================== 4. Monitors (6 items) ====================
        createProductWithReliability(
                techDirect, "Dell UltraSharp 27-Inch 4K USB-C Monitor", "Monitor", "Dell", "U2723QE",
                new BigDecimal("48000.00"), true, 28, 2,
                new BigDecimal("4.90"), new BigDecimal("0.96"), 3, "EXTENDED", 30,
                Map.of("screenSize", 27, "resolution", "4K", "panelType", "IPS Black", "refreshRate", 60, "usbHub", true, "powerDeliveryWatts", 90),
                new BigDecimal("0.0150"), new BigDecimal("1200.00"), new BigDecimal("300.00"), 720
        );

        createProductWithReliability(
                globalEquip, "LG 34-Inch UltraWide Curved IPS Monitor", "Monitor", "LG", "34WN80C-B",
                new BigDecimal("52000.00"), true, 16, 3,
                new BigDecimal("4.80"), new BigDecimal("0.94"), 3, "STANDARD", 30,
                Map.of("screenSize", 34, "resolution", "WQHD", "panelType", "IPS", "refreshRate", 75, "usbHub", true, "powerDeliveryWatts", 60),
                new BigDecimal("0.0220"), new BigDecimal("1800.00"), new BigDecimal("500.00"), 480
        );

        createProductWithReliability(
                megaRetail, "Samsung ViewFinity S6 27-Inch QHD", "Monitor", "Samsung", "S27A600",
                new BigDecimal("26000.00"), true, 35, 4,
                new BigDecimal("4.30"), new BigDecimal("0.86"), 2, "STANDARD", 15,
                Map.of("screenSize", 27, "resolution", "QHD", "panelType", "IPS", "refreshRate", 75, "usbHub", false, "powerDeliveryWatts", 0),
                new BigDecimal("0.0500"), new BigDecimal("2000.00"), new BigDecimal("800.00"), 360
        );

        createProductWithReliability(
                techDirect, "BenQ DesignVue 32-Inch 4K Professional Monitor", "Monitor", "BenQ", "PD3205U",
                new BigDecimal("62000.00"), true, 12, 3,
                new BigDecimal("4.85"), new BigDecimal("0.95"), 3, "ONSITE", 30,
                Map.of("screenSize", 32, "resolution", "4K", "panelType", "IPS", "refreshRate", 60, "usbHub", true, "powerDeliveryWatts", 90),
                new BigDecimal("0.0180"), new BigDecimal("1500.00"), new BigDecimal("400.00"), 310
        );

        createProductWithReliability(
                megaRetail, "Acer 24-Inch Full HD Office Monitor", "Monitor", "Acer", "EK240Y",
                new BigDecimal("8500.00"), true, 50, 4,
                new BigDecimal("4.10"), new BigDecimal("0.80"), 1, "STANDARD", 15,
                Map.of("screenSize", 24, "resolution", "FHD", "panelType", "VA", "refreshRate", 100, "usbHub", false, "powerDeliveryWatts", 0),
                new BigDecimal("0.0700"), new BigDecimal("1000.00"), new BigDecimal("300.00"), 520
        );

        createProductWithReliability(
                globalEquip, "ASUS ProArt Display 27-Inch 4K", "Monitor", "ASUS", "PA279CV",
                new BigDecimal("46000.00"), true, 14, 3,
                new BigDecimal("4.75"), new BigDecimal("0.92"), 3, "EXTENDED", 30,
                Map.of("screenSize", 27, "resolution", "4K", "panelType", "IPS", "refreshRate", 60, "usbHub", true, "powerDeliveryWatts", 65),
                new BigDecimal("0.0240"), new BigDecimal("1600.00"), new BigDecimal("450.00"), 400
        );

        // ==================== 5. Office Chairs (6 items) ====================
        createProductWithReliability(
                globalEquip, "Herman Miller Aeron Ergonomic Chair (Size B)", "Office chair", "Herman Miller", "AERON-B-GRY",
                new BigDecimal("115000.00"), true, 20, 5,
                new BigDecimal("4.98"), new BigDecimal("0.99"), 12, "EXTENDED", 45,
                Map.of("material", "Mesh", "weightCapacityKg", 159, "lumbarSupport", true, "adjustableArmrests", "4D", "tiltLock", true),
                new BigDecimal("0.0050"), new BigDecimal("1000.00"), new BigDecimal("100.00"), 1200
        );

        createProductWithReliability(
                techDirect, "Steelcase Gesture Ergonomic Office Chair", "Office chair", "Steelcase", "GESTURE-BLK",
                new BigDecimal("98000.00"), true, 15, 4,
                new BigDecimal("4.92"), new BigDecimal("0.98"), 10, "EXTENDED", 30,
                Map.of("material", "Fabric", "weightCapacityKg", 180, "lumbarSupport", true, "adjustableArmrests", "360-degree", "tiltLock", true),
                new BigDecimal("0.0080"), new BigDecimal("1200.00"), new BigDecimal("200.00"), 850
        );

        createProductWithReliability(
                techDirect, "Ergohuman Gen2 High Back Mesh Chair", "Office chair", "Ergohuman", "EH-GEN2-MSH",
                new BigDecimal("48000.00"), true, 30, 3,
                new BigDecimal("4.75"), new BigDecimal("0.93"), 5, "STANDARD", 30,
                Map.of("material", "Mesh", "weightCapacityKg", 135, "lumbarSupport", true, "adjustableArmrests", "3D", "tiltLock", true),
                new BigDecimal("0.0250"), new BigDecimal("1500.00"), new BigDecimal("400.00"), 640
        );

        createProductWithReliability(
                megaRetail, "Featherlite Helix High Back Ergonomic Chair", "Office chair", "Featherlite", "FL-HELIX-HB",
                new BigDecimal("18500.00"), true, 45, 5,
                new BigDecimal("4.35"), new BigDecimal("0.87"), 3, "STANDARD", 15,
                Map.of("material", "Mesh", "weightCapacityKg", 120, "lumbarSupport", true, "adjustableArmrests", "2D", "tiltLock", true),
                new BigDecimal("0.0450"), new BigDecimal("1800.00"), new BigDecimal("600.00"), 480
        );

        createProductWithReliability(
                megaRetail, "BasicComfort Executive Swivel Chair", "Office chair", "BasicComfort", "BC-EXEC-01",
                new BigDecimal("7500.00"), true, 60, 6,
                new BigDecimal("3.70"), new BigDecimal("0.70"), 1, "STANDARD", 15,
                Map.of("material", "PU Leather", "weightCapacityKg", 100, "lumbarSupport", false, "adjustableArmrests", "Fixed", "tiltLock", false),
                new BigDecimal("0.1600"), new BigDecimal("2200.00"), new BigDecimal("800.00"), 350
        );

        createProductWithReliability(
                globalEquip, "Green Soul Monster Ultimate Ergonomic Chair", "Office chair", "Green Soul", "GS-MONSTER-T",
                new BigDecimal("22000.00"), true, 25, 3,
                new BigDecimal("4.50"), new BigDecimal("0.89"), 3, "STANDARD", 30,
                Map.of("material", "Spandex Fabric", "weightCapacityKg", 130, "lumbarSupport", true, "adjustableArmrests", "4D", "tiltLock", true),
                new BigDecimal("0.0380"), new BigDecimal("1600.00"), new BigDecimal("500.00"), 520
        );

        // ==================== 6. Keyboards (6 items) ====================
        createProductWithReliability(
                techDirect, "Keychron Q1 Pro Wireless Custom Mechanical Keyboard", "Keyboard", "Keychron", "Q1P-M1",
                new BigDecimal("16500.00"), true, 40, 2,
                new BigDecimal("4.90"), new BigDecimal("0.96"), 2, "STANDARD", 30,
                Map.of("switchType", "Mechanical Red", "connectivity", "Wireless/Bluetooth/Type-C", "hotSwappable", true, "backlight", "RGB", "batteryCapacityMah", 4000),
                new BigDecimal("0.0150"), new BigDecimal("600.00"), new BigDecimal("150.00"), 490
        );

        createProductWithReliability(
                globalEquip, "Logitech MX Keys S Wireless Keyboard", "Keyboard", "Logitech", "MX-KEYS-S",
                new BigDecimal("11500.00"), true, 50, 2,
                new BigDecimal("4.85"), new BigDecimal("0.95"), 2, "STANDARD", 30,
                Map.of("switchType", "Scissor Membrane", "connectivity", "Wireless/Bluetooth", "hotSwappable", false, "backlight", "Smart White", "batteryCapacityMah", 1500),
                new BigDecimal("0.0180"), new BigDecimal("800.00"), new BigDecimal("200.00"), 820
        );

        createProductWithReliability(
                techDirect, "Corsair K70 RGB PRO Mechanical Gaming Keyboard", "Keyboard", "Corsair", "CH-9109410",
                new BigDecimal("14000.00"), true, 30, 3,
                new BigDecimal("4.75"), new BigDecimal("0.92"), 2, "STANDARD", 30,
                Map.of("switchType", "Cherry MX Red", "connectivity", "Type-C Wired", "hotSwappable", false, "backlight", "Per-Key RGB", "pollingRateHz", 8000),
                new BigDecimal("0.0250"), new BigDecimal("1000.00"), new BigDecimal("300.00"), 530
        );

        createProductWithReliability(
                megaRetail, "Logitech K120 USB Standard Office Keyboard", "Keyboard", "Logitech", "K120-BLK",
                new BigDecimal("550.00"), true, 150, 4,
                new BigDecimal("4.40"), new BigDecimal("0.90"), 3, "STANDARD", 15,
                Map.of("switchType", "Membrane", "connectivity", "USB Wired", "hotSwappable", false, "backlight", "None", "waterResistant", true),
                new BigDecimal("0.0300"), new BigDecimal("150.00"), new BigDecimal("50.00"), 1500
        );

        createProductWithReliability(
                globalEquip, "Epomaker RT100 Retro Mechanical Keyboard", "Keyboard", "Epomaker", "RT100-SEA",
                new BigDecimal("9500.00"), true, 22, 4,
                new BigDecimal("4.60"), new BigDecimal("0.88"), 1, "STANDARD", 30,
                Map.of("switchType", "Sea Salt Silent", "connectivity", "Triple Mode Wireless", "hotSwappable", true, "backlight", "RGB", "miniDisplay", true),
                new BigDecimal("0.0400"), new BigDecimal("800.00"), new BigDecimal("250.00"), 310
        );

        createProductWithReliability(
                megaRetail, "Razer BlackWidow V4 X Mechanical Gaming Keyboard", "Keyboard", "Razer", "RZ03-0470",
                new BigDecimal("10500.00"), true, 20, 5,
                new BigDecimal("4.50"), new BigDecimal("0.87"), 2, "STANDARD", 15,
                Map.of("switchType", "Razer Yellow Linear", "connectivity", "USB Wired", "hotSwappable", false, "backlight", "Chroma RGB", "macroKeys", 6),
                new BigDecimal("0.0350"), new BigDecimal("900.00"), new BigDecimal("300.00"), 420
        );
    }

    private void createProductWithReliability(
            Vendor vendor, String name, String category, String brand, String model,
            BigDecimal price, boolean availability, int availableQty, int deliveryDays,
            BigDecimal sellerRating, BigDecimal reliabilityScore, int warrantyDuration,
            String warrantyType, int returnWindow, Map<String, Object> specs,
            BigDecimal failureRate, BigDecimal repairCost, BigDecimal downtimeCost, int sampleSize
    ) {
        Product product = Product.builder()
                .vendor(vendor)
                .name(name)
                .category(category)
                .brand(brand)
                .model(model)
                .price(price)
                .currency("INR")
                .availability(availability)
                .availableQuantity(availableQty)
                .deliveryDays(deliveryDays)
                .sellerRating(sellerRating)
                .reliabilityScore(reliabilityScore)
                .warrantyDuration(warrantyDuration)
                .warrantyType(warrantyType)
                .returnWindow(returnWindow)
                .specifications(new HashMap<>(specs))
                .build();

        Product savedProduct = productRepository.save(product);

        ReliabilityHistory reliability = ReliabilityHistory.builder()
                .product(savedProduct)
                .failureRate(failureRate)
                .averageRepairCost(repairCost)
                .averageDowntimeCost(downtimeCost)
                .sampleSize(sampleSize)
                .recordedAt(Instant.now())
                .build();

        reliabilityHistoryRepository.save(reliability);
    }
}
