package vn.edu.ptit.english.dto;

import java.util.*;

public final class MaterialViews {
    private MaterialViews(){}
    public record Root(long id,long ownerId,long rootRevision,String status,Long draftVersionId,Long publishedVersionId,
        Map<String,Object> draft,Map<String,Object> published) {}
    public record Summary(long id,long ownerId,long rootRevision,String status,Long draftVersionId,Long publishedVersionId,String title,String kind) {}
    public record Issue(String path,String code,String message) {}
    public record Preview(long resourceId,long draftVersionId,long baseRevision,String previewToken,
        List<Issue> errors,List<String> warnings,Map<String,Object> data) {}
}
