package com.srip.repository;

import com.srip.domain.Role;
import com.srip.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByUsername(String username);

    /** Login accepts either the username or the e-mail address. */
    @Query("select u from UserAccount u where u.username = :identifier or u.email = :identifier")
    Optional<UserAccount> findByUsernameOrEmail(@Param("identifier") String identifier);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /**
     * Uniqueness checks for an edit, which must not collide with the row being
     * edited: a user keeping their own username is not a conflict.
     */
    boolean existsByUsernameAndIdNot(String username, Long id);

    boolean existsByEmailAndIdNot(String email, Long id);

    List<UserAccount> findByRole(Role role);

    /** The admin list, grouped by role so the three kinds of account read apart. */
    List<UserAccount> findAllByOrderByRoleAscUsernameAsc();
}
