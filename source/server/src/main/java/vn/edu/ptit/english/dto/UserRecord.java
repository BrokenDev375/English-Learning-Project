package vn.edu.ptit.english.dto;

import java.time.Instant;
import vn.edu.ptit.english.enums.AccountStatus;
import vn.edu.ptit.english.enums.Role;

public record UserRecord(long id, String email, String passwordHash, String fullName,
                         Role role, AccountStatus status, long securityVersion, Instant createdAt) {
    public UserView view() { return new UserView(id, email, fullName, role, status, createdAt); }
}
