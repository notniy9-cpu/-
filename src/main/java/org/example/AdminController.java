package org.example;

import java.time.Instant;

public class AdminController {
    public static final long ADMIN_CHAT_ID = 7898900287L;

    public static String handleGiveVip(String commandText, ChatManager manager) {
        try {
            String[] parts = commandText.split(" ");
            long targetId = Long.parseLong(parts[1].trim());
            int days = Integer.parseInt(parts[2].trim());

            UserProfile target = manager.profiles.computeIfAbsent(targetId, k -> {
                UserProfile p = new UserProfile();
                p.chatId = k;
                return p;
            });

            long seconds = days * 24L * 3600L;
            target.vipUntil = Instant.now().plusSeconds(seconds);

            return "SUCCESS_" + targetId + "_" + days;
        } catch (Exception e) {
            return "ERROR";
        }
    }
}
