package vn.edu.ptit.english.dto;

import jakarta.validation.constraints.*;

public final class CatalogRequests {
    private CatalogRequests() {}
    private static final String UUID = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";
    private static final String CODE = "^[a-z0-9]+(?:-[a-z0-9]+)*$";
    private static String clean(String value) { return value == null ? null : value.strip(); }
    public record TopicCreate(@NotNull @Pattern(regexp=UUID) String requestId,
        @NotNull @Size(min=2,max=64) @Pattern(regexp=CODE) String code,
        @NotBlank @Size(max=120) String name, @NotNull @Size(max=1000) String description) {
        public TopicCreate { name = clean(name); description = clean(description); }
    }
    public record TopicUpdate(@NotNull @Pattern(regexp=UUID) String requestId,
        @NotNull @Min(0) Long baseRevision, @NotBlank @Size(max=120) String name,
        @NotNull @Size(max=1000) String description) {
        public TopicUpdate { name = clean(name); description = clean(description); }
    }
    public record ObjectiveCreate(@NotNull @Pattern(regexp=UUID) String requestId,
        @NotNull @Min(1) @Max(9007199254740991L) Long topicId,
        @NotNull @Size(min=2,max=96) @Pattern(regexp=CODE) String code,
        @NotBlank @Size(max=160) String name, @NotNull @Size(max=2000) String description) {
        public ObjectiveCreate { name = clean(name); description = clean(description); }
    }
    public record ObjectiveUpdate(@NotNull @Pattern(regexp=UUID) String requestId,
        @NotNull @Min(0) Long baseRevision, @NotBlank @Size(max=160) String name,
        @NotNull @Size(max=2000) String description) {
        public ObjectiveUpdate { name = clean(name); description = clean(description); }
    }
    public record UnitCreate(@NotNull @Pattern(regexp=UUID) String requestId,
        @NotNull @Min(1) @Max(9007199254740991L) Long topicId,
        @NotBlank @Size(max=160) String title, @NotNull @Size(max=2000) String description,
        @NotNull @Min(1) Integer stage, @NotNull @Min(0) Integer sortOrder) {
        public UnitCreate { title = clean(title); description = clean(description); }
    }
    public record UnitUpdate(@NotNull @Pattern(regexp=UUID) String requestId,
        @NotNull @Min(0) Long baseRevision, @NotNull @Min(1) @Max(9007199254740991L) Long topicId,
        @NotBlank @Size(max=160) String title, @NotNull @Size(max=2000) String description,
        @NotNull @Min(1) Integer stage, @NotNull @Min(0) Integer sortOrder) {
        public UnitUpdate { title = clean(title); description = clean(description); }
    }
}
