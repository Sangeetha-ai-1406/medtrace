package com.medtrace.service;
import com.medtrace.model.*;
import com.medtrace.repo.Repos;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional
public class MedService {
    private final Repos.Users users;
    private final Repos.Batches batches;
    private final Repos.Shipments ships;
    private final Repos.Recalls recalls;
    private final PasswordEncoder enc;

    public AppUser register(String username, String password, String name, AppUser.Role role) {
        if (username == null || password == null || role == null) throw new IllegalArgumentException("username, password, role required");
        if (users.findByUsername(username).isPresent()) throw new IllegalArgumentException("Username already taken");
        AppUser u = new AppUser();
        u.setUsername(username); u.setPassword(enc.encode(password)); u.setName(name); u.setRole(role);
        return users.save(u);
    }

    private final Map<String, Long> tokens = new java.util.concurrent.ConcurrentHashMap<>();

    public String login(String username, String password) {
        AppUser u = users.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("Invalid login"));
        if (!enc.matches(password, u.getPassword())) throw new IllegalArgumentException("Invalid login");
        String token = UUID.randomUUID().toString();
        tokens.put(token, u.getId());
        return token;
    }

    /** Checks the token and (optionally) the role; returns the username. */
    public String who(String token, AppUser.Role... roles) {
        Long id = token == null ? null : tokens.get(token);
        if (id == null) throw new SecurityException("Login required");
        AppUser u = users.findById(id).orElseThrow();
        if (roles.length > 0 && !Arrays.asList(roles).contains(u.getRole()))
            throw new SecurityException("Not allowed for role " + u.getRole());
        return u.getUsername();
    }

    public AppUser me(String username) { return users.findByUsername(username).orElseThrow(); }

    public Batch createBatch(String username, Batch b) {
        if (b.getQuantity() <= 0) throw new IllegalArgumentException("Quantity must be positive");
        if (b.getMfgDate() == null || b.getExpiryDate() == null || !b.getExpiryDate().isAfter(b.getMfgDate()))
            throw new IllegalArgumentException("Expiry date must be after manufacturing date");
        if (batches.findByBatchNo(b.getBatchNo()).isPresent()) throw new IllegalArgumentException("Batch number exists");
        b.setStatus(Batch.Status.ACTIVE);
        b.setManufacturer(me(username));
        return batches.save(b);
    }

    /** Units of a batch currently held by a user (manufacturer, distributor or pharmacy). */
    public int held(Batch b, AppUser u) {
        int in = 0, out = 0, gone = 0;
        for (Shipment s : ships.findByBatch(b)) {
            if (s.getReceiver().getId().equals(u.getId()) && s.getStatus() == Shipment.Status.ACCEPTED)
                in += s.getQuantity() - s.getSoldQty() - s.getReturnedQty();
            if (s.getSender().getId().equals(u.getId())) out += s.getQuantity();
        }
        int base = b.getManufacturer().getId().equals(u.getId()) ? b.getQuantity() : 0;
        return base + in - out;
    }

    public Shipment ship(String username, Long batchId, Long receiverId, int qty) {
        AppUser sender = me(username);
        Batch b = batches.findById(batchId).orElseThrow(() -> new IllegalArgumentException("Batch not found"));
        AppUser rcv = users.findById(receiverId).orElseThrow(() -> new IllegalArgumentException("Receiver not found"));
        if (b.getStatus() != Batch.Status.ACTIVE) throw new IllegalArgumentException("Batch is recalled, cannot ship");
        boolean ok = (sender.getRole() == AppUser.Role.ADMIN && rcv.getRole() == AppUser.Role.DISTRIBUTOR)
                  || (sender.getRole() == AppUser.Role.DISTRIBUTOR && rcv.getRole() == AppUser.Role.PHARMACY);
        if (!ok) throw new IllegalArgumentException("Allowed flow: manufacturer->distributor->pharmacy");
        if (qty <= 0 || qty > held(b, sender)) throw new IllegalArgumentException("Invalid quantity. You hold " + held(b, sender));
        Shipment s = new Shipment();
        s.setBatch(b); s.setSender(sender); s.setReceiver(rcv); s.setQuantity(qty);
        return ships.save(s);
    }

    public Shipment accept(String username, Long shipId) {
        Shipment s = ships.findById(shipId).orElseThrow(() -> new IllegalArgumentException("Shipment not found"));
        if (!s.getReceiver().getUsername().equals(username)) throw new IllegalArgumentException("Not your shipment");
        if (s.getStatus() != Shipment.Status.SENT) throw new IllegalArgumentException("Already accepted");
        s.setStatus(Shipment.Status.ACCEPTED);
        return s;
    }

    public List<Shipment> incoming(String username) {
        return ships.findByReceiver(me(username)).stream().filter(s -> s.getStatus() == Shipment.Status.SENT).toList();
    }

    public Shipment sell(String username, Long shipId, int qty) {
        Shipment s = ships.findById(shipId).orElseThrow(() -> new IllegalArgumentException("Shipment not found"));
        if (!s.getReceiver().getUsername().equals(username) || s.getReceiver().getRole() != AppUser.Role.PHARMACY)
            throw new IllegalArgumentException("Only the receiving pharmacy can record sales");
        if (s.getBatch().getStatus() != Batch.Status.ACTIVE) throw new IllegalArgumentException("Batch recalled: sales blocked");
        if (s.getStatus() != Shipment.Status.ACCEPTED) throw new IllegalArgumentException("Accept shipment first");
        if (qty <= 0 || qty > s.getQuantity() - s.getSoldQty() - s.getReturnedQty()) throw new IllegalArgumentException("Not enough stock");
        s.setSoldQty(s.getSoldQty() + qty);
        return s;
    }

    public List<Shipment> trace(Long batchId) {
        Batch b = batches.findById(batchId).orElseThrow(() -> new IllegalArgumentException("Batch not found"));
        return ships.findByBatch(b);
    }

    public Recall startRecall(Long batchId, String reason) {
        Batch b = batches.findById(batchId).orElseThrow(() -> new IllegalArgumentException("Batch not found"));
        if (b.getStatus() != Batch.Status.ACTIVE) throw new IllegalArgumentException("Batch already recalled");
        b.setStatus(Batch.Status.RECALLED);
        Recall r = new Recall(); r.setBatch(b); r.setReason(reason);
        return recalls.save(r);
    }

    /** Every distributor/pharmacy that received the batch, with progress. */
    public List<Map<String, Object>> progress(Long recallId) {
        Recall r = recalls.findById(recallId).orElseThrow(() -> new IllegalArgumentException("Recall not found"));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Shipment s : ships.findByBatch(r.getBatch())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("shipmentId", s.getId()); m.put("party", s.getReceiver().getName());
            m.put("role", s.getReceiver().getRole()); m.put("received", s.getQuantity());
            m.put("sold", s.getSoldQty()); m.put("returned", s.getReturnedQty());
            m.put("pendingReturn", s.getStatus() == Shipment.Status.ACCEPTED ? s.getQuantity() - s.getSoldQty() - s.getReturnedQty() : 0);
            out.add(m);
        }
        return out;
    }

    public List<Shipment> myRecallNotices(String username) {
        return ships.findByReceiver(me(username)).stream()
            .filter(s -> s.getBatch().getStatus() == Batch.Status.RECALLED).toList();
    }

    public Shipment returnStock(String username, Long shipId, int qty) {
        Shipment s = ships.findById(shipId).orElseThrow(() -> new IllegalArgumentException("Shipment not found"));
        if (!s.getReceiver().getUsername().equals(username)) throw new IllegalArgumentException("Not your shipment");
        if (s.getBatch().getStatus() != Batch.Status.RECALLED) throw new IllegalArgumentException("Batch is not under recall");
        if (qty <= 0 || qty > s.getQuantity() - s.getSoldQty() - s.getReturnedQty()) throw new IllegalArgumentException("Invalid return quantity");
        s.setReturnedQty(s.getReturnedQty() + qty);
        return s;
    }

    public Recall closeRecall(Long recallId) {
        Recall r = recalls.findById(recallId).orElseThrow(() -> new IllegalArgumentException("Recall not found"));
        int pending = progress(recallId).stream().mapToInt(m -> (int) m.get("pendingReturn")).sum();
        if (pending > 0) throw new IllegalArgumentException("Cannot close: " + pending + " units still pending return");
        r.setStatus(Recall.Status.CLOSED); r.setClosedAt(LocalDateTime.now());
        r.getBatch().setStatus(Batch.Status.RECALL_CLOSED);
        return r;
    }
}
