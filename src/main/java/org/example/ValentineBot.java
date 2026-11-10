package org.example;

import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerPreCheckoutQuery;
import org.telegram.telegrambots.meta.api.methods.invoices.SendInvoice;
import org.telegram.telegrambots.meta.api.methods.send.*;
import org.telegram.telegrambots.meta.api.methods.payments.*;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.*;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.payments.LabeledPrice;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public class ValentineBot implements LongPollingSingleThreadUpdateConsumer {

    private final TelegramClient client;
    private final ChatManager manager = new ChatManager();

    public ValentineBot(String botToken) {
        this.client = new OkHttpTelegramClient(botToken);
    }

    @Override
    public void consume(Update update) {
        if (update.hasPreCheckoutQuery()) {
            new Thread(() -> {
                try {
                    client.execute(AnswerPreCheckoutQuery.builder()
                            .preCheckoutQueryId(update.getPreCheckoutQuery().getId()).ok(true).build());
                } catch (Exception ignored) {}
            }).start();
            return;
        }
        new Thread(() -> handleUpdate(update)).start();
    }

    private void handleUpdate(Update update) {
        if (update.hasMessage() && update.getMessage().getSuccessfulPayment() != null) {
            long chatId = update.getMessage().getChatId();
            String payload = update.getMessage().getSuccessfulPayment().getInvoicePayload();
            handleRealPayment(chatId, payload);
            return;
        }

        if (update.hasCallbackQuery()) {
            handleCallbackQuery(update);
            return;
        }

        if (!update.hasMessage() || !update.getMessage().hasText()) return;

        String text = update.getMessage().getText().trim();
        long chatId = update.getMessage().getChatId();

        String userTag = null;
        if (update.getMessage().getFrom() != null) {
            userTag = update.getMessage().getFrom().getUserName();
        }

        manager.userNames.put(chatId, userTag != null ? "@" + userTag : "Скрыт");
        UserProfile user = manager.profiles.computeIfAbsent(chatId, k -> {
            UserProfile p = new UserProfile();
            p.chatId = chatId;
            return p;
        });

        if (user.regState != UserProfile.RegState.NONE) {
            processRegistrationOrEdit(user, text);
            return;
        }

        if (chatId == AdminController.ADMIN_CHAT_ID && text.startsWith("givevip ")) {
            String result = AdminController.handleGiveVip(text, manager);
            if (result.startsWith("SUCCESS_")) {
                String[] parts = result.split("_");
                sendMessage(AdminController.ADMIN_CHAT_ID, "✅ VIP выдан пользователю " + parts[1] + " на " + parts[2] + " дн.", null, null);
                sendMessage(Long.parseLong(parts[1]), "👑 <b>Вам выдан VIP-статус администратором на " + parts[2] + " дней!</b>", null, null);
            } else {
                sendMessage(AdminController.ADMIN_CHAT_ID, "❌ Ошибка! Формат: givevip [ID] [дней]", null, null);
            }
            return;
        }

        if (manager.activeChats.containsKey(chatId)) {
            long partnerId = manager.activeChats.get(chatId);
            if (text.equals("❌ Покинуть чат")) {
                closeChat(chatId, partnerId);
            } else if (text.equals("🔗 Отправить свой Юз")) {
                sendMyTag(chatId, partnerId);
            } else {
                sendMessage(partnerId, text, null, null);
                if (chatId != AdminController.ADMIN_CHAT_ID && partnerId != AdminController.ADMIN_CHAT_ID) {
                    sendMessage(AdminController.ADMIN_CHAT_ID, "👁 <b>[Лог]</b> ID " + chatId + " -> ID " + partnerId + ":\n« <i>" + text + "</i> »", null, null);
                }
            }
            return;
        }

        if (text.startsWith("/start")) {
            if (user.age != 0 && user.gender != null && !user.gender.isEmpty()) {
                openMainMenu(user);
                return;
            }
            if (text.contains(" ref")) {
                try { user.referrerId = Long.parseLong(text.split(" ref")[1].trim()); } catch (Exception ignored) {}
            }
            user.regState = UserProfile.RegState.SET_AGE;
            sendMessage(chatId, "Привет! 👋 Пожалуйста, укажи свой <b>возраст</b> (цифрами):", null, null);
            return;
        }

        if (text.contains("Случайный выбор")) { findPartner(user, "RANDOM"); }
        else if (text.contains("Флирт (18+)")) { findPartner(user, "ADULT"); }
        else if (text.contains("Парни (VIP)")) { if (!user.isVip() && chatId != AdminController.ADMIN_CHAT_ID) sendVipAlert(chatId); else findPartner(user, "MALE"); }
        else if (text.contains("Девушки (VIP)")) { if (!user.isVip() && chatId != AdminController.ADMIN_CHAT_ID) sendVipAlert(chatId); else findPartner(user, "FEMALE"); }
        else if (text.contains("VIP-Шпионаж")) { handleVipSpyCommand(user); }
        else if (text.contains("Мой Профиль / VIP")) { showProfileMenu(user); }
        else if (text.contains("Купить VIP статус")) { openVipShop(chatId); }
        else if (text.contains("Командная строка") && chatId == AdminController.ADMIN_CHAT_ID) { sendAdminConsoleInfo(); }
        else if (text.contains("Помощь")) { sendHelpMessage(chatId); }
        else if (text.contains("В главное меню")) { manager.removeFromQueues(chatId); openMainMenu(user); }
        else if (text.contains("Остановить поиск")) { manager.removeFromQueues(chatId); openMainMenu(user); }
        else if (text.contains("Изменить возраст")) { user.regState = UserProfile.RegState.EDIT_AGE; sendMessage(chatId, "Введите новый возраст:", null, null); }
        else if (text.contains("Изменить пол")) { user.gender = user.gender.equals("👨 Мужской") ? "👩 Женский" : "👨 Мужской"; sendMessage(chatId, "Пол изменен!", null, null); showProfileMenu(user); }
        else if (text.contains("Изменить город")) { user.regState = UserProfile.RegState.EDIT_CITY; sendMessage(chatId, "Введите новый город:", null, null); }
        else { sendMessage(chatId, "🤖 Пожалуйста, нажимайте кнопки на встроенной клавиатуре.", null, null); }
    }

    private void processRegistrationOrEdit(UserProfile user, String text) {
        long id = user.chatId;
        if (user.regState == UserProfile.RegState.SET_AGE || user.regState == UserProfile.RegState.EDIT_AGE) {
            try {
                int age = Integer.parseInt(text.trim());
                if (age < 12 || age > 80) { sendMessage(id, "❌ Укажите возраст (12-80):", null, null); return; }
                user.age = age;
                if (user.regState == UserProfile.RegState.EDIT_AGE) {
                    user.regState = UserProfile.RegState.NONE; sendMessage(id, "✅ Возраст изменен!", null, null); showProfileMenu(user);
                } else {
                    user.regState = UserProfile.RegState.SET_GENDER;
                    sendMessage(id, "Выберите ваш пол:", KeyboardFactory.create(List.of("👨 Мужской", "👩 Женский")), null);
                }
            } catch (Exception e) { sendMessage(id, "❌ Напишите цифрами:", null, null); }
        } else if (user.regState == UserProfile.RegState.SET_GENDER) {
            if (!text.equals("👨 Мужской") && !text.equals("👩 Женский")) return;
            user.gender = text; user.regState = UserProfile.RegState.SET_CITY;
            sendMessage(id, "Из какого вы города?:", null, null);
        } else if (user.regState == UserProfile.RegState.SET_CITY || user.regState == UserProfile.RegState.EDIT_CITY) {
            user.city = text.trim(); user.regState = UserProfile.RegState.NONE;
            sendMessage(id, "✅ Профиль сохранен!", null, null);
            if (user.referrerId != 0 && manager.profiles.containsKey(user.referrerId)) {
                UserProfile ref = manager.profiles.get(user.referrerId);
                ref.vipUntil = ref.vipUntil.isBefore(Instant.now()) ? Instant.now().plusSeconds(3600) : ref.vipUntil.plusSeconds(3600);
                sendMessage(user.referrerId, "🎁 Начислен 1 час VIP за друга!", null, null);
            }
            openMainMenu(user);
        }
    }

    // ИСПРАВЛЕНО: Полное и изолированное разделение всех комнат подбора
    private void findPartner(UserProfile user, String searchType) {
        long id = user.chatId;
        manager.removeFromQueues(id);

        Long partnerId = null;
        java.util.Queue<Long> activeQueue = null;

        if (searchType.equals("MALE")) {
            activeQueue = manager.queueMale;
            for (Long candidateId : activeQueue) {
                UserProfile candidate = manager.profiles.get(candidateId);
                if (candidate != null && candidateId != id && candidate.gender.equals("👨 Мужской")) { partnerId = candidateId; activeQueue.remove(candidateId); break; }
            }
        } else if (searchType.equals("FEMALE")) {
            activeQueue = manager.queueFemale;
            for (Long candidateId : activeQueue) {
                UserProfile candidate = manager.profiles.get(candidateId);
                if (candidate != null && candidateId != id && candidate.gender.equals("👩 Женский")) { partnerId = candidateId; activeQueue.remove(candidateId); break; }
            }
        } else if (searchType.equals("ADULT")) {
            activeQueue = manager.queueAdult;
            for (Long candidateId : activeQueue) {
                if (candidateId != id) { partnerId = candidateId; activeQueue.remove(candidateId); break; }
            }
        } else {
            activeQueue = manager.queueRandom;
            for (Long candidateId : activeQueue) {
                if (candidateId != id) { partnerId = candidateId; activeQueue.remove(candidateId); break; }
            }
        }
        if (partnerId != null) {
            manager.activeChats.put(id, partnerId);
            manager.activeChats.put(partnerId, id);
            UserProfile partner = manager.profiles.get(partnerId);
            boolean canSeeInfo = user.isVip() || id == AdminController.ADMIN_CHAT_ID;
            String toMe = "\n📊 Собеседник: " + (canSeeInfo ? partner.gender + ", " + partner.age + " лет, г. " + partner.city : "🔒 Скрыто (Только для VIP)");
            String toPartner = "\n📊 Собеседник: " + (partner.isVip() || partnerId == AdminController.ADMIN_CHAT_ID ? user.gender + ", " + user.age + " лет, г. " + user.city : "🔒 Скрыто (Только для VIP)");
            ReplyKeyboardMarkup chatKey = KeyboardFactory.create(List.of("🔗 Отправить свой Юз", "❌ Покинуть чат"));
            InlineKeyboardMarkup rateKeyMe = InlineKeyboardFactory.createChatRating(partnerId);
            InlineKeyboardMarkup rateKeyPartner = InlineKeyboardFactory.createChatRating(id);
            sendMessage(id, "🎉 Пара найдена! Режим: " + searchType + toMe + "\nВы можете оценивать собеседника кнопками в любой момент диалога:", chatKey, rateKeyMe);
            sendMessage(partnerId, "🎉 Пара найдена! Режим: " + searchType + toPartner + "\nВы можете оценивать собеседника кнопками в любой момент диалога:", chatKey, rateKeyPartner);
        } else {
            if (searchType.equals("MALE")) manager.queueMale.add(id);
            else if (searchType.equals("FEMALE")) manager.queueFemale.add(id);
            else if (searchType.equals("ADULT")) manager.queueAdult.add(id);
            else manager.queueRandom.add(id);
            sendMessage(id, "🔍 Поиск запущен в режиме: " + searchType + ". Ожидайте...", KeyboardFactory.create(List.of("❌ Остановить поиск")), null);
        }
    }
    // ИСПРАВЛЕНО: Кнопки фидбека лайк/дизлайк теперь приходят 100% при завершении чата!
    private void closeChat(long userLeft, long partnerId) {
        manager.activeChats.remove(userLeft);
        manager.activeChats.remove(partnerId);
        InlineKeyboardMarkup rateForPartner = InlineKeyboardFactory.createChatRating(userLeft);
        InlineKeyboardMarkup rateForMe = InlineKeyboardFactory.createChatRating(partnerId);
        sendMessage(partnerId, "🛑 Собеседник закончил общение!\nПожалуйста, оставьте оценку качеству диалога:", null, rateForMe);
        sendMessage(userLeft, "🛑 Вы покинули чат.\nОставьте финальную оценку вашему собеседнику:", null, rateForPartner);
        openMainMenu(manager.profiles.get(userLeft));
        openMainMenu(manager.profiles.get(partnerId));
    }
    private void handleCallbackQuery(Update update) {
        String data = update.getCallbackQuery().getData();
        if (data == null) return;
        long clickerId = update.getCallbackQuery().getMessage().getChatId();
        long messageId = update.getCallbackQuery().getMessage().getMessageId();
        if (data.startsWith("like_") || data.startsWith("dislike_")) {
            String[] parts = data.split("_");
            UserProfile target = manager.profiles.get(Long.parseLong(parts[1]));
            if (target != null) {
                if (parts[0].equals("like")) {
                    target.likes++;
                    sendMessage(clickerId, "👍 Вы поставили лайк собеседнику!", null, null);
                } else {
                    target.dislikes++;
                    sendMessage(clickerId, "👎 Вы поставили дизлайк.", null, null);
                }
            }
            try { client.execute(DeleteMessage.builder().chatId(String.valueOf(clickerId)).messageId((int)messageId).build()); } catch (Exception ignored) {}
        }
    }
    private void handleRealPayment(long chatId, String payload) {
        UserProfile user = manager.profiles.get(chatId);
        if (user == null) return;
        long seconds = payload.equals("VIP_1_DAY") ? 24 * 3600 : payload.equals("VIP_7_DAYS") ? 7 * 24 * 3600 : 30 * 24 * 3600;
        user.vipUntil = user.vipUntil.isBefore(Instant.now()) ? Instant.now().plusSeconds(seconds) : user.vipUntil.plusSeconds(seconds);
        sendMessage(chatId, "🎉 Оплата прошла успешно! Статус VIP успешно начислен на аккаунт.", null, null);
        openMainMenu(user);
    }
    private void handleVipSpyCommand(UserProfile user) {
        long chatId = user.chatId;
        if (!user.isVip() && chatId != AdminController.ADMIN_CHAT_ID) { sendMessage(chatId, "❌ Только для VIP!", null, null); openVipShop(chatId); return; }
        if (user.lastSpyUsed.isAfter(Instant.now().minus(1, ChronoUnit.DAYS))) {
            sendMessage(chatId, "⏳ Шпионаж уже использован сегодня.", null, null); return;
        }
        long p1 = 0;
        for (Map.Entry<Long, Long> entry : manager.activeChats.entrySet()) {
            if (entry.getKey() != chatId && entry.getValue() != chatId && entry.getKey() != AdminController.ADMIN_CHAT_ID) {
                p1 = entry.getKey(); break;
            }
        }
        if (p1 != 0) {
            user.lastSpyUsed = Instant.now();
            sendMessage(chatId, "🛰 [Шпионаж успешен] Частота перехвачена!", null, null);
        } else { sendMessage(chatId, "🤷‍♂️ Активных чатов сейчас нет.", null, null); }
    }
    private void openMainMenu(UserProfile user) {
        List buttons = new ArrayList<>(List.of("🎲 Случайный выбор (Все подряд)", "🔥 Флирт (18+)", "🔍 Поиск: 👨 Парни (VIP)", "🔍 Поиск: 👩 Девушки (VIP)", "👑 Включить VIP-Шпионаж (Раз в день)", "👤 Мой Профиль / VIP", "💎 Купить VIP статус", "❓ Помощь"));
        if (user.chatId == AdminController.ADMIN_CHAT_ID) buttons.add("💻 Командная строка");
        sendMessage(user.chatId, "🎭 Вы в главном меню анонимного чата:", KeyboardFactory.create(buttons), null);
    }
    private void showProfileMenu(UserProfile user) {
        String status = user.isVip() || user.chatId == AdminController.ADMIN_CHAT_ID ? "🔥 Активен" : "❌ Отключен";
        String expireDate = user.isVip() && user.chatId != AdminController.ADMIN_CHAT_ID ? DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault()).format(user.vipUntil) : "Бессрочно";
        String profileText = "👤 ВАШ ПРОФИЛЬ:\n\n• Пол: " + user.gender + "\n• Возраст: " + user.age + " лет\n• Город: " + user.city + "\n\n**👑 VIP СТАТУС:\n• Статус: " + status + "\n• До: " + expireDate + "\n\n⭐ РЕЙТИНГ:** " + user.getRating() + " (👍 " + user.likes + " / 👎 " + user.dislikes + ")\n\n**🔗 Ссылка:**\nt.me/my_anon_valentinki_bot?start=ref" + user.chatId;
        sendMessage(user.chatId, profileText, KeyboardFactory.create(List.of("⚙️ Изменить возраст", "⚙️ Изменить пол", "⚙️ Изменить город", "↩️ В главное меню")), null);
    }
    private void openVipShop(long chatId) {
        sendStarsInvoice(chatId, "VIP на 1 день", "Разблокирует фильтры поиска по полу на 24 часа", "VIP_1_DAY", 15);
        sendStarsInvoice(chatId, "VIP на 7 дней", "Разблокирует фильтры поиска по полу на неделю", "VIP_7_DAYS", 100);
        sendStarsInvoice(chatId, "VIP на 1 месяц", "Разблокирует фильтры поиска по полу на 30 дней", "VIP_1_MONTH", 250);
    }
    private void sendStarsInvoice(long chatId, String title, String desc, String payload, int stars) {
        SendInvoice invoice = SendInvoice.builder()
                .chatId(String.valueOf(chatId))
                .title(title)
                .description(desc)
                .payload(payload)
                .providerToken("")
                .currency("XTR")
                .prices(List.of(new LabeledPrice("Цена", stars)))
                .build();
        try { client.execute(invoice); } catch (TelegramApiException ignored) {}
    }
    private void sendAdminConsoleInfo() { sendMessage(AdminController.ADMIN_CHAT_ID, "💻 ТЕРМИНАЛ:\n\nВыдача VIP:\ngivevip [chatId] [дней]", null, null); }
    private void sendHelpMessage(long chatId) { sendMessage(chatId, "📖 Справка:\n/start — Меню\n/profile — Анкета\n/help — Помощь", null, null); }
    private void sendVipAlert(long chatId) { sendMessage(chatId, "⚠️ Доступно только VIP-пользователям.", null, null); openVipShop(chatId); }
    private void sendMyTag(long senderId, long receiverId) { String tag = manager.userNames.get(senderId); if (tag.equals("Скрыт")) sendMessage(senderId, "❌ Нет юзернейма!", null, null); else { sendMessage(senderId, "✅ Отправлено!", null, null); sendMessage(receiverId, "🔑 Профиль партнера: " + tag, null, null); } }
    private void sendMessage(long chatId, String text, ReplyKeyboardMarkup kb, InlineKeyboardMarkup ikb) {
        SendMessage msg = SendMessage.builder().chatId(String.valueOf(chatId)).text(text).replyMarkup(kb != null ? kb : ikb).parseMode("HTML").build();
        try { client.execute(msg); } catch (TelegramApiException ignored) {}
    }
}