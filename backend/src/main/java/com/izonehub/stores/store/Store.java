package com.izonehub.stores.store;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.izonehub.stores.common.BaseEntity;import com.izonehub.stores.user.AppUser;import jakarta.persistence.*;
@Entity

public class Store extends BaseEntity{
    @Column(nullable=false,unique=true) private String name;
    @Enumerated(EnumType.STRING)@Column(nullable=false) private StoreType type;
    @Column(nullable=false) private String location;
    @JsonIgnoreProperties("assignedStore")
    @ManyToOne(fetch=FetchType.LAZY) private AppUser manager;

    // A SITE store is usually created for a specific project; when that
    // project closes there is no longer any reason for its site store to
    // stay open for new material requests, GRNs, etc. See
    // ProjectController.close(), which flips this off automatically when
    // no other active project still references the store.
    @Column(nullable=false) private boolean active = true;
    
    @Column(nullable=false) private boolean closing = false;

    protected Store(){}
    public Store(String name,StoreType type,String location,AppUser manager){this.name=name;this.type=type;this.location=location;this.manager=manager;}
    public String getName(){return name;}
    public StoreType getType(){return type;}
    public String getLocation(){return location;}
    public AppUser getManager(){return manager;}
    public boolean isActive(){return active;}
    public boolean isClosing(){return closing;}
    public void markClosing(){closing=true;}
    public void close(){active=false;closing=false;}
    public void reopen(){active=true;closing=false;}
    public void setManager(AppUser manager) { this.manager = manager; }
    public void setType(StoreType type) { this.type = type; }

    public void update(String name, StoreType type, String location, AppUser manager) {
        this.name = name;
        this.type = type;
        this.location = location;
        this.manager = manager;
    }
}
