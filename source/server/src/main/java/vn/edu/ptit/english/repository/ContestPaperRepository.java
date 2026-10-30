package vn.edu.ptit.english.repository;

import java.util.*;
import vn.edu.ptit.english.entity.*;
public interface ContestPaperRepository {
    Optional<ContestPermissionEntity> permission(long user,boolean lock);
    List<Object[]> teachers(String query,boolean proctorOnly,int offset,int size);
    long teacherCount(String query,boolean proctorOnly);
    Optional<ContestPaperEntity> paper(long id,boolean lock);
    List<ContestPaperEntity> papers(Long owner,String state,String query,int offset,int size);
    long paperCount(Long owner,String state,String query);
    long usage(long paper);
    ContestPermissionEntity save(ContestPermissionEntity value);
    ContestPaperEntity save(ContestPaperEntity value);
}
