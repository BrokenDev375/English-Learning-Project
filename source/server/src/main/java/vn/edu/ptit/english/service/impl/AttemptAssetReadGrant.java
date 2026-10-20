package vn.edu.ptit.english.service.impl;
import org.springframework.stereotype.Component;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.repository.AttemptRepository;
import vn.edu.ptit.english.security.UserPrincipal;
import vn.edu.ptit.english.service.AssetReadGrant;
@Component
public class AttemptAssetReadGrant implements AssetReadGrant {
    private final AttemptRepository attempts;
    public AttemptAssetReadGrant(AttemptRepository attempts){this.attempts=attempts;}
    public boolean canRead(UserPrincipal actor,long assetId){return actor.role()==Role.STUDENT&&attempts.assetReadable(actor.id(),assetId);}
}
