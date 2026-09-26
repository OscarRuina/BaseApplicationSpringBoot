package com.organization.application.models.enums;

public enum RoleType {

    ADMIN,

    USER;

    public String getPrefixedName() {
        return "ROLE_" + this.name();
    }
}
