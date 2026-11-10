package org.example;

import java.time.Instant;

public class UserProfile {
    public enum RegState { NONE, SET_AGE, SET_GENDER, SET_CITY, EDIT_AGE, EDIT_CITY }

    public long chatId;
    public int age = 0;
    public String gender = "";
    public String city = "";
    public RegState regState = RegState.NONE;
    public Instant vipUntil = Instant.MIN;
    public long referrerId = 0;

    public int likes = 0;
    public int dislikes = 0;
    public Instant lastSpyUsed = Instant.MIN;

    public int getRating() {
        return likes - dislikes;
    }

    public boolean isVip() {
        return Instant.now().isBefore(vipUntil);
    }
}
