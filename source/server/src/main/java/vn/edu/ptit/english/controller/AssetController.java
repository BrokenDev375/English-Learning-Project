package vn.edu.ptit.english.controller;

import java.nio.charset.StandardCharsets;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.*;
import vn.edu.ptit.english.exception.ApiException;
import vn.edu.ptit.english.security.UserPrincipal;
import vn.edu.ptit.english.service.AssetService;

@RestController @RequestMapping("/api/assets")
public class AssetController {
    private final AssetService service;
    public AssetController(AssetService service){this.service=service;}
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file,@RequestParam("requestId") String requestId,
        MultipartHttpServletRequest request,@AuthenticationPrincipal UserPrincipal actor){
        MultiValueMap<String,MultipartFile> files=request.getMultiFileMap();
        if(files.size()!=1||files.get("file").size()!=1||request.getParameterMap().size()!=1||request.getParameterValues("requestId").length!=1)
            throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","Chỉ gửi một file và một mã yêu cầu.");
        var result=service.upload(file,requestId,actor);
        var response=ResponseEntity.status(result.status()).cacheControl(CacheControl.noStore().cachePrivate());
        if(result.replayed())response.header("Idempotent-Replayed","true");
        return response.body(result.body());
    }
    @GetMapping("/{id}") public ResponseEntity<Resource> read(@PathVariable long id,@AuthenticationPrincipal UserPrincipal actor){
        var file=service.read(id,actor);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
            .contentType(MediaType.parseMediaType(file.asset().mimeType()))
            .header("X-Content-Type-Options","nosniff")
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.inline().filename(file.asset().name(),StandardCharsets.UTF_8).build().toString())
            .body(new FileSystemResource(file.path()));
    }
}
