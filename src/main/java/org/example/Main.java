package org.example;

import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;

public class Main {
    public static void main(String[] args) {
        // Настройка прокси для VPN
        System.setProperty("socksProxyHost", "127.0.0.1");
        System.setProperty("socksProxyPort", "10808");

        String botToken = "8946768794:AAEQrEfnipdURyYzawZrMBHzy5EOGeYV9p0";

        try (TelegramBotsLongPollingApplication botsApplication = new TelegramBotsLongPollingApplication()) {
            botsApplication.registerBot(botToken, new ValentineBot(botToken));
            System.out.println("Бот успешно запущен и готов к работе! 🚀");
            Thread.currentThread().join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
