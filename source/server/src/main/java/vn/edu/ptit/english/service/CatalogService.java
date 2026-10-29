package vn.edu.ptit.english.service;

import vn.edu.ptit.english.dto.CatalogRequests.*;
import vn.edu.ptit.english.dto.CatalogViews.*;
import vn.edu.ptit.english.security.UserPrincipal;
import vn.edu.ptit.english.service.RequestReceiptService.Result;

public interface CatalogService {
    CatalogPage<Topic> topics(String q, String scope, int page, int size, UserPrincipal actor);
    CatalogPage<Objective> objectives(String q, String scope, Long topicId, int page, int size, UserPrincipal actor);
    CatalogPage<java.util.Map<String,Object>> teachers(int page,int size,UserPrincipal actor);
    CatalogPage<Unit> units(String q, String scope, Long topicId, int page, int size, UserPrincipal actor);
    Result createTopic(TopicCreate input, UserPrincipal actor);
    Result updateTopic(long id, TopicUpdate input, UserPrincipal actor);
    Result createObjective(ObjectiveCreate input, UserPrincipal actor);
    Result updateObjective(long id, ObjectiveUpdate input, UserPrincipal actor);
    Result createUnit(UnitCreate input, UserPrincipal actor);
    Result updateUnit(long id, UnitUpdate input, UserPrincipal actor);
}
