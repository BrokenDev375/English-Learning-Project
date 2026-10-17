package vn.edu.ptit.english.service.impl;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.edu.ptit.english.entity.RequestReceiptEntity;
import vn.edu.ptit.english.exception.ApiException;
import vn.edu.ptit.english.repository.CatalogRepository;
import vn.edu.ptit.english.security.*;
import vn.edu.ptit.english.service.RequestReceiptService;

@Service
public class RequestReceiptServiceImpl implements RequestReceiptService {
    private final CatalogRepository repository;
    private final AccountGuard guard;
    private final ObjectMapper json;
    public RequestReceiptServiceImpl(CatalogRepository repository, AccountGuard guard, ObjectMapper json) {
        this.repository = repository; this.guard = guard; this.json = json;
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public Result execute(UserPrincipal actor, String requestId, String operation, Object payload, int status, Supplier<?> action) {
        // Serializing mutations by actor also closes concurrent-create and duplicate-request races.
        guard.lockCurrent(actor);
        JsonNode tree = json.valueToTree(payload);
        if (tree instanceof ObjectNode object) object.remove("requestId");
        String hash = hash(tree);
        var previous = repository.receipt(actor.id(), requestId);
        if (previous.isPresent()) {
            var receipt = previous.get();
            if (!operation.equals(receipt.getOperation()) || !hash.equals(receipt.getPayloadHash()))
                throw new ApiException(HttpStatus.CONFLICT,"REQUEST_ID_REUSED","Mã yêu cầu đã được dùng với nội dung khác.");
            return new Result(receipt.getResponseStatus(),receipt.getResponseJson(),true);
        }
        JsonNode body = json.valueToTree(action.get());
        repository.save(new RequestReceiptEntity(actor.id(),requestId,operation,hash,status,body));
        return new Result(status,body,false);
    }
    private String hash(JsonNode node) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical(node).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) { throw new IllegalStateException("Cannot hash request",ex); }
    }
    private String canonical(JsonNode node) throws Exception {
        if (node.isObject()) {
            var keys = new ArrayList<String>(); node.fieldNames().forEachRemaining(keys::add); Collections.sort(keys);
            var pairs = new ArrayList<String>();
            for (String key : keys) pairs.add(json.writeValueAsString(key) + ":" + canonical(node.get(key)));
            return "{" + String.join(",",pairs) + "}";
        }
        if (node.isArray()) {
            var values = new ArrayList<String>(); for (JsonNode value : node) values.add(canonical(value));
            return "[" + String.join(",",values) + "]";
        }
        if (node.isNumber()) return node.decimalValue().stripTrailingZeros().toPlainString();
        return json.writeValueAsString(node);
    }
}
