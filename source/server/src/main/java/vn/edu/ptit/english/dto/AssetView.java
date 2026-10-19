package vn.edu.ptit.english.dto;

public record AssetView(long id,long ownerId,String name,String mimeType,long sizeBytes,String sha256,String url) {}
