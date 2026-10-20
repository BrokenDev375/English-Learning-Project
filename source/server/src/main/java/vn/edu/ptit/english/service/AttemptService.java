package vn.edu.ptit.english.service;
import vn.edu.ptit.english.dto.AttemptRequests.Create;
import vn.edu.ptit.english.dto.AttemptViews.Summary;
import vn.edu.ptit.english.dto.CatalogViews.CatalogPage;
import vn.edu.ptit.english.security.UserPrincipal;
public interface AttemptService {
    RequestReceiptService.Result create(Create input,UserPrincipal actor);
    CatalogPage<Summary> list(String mode,String state,int page,int size,UserPrincipal actor);
}
