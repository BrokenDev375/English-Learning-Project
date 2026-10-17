package vn.edu.ptit.english.dto;

import jakarta.validation.constraints.NotNull;
import vn.edu.ptit.english.enums.AccountStatus;
import vn.edu.ptit.english.enums.Role;

public final class AdminRequests {
    private AdminRequests() {}
    public record ChangeRole(@NotNull Role role) {}
    public record ChangeStatus(@NotNull AccountStatus status) {}
}
