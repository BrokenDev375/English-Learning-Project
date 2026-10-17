package vn.edu.ptit.english.service;

import vn.edu.ptit.english.security.UserPrincipal;

/** M03 can supply another provider without making M02 depend on attempt tables. */
public interface AssetReadGrant {
    boolean canRead(UserPrincipal actor,long assetId);
}
