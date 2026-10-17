package vn.edu.ptit.english.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public final class CatalogViews {
    private CatalogViews() {}
    public record Topic(long id, long ownerId, String code, String name, String description, long revision) {}
    public record Objective(long id, long ownerId, long topicId, String code, String name, String description, long revision) {}
    public record Unit(long id, long ownerId, long topicId, String title, String description,
                       int stage, int sortOrder, long revision, List<Long> objectiveIds) {}
    public record CatalogPage<T>(List<T> items, long totalElements, int page, int size, int totalPages) {
        public static <T> CatalogPage<T> from(Page<T> result) {
            return new CatalogPage<>(result.getContent(), result.getTotalElements(), result.getNumber(),
                result.getSize(), result.getTotalPages());
        }
    }
}
