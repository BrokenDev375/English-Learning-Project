package vn.edu.ptit.english.service;

import java.util.Map;
import vn.edu.ptit.english.dto.CatalogViews.CatalogPage;
import vn.edu.ptit.english.dto.MaterialRequests.*;
import vn.edu.ptit.english.dto.MaterialViews.*;
import vn.edu.ptit.english.security.UserPrincipal;
import vn.edu.ptit.english.service.RequestReceiptService.Result;

public interface MaterialService {
    CatalogPage<Summary> list(String scope,String q,String status,String kind,int page,int size,UserPrincipal actor);
    Root get(long id,UserPrincipal actor);
    Map<String,Object> version(long id,long versionId,UserPrincipal actor);
    Result create(Input input,UserPrincipal actor);
    Result start(long id,Command command,UserPrincipal actor);
    Result update(long id,Input input,UserPrincipal actor);
    Result discard(long id,Command command,UserPrincipal actor);
    Preview preview(long id,UserPrincipal actor);
    Result publish(long id,Command command,UserPrincipal actor);
    Result retire(long id,Command command,UserPrincipal actor);
}
