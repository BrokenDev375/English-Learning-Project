package vn.edu.ptit.english.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name="gamification_policies")
public class GamificationPolicyEntity {
    @Id @Column(length=32,updatable=false) private String version;
    @Column(name="activated_at",nullable=false,updatable=false,columnDefinition="datetime(6)") private Instant activatedAt;
    @Column(name="effective_at",nullable=false,updatable=false,columnDefinition="datetime(6)") private Instant effectiveAt;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="policy_json",nullable=false,updatable=false) private JsonNode policy;
    protected GamificationPolicyEntity() {}
    public String getVersion() { return version; }
    public Instant getActivatedAt() { return activatedAt; }
    public Instant getEffectiveAt() { return effectiveAt; }
    public JsonNode getPolicy() { return policy.deepCopy(); }
}
