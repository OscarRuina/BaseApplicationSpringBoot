package com.organization.application.repositories;

import com.organization.application.models.entities.UserEntity;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNullApi;
import org.springframework.stereotype.Repository;

@Repository
public interface IUserRepository extends JpaRepository<UserEntity, Integer> {

    @Override
    Optional<UserEntity> findById(Integer integer);

    Optional<UserEntity> findByEmail(String email);

    List<UserEntity> findAll();

    List<UserEntity> findAllByActive(boolean active);

}
