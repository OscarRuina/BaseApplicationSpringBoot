package com.organization.application.services.interfaces;

import java.util.List;

public interface IGenericService {

    Object findObjectById(String partialUrl, Integer id);

    List<Object> findAll(String partialUrl);
}
