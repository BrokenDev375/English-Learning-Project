package vn.edu.ptit.english.service.impl;
import java.util.*;
import org.springframework.http.HttpStatus;
import vn.edu.ptit.english.entity.CatalogEntity;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.exception.ApiException;
import vn.edu.ptit.english.security.UserPrincipal;
public final class AuthoringAccess {
    private AuthoringAccess(){}
    public static boolean own(CatalogEntity root,UserPrincipal actor){return root.getOwnerId()==actor.id()||actor.role()==Role.ADMIN;}
    public static void editable(CatalogEntity root,UserPrincipal actor){if(!own(root,actor))throw missing();}
    public static void revision(long actual,long expected){if(actual!=expected)throw conflict("REVISION_CONFLICT","Nội dung đã thay đổi ở nơi khác. Hãy tải lại để đối chiếu trước khi sửa.");}
    public static ApiException missing(){return new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","Không tìm thấy nội dung được phép truy cập.");}
    public static ApiException conflict(String code,String message){return new ApiException(HttpStatus.CONFLICT,code,message);}
    public static Map<String,Object> root(CatalogEntity root,String status,Long draft,Long published){var value=new LinkedHashMap<String,Object>();value.put("id",root.getId());value.put("ownerId",root.getOwnerId());value.put("rootRevision",root.getRevision());value.put("status",status);value.put("draftVersionId",draft);value.put("publishedVersionId",published);return value;}
}
