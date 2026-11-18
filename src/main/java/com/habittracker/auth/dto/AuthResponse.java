package com.habittracker.auth.dto;

public record AuthResponse(String token, UserResponse user) {}
