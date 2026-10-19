package vn.edu.ptit.english.dto;

import java.math.BigDecimal;
import java.util.*;

public record MaterialData(String kind,String title,String readingText,Long assetId,String transcript,
    String altText,String caption,BigDecimal startSeconds,BigDecimal endSeconds,Source source) {
    public record Source(String label,String url,String license) {}
    public Map<String,Object> fields() {
        var result=new LinkedHashMap<String,Object>();result.put("kind",kind);result.put("title",title);
        switch(kind){
            case "READING" -> {result.put("readingText",readingText);result.put("assetId",assetId);result.put("altText",altText);}
            case "AUDIO" -> {result.put("assetId",assetId);result.put("transcript",transcript);result.put("startSeconds",startSeconds);result.put("endSeconds",endSeconds);}
            case "IMAGE" -> {result.put("assetId",assetId);result.put("altText",altText);result.put("caption",caption);}
            default -> throw new IllegalStateException("Unknown material kind");
        }
        result.put("source",source);return result;
    }
}
