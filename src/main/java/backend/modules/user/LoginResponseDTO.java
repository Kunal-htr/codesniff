package backend.modules.user;

public class LoginResponseDTO {
    private String email;

    public LoginResponseDTO() {
    }

    public LoginResponseDTO(String email) {
        this.email = email;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
