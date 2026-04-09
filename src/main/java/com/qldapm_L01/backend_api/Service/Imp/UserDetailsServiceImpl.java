package com.qldapm_L01.backend_api.Service.Imp;

import com.qldapm_L01.backend_api.Entity.User;
import com.qldapm_L01.backend_api.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));

        String normalizedRole = user.getRole() == null || user.getRole().isBlank()
            ? "USER"
            : user.getRole().trim().toUpperCase(Locale.ROOT);

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
            List.of(new SimpleGrantedAuthority("ROLE_" + normalizedRole))
        );
    }
}

