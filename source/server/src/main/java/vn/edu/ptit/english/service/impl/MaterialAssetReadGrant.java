package vn.edu.ptit.english.service.impl;

import org.springframework.stereotype.Component;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.repository.MaterialRepository;
import vn.edu.ptit.english.security.UserPrincipal;
import vn.edu.ptit.english.service.AssetReadGrant;

@Component
public class MaterialAssetReadGrant implements AssetReadGrant {
    private final MaterialRepository materials;private final ContentGovernance governance;
    public MaterialAssetReadGrant(MaterialRepository materials,ContentGovernance governance){this.materials=materials;this.governance=governance;}
    public boolean canRead(UserPrincipal actor,long assetId){return actor.role()==Role.TEACHER&&(materials.assetReadable(assetId,actor.id())||governance.knowsAsset(assetId,actor));}
}
