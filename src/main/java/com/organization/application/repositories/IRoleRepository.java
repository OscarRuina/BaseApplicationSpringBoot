package com.organization.application.repositories;

import com.organization.application.models.entities.RoleEntity;
import com.organization.application.models.enums.RoleType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IRoleRepository extends JpaRepository<RoleEntity, Integer> {

    @Override
    Optional<RoleEntity> findById(Integer integer);

    Optional<RoleEntity> findByType(RoleType type);

}
