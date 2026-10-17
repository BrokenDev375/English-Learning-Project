package vn.edu.ptit.english.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import vn.edu.ptit.english.dto.UserRecord;
import vn.edu.ptit.english.enums.AccountStatus;
import vn.edu.ptit.english.enums.Role;

public record UserPrincipal(long id, String email, String passwordHash, Role role,
                            AccountStatus status, long securityVersion) implements UserDetails {
    public static UserPrincipal from(UserRecord user) {
        return new UserPrincipal(user.id(), user.email(), user.passwordHash(), user.role(),
            user.status(), user.securityVersion());
    }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }
    @Override public boolean isEnabled() { return status != AccountStatus.DELETED; }
    @Override public boolean isAccountNonLocked() { return status == AccountStatus.ACTIVE; }
    public boolean matches(UserRecord user) {
        return user.status() == AccountStatus.ACTIVE && user.securityVersion() == securityVersion
            && user.role() == role;
    }
}
