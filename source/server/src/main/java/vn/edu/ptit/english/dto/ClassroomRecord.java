package vn.edu.ptit.english.dto;

import java.time.Instant;
import vn.edu.ptit.english.enums.Role;

public record ClassroomRecord(long id, long teacherId, String teacherName, String name, String description,
                              String joinCode, String createRequestId, long memberCount, Instant createdAt, Instant joinedAt) {
    public ClassroomView view(Role role) {
        return new ClassroomView(id, teacherId, teacherName, name, description,
            role == Role.TEACHER ? joinCode : null, memberCount, createdAt, joinedAt);
    }
}
