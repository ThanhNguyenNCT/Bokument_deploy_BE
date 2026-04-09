package com.qldapm_L01.backend_api.Controller;

import com.qldapm_L01.backend_api.Payload.Request.LoginRequest;
import com.qldapm_L01.backend_api.Payload.Request.RegisterRequest;
import com.qldapm_L01.backend_api.Payload.Response.BaseResponse;
import com.qldapm_L01.backend_api.Service.AuthService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.crypto.SecretKey;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        String token = authService.register(request);
        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Registration successful");
        response.setData(token);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        String token = authService.login(request);
        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Login successful");
        response.setData(token);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new IllegalArgumentException("Unauthorized logout request");
        }

        authService.logout(authentication.getName());

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Logout successful");
        response.setData(Map.of("revoked", true));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/test")
    public ResponseEntity<?> test() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        String secretKey = Encoders.BASE64.encode(key.getEncoded());
        System.out.println("Secret Key: " + secretKey);

        BaseResponse response = new BaseResponse();
        response.setStatusCode(200);
        response.setMessage("Test successful");
        response.setData(secretKey);
        return ResponseEntity.ok(response);
    }


}
