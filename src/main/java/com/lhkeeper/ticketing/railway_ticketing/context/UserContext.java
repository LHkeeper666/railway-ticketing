package com.lhkeeper.ticketing.railway_ticketing.context;

public final class UserContext {

    private static final ThreadLocal<UserInfo> USER = new ThreadLocal<>();

    private UserContext() {}

    public static void set(UserInfo user) {
        USER.set(user);
    }

    public static UserInfo get() {
        return USER.get();
    }

    public static void clear() {
        USER.remove();
    }
}
