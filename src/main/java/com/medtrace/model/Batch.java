package com.medtrace.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity @Getter @Setter
public class Batch {
    public enum Status { ACTIVE, RECALLED, RECALL_CLOSED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(unique = true, nullable = false) private String batchNo;
    private String medicineName;
    private int quantity;
    private LocalDate mfgDate;
    private LocalDate expiryDate;
    @Enumerated(EnumType.STRING) private Status status = Status.ACTIVE;
    @ManyToOne private AppUser manufacturer;
}
