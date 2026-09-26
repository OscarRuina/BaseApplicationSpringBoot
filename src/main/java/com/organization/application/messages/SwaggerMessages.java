package com.organization.application.messages;

public final class SwaggerMessages {

    private SwaggerMessages(){}

    //Config Messages
    public static final String TITTLE = "Spring Base Application";

    public static final String DESCRIPTION = "Base app to manage users and permissions";

    public static final String SERVER_DESCRIPTION = "Developer Server";

    public static final String SERVER_URL = "http://localhost:8085/api";

    public static final String SECURITY_DESCRIPTION = "Security using JWT";

    public static final String SECURITY_SCHEME_NAME = "bearer";

    public static final String SECURITY_BEARER_FORMAT = "JWT";

    //Common Messages
    public static final String ERROR_RESPONSE_400 = "Invalid request";

    public static final String ERROR_RESPONSE_401 = "Unauthorized";

    public static final String ERROR_RESPONSE_500 = "Internal server error";

    //Security Controller Messages
    public static final String LOGIN_OPERATION = "Login user with username and password and then "
            + "return the user logged in with his token";

    public static final String LOGIN_RESPONSE_200 = "User logged successfully";

    //User Controller Messages
    public static final String USER_ME_OPERATION = "Return the user logged in with his token";

    public static final String USER_ME_RESPONSE_200 = "User logged return successfully";

    public static final String USER_ALL_OPERATION = "Return all the users";

    public static final String USER_ALL_RESPONSE_200 = "Users returns successfully";

    public static final String USER_REGISTER_OPERATION = "Return the user created";

    public static final String USER_REGISTER_RESPONSE_200 = "User created successfully";

    public static final String USER_ALL_ACTIVE_OPERATION = "Return all the active users";

    public static final String USER_ALL_ACTIVE_RESPONSE_200 = "Active users returns successfully";

    public static final String USER_FIND_ID_OPERATION = "Return the user by id";

    public static final String USER_FIND_ID_RESPONSE_200 = "User get by id successfully";

    public static final String USER_DELETE_ID_OPERATION = "Delete the user by id";

    public static final String USER_DELETE_ID_RESPONSE_200 = "User delete by id successfully";

    public static final String USER_UPDATE_STATUS_ID_OPERATION = "Update the user status by id";

    public static final String USER_UPDATE_STATUS_ID_RESPONSE_200 = "User status updated by id successfully";

    public static final String USER_UPDATE_ROLE_ID_OPERATION = "Update the user role by id";

    public static final String USER_UPDATE_ROLE_ID_RESPONSE_200 = "User role updated by id successfully";

    public static final String USER_UPDATE_OPERATION = "Update the user logged";

    public static final String USER_UPDATE_RESPONSE_200 = "User updated successfully";
}