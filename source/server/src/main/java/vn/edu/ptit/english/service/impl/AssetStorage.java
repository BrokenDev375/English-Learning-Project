package vn.edu.ptit.english.service.impl;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;
import vn.edu.ptit.english.exception.ApiException;

@Component
public class AssetStorage {
    public static final int MAX_BYTES=5242880;
    private static final Logger log=LoggerFactory.getLogger(AssetStorage.class);
    private final Path root;
    public AssetStorage(@Value("${app.assets.directory}") String directory){root=Path.of(directory).toAbsolutePath().normalize();}
    public Path stage(InputStream source) {
        Path temporary=null;
        try {
            Files.createDirectories(root);
            temporary=Files.createTempFile(root,"upload-",".tmp");
            byte[] bytes=source.readNBytes(MAX_BYTES+1);
            if(bytes.length>MAX_BYTES)throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,"FILE_TOO_LARGE","File tối đa 5 MiB.");
            if(bytes.length==0)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_FILE","File không có nội dung.");
            Files.write(temporary,bytes);return temporary;
        } catch(IOException ex){discard(temporary);throw unavailable();}
        catch(RuntimeException ex){discard(temporary);throw ex;}
    }
    public String commit(Path temporary) {
        if(!TransactionSynchronizationManager.isSynchronizationActive())throw new IllegalStateException("Asset writes require a transaction");
        String key=UUID.randomUUID().toString();Path target=root.resolve(key);
        try {Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE);}
        catch(IOException ex){throw unavailable();}
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCompletion(int status){if(status==STATUS_ROLLED_BACK)discard(target);}
        });
        return key;
    }
    public Path existing(String key) {
        try {
            if(!UUID.fromString(key).toString().equals(key))throw unavailable();
            Path target=root.resolve(key).normalize();
            if(!target.getParent().equals(root)||!Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS))throw unavailable();
            return target;
        }catch(IllegalArgumentException ex){throw unavailable();}
    }
    public void discard(Path path){if(path!=null)try{Files.deleteIfExists(path);}catch(IOException ex){log.warn("Unable to clean an asset staging file");}}
    public static ApiException unavailable(){return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"ASSET_UNAVAILABLE","Chưa đọc hoặc lưu được file. Vui lòng thử lại.");}
}
