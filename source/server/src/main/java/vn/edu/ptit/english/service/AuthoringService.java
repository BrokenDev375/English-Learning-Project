package vn.edu.ptit.english.service;
import java.util.Map;
import vn.edu.ptit.english.dto.AuthoringData.Preview;
import vn.edu.ptit.english.dto.AuthoringRequests.*;
import vn.edu.ptit.english.dto.CatalogViews.CatalogPage;
import vn.edu.ptit.english.dto.MaterialRequests.Command;
import vn.edu.ptit.english.repository.ContentRepository.Filter;
import vn.edu.ptit.english.security.UserPrincipal;
import vn.edu.ptit.english.service.RequestReceiptService.Result;
public interface AuthoringService {
    CatalogPage<Map<String,Object>> list(String kind,Filter filter,UserPrincipal actor);
    Map<String,Object> get(String kind,long id,UserPrincipal actor);
    Map<String,Object> version(String kind,long id,long version,UserPrincipal actor);
    Result create(QuestionInput input,UserPrincipal actor);Result create(LessonInput input,UserPrincipal actor);
    Result update(long id,QuestionInput input,UserPrincipal actor);Result update(long id,LessonInput input,UserPrincipal actor);
    Result start(String kind,long id,Command input,UserPrincipal actor);
    Result discard(String kind,long id,Command input,UserPrincipal actor);
    Preview preview(String kind,long id,UserPrincipal actor);
    Result publish(String kind,long id,Command input,UserPrincipal actor);
    Result retire(String kind,long id,Command input,UserPrincipal actor);
    Result copy(long id,Copy input,UserPrincipal actor);
    Object unit(long id,UserPrincipal actor);Result order(long id,Order input,UserPrincipal actor);
}
