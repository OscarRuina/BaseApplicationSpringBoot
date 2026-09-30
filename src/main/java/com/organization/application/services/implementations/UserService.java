package com.organization.application.services.implementations;

import com.organization.application.configurations.email.service.IEmailService;
import com.organization.application.configurations.exceptions.ActivationTokenAlreadyUsedException;
import com.organization.application.configurations.exceptions.CurrentPasswordInvalidException;
import com.organization.application.configurations.exceptions.CurrentPasswordRequiredException;
import com.organization.application.configurations.exceptions.ExpiredActivationTokenException;
import com.organization.application.configurations.exceptions.ForbiddenException;
import com.organization.application.configurations.exceptions.InvalidActivationTokenException;
import com.organization.application.configurations.exceptions.PendingActivationException;
import com.organization.application.configurations.exceptions.TooManyAttemptsException;
import com.organization.application.configurations.exceptions.UserAlreadyExistException;
import com.organization.application.configurations.exceptions.UserInactiveException;
import com.organization.application.configurations.exceptions.UserNotExistException;
import com.organization.application.configurations.security.throttle.LoginThrottle;
import com.organization.application.configurations.security.throttle.RegistrationThrottle;
import com.organization.application.converters.UserConverter;
import com.organization.application.dtos.request.ActivateAccountRequestDTO;
import com.organization.application.dtos.request.RegisterUserRequestDTO;
import com.organization.application.dtos.request.UpdateUserRequestDTO;
import com.organization.application.dtos.response.UserResponseDTO;
import com.organization.application.messages.ExceptionMessages;
import com.organization.application.models.entities.RoleEntity;
import com.organization.application.models.entities.UserEntity;
import com.organization.application.models.enums.RoleType;
import com.organization.application.repositories.IUserRepository;
import com.organization.application.services.interfaces.IRoleService;
import com.organization.application.services.interfaces.IUserService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class UserService implements IUserService {

    private final IUserRepository userRepository;

    private final UserConverter userConverter;

    private final IRoleService roleService;

    private final IEmailService emailService;

    private final PasswordEncoder passwordEncoder;

    private final LoginThrottle loginThrottle;

    private final RegistrationThrottle registrationThrottle;

    private final String frontendBaseUrl;

    private static final String EMAIL_SUBJECT_ACTIVATION = "Confirme su registro";

    private static final String EMAIL_SUBJECT_REACTIVATED = "Cuenta reactivada";

    private static final String TEMPLATE_ACTIVATE_ACCOUNT = "email_activate_account";

    private static final String TEMPLATE_REACTIVATED_USER = "email_reactivated_user";

    private static final char[] PASSWORD_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    private static final int RANDOM_PASSWORD_LENGTH = 16;

    private static final int ACTIVATION_TOKEN_BYTES = 32;

    private static final Duration ACTIVATION_TTL = Duration.ofDays(7);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public UserService(IUserRepository userRepository, UserConverter userConverter,
            IRoleService roleService,
            IEmailService emailService, PasswordEncoder passwordEncoder,
            LoginThrottle loginThrottle, RegistrationThrottle registrationThrottle,
            @Value("${app.frontend-base-url}") String frontendBaseUrl) {
        this.userRepository = userRepository;
        this.userConverter = userConverter;
        this.roleService = roleService;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.loginThrottle = loginThrottle;
        this.registrationThrottle = registrationThrottle;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /**
     * Método encargado de retornar los datos del usuario autenticado en la aplicación
     * @param callerEmail
     * @return UserResponseDTO
     */
    @Override
    public UserResponseDTO me(String callerEmail) {
        log.info("Inside user service method me ");

        UserEntity user = userRepository.findByEmail(callerEmail).orElseThrow(
                () -> new UserNotExistException(ExceptionMessages.USER_NOT_EXIST));

        return userConverter.userToUserResponseDTO(user);
    }

    /**
     * Método encargado de crear un nuevo usuario en la aplicación
     *
     * <p>El registro es público y no lleva contraseña: la cuenta nace inactiva y pendiente de
     * confirmación, con un token de un solo uso que viaja por mail. El rol se fija en USER acá
     * y no en el request porque aceptarlo abriría la auto-asignación de ADMIN.
     *
     * <p>La creación del usuario y el envío del mail ocurren dentro de la misma transacción,
     * por lo que un fallo en el envío revierte el alta y no queda ningún usuario persistido
     *
     * @param registerUserRequestDTO
     * @param clientIp
     * @return UserResponseDTO
     */
    @Override
    @Transactional
    public UserResponseDTO register(RegisterUserRequestDTO registerUserRequestDTO, String clientIp) {
        log.info("Inside user service method register");

        long retryAfter = registrationThrottle.retryAfterSeconds(clientIp);
        if (retryAfter > 0) {
            throw new TooManyAttemptsException(ExceptionMessages.TOO_MANY_ATTEMPTS, retryAfter);
        }

        Optional<UserEntity> existing = userRepository.findByEmail(registerUserRequestDTO.getEmail());

        if (existing.isEmpty()) {
            return createPendingUser(registerUserRequestDTO, clientIp);
        }

        // Un token vencido sobre un registro sin confirmar es un callejón sin salida: el correo
        // ya está tomado y la cuenta no tiene forma de activarse. Se rota el token y se vuelve a
        // enviar en lugar de devolver un 409 que no le deja hacer nada al usuario.
        UserEntity pending = existing.get();
        if (pending.isPendingActivation() && isExpired(pending)) {
            log.info("Reissuing an expired activation token");
            return reissueActivationToken(pending, clientIp);
        }

        throw new UserAlreadyExistException(ExceptionMessages.USER_ALREADY_EXIST);
    }

    private UserResponseDTO createPendingUser(RegisterUserRequestDTO request, String clientIp) {
        RoleEntity role = roleService.findRoleByType(RoleType.USER);
        String token = newActivationToken();

        UserEntity user = UserEntity.builder()
                .firstname(request.getFirstname())
                .lastname(request.getLastname())
                .email(request.getEmail())
                .password(placeholderPassword())
                .active(false)
                // activatedAt queda null a propósito: es lo que marca la cuenta como pendiente.
                .activatedAt(null)
                .activationToken(hash(token))
                .activationExpiresAt(expirationFromNow())
                .roleEntities(Set.of(role))
                .build();

        user = saveOrTranslateConflict(user);
        recordRegistrationAttempt(clientIp);
        sendActivationMail(user, token);
        return userConverter.userToUserResponseDTO(user);
    }

    private UserResponseDTO reissueActivationToken(UserEntity user, String clientIp) {
        String token = newActivationToken();
        user.setActivationToken(hash(token));
        user.setActivationExpiresAt(expirationFromNow());
        user = saveOrTranslateConflict(user);
        recordRegistrationAttempt(clientIp);
        sendActivationMail(user, token);
        return userConverter.userToUserResponseDTO(user);
    }

    /**
     * Cuenta el intento recién cuando la llamada llegó al envío del mail. Los 409 por correo
     * duplicado no cuentan: si contaran, el endpoint serviría para agotarle el presupuesto de
     * correo a un tercero sin necesidad, mandando correos que ya existen.
     */
    private void recordRegistrationAttempt(String clientIp) {
        registrationThrottle.recordAttempt(clientIp);
    }

    private UserEntity saveOrTranslateConflict(UserEntity user) {
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent registration detected for email {}", user.getEmail());
            throw new UserAlreadyExistException(ExceptionMessages.USER_ALREADY_EXIST);
        }
    }

    /**
     * Método encargado de canjear el token de activación por la contraseña definitiva
     *
     * <p>El orden de las validaciones importa y no es arbitrario: primero se busca el token,
     * después se descarta que ya se haya usado, y recién al final se mira la expiración. Al
     * revés, un token vencido de una cuenta ya activada se reportaría como expirado cuando en
     * realidad está quemado, y el usuario recibiría un mensaje que lo invite a esperar algo que
     * no va a pasar.
     *
     * @param activateAccountRequestDTO
     * @return UserResponseDTO
     */
    @Override
    @Transactional
    public UserResponseDTO activate(ActivateAccountRequestDTO activateAccountRequestDTO) {
        log.info("Inside user service method activate");

        UserEntity user = userRepository.findByActivationToken(
                hash(activateAccountRequestDTO.getToken())).orElseThrow(
                () -> new InvalidActivationTokenException(
                        ExceptionMessages.INVALID_ACTIVATION_TOKEN));

        if (!user.isPendingActivation()) {
            throw new ActivationTokenAlreadyUsedException(
                    ExceptionMessages.ACTIVATION_TOKEN_ALREADY_USED);
        }

        if (isExpired(user)) {
            throw new ExpiredActivationTokenException(ExceptionMessages.ACTIVATION_TOKEN_EXPIRED);
        }

        user.setPassword(passwordEncoder.encode(activateAccountRequestDTO.getPassword()));
        user.setActivatedAt(now());
        user.setActive(true);

        // El hash del token se conserva a propósito: es lo que permite distinguir "ya usado"
        // (409) de "desconocido" (400). El uso único lo garantiza el guard de pendiente, no
        // borrar la fila.
        return userConverter.userToUserResponseDTO(userRepository.saveAndFlush(user));
    }

    private String generateRandomPassword() {
        StringBuilder password = new StringBuilder(RANDOM_PASSWORD_LENGTH);
        for (int i = 0; i < RANDOM_PASSWORD_LENGTH; i++) {
            password.append(PASSWORD_ALPHABET[SECURE_RANDOM.nextInt(PASSWORD_ALPHABET.length)]);
        }
        return password.toString();
    }

    /**
     * La columna de contraseña es NOT NULL sin default, así que la cuenta pendiente necesita
     * algo guardado. Es un valor aleatorio que el usuario nunca conoce y que se reemplaza en el
     * momento de activarse: sirve para que una fila pendiente no sea activable por la vía
     * rápida del login, no para ser una credencial.
     */
    private String placeholderPassword() {
        return passwordEncoder.encode(generateRandomPassword());
    }

    private String newActivationToken() {
        byte[] bytes = new byte[ACTIVATION_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Lo que se persiste es el hash. Un dump de la tabla no sirve para activar cuentas, que es
     * justamente el motivo de no guardar el token en claro.
     */
    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JRE", e);
        }
    }

    private Timestamp expirationFromNow() {
        return Timestamp.from(Instant.now().plus(ACTIVATION_TTL));
    }

    private boolean isExpired(UserEntity user) {
        return user.getActivationExpiresAt() == null
                || user.getActivationExpiresAt().before(Timestamp.from(Instant.now()));
    }

    private void sendActivationMail(UserEntity user, String token) {
        Map<String, Object> message = new HashMap<>();
        // Único momento en que el token en claro existe fuera del buzón del usuario.
        message.put("activationLink", activationLink(token));
        message.put("expiryDays", ACTIVATION_TTL.toDays());
        emailService.sendEmail(new String[]{user.getEmail()}, EMAIL_SUBJECT_ACTIVATION,
                TEMPLATE_ACTIVATE_ACCOUNT, message);
    }

    private String activationLink(String token) {
        return "%s/activate?token=%s".formatted(frontendBaseUrl, token);
    }

    private Timestamp now() {
        return Timestamp.from(Instant.now());
    }

    /**
     * Aviso de que la cuenta volvió a estar activa. No lleva credenciales porque la contraseña
     * no se tocan: sigue siendo la que el usuario eligió al registrarse o al activarse.
     */
    private void sendReactivationNotification(UserEntity user) {
        emailService.sendEmail(new String[]{user.getEmail()}, EMAIL_SUBJECT_REACTIVATED,
                TEMPLATE_REACTIVATED_USER, Map.of());
    }

    /**
     * Método encargado de buscar todos los usuarios de la aplicación
     * @return List<UserResponseDTO>
     */
    @Override
    public List<UserResponseDTO> findUsers() {
        log.info("Inside user service method find users");
        return userRepository.findAllByOrderByIdAsc().stream()
                .map(userConverter::userToUserResponseDTO)
                .toList();
    }

    /**
     * Método encargado de buscar todos los usuarios que no han sido eliminados de la aplicación
     * @return List<UserResponseDTO>
     */
    @Override
    public List<UserResponseDTO> findActiveUsers() {
        log.info("Inside user service method find active users");
        return userRepository.findAllByActive(true).stream()
                .map(userConverter::userToUserResponseDTO)
                .toList();
    }

    /**
     * Método encargado de buscar un usuario en la aplicación
     * @param id
     * @return UserResponseDTO
     */
    @Override
    public UserResponseDTO findUser(Integer id) {
        log.info("Inside user service method find user by id");
        UserEntity user = userRepository.findById(id).orElseThrow(
                () -> new UserNotExistException(ExceptionMessages.USER_NOT_EXIST));
        return userConverter.userToUserResponseDTO(user);
    }

    private boolean isAdmin(UserEntity user) {
        return user.getRoleEntities().stream()
                .anyMatch(roleEntity -> roleEntity.getType() == RoleType.ADMIN);
    }

    private long countActiveAdmins() {
        return userRepository.findAllByRoleForUpdate(RoleType.ADMIN).stream()
                .filter(UserEntity::isActive)
                .count();
    }

    /**
     * Método encargado de eliminar un usuario en la aplicación
     * @param id
     * @param callerEmail
     * @return UserResponseDTO
     */
    @Override
    public UserResponseDTO delete(Integer id, String callerEmail) {
        log.info("Inside user service method delete user by id");

        UserEntity user = userRepository.findById(id).orElseThrow(
                () -> new UserNotExistException(ExceptionMessages.USER_NOT_EXIST));

        if (!user.isActive()){
            throw new UserInactiveException(ExceptionMessages.USER_NOT_ACTIVE);
        }

        if (isAdmin(user)){
            throw new ForbiddenException(ExceptionMessages.CANT_DELETE);
        }

        if (user.getEmail().equalsIgnoreCase(callerEmail)){
            throw new ForbiddenException(ExceptionMessages.CANT_DELETE);
        }

        user.setActive(false);
        userRepository.save(user);
        return userConverter.userToUserResponseDTO(user);
    }

    /**
     * Método encargado de actualizar el estado de un usuario en la aplicación
     *
     * <p>Reactivar no rota la contraseña ni manda credenciales: el usuario recupera el acceso
     * con la que él mismo eligió. Si se olvidó, el forgot/reset documentado en el backlog es el
     * camino, no este endpoint.
     *
     * @param id
     * @param active
     * @param callerEmail
     * @return UserResponseDTO
     */
    @Override
    @Transactional
    public UserResponseDTO updateStatus(Integer id, boolean active, String callerEmail) {
        log.info("Inside user service method update status user by id, active: {}", active);

        UserEntity user = userRepository.findById(id).orElseThrow(
                () -> new UserNotExistException(ExceptionMessages.USER_NOT_EXIST));

        if (user.getEmail().equalsIgnoreCase(callerEmail)){
            throw new ForbiddenException(ExceptionMessages.CANT_UPDATE_STATUS);
        }

        // Una cuenta pendiente no está suspendida: está esperando el mail de activación. El
        // guard va antes del no-op a propósito, porque "suspender" una cuenta ya inactiva
        // devolvería un 200 silencioso y el admin creería que la suspendió.
        if (user.isPendingActivation()){
            throw new PendingActivationException(ExceptionMessages.USER_PENDING_ACTIVATION);
        }

        if (user.isActive() == active){
            return userConverter.userToUserResponseDTO(user);
        }

        if (!active && isAdmin(user) && countActiveAdmins() == 1) {
            throw new ForbiddenException(ExceptionMessages.LAST_ADMIN_PROTECTED);
        }

        user.setActive(active);
        userRepository.saveAndFlush(user);

        if (active) {
            sendReactivationNotification(user);
        }
        return userConverter.userToUserResponseDTO(user);
    }

    /**
     * Método encargado de actualizar el rol de un usuario en la aplicación
     * @param id
     * @param role
     * @param callerEmail
     * @return UserResponseDTO
     */
    @Override
    @Transactional
    public UserResponseDTO updateRole(Integer id, RoleType role, String callerEmail) {
        log.info("Inside user service method update role");

        UserEntity user = userRepository.findById(id).orElseThrow(
                () -> new UserNotExistException(ExceptionMessages.USER_NOT_EXIST));

        if (!user.isActive()){
            throw new UserInactiveException(ExceptionMessages.USER_NOT_ACTIVE);
        }

        if (user.getEmail().equalsIgnoreCase(callerEmail)){
            throw new ForbiddenException(ExceptionMessages.CANT_UPDATE_ROLE);
        }

        RoleEntity roleEntity = roleService.findRoleByType(role);

        if (isAdmin(user) && roleEntity.getType() != RoleType.ADMIN
                && countActiveAdmins() == 1) {
            throw new ForbiddenException(ExceptionMessages.LAST_ADMIN_PROTECTED);
        }

        user.getRoleEntities().clear();
        user.getRoleEntities().add(roleEntity);

        return userConverter.userToUserResponseDTO(userRepository.save(user));
    }

    /**
     * Método encargado de actualizar el usuario autenticado en la aplicación
     * @param updateUserRequestDTO
     * @param callerEmail
     * @param clientIp
     * @return UserResponseDTO
     */
    @Override
    public UserResponseDTO updateUser(UpdateUserRequestDTO updateUserRequestDTO, String callerEmail,
            String clientIp) {
        String newPassword = updateUserRequestDTO.getPassword();
        boolean passwordChange = newPassword != null && !newPassword.isBlank();

        if (passwordChange) {
            long retryAfter = loginThrottle.retryAfterSeconds(callerEmail, clientIp);
            if (retryAfter > 0) {
                throw new TooManyAttemptsException(ExceptionMessages.TOO_MANY_ATTEMPTS, retryAfter);
            }
            if (updateUserRequestDTO.getCurrentPassword() == null
                    || updateUserRequestDTO.getCurrentPassword().isBlank()) {
                throw new CurrentPasswordRequiredException(
                        ExceptionMessages.CURRENT_PASSWORD_REQUIRED);
            }
        }

        UserEntity user = userRepository.findByEmail(callerEmail).orElseThrow(
                () -> new UserNotExistException(ExceptionMessages.USER_NOT_EXIST));

        if (passwordChange && !passwordEncoder.matches(updateUserRequestDTO.getCurrentPassword(),
                user.getPassword())) {
            loginThrottle.recordFailure(callerEmail, clientIp);
            throw new CurrentPasswordInvalidException(ExceptionMessages.CURRENT_PASSWORD_INVALID);
        }

        user.setFirstname(updateUserRequestDTO.getFirstname());
        user.setLastname(updateUserRequestDTO.getLastname());

        if (passwordChange) {
            user.setPassword(passwordEncoder.encode(newPassword));
        }

        UserResponseDTO dto = userConverter.userToUserResponseDTO(userRepository.save(user));

        if (passwordChange) {
            loginThrottle.recordSuccess(callerEmail);
        }

        return dto;
    }
}
