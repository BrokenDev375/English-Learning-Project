package vn.edu.ptit.english.service;

import vn.edu.ptit.english.dto.*;
import vn.edu.ptit.english.enums.*;
import vn.edu.ptit.english.security.UserPrincipal;

public interface AdminUserService {
    UserPage search(String query, Role role, AccountStatus status, int page, int size, UserPrincipal actor);
    UserView changeRole(long id, Role role, UserPrincipal actor);
    UserView changeStatus(long id, AccountStatus status, UserPrincipal actor);
    void delete(long id, UserPrincipal actor);
}
