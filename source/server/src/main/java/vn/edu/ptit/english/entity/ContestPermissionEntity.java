package vn.edu.ptit.english.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="contest_teacher_permissions")
public class ContestPermissionEntity {
    @Id @Column(name="user_id") private long userId;
    @Column(name="can_author",nullable=false) private boolean canAuthor;
    @Column(name="can_proctor",nullable=false) private boolean canProctor;
    @Column(nullable=false) private long revision;
    @Column(name="updated_by",nullable=false) private long updatedBy;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    protected ContestPermissionEntity() {}
    public ContestPermissionEntity(long user){userId=user;}
    public void update(boolean author,boolean proctor,long actor,Instant now){canAuthor=author;canProctor=proctor;updatedBy=actor;updatedAt=now;revision++;}
    public long userId(){return userId;} public boolean canAuthor(){return canAuthor;} public boolean canProctor(){return canProctor;}
    public long revision(){return revision;} public Long updatedBy(){return updatedBy;} public Instant updatedAt(){return updatedAt;}
}
