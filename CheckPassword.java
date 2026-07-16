import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class CheckPassword {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = "$2a$10$w0f5uN5t32h.5kH5E2l4T.bC2rE3pX02q0l4xW6hD3rI4l0vM9d2q";
        String raw = "password123";
        boolean match = encoder.matches(raw, hash);
        System.out.println("Match? " + match);
        System.out.println("New Hash: " + encoder.encode(raw));
    }
}
