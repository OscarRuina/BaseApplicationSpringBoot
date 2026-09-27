package com.organization.application.repositories;

import com.organization.application.models.entities.UserEntity;
import com.organization.application.models.enums.RoleType;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IUserRepository extends JpaRepository<UserEntity, Integer> {

    Optional<UserEntity> findById(Integer integer);

    Optional<UserEntity> findByEmail(String email);

    @Query(value = "from UserEntity u order by u.id")
    List<UserEntity> findAll();

    List<UserEntity> findAllByActive(boolean active);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = "select u from UserEntity u join u.roleEntities r "
            + "where r.type = :type order by u.id")
    List<UserEntity> findAllByRoleForUpdate(@Param("type") RoleType type);
}
