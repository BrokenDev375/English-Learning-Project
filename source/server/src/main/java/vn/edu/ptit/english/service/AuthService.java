package vn.edu.ptit.english.service;

import vn.edu.ptit.english.dto.AuthRequests;
import vn.edu.ptit.english.dto.UserView;

/** Contract injected by callers; implementation is in service.impl. */
public interface AuthService {
    UserView register(AuthRequests.Register input);
    UserView current(long id);
}
