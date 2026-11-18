package com.medtrace.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Getter @Setter
public class Recall {
    public enum Status { OPEN, CLOSED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne private Batch batch;
    private String reason;
    @Enumerated(EnumType.STRING) private Status status = Status.OPEN;
    private LocalDateTime startedAt = LocalDateTime.now();
    private LocalDateTime closedAt;
}
