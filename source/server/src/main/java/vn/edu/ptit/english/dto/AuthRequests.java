package vn.edu.ptit.english.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthRequests {
    private AuthRequests() {}
    public record Login(
        @NotBlank(message = "Nhập email.") @Email(message = "Email chưa hợp lệ.") @Size(max = 254) String email,
        @NotBlank(message = "Nhập mật khẩu.") @Size(max = 72, message = "Mật khẩu tối đa 72 ký tự.") String password) {}
    public record Register(
        @NotBlank(message = "Nhập họ tên.") @Size(min = 2, max = 100, message = "Họ tên từ 2 đến 100 ký tự.") String fullName,
        @NotBlank(message = "Nhập email.") @Email(message = "Email chưa hợp lệ.") @Size(max = 254) String email,
        @NotBlank(message = "Nhập mật khẩu.") @Size(min = 8, max = 72, message = "Mật khẩu từ 8 đến 72 ký tự.") String password) {}
}
