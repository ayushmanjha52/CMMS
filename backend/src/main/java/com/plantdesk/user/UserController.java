package com.plantdesk.user;

import com.plantdesk.auth.RefreshTokenRepository;
import com.plantdesk.common.ConflictException;
import com.plantdesk.common.NotFoundException;
import com.plantdesk.config.PlantDeskProperties;
import com.plantdesk.demo.DemoDataSeeder;
import com.plantdesk.security.AuthenticatedUser;
import com.plantdesk.security.Role;
import com.plantdesk.security.Roles;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final boolean demoMode;

    public UserController(UserRepository users, RefreshTokenRepository refreshTokens,
                          PasswordEncoder passwordEncoder, Clock clock, PlantDeskProperties props) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.demoMode = props.demo().enabled();
    }

    public record UserView(UUID id, String email, String fullName, Role role, Trade trade, Shift shift, boolean active) {
        static UserView of(User u) {
            return new UserView(u.getId(), u.getEmail(), u.getFullName(), u.getRole(), u.getTrade(), u.getShift(), u.isActive());
        }
    }

    public record CreateUserRequest(@NotBlank @Email @Size(max = 254) String email,
                                    @NotBlank @Size(min = 10, max = 72) String password,
                                    @NotBlank @Size(max = 200) String fullName,
                                    @NotNull Role role, Trade trade, Shift shift) {}

    /** Managers need the technician list to assign work; only admins see everyone. */
    @GetMapping
    @PreAuthorize(Roles.MANAGE_WORK)
    @Transactional(readOnly = true)
    public List<UserView> list(@RequestParam(name = "role", required = false) Role role,
                               @AuthenticationPrincipal AuthenticatedUser me) {
        if (role != null) {
            return users.findByRoleAndActiveTrueOrderByFullNameAsc(role).stream().map(UserView::of).toList();
        }
        if (!me.is(Role.PLANT_ADMIN)) {
            return users.findByRoleAndActiveTrueOrderByFullNameAsc(Role.TECHNICIAN).stream().map(UserView::of).toList();
        }
        return users.findAllByOrderByFullNameAsc().stream().map(UserView::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.ADMIN)
    @Transactional
    public UserView create(@Valid @RequestBody CreateUserRequest req) {
        // BCrypt silently truncates at 72 bytes, hence the max above.
        User user = new User(req.email(), passwordEncoder.encode(req.password()), req.fullName(), req.role(),
                req.trade(), req.shift(), clock.instant());
        return UserView.of(users.save(user));
    }

    /** Deactivation also revokes every refresh token, so the person is out within one access-token lifetime. */
    @PostMapping("/{id}/deactivate")
    @PreAuthorize(Roles.ADMIN)
    @Transactional
    public UserView deactivate(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser me) {
        if (id.equals(me.userId())) {
            throw new ConflictException("You cannot deactivate your own account");
        }
        User user = users.findById(id).orElseThrow(() -> new NotFoundException("User", id));
        if (demoMode && DemoDataSeeder.isDemoAccount(user.getEmail())) {
            throw new ConflictException("Demo accounts can't be deactivated on the public demo, so the next visitor can still sign in.");
        }
        user.deactivate();
        refreshTokens.revokeAllForUser(id, clock.instant());
        return UserView.of(user);
    }
}
