package vn.edu.ptit.english.dto;

import java.time.Instant;
import vn.edu.ptit.english.enums.AccountStatus;
import vn.edu.ptit.english.enums.Role;

public record UserView(long id, String email, String fullName, Role role,
                       AccountStatus status, Instant createdAt) {}
