package com.srip.security;

import com.srip.domain.Role;
import com.srip.domain.UserAccount;
import com.srip.repository.StudentRepository;
import com.srip.repository.UserAccountRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserAccountRepository users;
    private final StudentRepository students;

    public AppUserDetailsService(UserAccountRepository users, StudentRepository students) {
        this.users = users;
        this.students = students;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        UserAccount user = users.findByUsernameOrEmail(identifier)
                .orElseThrow(() -> new UsernameNotFoundException("No account for " + identifier));
        return toPrincipal(user);
    }

    @Transactional(readOnly = true)
    public UserDetails loadByUserId(Long userId) {
        UserAccount user = users.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("No account with id " + userId));
        return toPrincipal(user);
    }

    public AppUserPrincipal toPrincipal(UserAccount user) {
        Long studentId = user.getRole() == Role.STUDENT
                ? students.findByUserId(user.getId()).map(s -> s.getId()).orElse(null)
                : null;
        return new AppUserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getRole(),
                user.isEnabled(),
                studentId);
    }
}
