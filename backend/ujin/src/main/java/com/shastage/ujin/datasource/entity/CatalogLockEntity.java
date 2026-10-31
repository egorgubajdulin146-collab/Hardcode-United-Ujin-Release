package com.shastage.ujin.datasource.entity;

import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name="catalog_lock")
public class CatalogLockEntity {
    @Id
    private Integer id;
    public CatalogLockEntity() {}
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
}
