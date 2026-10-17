package vn.edu.ptit.english.service.impl;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.english.dto.ClassroomRecord;
import vn.edu.ptit.english.dto.ClassroomRequests;
import vn.edu.ptit.english.dto.ClassroomView;
import vn.edu.ptit.english.dto.MemberView;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.exception.ApiException;
import vn.edu.ptit.english.repository.ClassroomRepository;
import vn.edu.ptit.english.security.UserPrincipal;
import vn.edu.ptit.english.service.ClassroomService;

@Service
@Transactional(readOnly=true)
public class ClassroomServiceImpl implements ClassroomService {
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();
    private final ClassroomRepository rooms;
    private final vn.edu.ptit.english.security.AccountGuard accounts;
    public ClassroomServiceImpl(ClassroomRepository rooms, vn.edu.ptit.english.security.AccountGuard accounts) { this.rooms = rooms; this.accounts = accounts; }
    @Override
    public List<ClassroomView> list(UserPrincipal user) { return rooms.list(user.id(), user.role()).stream().map(c -> c.view(user.role())).toList(); }
    @Override
    public ClassroomView get(long id, UserPrincipal user) { return accessible(id, user).view(user.role()); }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Override
    public Created create(ClassroomRequests.Create input, UserPrincipal user) {
        accounts.lockCurrent(user);
        requireRole(user, Role.TEACHER);
        String name = cleanName(input.name());
        String description = cleanDescription(input.description());
        String requestId = input.requestId().toString();
        rooms.lockTeacher(user.id());
        var existing = rooms.findByRequest(user.id(), requestId);
        if (existing.isPresent()) return replay(existing.get(), name, description);
        for (int attempt = 0; attempt < 5; attempt++) {
            String code=newCode();
            if(rooms.codeExists(code))continue;
            long id=rooms.create(user.id(),name,description,code,requestId);
            return new Created(rooms.findById(id,user.id()).orElseThrow().view(Role.TEACHER),true);
        }
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CLASS_CODE_RETRY", "Chưa tạo được mã lớp. Vui lòng thử lại.");
    }
    private Created replay(ClassroomRecord existing, String name, String description) {
        if (!existing.name().equals(name) || !existing.description().equals(description)) {
            throw new ApiException(HttpStatus.CONFLICT, "REQUEST_ID_REUSED", "Mã yêu cầu đã được dùng với nội dung khác. Vui lòng mở lại biểu mẫu tạo lớp.");
        }
        return new Created(existing.view(Role.TEACHER), false);
    }
    @Transactional
    @Override
    public ClassroomView edit(long id, ClassroomRequests.Edit input, UserPrincipal user) {
        accounts.lockCurrent(user);
        owned(id, user);
        rooms.lock(id);
        rooms.edit(id, cleanName(input.name()), cleanDescription(input.description()));
        return rooms.findById(id, user.id()).orElseThrow().view(Role.TEACHER);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Override
    public Joined join(ClassroomRequests.Join input, UserPrincipal user) {
        accounts.lockCurrent(user);
        requireRole(user, Role.STUDENT);
        String code = input.code().strip().toUpperCase(Locale.ROOT);
        if (code.length() != 8 || code.chars().anyMatch(c -> CODE_ALPHABET.indexOf(c) < 0)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CLASS_CODE", "Mã lớp gồm 8 chữ hoặc số. Kiểm tra mã giảng viên cung cấp.");
        }
        var classroom = rooms.findByCode(code, user.id()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CLASS_NOT_FOUND", "Không tìm thấy lớp với mã này."));
        rooms.lock(classroom.id()).orElseThrow();
        boolean already = rooms.isMember(classroom.id(), user.id());
        if (!already) {
            rooms.addMember(classroom.id(), user.id());
        }
        return new Joined(rooms.findById(classroom.id(), user.id()).orElseThrow().view(Role.STUDENT), already);
    }
    @Override
    public List<MemberView> members(long id, UserPrincipal user) {
        owned(id, user);
        return rooms.members(id);
    }
    @Transactional
    @Override
    public void remove(long id, long studentId, UserPrincipal user) {
        accounts.lockCurrent(user);
        owned(id, user);
        rooms.lock(id);
        rooms.removeMember(id, studentId);
    }
    @Transactional
    @Override
    public void leave(long id, UserPrincipal user) {
        accounts.lockCurrent(user);
        requireRole(user, Role.STUDENT);
        rooms.lock(id);
        rooms.removeMember(id, user.id());
    }
    private ClassroomRecord owned(long id, UserPrincipal user) {
        requireRole(user, Role.TEACHER);
        var room = find(id, user.id());
        if (room.teacherId() != user.id()) throw forbidden();
        return room;
    }
    private ClassroomRecord accessible(long id, UserPrincipal user) {
        var room = find(id, user.id());
        if (user.role() == Role.TEACHER ? room.teacherId() != user.id() : room.joinedAt() == null) throw forbidden();
        return room;
    }
    private ClassroomRecord find(long id, long userId) {
        return rooms.findById(id, userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CLASS_NOT_FOUND", "Không tìm thấy lớp học."));
    }
    private static void requireRole(UserPrincipal user, Role expected) { if (user.role() != expected) throw forbidden(); }
    private static ApiException forbidden() { return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền truy cập hoặc thay đổi lớp này."); }
    private static String cleanName(String name) {
        String clean = name.strip();
        if (clean.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CLASS_NAME", "Nhập tên lớp.");
        return clean;
    }
    private static String cleanDescription(String description) { return description == null ? "" : description.strip(); }
    private String newCode() {
        var code = new StringBuilder(8);
        for (int i = 0; i < 8; i++) code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        return code.toString();
    }
}

