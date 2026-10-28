package vn.edu.ptit.english.entity;
import jakarta.persistence.*;
@Entity @Table(name="content_control")
public class ContentControlEntity {
    @Id private int id;
    protected ContentControlEntity() {}
}
