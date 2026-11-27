package com.hotelsys.model;

public record User(long id, String name, String username, String role) {
    public boolean admin() { return role.equals("ADMIN"); }
}
