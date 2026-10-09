package com.example.library.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_login_snapshot", nullable = false, length = 50)
    private String actorLoginSnapshot;

    @Column(name = "actor_role_snapshot", nullable = false, length = 20)
    private String actorRoleSnapshot;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(name = "target_type", nullable = false, length = 50)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(nullable = false, length = 20)
    private String result;

    @Column(nullable = false, length = 500)
    private String summary;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AuditLog() {}

    AuditLog(Long actorUserId, String actorLoginSnapshot, String actorRoleSnapshot,
             String action, String targetType, Long targetId, String result, String summary, Instant occurredAt) {
        this.actorUserId = actorUserId;
        this.actorLoginSnapshot = actorLoginSnapshot;
        this.actorRoleSnapshot = actorRoleSnapshot;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.result = result;
        this.summary = summary;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public Long getActorUserId() { return actorUserId; }
    public String getActorLoginSnapshot() { return actorLoginSnapshot; }
    public String getActorRoleSnapshot() { return actorRoleSnapshot; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public String getResult() { return result; }
    public String getSummary() { return summary; }
    public Instant getOccurredAt() { return occurredAt; }
}
