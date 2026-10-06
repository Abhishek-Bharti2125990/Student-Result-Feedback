package com.srip.controller;

import com.srip.domain.Role;
import com.srip.dto.admin.UserAdminDtos.CreateUserRequest;
import com.srip.dto.admin.UserAdminDtos.UpdateUserRequest;
import com.srip.dto.admin.UserAdminDtos.UserStatusRequest;
import com.srip.dto.admin.UserAdminDtos.UserView;
import com.srip.service.UserAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * User management.
 *
 * <p>ADMIN only, and not by an annotation here: {@code /api/admin/**} is already
 * gated in {@code SecurityConfig}, so the whole access model stays readable in
 * one place and a new method on this class cannot forget the role check.
 *
 * <p>Separate from {@link AdminController}, which is about imports and reference
 * data. Accounts are their own concern and the two have no shared state.
 */
@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {

    private final UserAdminService userAdmin;

    public UserAdminController(UserAdminService userAdmin) {
        this.userAdmin = userAdmin;
    }

    /**
     * The account list.
     *
     * <p>Filtered server-side rather than in the browser so the search also
     * works once a school has more logins than one page should carry.
     *
     * @param role  optional role filter
     * @param query optional substring of username, e-mail or full name
     */
    @GetMapping
    public List<UserView> users(@RequestParam(required = false) Role role,
                                @RequestParam(required = false) String query) {
        return userAdmin.list(role, query);
    }

    @GetMapping("/{userId}")
    public UserView user(@PathVariable Long userId) {
        return userAdmin.get(userId);
    }

    @PostMapping
    public ResponseEntity<UserView> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userAdmin.create(request));
    }

    @PutMapping("/{userId}")
    public UserView update(@PathVariable Long userId,
                           @Valid @RequestBody UpdateUserRequest request) {
        return userAdmin.update(userId, request);
    }

    /**
     * Activates or deactivates an account.
     *
     * <p>A PATCH on its own sub-resource rather than a field of the edit body:
     * switching an account off is a one-click action from the list, and routing
     * it through the full edit form would mean re-sending - and re-validating -
     * every other field to flip one flag.
     */
    @PatchMapping("/{userId}/status")
    public UserView setStatus(@PathVariable Long userId,
                              @Valid @RequestBody UserStatusRequest request) {
        return userAdmin.setEnabled(userId, request.enabled());
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> delete(@PathVariable Long userId) {
        userAdmin.delete(userId);
        return ResponseEntity.noContent().build();
    }
}
