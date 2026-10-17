package vn.edu.ptit.english.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="materials")
public class MaterialEntity extends CatalogEntity {
    @Column(name="seed_key",length=96) private String seedKey;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="draft_version_id") private MaterialVersionEntity draft;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="published_version_id") private MaterialVersionEntity published;
    @Column(name="last_version_no",nullable=false) private int lastVersionNo=1;
    @Column(name="retired_at",columnDefinition="datetime(6)") private Instant retiredAt;
    protected MaterialEntity(){}
    public MaterialEntity(long owner){ownerId=owner;}
    public MaterialVersionEntity getDraft(){return draft;}
    public MaterialVersionEntity getPublished(){return published;}
    public int getLastVersionNo(){return lastVersionNo;}
    public Instant getRetiredAt(){return retiredAt;}
    public void setSeedKey(String key){seedKey=key;}
    public void initialDraft(MaterialVersionEntity version){draft=version;}
    public void startDraft(MaterialVersionEntity version){draft=version;lastVersionNo=version.getVersionNo();revision++;}
    public void changed(){revision++;}
    public void discard(){draft=null;revision++;}
    public void publish(){draft.publish();published=draft;draft=null;retiredAt=null;revision++;}
    public void retire(){if(retiredAt==null){retiredAt=Instant.now();revision++;}}
    public String status(){return retiredAt!=null?"RETIRED":published!=null?"PUBLISHED":"DRAFT";}
}
