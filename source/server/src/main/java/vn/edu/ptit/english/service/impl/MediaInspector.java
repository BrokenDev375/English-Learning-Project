package vn.edu.ptit.english.service.impl;

import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.edu.ptit.english.exception.ApiException;

@Component
public class MediaInspector {
    public record Media(String mimeType,Double duration) {}
    private final ObjectMapper json;
    private final String probe;
    public MediaInspector(ObjectMapper json,@Value("${app.assets.probe-executable}") String probe){this.json=json;this.probe=probe;}
    public Media inspect(Path path) {
        try {
            byte[] prefix;
            try(var input=Files.newInputStream(path)){prefix=input.readNBytes(12);}
            if(prefix.length>=8&&prefix[0]==(byte)0x89&&prefix[1]=='P'&&prefix[2]=='N'&&prefix[3]=='G')return image(path,"PNG","image/png");
            if(prefix.length>=3&&prefix[0]==(byte)0xff&&prefix[1]==(byte)0xd8&&prefix[2]==(byte)0xff)return image(path,"JPEG","image/jpeg");
            String container=null;
            if(prefix.length>=12&&prefix[0]=='R'&&prefix[1]=='I'&&prefix[2]=='F'&&prefix[3]=='F'&&prefix[8]=='W'&&prefix[9]=='A'&&prefix[10]=='V'&&prefix[11]=='E')container="wav";
            else if(prefix.length>=4&&prefix[0]=='O'&&prefix[1]=='g'&&prefix[2]=='g'&&prefix[3]=='S')container="ogg";
            else if(prefix.length>=3&&((prefix[0]=='I'&&prefix[1]=='D'&&prefix[2]=='3')||((prefix[0]&255)==255&&(prefix[1]&224)==224)))container="mp3";
            if(container==null)throw invalid();
            return audio(path,container);
        }catch(IOException ex){throw AssetStorage.unavailable();}
    }
    private Media image(Path path,String format,String mime) throws IOException {
        try(var stream=ImageIO.createImageInputStream(path.toFile())) {
            var readers=ImageIO.getImageReaders(stream);
            if(!readers.hasNext())throw invalid();
            var reader=readers.next();
            try {
                reader.setInput(stream,true,true);
                long width=reader.getWidth(0),height=reader.getHeight(0);
                if(!reader.getFormatName().equalsIgnoreCase(format)||width<1||height<1||width*height>20000000L)throw invalid();
                if(reader.read(0)==null)throw invalid();
                return new Media(mime,null);
            }catch(IOException|IllegalArgumentException ex){throw invalid();}
            finally{reader.dispose();}
        }
    }
    private Media audio(Path path,String container) {
        Path output=null;Process process=null;
        try {
            output=Files.createTempFile("english-probe-",".json");
            // No shell, no URLs, no network protocols, bounded output fields and execution time.
            process=new ProcessBuilder(probe,"-v","error","-protocol_whitelist","file","-f",container,"-show_entries",
                "format=format_name,duration:stream=codec_type,codec_name,duration","-of","json",path.toString())
                .redirectError(ProcessBuilder.Redirect.DISCARD).redirectOutput(output.toFile()).start();
            if(!process.waitFor(8,TimeUnit.SECONDS)){process.destroyForcibly();throw invalid();}
            if(process.exitValue()!=0||Files.size(output)>65536)throw invalid();
            JsonNode metadata=json.readTree(output.toFile());var streams=metadata.path("streams");
            if(streams.size()!=1||!streams.get(0).path("codec_type").asText().equals("audio"))throw invalid();
            String format=metadata.path("format").path("format_name").asText();
            String codec=streams.get(0).path("codec_name").asText();
            String mime=switch(format){
                case "mp3" -> codec.equals("mp3")?"audio/mpeg":null;
                case "wav" -> codec.startsWith("pcm_")||Set.of("adpcm_ima_wav","adpcm_ms","mp3").contains(codec)?"audio/wav":null;
                case "ogg" -> Set.of("vorbis","opus","flac").contains(codec)?"audio/ogg":null;
                default -> null;
            };
            double duration=metadata.path("format").path("duration").asDouble(streams.get(0).path("duration").asDouble(0));
            if(mime==null||!Double.isFinite(duration)||duration<=0)throw invalid();
            return new Media(mime,duration);
        }catch(InterruptedException ex){Thread.currentThread().interrupt();throw AssetStorage.unavailable();}
        catch(IOException ex){throw AssetStorage.unavailable();}
        finally{
            if(process!=null&&process.isAlive())process.destroyForcibly();
            if(output!=null)try{Files.deleteIfExists(output);}catch(IOException ignored){}
        }
    }
    private static ApiException invalid(){return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_FILE_TYPE","Cần file PNG/JPEG hoặc audio MP3/WAV/OGG hợp lệ; ảnh tối đa 20 triệu điểm ảnh.");}
}
