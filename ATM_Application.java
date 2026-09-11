
package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@SpringBootApplication
@RestController
@RequestMapping("/api/atm")
@CrossOrigin(origins = "*") // بالسماح للواجهة بالاتصال
public class AtmApplication {

    public static void main(String[] args) {
        SpringApplication.run(AtmApplication.class, args);
    }

    // --- In-Memory Database ---
    private static final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private static final Map<String, Integer> failedAttempts = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> lockedAccounts = new ConcurrentHashMap<>();

    static {
        // حسابات تجريبية للأختبار
        Account acc1 = new Account("user101", "1234", 1000.00);
        Account acc2 = new Account("user102", "5678", 500.00);
        accounts.put(acc1.getUserId(), acc1);
        accounts.put(acc2.getUserId(), acc2);
    }

    // --- Endpoints / APIs ---

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        String userId = request.getUserId();
        String pin = request.getPin();

        if (lockedAccounts.getOrDefault(userId, false)) {
            return ResponseEntity.status(403).body(Map.of("message", "Account locked due to 3 failed login attempts."));
        }

        Account acc = accounts.get(userId);
        if (acc != null && acc.validatePin(pin)) {
            failedAttempts.put(userId, 0); // إعادة تعيين المحاولات الخاطئة
            return ResponseEntity.ok(Map.of("message", "Login successful", "account", acc));
        } else {
            int attempts = failedAttempts.getOrDefault(userId, 0) + 1;
            failedAttempts.put(userId, attempts);

            if (attempts >= 3) {
                lockedAccounts.put(userId, true);
                return ResponseEntity.status(403).body(Map.of("message", "Account has been locked after 3 failed attempts!"));
            }

            int remaining = 3 - attempts;
            return ResponseEntity.status(401).body(Map.of("message", "Invalid credentials. Attempts remaining: " + remaining));
        }
    }

    @GetMapping("/account/{userId}")
    public ResponseEntity<?> getAccount(@PathVariable String userId) {
        Account acc = accounts.get(userId);
        if (acc == null) return ResponseEntity.status(404).body(Map.of("message", "Account not found"));
        return ResponseEntity.ok(acc);
    }

    @PostMapping("/deposit")
    public ResponseEntity<?> deposit(@RequestBody TransactionRequest req) {
        Account acc = accounts.get(req.getUserId());
        if (acc == null) return ResponseEntity.badRequest().body(Map.of("message", "Account not found"));

        if (req.getAmount() <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Amount must be greater than zero."));
        }

        acc.deposit(req.getAmount());
        return ResponseEntity.ok(Map.of("message", "Deposit successful", "balance", acc.getBalance()));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<?> withdraw(@RequestBody TransactionRequest req) {
        Account acc = accounts.get(req.getUserId());
        if (acc == null) return ResponseEntity.badRequest().body(Map.of("message", "Account not found"));

        if (req.getAmount() <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Amount must be greater than zero."));
        }

        if (req.getAmount() > acc.getBalance()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Insufficient Funds!"));
        }

        acc.withdraw(req.getAmount());
        return ResponseEntity.ok(Map.of("message", "Withdrawal successful", "balance", acc.getBalance()));
    }

    @PostMapping("/transfer")
    public ResponseEntity<?> transfer(@RequestBody TransferRequest req) {
        Account sender = accounts.get(req.getSenderId());
        Account recipient = accounts.get(req.getRecipientId());

        if (sender == null || recipient == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid sender or recipient account."));
        }

        if (sender.getUserId().equals(recipient.getUserId())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Cannot transfer money to yourself."));
        }

        if (req.getAmount() <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Amount must be greater than zero."));
        }

        if (req.getAmount() > sender.getBalance()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Insufficient Funds!"));
        }

        sender.transfer(recipient, req.getAmount());
        return ResponseEntity.ok(Map.of("message", "Transfer successful", "balance", sender.getBalance()));
    }

    // --- Domain Models ---

    public static class Account {
        private String userId;
        private String pin;
        private double balance;
        private List<Transaction> history = new ArrayList<>();

        public Account(String userId, String pin, double balance) {
            this.userId = userId;
            this.pin = pin;
            this.balance = balance;
            addTransaction("Initial Deposit", balance);
        }

        public String getUserId() { return userId; }
        public boolean validatePin(String pin) { return this.pin.equals(pin); }
        public double getBalance() { return balance; }
        public List<Transaction> getHistory() { return history; }

        public void deposit(double amount) {
            balance += amount;
            addTransaction("Deposit", amount);
        }

        public void withdraw(double amount) {
            balance -= amount;
            addTransaction("Withdrawal", amount);
        }

        public void transfer(Account recipient, double amount) {
            this.balance -= amount;
            this.addTransaction("Transfer Out to " + recipient.getUserId(), amount);
            recipient.balance += amount;
            recipient.addTransaction("Transfer In from " + this.userId, amount);
        }

        private void addTransaction(String type, double amount) {
            history.add(0, new Transaction(type, amount, balance)); // Newest first
        }
    }

    public static class Transaction {
        private String type;
        private double amount;
        private double postBalance;
        private String timestamp;

        public Transaction(String type, double amount, double postBalance) {
            this.type = type;
            this.amount = amount;
            this.postBalance = postBalance;
            this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }

        public String getType() { return type; }
        public double getAmount() { return amount; }
        public double getPostBalance() { return postBalance; }
        public String getTimestamp() { return timestamp; }
    }

    // --- DTO Requests ---
    public static class LoginRequest {
        private String userId; private String pin;
        public String getUserId() { return userId; } public String getPin() { return pin; }
    }
    public static class TransactionRequest {
        private String userId; private double amount;
        public String getUserId() { return userId; } public double getAmount() { return amount; }
    }
    public static class TransferRequest {
        private String senderId; private String recipientId; private double amount;
        public String getSenderId() { return senderId; } public String getRecipientId() { return recipientId; } public double getAmount() { return amount; }
    }
}
