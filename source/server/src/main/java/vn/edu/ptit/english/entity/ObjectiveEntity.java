package vn.edu.ptit.english.entity;

import jakarta.persistence.*;
import vn.edu.ptit.english.dto.CatalogViews.Objective;

@Entity @Table(name = "learning_objectives")
public class ObjectiveEntity extends CatalogEntity {
    @Column(name = "topic_id", nullable = false) private long topicId;
    @Column(nullable = false, length = 96) private String code;
    @Column(nullable = false, length = 160) private String name;
    @Column(nullable = false, length = 2000) private String description;
    protected ObjectiveEntity() {}
    public ObjectiveEntity(long ownerId, long topicId, String code, String name, String description) {
        this.ownerId = ownerId; this.topicId = topicId; this.code = code; this.name = name; this.description = description;
    }
    public void update(String name, String description) {
        if (!this.name.equals(name) || !this.description.equals(description)) {
            this.name = name; this.description = description; revision++;
        }
    }
    public Objective view() { return new Objective(id, ownerId, topicId, code, name, description, revision); }
}
