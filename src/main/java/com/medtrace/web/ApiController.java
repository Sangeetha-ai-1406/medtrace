package com.medtrace.web;
import com.medtrace.model.*;
import com.medtrace.model.AppUser.Role;
import com.medtrace.service.MedService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api") @RequiredArgsConstructor
public class ApiController {
    private final MedService svc;
    private static final String T = "X-Token";
    record Reg(String username, String password, String name, Role role) {}
    record Login(String username, String password) {}
    record ShipReq(Long batchId, Long receiverId, int quantity) {}
    record Qty(int quantity) {}
    record RecallReq(Long batchId, String reason) {}

    @PostMapping("/auth/register")
    public AppUser register(@RequestBody Reg r) { return svc.register(r.username(), r.password(), r.name(), r.role()); }

    @PostMapping("/auth/login")
    public Map<String, String> login(@RequestBody Login l) { return Map.of("token", svc.login(l.username(), l.password())); }

    @PostMapping("/batches")
    public Batch createBatch(@RequestBody Batch b, @RequestHeader(T) String t) { return svc.createBatch(svc.who(t, Role.ADMIN), b); }

    @GetMapping("/batches/{id}/trace")
    public List<Shipment> trace(@PathVariable Long id, @RequestHeader(T) String t) { svc.who(t); return svc.trace(id); }

    @PostMapping("/shipments")
    public Shipment ship(@RequestBody ShipReq r, @RequestHeader(T) String t) {
        return svc.ship(svc.who(t, Role.ADMIN, Role.DISTRIBUTOR), r.batchId(), r.receiverId(), r.quantity());
    }

    @GetMapping("/shipments/incoming")
    public List<Shipment> incoming(@RequestHeader(T) String t) { return svc.incoming(svc.who(t)); }

    @PostMapping("/shipments/{id}/accept")
    public Shipment accept(@PathVariable Long id, @RequestHeader(T) String t) { return svc.accept(svc.who(t), id); }

    @PostMapping("/shipments/{id}/sell")
    public Shipment sell(@PathVariable Long id, @RequestBody Qty q, @RequestHeader(T) String t) {
        return svc.sell(svc.who(t, Role.PHARMACY), id, q.quantity());
    }

    @PostMapping("/recalls")
    public Recall startRecall(@RequestBody RecallReq r, @RequestHeader(T) String t) { svc.who(t, Role.ADMIN); return svc.startRecall(r.batchId(), r.reason()); }

    @GetMapping("/recalls/{id}/progress")
    public List<Map<String, Object>> progress(@PathVariable Long id, @RequestHeader(T) String t) { svc.who(t, Role.ADMIN); return svc.progress(id); }

    @GetMapping("/recalls/notices")
    public List<Shipment> notices(@RequestHeader(T) String t) { return svc.myRecallNotices(svc.who(t)); }

    @PostMapping("/shipments/{id}/return")
    public Shipment returnStock(@PathVariable Long id, @RequestBody Qty q, @RequestHeader(T) String t) {
        return svc.returnStock(svc.who(t, Role.DISTRIBUTOR, Role.PHARMACY), id, q.quantity());
    }

    @PostMapping("/recalls/{id}/close")
    public Recall close(@PathVariable Long id, @RequestHeader(T) String t) { svc.who(t, Role.ADMIN); return svc.closeRecall(id); }

    @RestControllerAdvice
    static class Errors {
        @ExceptionHandler(IllegalArgumentException.class)
        ResponseEntity<Map<String, String>> bad(IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
        @ExceptionHandler(SecurityException.class)
        ResponseEntity<Map<String, String>> denied(SecurityException e) { return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage())); }
        @ExceptionHandler(org.springframework.web.bind.MissingRequestHeaderException.class)
        ResponseEntity<Map<String, String>> noToken(Exception e) { return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Login required: send X-Token header")); }
    }
}
