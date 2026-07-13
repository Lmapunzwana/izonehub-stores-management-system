package com.izonehub.stores.auth;

import com.izonehub.stores.user.AppUser;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_token")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String token;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser user;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean used = false;

    protected PasswordResetToken() {}

    public PasswordResetToken(String token, AppUser user, Instant expiresAt) {
        this.token     = token;
        this.user      = user;
        this.expiresAt = expiresAt;
    }

    public String  getToken()     { return token; }
    public AppUser getUser()      { return user; }
    public boolean isUsed()       { return used; }
    public boolean isExpired()    { return Instant.now().isAfter(expiresAt); }
    public void    markUsed()     { used = true; }
}
