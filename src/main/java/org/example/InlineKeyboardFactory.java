package org.example;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import java.util.ArrayList;
import java.util.List;

public class InlineKeyboardFactory {

    // Исправлено: Каждая инлайн кнопка теперь строго оборачивается в отдельный InlineKeyboardRow
    public static InlineKeyboardMarkup createVipShop() {
        List<InlineKeyboardRow> rows = new ArrayList<>();

        rows.add(new InlineKeyboardRow(InlineKeyboardButton.builder().text("🎟 1 день (15 ⭐)").callbackData("buy_1").build()));
        rows.add(new InlineKeyboardRow(InlineKeyboardButton.builder().text("🎟 7 дней (100 ⭐)").callbackData("buy_7").build()));
        rows.add(new InlineKeyboardRow(InlineKeyboardButton.builder().text("🎟 1 месяц (250 ⭐)").callbackData("buy_30").build()));

        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    public static InlineKeyboardMarkup createChatRating(long targetId) {
        List<InlineKeyboardRow> rows = new ArrayList<>();

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("👍 Лайк").callbackData("like_" + targetId).build(),
                InlineKeyboardButton.builder().text("👎 Дизлайк").callbackData("dislike_" + targetId).build()
        ));

        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }
}
