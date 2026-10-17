package vn.edu.ptit.english.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class ClassroomRequests {
    private ClassroomRequests() {}
    public record Create(
        @NotBlank(message = "Nhập tên lớp.") @Size(max = 100, message = "Tên lớp tối đa 100 ký tự.") String name,
        @Size(max = 1000, message = "Mô tả tối đa 1000 ký tự.") String description,
        @NotNull(message = "Thiếu mã yêu cầu tạo lớp.") UUID requestId) {}
    public record Edit(
        @NotBlank(message = "Nhập tên lớp.") @Size(max = 100, message = "Tên lớp tối đa 100 ký tự.") String name,
        @Size(max = 1000, message = "Mô tả tối đa 1000 ký tự.") String description) {}
    public record Join(@NotBlank(message = "Nhập mã lớp.") @Size(min = 8, max = 8, message = "Mã lớp gồm 8 ký tự.") String code) {}
}
