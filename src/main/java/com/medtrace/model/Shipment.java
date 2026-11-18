package com.medtrace.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Getter @Setter
public class Shipment {
    public enum Status { SENT, ACCEPTED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne private Batch batch;
    @ManyToOne private AppUser sender;
    @ManyToOne private AppUser receiver;
    private int quantity;
    private int soldQty;      // sold to customers (pharmacy)
    private int returnedQty;  // returned during recall
    @Enumerated(EnumType.STRING) private Status status = Status.SENT;
    private LocalDateTime sentAt = LocalDateTime.now();
}
