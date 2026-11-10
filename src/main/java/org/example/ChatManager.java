package org.example;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ChatManager {
    public final ConcurrentHashMap<Long, UserProfile> profiles = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<Long, Long> activeChats = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<Long, String> userNames = new ConcurrentHashMap<>();

    // Раздельные очереди, чтобы никто не перемешивался!
    public final ConcurrentLinkedQueue<Long> queueMale = new ConcurrentLinkedQueue<>();
    public final ConcurrentLinkedQueue<Long> queueFemale = new ConcurrentLinkedQueue<>();
    public final ConcurrentLinkedQueue<Long> queueRandom = new ConcurrentLinkedQueue<>();
    public final ConcurrentLinkedQueue<Long> queueAdult = new ConcurrentLinkedQueue<>();

    public void removeFromQueues(long chatId) {
        queueRandom.remove(chatId);
        queueAdult.remove(chatId);
        queueMale.remove(chatId);
        queueFemale.remove(chatId);
    }
}
