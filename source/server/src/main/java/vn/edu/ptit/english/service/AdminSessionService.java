package vn.edu.ptit.english.service;
import vn.edu.ptit.english.dto.*;
import vn.edu.ptit.english.security.UserPrincipal;
public interface AdminSessionService {
    AdminSessionViews.Page list(AdminSessionQuery query,UserPrincipal actor);
    AdminSessionViews.Detail detail(String sessionRef,UserPrincipal actor);
}
