package com.izonehub.stores.batch;

import com.izonehub.stores.item.Item;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "batches")
public class Batch {

    @Id
    private UUID id = UUID.randomUUID();

    private String batchNo;

    @ManyToOne(fetch = FetchType.LAZY)
    private Item item;

    private String receivedVia;
    
    private String project;

    private String status;

    private LocalDate expiryDate;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "batch_id")
    private List<SerialNumber> serials = new ArrayList<>();

    protected Batch() {}

    public Batch(String batchNo, Item item, String receivedVia, String project, String status, LocalDate expiryDate) {
        this.batchNo = batchNo;
        this.item = item;
        this.receivedVia = receivedVia;
        this.project = project;
        this.status = status;
        this.expiryDate = expiryDate;
    }

    public void addSerial(String serialNo) {
        this.serials.add(new SerialNumber(serialNo));
    }

    public UUID getId() {
        return id;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public String getReceivedVia() {
        return receivedVia;
    }

    public void setReceivedVia(String receivedVia) {
        this.receivedVia = receivedVia;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public List<SerialNumber> getSerials() {
        return serials;
    }
}
