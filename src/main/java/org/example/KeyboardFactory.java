package org.example;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import java.util.ArrayList;
import java.util.List;

public class KeyboardFactory {
    public static ReplyKeyboardMarkup create(List<String> buttons) {
        List<KeyboardRow> rows = new ArrayList<>();
        KeyboardRow currentRow = new KeyboardRow();

        for (int i = 0; i < buttons.size(); i++) {
            currentRow.add(buttons.get(i));
            if ((i + 1) % 2 == 0 || i == buttons.size() - 1) {
                rows.add(currentRow);
                currentRow = new KeyboardRow();
            }
        }
        return ReplyKeyboardMarkup.builder().keyboard(rows).resizeKeyboard(true).build();
    }
}
