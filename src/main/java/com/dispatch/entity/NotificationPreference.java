package com.dispatch.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(
    name = "notification_preferences",
    indexes = {
        @Index(name = "idx_notification_preferences_user_id", columnList = "user_id", unique = true)
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Builder.Default
    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = true;

    @Builder.Default
    @Column(name = "sms_enabled", nullable = false)
    private boolean smsEnabled = true;

    @Builder.Default
    @Column(name = "in_app_enabled", nullable = false)
    private boolean inAppEnabled = true;

    @Column(name = "preferred_email", length = 255)
    private String preferredEmail;

    @Column(name = "preferred_phone", length = 20)
    private String preferredPhone;

    @Column(name = "quiet_hours_start")
    private LocalTime quietHoursStart;

    @Column(name = "quiet_hours_end")
    private LocalTime quietHoursEnd;

    public void setQuietHoursStart(LocalTime quietHoursStart) {
        this.quietHoursStart = quietHoursStart != null
                ? quietHoursStart.truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
                : null;
    }

    public void setQuietHoursEnd(LocalTime quietHoursEnd) {
        this.quietHoursEnd = quietHoursEnd != null
                ? quietHoursEnd.truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
                : null;
    }

    @PrePersist
    @PreUpdate
    public void sanitizeQuietHours() {
        if (quietHoursStart != null) {
            quietHoursStart = quietHoursStart.truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        }
        if (quietHoursEnd != null) {
            quietHoursEnd = quietHoursEnd.truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        }
    }

    @Builder.Default
    @Column(name = "timezone", nullable = false, length = 100)
    private String timezone = "UTC";

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
