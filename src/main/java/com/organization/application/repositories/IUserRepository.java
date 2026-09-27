package com.organization.application.repositories;

import com.organization.application.models.entities.UserEntity;
import com.organization.application.models.enums.RoleType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
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

    @Query(value = "select count(u) from UserEntity u join u.roleEntities r "
            + "where r.type = :type and u.active = true")
    long countActiveByRole(@Param("type") RoleType type);
}
