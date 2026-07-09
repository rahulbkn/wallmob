package com.wall.mob;

public class User {
    private String id;
    private String fullName;
    private String email;
    private String phone;
    private String password;
    private String photoUrl;
    
    public User() {
        // Default constructor required for Firebase
    }
    
    public User(String id, String fullName, String email, String phone, String password) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.password = password;
    }
    
    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
}
// test
