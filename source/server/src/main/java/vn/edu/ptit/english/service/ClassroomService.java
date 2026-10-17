package vn.edu.ptit.english.service;

import java.util.List;
import vn.edu.ptit.english.dto.ClassroomRequests;
import vn.edu.ptit.english.dto.ClassroomView;
import vn.edu.ptit.english.dto.MemberView;
import vn.edu.ptit.english.security.UserPrincipal;

/** Contract injected by callers; implementation is in service.impl. */
public interface ClassroomService {
    record Created(ClassroomView classroom, boolean created) {}
    record Joined(ClassroomView classroom, boolean alreadyJoined) {}

    List<ClassroomView> list(UserPrincipal user);
    ClassroomView get(long id, UserPrincipal user);
    Created create(ClassroomRequests.Create input, UserPrincipal user);
    ClassroomView edit(long id, ClassroomRequests.Edit input, UserPrincipal user);
    Joined join(ClassroomRequests.Join input, UserPrincipal user);
    List<MemberView> members(long id, UserPrincipal user);
    void remove(long id, long studentId, UserPrincipal user);
    void leave(long id, UserPrincipal user);
}
