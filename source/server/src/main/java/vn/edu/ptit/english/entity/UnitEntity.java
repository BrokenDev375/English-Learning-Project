package vn.edu.ptit.english.entity;

import jakarta.persistence.*;
import java.util.List;
import vn.edu.ptit.english.dto.CatalogViews.Unit;

@Entity @Table(name = "units")
public class UnitEntity extends CatalogEntity {
    @Column(name = "topic_id", nullable = false) private long topicId;
    @Column(name = "seed_key", length = 96) private String seedKey;
    @Column(nullable = false, length = 160) private String title;
    @Column(nullable = false, length = 2000) private String description;
    @Column(nullable = false) private int stage;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    protected UnitEntity() {}
    public UnitEntity(long ownerId, long topicId, String title, String description, int stage, int sortOrder) {
        this.ownerId = ownerId; this.topicId = topicId; this.title = title; this.description = description;
        this.stage = stage; this.sortOrder = sortOrder;
    }
    public long getTopicId() { return topicId; }
    public void setSeedKey(String value) { seedKey = value; }
    public void update(long topicId, String title, String description, int stage, int sortOrder) {
        if (this.topicId != topicId || !this.title.equals(title) || !this.description.equals(description)
            || this.stage != stage || this.sortOrder != sortOrder) {
            this.topicId = topicId; this.title = title; this.description = description;
            this.stage = stage; this.sortOrder = sortOrder; revision++;
        }
    }
    public void changed() { revision++; }
    public Unit view() { return new Unit(id, ownerId, topicId, title, description, stage, sortOrder, revision, List.of()); }
}
