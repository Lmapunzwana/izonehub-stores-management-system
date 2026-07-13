package com.izonehub.stores.item;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.izonehub.stores.common.BaseEntity;import jakarta.persistence.*;import java.math.BigDecimal;
@Entity

public class Item extends BaseEntity{@Column(nullable=false,unique=true) private String code;@Column(nullable=false) private String name;@Column(length=1000) private String description;@Column(nullable=false) private String unitOfMeasure;@Enumerated(EnumType.STRING)@Column(nullable=false) private ItemCategory category;@Column(nullable=false,precision=19,scale=4) private BigDecimal reorderThreshold=BigDecimal.ZERO;@Column(nullable=false) private boolean active=true;protected Item(){} public Item(String code,String name,String description,String unitOfMeasure,ItemCategory category,BigDecimal reorderThreshold){this.code=code;this.name=name;this.description=description;this.unitOfMeasure=unitOfMeasure;this.category=category;this.reorderThreshold=reorderThreshold;} public String getCode(){return code;} public String getName(){return name;} public boolean isActive(){return active;} public BigDecimal getReorderThreshold(){return reorderThreshold;} public void deactivate(){active=false;}
 public void update(String name,String description,String unitOfMeasure,ItemCategory category,java.math.BigDecimal reorderThreshold){this.name=name;this.description=description;this.unitOfMeasure=unitOfMeasure;this.category=category;this.reorderThreshold=reorderThreshold;}
 public ItemCategory getCategory(){return category;}
 public String getDescription(){return description;}
 public String getUnitOfMeasure(){return unitOfMeasure;}}
