package vn.edu.ptit.english.repository;

import java.util.*;
import vn.edu.ptit.english.dto.AdminSessionViews.User;

public interface AdminSessionRepository {
    record AttemptContext(long attemptId,long userId,String mode,String state,String lessonTitle,Integer roundNumber) {}
    List<User> users(Set<Long> ids);
    List<AttemptContext> attempts(Set<Long> ids);
}
