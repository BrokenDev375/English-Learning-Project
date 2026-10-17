package vn.edu.ptit.english.dto;

import java.time.Instant;

public record ClassroomView(long id, long teacherId, String teacherName, String name, String description,
                            String joinCode, long memberCount, Instant createdAt, Instant joinedAt) {}
