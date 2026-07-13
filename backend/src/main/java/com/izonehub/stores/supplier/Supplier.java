package com.izonehub.stores.supplier;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "suppliers")
public class Supplier {

    @Id
    private UUID id = UUID.randomUUID();
    private String name;
    private String category;
    private String email;
    private String phone;
    private String address;
    private BigDecimal leadTime;
    private BigDecimal accuracy;
    private BigDecimal rating;
    private String status;

    protected Supplier() {}

    public Supplier(String name, String category, String email, String phone, String address,
                    BigDecimal leadTime, BigDecimal accuracy, BigDecimal rating, String status) {
        this.name = name;
        this.category = category;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.leadTime = leadTime;
        this.accuracy = accuracy;
        this.rating = rating;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public BigDecimal getLeadTime() {
        return leadTime;
    }

    public void setLeadTime(BigDecimal leadTime) {
        this.leadTime = leadTime;
    }

    public BigDecimal getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(BigDecimal accuracy) {
        this.accuracy = accuracy;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public void setRating(BigDecimal rating) {
        this.rating = rating;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
