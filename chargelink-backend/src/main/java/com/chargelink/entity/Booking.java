package com.chargelink.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "bookings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charger_id", nullable = false)
    private Charger charger;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @Column(name = "slot_start", nullable = false)
    private ZonedDateTime slotStart;

    @Column(name = "slot_end", nullable = false)
    private ZonedDateTime slotEnd;

    @Column(name = "status")
    @Builder.Default
    private String status = "pending";

    @Column(name = "estimated_kwh", precision = 5, scale = 1)
    private BigDecimal estimatedKwh;

    @Column(name = "notes")
    private String notes;

    @Column(name = "cancelled_at")
    private ZonedDateTime cancelledAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "buffer_expires_at")
    private ZonedDateTime bufferExpiresAt;

    @Version
    @Column(name = "version")
    @Builder.Default
    private Long version = 0L;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
}
