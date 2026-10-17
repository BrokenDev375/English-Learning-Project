package vn.edu.ptit.english.service.impl;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.ptit.english.entity.AssetEntity;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.exception.ApiException;
import vn.edu.ptit.english.repository.AssetRepository;
import vn.edu.ptit.english.security.*;
import vn.edu.ptit.english.service.*;
import vn.edu.ptit.english.service.RequestReceiptService.Result;

@Service @Transactional(readOnly=true)
public class AssetServiceImpl implements AssetService {
    private final AssetRepository assets;
    private final AssetStorage storage;
    private final MediaInspector media;
    private final RequestReceiptService receipts;
    private final AccountGuard accounts;
    private final List<AssetReadGrant> grants;
    public AssetServiceImpl(AssetRepository assets,AssetStorage storage,MediaInspector media,RequestReceiptService receipts,
        AccountGuard accounts,List<AssetReadGrant> grants){this.assets=assets;this.storage=storage;this.media=media;this.receipts=receipts;this.accounts=accounts;this.grants=grants;}
    @Override @Transactional public Result upload(MultipartFile file,String requestId,UserPrincipal actor) {
        author(actor);validateRequestId(requestId);
        String name=file.getOriginalFilename();
        if(name==null||name.isBlank()||name.length()>200||name.equals(".")||name.equals("..")||name.indexOf('/')>=0||name.indexOf('\\')>=0||name.chars().anyMatch(Character::isISOControl))
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_FILE_NAME","Tên file không hợp lệ. Chỉ gửi tên file, không gửi đường dẫn.");
        name=name.strip();
        Path temporary;
        try(var source=file.getInputStream()){temporary=storage.stage(source);}
        catch(IOException ex){throw AssetStorage.unavailable();}
        try {
            var detected=media.inspect(temporary);String sha;
            try {sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(temporary)));}
            catch(Exception ex){throw AssetStorage.unavailable();}
            long bytes;
            try{bytes=Files.size(temporary);}catch(IOException ex){throw AssetStorage.unavailable();}
            String originalName=name;
            return receipts.execute(actor,requestId,"POST /api/assets",Map.of("name",name,"mimeType",detected.mimeType(),"fileSha256",sha),201,() -> {
                String key=storage.commit(temporary);
                return assets.save(new AssetEntity(actor.id(),key,originalName,detected.mimeType(),bytes,sha)).view();
            });
        }finally{storage.discard(temporary);}
    }
    @Override public FileRead read(long id,UserPrincipal actor) {
        accounts.requireCurrent(actor);
        var asset=allowed(id,actor);
        return new FileRead(asset.view(),storage.existing(asset.getStorageKey()));
    }
    @Override public MaterialAsset forMaterial(long id,String kind,UserPrincipal actor) {
        var file=read(id,actor);var asset=assets.find(id).orElseThrow(AssetServiceImpl::missing);
        if(!asset.getState().equals("READY"))throw new ApiException(HttpStatus.CONFLICT,"ASSET_UNAVAILABLE","File không còn nhận tham chiếu mới.");
        boolean image=asset.getMimeType().startsWith("image/");
        if(kind.equals("AUDIO")==image)throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","Loại file không phù hợp với tài liệu.");
        var inspected=media.inspect(file.path());
        if(!asset.getMimeType().equals(inspected.mimeType()))throw AssetStorage.unavailable();
        return new MaterialAsset(asset.view(),inspected.duration());
    }
    private AssetEntity allowed(long id,UserPrincipal actor) {
        if(id<1||id>9007199254740991L)throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","Mã file không hợp lệ.");
        var asset=assets.find(id).orElseThrow(AssetServiceImpl::missing);
        if(actor.role()==Role.ADMIN||(actor.role()==Role.TEACHER&&(asset.getOwnerId()==actor.id()||grants.stream().anyMatch(grant -> grant.canRead(actor,id)))))return asset;
        if(actor.role()==Role.STUDENT&&grants.stream().anyMatch(grant -> grant.canRead(actor,id)))return asset;
        throw missing();
    }
    public static void author(UserPrincipal actor){if(actor.role()!=Role.TEACHER&&actor.role()!=Role.ADMIN)throw new ApiException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Chỉ người biên soạn và quản trị viên được tải file.");}
    public static void validateRequestId(String value){if(value==null||!value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","Mã yêu cầu không hợp lệ.");}
    private static ApiException missing(){return new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","Không tìm thấy file được phép truy cập.");}
}
