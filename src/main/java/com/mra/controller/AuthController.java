package com.mra.controller;


import com.mra.DTO.LoginRequest;
import com.mra.Entity.User;
import com.mra.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/login")
    public String login(@RequestBody LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername());

        if (user == null) {
            return "❌ User not found!";
        }

        if (!user.getPassword().equals(request.getPassword())) {
            return "❌ Invalid password!";
        }

        return "✅ Login successful! Role: " + user.getRole();
    }

    // Endpoint to add a new user for testing
    @PostMapping("/register")
    public String register(@RequestBody User user) {
        if (userRepository.findByUsername(user.getUsername()) != null) {
            return "❌ Username already exists!";
        }
        userRepository.save(user);
        return "✅ User registered successfully!";
    }
}

