package vn.edu.ptit.english.service;

import java.nio.file.Path;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.ptit.english.dto.AssetView;
import vn.edu.ptit.english.security.UserPrincipal;
import vn.edu.ptit.english.service.RequestReceiptService.Result;

public interface AssetService {
    record FileRead(AssetView asset,Path path) {}
    record MaterialAsset(AssetView asset,Double duration) {}
    Result upload(MultipartFile file,String requestId,UserPrincipal actor);
    FileRead read(long id,UserPrincipal actor);
    MaterialAsset forMaterial(long id,String kind,UserPrincipal actor);
}
