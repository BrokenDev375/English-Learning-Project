package vn.edu.ptit.english.entity;

import jakarta.persistence.*;
import vn.edu.ptit.english.dto.CatalogViews.Topic;

@Entity @Table(name = "topics")
public class TopicEntity extends CatalogEntity {
    @Column(nullable = false, length = 64) private String code;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, length = 1000) private String description;
    protected TopicEntity() {}
    public TopicEntity(long ownerId, String code, String name, String description) {
        this.ownerId = ownerId; this.code = code; this.name = name; this.description = description;
    }
    public void update(String name, String description) {
        if (!this.name.equals(name) || !this.description.equals(description)) {
            this.name = name; this.description = description; revision++;
        }
    }
    public Topic view() { return new Topic(id, ownerId, code, name, description, revision); }
}
