package vn.edu.ptit.english.service;
import vn.edu.ptit.english.security.UserPrincipal;
public interface LearnerCatalogService {
    Object list(String kind,String q,Long topic,int page,int size,UserPrincipal actor);
    Object unit(long id,UserPrincipal actor);
    Object lesson(long id,UserPrincipal actor);
}
