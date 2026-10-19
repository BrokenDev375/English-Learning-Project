package vn.edu.ptit.english.service.impl;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import vn.edu.ptit.english.dto.*;
import vn.edu.ptit.english.dto.CatalogViews.CatalogPage;
import vn.edu.ptit.english.dto.MaterialRequests.*;
import vn.edu.ptit.english.dto.MaterialViews.*;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.exception.ApiException;
import vn.edu.ptit.english.repository.MaterialRepository;
import vn.edu.ptit.english.security.*;
import vn.edu.ptit.english.service.*;
import vn.edu.ptit.english.service.RequestReceiptService.Result;

@Service @Transactional(readOnly=true)
public class MaterialServiceImpl implements MaterialService {
    private final MaterialRepository materials;
    private final AssetService assets;
    private final RequestReceiptService receipts;
    private final AccountGuard accounts;
    public MaterialServiceImpl(MaterialRepository materials,AssetService assets,RequestReceiptService receipts,AccountGuard accounts){this.materials=materials;this.assets=assets;this.receipts=receipts;this.accounts=accounts;}
    private boolean own(MaterialEntity root,UserPrincipal actor){return root.getOwnerId()==actor.id()||actor.role()==Role.ADMIN;}
    private void readAuthor(UserPrincipal actor){accounts.requireCurrent(actor);AssetServiceImpl.author(actor);}
    private MaterialEntity root(long id,boolean lock){id(id);return materials.find(id,lock).orElseThrow(MaterialServiceImpl::missing);}
    private void editable(MaterialEntity root,UserPrincipal actor){if(!own(root,actor))throw missing();}
    private void readable(MaterialEntity root,UserPrincipal actor){if(!own(root,actor)&&(root.getPublished()==null||root.getRetiredAt()!=null))throw missing();}
    private void revision(long actual,long expected){if(actual!=expected)throw conflict("REVISION_CONFLICT","Tài liệu đã thay đổi ở nơi khác. Hãy tải lại trước khi sửa.");}
    private static void id(long id){if(id<1||id>9007199254740991L)throw MaterialRequests.invalid("id","Mã tài liệu không hợp lệ.");}
    private MaterialVersionEntity draft(MaterialEntity root,Long id){
        var draft=root.getDraft();if(draft==null||(id!=null&&!draft.getId().equals(id)))throw conflict("DRAFT_MISMATCH","Bản nháp đã thay đổi hoặc không còn tồn tại.");return draft;
    }
    private Root view(MaterialEntity root,UserPrincipal actor){
        var draft=own(root,actor)?root.getDraft():null;var published=root.getPublished();
        return new Root(root.getId(),root.getOwnerId(),root.getRevision(),root.status(),draft==null?null:draft.getId(),published==null?null:published.getId(),draft==null?null:draft.view(),published==null?null:published.view());
    }
    private void validateFile(MaterialData data,UserPrincipal actor){
        if(data.assetId()==null)return;
        var asset=assets.forMaterial(data.assetId(),data.kind(),actor);
        if(data.kind().equals("AUDIO")){
            BigDecimal duration=BigDecimal.valueOf(asset.duration());
            if(data.startSeconds().compareTo(duration)>=0)throw MaterialRequests.invalid("startSeconds","Điểm bắt đầu phải nằm trong audio.");
            if(data.endSeconds()!=null&&data.endSeconds().compareTo(duration)>0)throw MaterialRequests.invalid("endSeconds","Điểm kết thúc vượt thời lượng audio.");
        }
    }
    List<Issue> errors(MaterialData data,UserPrincipal actor){
        validateFile(data,actor);var issues=new ArrayList<Issue>();
        if(data.title().isBlank())issues.add(new Issue("title","REQUIRED","Cần nhập tên tài liệu."));
        if(data.kind().equals("READING")&&data.readingText().isBlank())issues.add(new Issue("readingText","REQUIRED","Cần nhập nội dung đoạn đọc."));
        if(data.kind().equals("AUDIO")&&data.transcript().isBlank())issues.add(new Issue("transcript","REQUIRED","Cần nhập bản chép lời khớp với audio."));
        if(data.assetId()!=null&&!data.kind().equals("AUDIO")&&(data.altText()==null||data.altText().isBlank()))issues.add(new Issue("altText","REQUIRED","Cần mô tả ảnh để người học có thể tiếp cận nội dung."));
        return issues;
    }
    private String token(MaterialEntity root,MaterialVersionEntity draft){
        String text="v1|MATERIAL|"+root.getId()+"|"+draft.getId()+"|"+draft.getRevision();
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception ex){throw new IllegalStateException(ex);}
    }
    @Override public CatalogPage<Summary> list(String scope,String q,String status,String kind,int page,int size,UserPrincipal actor){
        readAuthor(actor);
        if(!Set.of("OWN","ALL","PUBLISHED").contains(scope)||q.length()>100||page<0||size<1||size>50
            ||(status!=null&&!Set.of("DRAFT","PUBLISHED","RETIRED").contains(status))||(kind!=null&&!Set.of("READING","AUDIO","IMAGE").contains(kind)))throw MaterialRequests.invalid("query","Bộ lọc hoặc phân trang không hợp lệ.");
        if(scope.equals("ALL")&&actor.role()!=Role.ADMIN)throw new ApiException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Chỉ quản trị viên được xem mọi bản nháp.");
        return CatalogPage.from(materials.search(actor.id(),actor.role()==Role.ADMIN,scope,q.strip(),status,kind,page,size).map(root -> {
            var selected=!scope.equals("PUBLISHED")&&own(root,actor)&&root.getDraft()!=null?root.getDraft():root.getPublished();
            Long draftId=own(root,actor)&&root.getDraft()!=null?root.getDraft().getId():null;
            return new Summary(root.getId(),root.getOwnerId(),root.getRevision(),root.status(),draftId,root.getPublished()==null?null:root.getPublished().getId(),selected.data().title(),selected.data().kind());
        }));
    }
    @Override public Root get(long id,UserPrincipal actor){readAuthor(actor);var root=root(id,false);readable(root,actor);return view(root,actor);}
    @Override public Map<String,Object> version(long id,long versionId,UserPrincipal actor){
        readAuthor(actor);var root=root(id,false);readable(root,actor);id(versionId);
        var version=materials.version(versionId,id).orElseThrow(MaterialServiceImpl::missing);
        if(!own(root,actor)&&!version.getState().equals("PUBLISHED"))throw missing();return version.view();
    }
    @Override @Transactional public Result create(Input input,UserPrincipal actor){
        AssetServiceImpl.author(actor);
        return receipts.execute(actor,input.requestId(),"POST /api/author/materials",input.payload(),201,() -> {
            validateFile(input.data(),actor);var root=materials.save(new MaterialEntity(actor.id()));
            var draft=materials.save(new MaterialVersionEntity(root.getId(),1,input.data()));root.initialDraft(draft);
            return view(materials.save(root),actor);
        });
    }
    @Override @Transactional public Result start(long id,Command command,UserPrincipal actor){
        AssetServiceImpl.author(actor);
        return receipts.execute(actor,command.requestId(),"POST /api/author/materials/"+id+"/draft",command,200,() -> {
            var root=root(id,true);editable(root,actor);revision(root.getRevision(),command.baseRevision());
            if(root.getDraft()==null){
                var data=root.getPublished()==null?new MaterialData("READING","","",null,null,null,null,null,null,new MaterialData.Source("Tự biên soạn",null,null)):root.getPublished().data();
                var draft=materials.save(new MaterialVersionEntity(id,root.getLastVersionNo()+1,data));root.startDraft(draft);materials.save(root);
            }
            return view(root,actor);
        });
    }
    @Override @Transactional public Result update(long id,Input input,UserPrincipal actor){
        AssetServiceImpl.author(actor);
        return receipts.execute(actor,input.requestId(),"PUT /api/author/materials/"+id+"/draft",input.payload(),200,() -> {
            var root=root(id,true);editable(root,actor);var draft=draft(root,input.draftVersionId());revision(draft.getRevision(),input.baseRevision());validateFile(input.data(),actor);
            if(draft.update(input.data())){materials.save(draft);root.changed();materials.save(root);}return view(root,actor);
        });
    }
    @Override @Transactional public Result discard(long id,Command command,UserPrincipal actor){
        AssetServiceImpl.author(actor);
        return receipts.execute(actor,command.requestId(),"DELETE /api/author/materials/"+id+"/draft",command,204,() -> {
            var root=root(id,true);editable(root,actor);var draft=draft(root,command.draftVersionId());revision(draft.getRevision(),command.baseRevision());
            if(materials.versionReferenced(draft.getId()))throw conflict("CONTENT_IN_USE","Bản nháp đang được câu hỏi tham chiếu.");
            root.discard();materials.save(root);
            try{materials.delete(draft);}catch(DataIntegrityViolationException ex){throw conflict("CONTENT_IN_USE","Bản nháp đang được tham chiếu.");}return null;
        });
    }
    @Override public Preview preview(long id,UserPrincipal actor){
        readAuthor(actor);var root=root(id,false);editable(root,actor);var draft=draft(root,null);
        var warnings=new ArrayList<String>();if(draft.data().source().license()==null||draft.data().source().license().isBlank())warnings.add("Chưa ghi điều kiện sử dụng hoặc giấy phép của nguồn.");
        return new Preview(id,draft.getId(),draft.getRevision(),token(root,draft),errors(draft.data(),actor),warnings,draft.view());
    }
    @Override @Transactional public Result publish(long id,Command command,UserPrincipal actor){
        AssetServiceImpl.author(actor);
        return receipts.execute(actor,command.requestId(),"POST /api/author/materials/"+id+"/publish",command,200,() -> {
            var root=root(id,true);editable(root,actor);var draft=draft(root,command.draftVersionId());
            if(!token(root,draft).equals(command.previewToken()))throw conflict("PREVIEW_STALE","Tài liệu đã đổi sau lần xem trước. Hãy xem trước lại.");
            revision(draft.getRevision(),command.baseRevision());
            var errors=errors(draft.data(),actor);if(!errors.isEmpty()){
                var fields=new LinkedHashMap<String,String>();errors.forEach(error -> fields.put(error.path(),error.message()));
                throw new ApiException(HttpStatus.BAD_REQUEST,"PUBLISH_VALIDATION_FAILED","Tài liệu chưa đủ điều kiện xuất bản.",fields);
            }
            root.publish();materials.save(draft);materials.save(root);return view(root,actor);
        });
    }
    @Override @Transactional(isolation=Isolation.READ_COMMITTED) public Result retire(long id,Command command,UserPrincipal actor){
        AssetServiceImpl.author(actor);
        return receipts.execute(actor,command.requestId(),"POST /api/author/materials/"+id+"/retire",command,200,() -> {
            var root=root(id,true);editable(root,actor);revision(root.getRevision(),command.baseRevision());
            if(materials.publishedUsage(id))throw conflict("CONTENT_IN_USE","Tài liệu đang được nội dung đã xuất bản sử dụng.");
            root.retire();return view(materials.save(root),actor);
        });
    }
    private static ApiException missing(){return new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","Không tìm thấy tài liệu được phép truy cập.");}
    private static ApiException conflict(String code,String message){return new ApiException(HttpStatus.CONFLICT,code,message);}
}
