package com.ledgerguard.ledger.api;

import com.ledgerguard.ledger.api.dto.AuthRequest;
import com.ledgerguard.ledger.api.dto.AuthResponse;
import com.ledgerguard.ledger.security.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "Auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @PostMapping("/token")
    @Operation(summary = "Exchange demo credentials for a JWT")
    public AuthResponse token(@Valid @RequestBody AuthRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        var roles = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        String token = jwtService.issue(authentication.getName(), roles);
        return new AuthResponse(token, "Bearer", jwtService.expiresIn().toSeconds());
    }
}
