package com.izonehub.stores.batch;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "serial_numbers")
public class SerialNumber {
    
    @Id
    private UUID id = UUID.randomUUID();

    private String serialNo;

    protected SerialNumber() {}

    public SerialNumber(String serialNo) {
        this.serialNo = serialNo;
    }

    public UUID getId() {
        return id;
    }

    public String getSerialNo() {
        return serialNo;
    }

    public void setSerialNo(String serialNo) {
        this.serialNo = serialNo;
    }
}
