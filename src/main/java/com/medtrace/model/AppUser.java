package com.medtrace.model;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "users") @Getter @Setter
public class AppUser {
    public enum Role { ADMIN, DISTRIBUTOR, PHARMACY }   // ADMIN = manufacturer
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(unique = true, nullable = false) private String username;
    @JsonIgnore @Column(nullable = false) private String password;  // BCrypt hash
    private String name;
    @Enumerated(EnumType.STRING) private Role role;
}
