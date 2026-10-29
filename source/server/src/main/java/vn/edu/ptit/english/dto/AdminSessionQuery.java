package vn.edu.ptit.english.dto;

import java.util.*;
import org.springframework.util.MultiValueMap;
import org.springframework.http.HttpStatus;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.exception.ApiException;

public record AdminSessionQuery(String q, Role role, String activity, int page, int size) {
    public static final Set<String> ACTIVITIES=Set.of("HOME","LEARN","REVIEW","RESULT","CATALOG","ACCOUNT","PROFILE","REWARDS","LEADERBOARD","AUTHORING","ADMIN_USERS","STATISTICS","OTHER");
    public static AdminSessionQuery parse(MultiValueMap<String,String> params) {
        try {
            if(params.entrySet().stream().anyMatch(e->!Set.of("q","role","activity","page","size").contains(e.getKey()) || e.getValue().size()!=1))throw new IllegalArgumentException();
            String q=Optional.ofNullable(params.getFirst("q")).orElse("").strip();
            String role=params.getFirst("role"),activity=params.getFirst("activity");
            int page=number(params.getFirst("page"),0),size=number(params.getFirst("size"),20);
            if(q.length()>100 || page>1000000 || size<1 || size>50 || activity!=null && !ACTIVITIES.contains(activity))throw new IllegalArgumentException();
            return new AdminSessionQuery(q,role==null?null:Role.valueOf(role),activity,page,size);
        } catch(IllegalArgumentException ex) {throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_SESSION_QUERY","Bộ lọc giám sát không hợp lệ.");}
    }
    private static int number(String v,int fallback){if(v==null)return fallback;if(!v.matches("[0-9]{1,7}"))throw new IllegalArgumentException();return Integer.parseInt(v);}
}
