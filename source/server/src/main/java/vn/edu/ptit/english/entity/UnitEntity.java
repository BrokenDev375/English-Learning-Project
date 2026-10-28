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
    @Column(name = "round_number", nullable = false) private int roundNumber;
    @Column(name="assigned_teacher_id") private Long assignedTeacherId;
    protected UnitEntity() {}
    public UnitEntity(long ownerId, long topicId, String title, String description, int stage, int sortOrder, int roundNumber) {
        this(ownerId,topicId,title,description,stage,sortOrder,roundNumber,ownerId);
    }
    public UnitEntity(long ownerId,long topicId,String title,String description,int stage,int sortOrder,int roundNumber,Long assignedTeacherId) {
        this.assignedTeacherId = assignedTeacherId;
        this.ownerId = ownerId; this.topicId = topicId; this.title = title; this.description = description;
        this.stage = stage; this.sortOrder = sortOrder; this.roundNumber = roundNumber;
    }
    public Long getAssignedTeacherId(){return assignedTeacherId;}
    public void assign(Long teacherId){if(!java.util.Objects.equals(assignedTeacherId,teacherId)){assignedTeacherId=teacherId;revision++;}}
    public int getRoundNumber() { return roundNumber; }
    public long getTopicId() { return topicId; }
    public void setSeedKey(String value) { seedKey = value; }
    public void update(long topicId, String title, String description, int stage, int sortOrder, int roundNumber) {
        if (this.topicId != topicId || !this.title.equals(title) || !this.description.equals(description)
            || this.stage != stage || this.sortOrder != sortOrder || this.roundNumber != roundNumber) {
            this.topicId = topicId; this.title = title; this.description = description;
            this.stage = stage; this.sortOrder = sortOrder; this.roundNumber = roundNumber; revision++;
        }
    }
    public void changed() { revision++; }
    public Unit view() { return new Unit(id, ownerId, topicId, title, description, stage, sortOrder, roundNumber, assignedTeacherId, revision, List.of()); }
}
