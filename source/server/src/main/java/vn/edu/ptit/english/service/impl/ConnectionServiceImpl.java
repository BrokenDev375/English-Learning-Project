package vn.edu.ptit.english.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.english.dto.ConnectionViews.Presence;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.repository.AttemptRepository;
import vn.edu.ptit.english.security.*;
import vn.edu.ptit.english.service.ConnectionService;

@Service @Transactional(readOnly = true)
public class ConnectionServiceImpl implements ConnectionService {
    private final AccountGuard accounts;
    private final LiveConnections connections;
    private final AttemptRepository attempts;
    public ConnectionServiceImpl(AccountGuard accounts, LiveConnections connections, AttemptRepository attempts) {
        this.accounts = accounts; this.connections = connections; this.attempts = attempts;
    }
    public Presence presence(UserPrincipal actor) {
        accounts.requireCurrent(actor);
        return connections.presence(actor.id());
    }
    public boolean canSubscribe(long attemptId, UserPrincipal actor) {
        accounts.requireCurrent(actor);
        return actor.role() == Role.STUDENT && attemptId > 0 && attempts.attempt(attemptId, actor.id(), false).isPresent();
    }
}
