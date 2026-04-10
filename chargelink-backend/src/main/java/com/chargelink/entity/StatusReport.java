package com.chargelink.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "status_reports")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class StatusReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charger_id", nullable = false)
    private Charger charger;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_by")
    private User reportedBy;

    @Column(name = "reported_status", nullable = false)
    private String reportedStatus;

    @Column(name = "note")
    private String note;

    @Column(name = "photo_url")
    private String photoUrl;

    @Column(name = "confidence")
    @Builder.Default
    private Integer confidence = 5;

    @Column(name = "is_applied")
    @Builder.Default
    private Boolean isApplied = false;

    @CreationTimestamp
    @Column(name = "reported_at", updatable = false)
    private ZonedDateTime reportedAt;
}
