package vn.edu.ptit.english.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name = "request_receipts")
public class RequestReceiptEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "actor_id", nullable = false) private long actorId;
    @Column(name = "request_id", nullable = false, columnDefinition = "char(36)") private String requestId;
    @Column(nullable = false, length = 200) private String operation;
    @Column(name = "payload_hash", nullable = false, columnDefinition = "char(64)") private String payloadHash;
    @Column(name = "response_status", nullable = false) private short responseStatus;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "response_json", nullable = false) private JsonNode responseJson;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;
    protected RequestReceiptEntity() {}
    public RequestReceiptEntity(long actorId, String requestId, String operation, String hash, int status, JsonNode body) {
        this.actorId = actorId; this.requestId = requestId; this.operation = operation;
        payloadHash = hash; responseStatus = (short) status; responseJson = body;
    }
    public String getOperation() { return operation; }
    public String getPayloadHash() { return payloadHash; }
    public int getResponseStatus() { return responseStatus; }
    public JsonNode getResponseJson() { return responseJson; }
}
