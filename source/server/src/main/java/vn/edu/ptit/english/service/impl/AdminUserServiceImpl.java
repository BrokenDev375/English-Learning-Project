package vn.edu.ptit.english.service.impl;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.english.dto.*;
import vn.edu.ptit.english.entity.UserEntity;
import vn.edu.ptit.english.enums.*;
import vn.edu.ptit.english.exception.ApiException;
import vn.edu.ptit.english.repository.AdminUserRepository;
import vn.edu.ptit.english.security.*;
import vn.edu.ptit.english.service.AdminUserService;

@Service
@Transactional(readOnly = true)
public class AdminUserServiceImpl implements AdminUserService {
    private final AdminUserRepository users;
    private final AccountGuard guard;
    private final ApplicationEventPublisher events;
    public AdminUserServiceImpl(AdminUserRepository users, AccountGuard guard, ApplicationEventPublisher events) {
        this.users = users;
        this.guard = guard;
        this.events = events;
    }
    private void requireAdmin(UserPrincipal actor) {
        guard.requireCurrent(actor);
        if (actor.role() != Role.ADMIN) throw forbidden();
    }
    @Override
    public UserPage search(String query, Role role, AccountStatus status, int page, int size, UserPrincipal actor) {
        requireAdmin(actor);
        if (page < 0 || size < 1 || size > 50 || query.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SEARCH",
                "Trang phải từ 0, kích thước từ 1–50 và từ khóa tối đa 100 ký tự.");
        }
        var result = users.search(query.strip(), role, status, page, size);
        return new UserPage(result.getContent().stream().map(u -> u.record().view()).toList(),
            result.getTotalElements(), page, size, result.getTotalPages());
    }
    private UserEntity target(long id, UserPrincipal actor) {
        // Serialize management changes, including concurrent changes to the last active admin.
        users.lockControl();
        var current = guard.lockCurrent(actor);
        if (current.role() != Role.ADMIN) throw forbidden();
        if (id == actor.id()) throw new ApiException(HttpStatus.CONFLICT, "SELF_MANAGEMENT",
            "Bạn không thể tự khóa, xóa hoặc đổi quyền tài khoản đang sử dụng.");
        return users.lockById(id).orElseThrow(() ->
            new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Không tìm thấy tài khoản."));
    }
    private void requireRemainingAdmin(UserEntity target) {
        if (target.getRole() == Role.ADMIN && target.getStatus() == AccountStatus.ACTIVE
            && users.countActiveAdmins() <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "LAST_ADMIN", "Cần giữ ít nhất một quản trị viên hoạt động.");
        }
    }
    private UserView saved(UserEntity target, long previousVersion) {
        var result = users.save(target).record().view();
        if (target.getSecurityVersion() != previousVersion) events.publishEvent(new AccountAccessChanged(target.getId()));
        return result;
    }
    @Override @Transactional
    public UserView changeRole(long id, Role role, UserPrincipal actor) {
        var target = target(id, actor);
        if (target.getStatus() == AccountStatus.DELETED) throw deleted();
        long version = target.getSecurityVersion();
        if (target.getRole() != role) { requireRemainingAdmin(target); target.changeRole(role); }
        return saved(target, version);
    }
    @Override @Transactional
    public UserView changeStatus(long id, AccountStatus status, UserPrincipal actor) {
        if (status == AccountStatus.DELETED) throw new ApiException(HttpStatus.BAD_REQUEST,
            "USE_DELETE", "Dùng thao tác xóa tài khoản để xóa logic.");
        var target = target(id, actor);
        if (target.getStatus() == AccountStatus.DELETED) throw deleted();
        long version = target.getSecurityVersion();
        if (target.getStatus() != status) {
            if (status != AccountStatus.ACTIVE) requireRemainingAdmin(target);
            target.changeStatus(status);
        }
        return saved(target, version);
    }
    @Override @Transactional
    public void delete(long id, UserPrincipal actor) {
        var target = target(id, actor);
        if (target.getStatus() == AccountStatus.DELETED) return;
        requireRemainingAdmin(target);
        long version = target.getSecurityVersion();
        target.changeStatus(AccountStatus.DELETED);
        saved(target, version);
    }
    private static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Chỉ quản trị viên được quản lý tài khoản.");
    }
    private static ApiException deleted() {
        return new ApiException(HttpStatus.CONFLICT, "ACCOUNT_DELETED", "Tài khoản đã xóa không thể mở lại hoặc đổi quyền.");
    }
}
