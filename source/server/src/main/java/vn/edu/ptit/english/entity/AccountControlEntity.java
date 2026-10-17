package vn.edu.ptit.english.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "account_control")
public class AccountControlEntity {
    @Id private Integer id;
    protected AccountControlEntity() {}
}
