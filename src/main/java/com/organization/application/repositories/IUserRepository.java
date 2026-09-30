package com.organization.application.repositories;

import com.organization.application.models.entities.UserEntity;
import com.organization.application.models.enums.RoleType;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IUserRepository extends JpaRepository<UserEntity, Integer> {

    @EntityGraph(attributePaths = "roleEntities")
    Optional<UserEntity> findByEmail(String email);

    /**
     * Busca por el SHA-256 del token, nunca por el token en claro. La columna es UNIQUE, así
     * que el Optional ya es single-row sin necesidad de un {@code distinct}.
     */
    @EntityGraph(attributePaths = "roleEntities")
    Optional<UserEntity> findByActivationToken(String activationTokenHash);

    boolean existsByEmail(String email);

    boolean existsByRoleEntitiesType(RoleType type);

    @EntityGraph(attributePaths = "roleEntities")
    List<UserEntity> findAllByOrderByIdAsc();

    @EntityGraph(attributePaths = "roleEntities")
    List<UserEntity> findAllByActive(boolean active);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = "select distinct u from UserEntity u join u.roleEntities r "
            + "where r.type = :type order by u.id")
    List<UserEntity> findAllByRoleForUpdate(@Param("type") RoleType type);
}
