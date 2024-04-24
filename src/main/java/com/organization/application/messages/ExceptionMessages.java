package com.organization.application.messages;

public final class ExceptionMessages {

    private ExceptionMessages(){}

    public static final String USER_NOT_ACTIVE = "ERROR User is not active";

    public static final String BAD_CREDENTIALS  = "ERROR Bad Credentials";

    public static final String USER_ALREADY_EXIST = "ERROR User Already Exist";

    public static final String USER_NOT_EXIST = "ERROR User Not Exist";

    public static final String ROLE_NOT_VALID = "ERROR Role Not Valid";

    public static final String CANT_CREATE_USER = "ERROR Cant Create User";

    public static final String CANT_DELETE = "ERROR Cant Delete";

    public static final String INVALIDATE_TOKEN = "ERROR validating token";

    public static final String INVALID_ATTRIBUTES = "ERROR One or More Attributes has errors";
}
