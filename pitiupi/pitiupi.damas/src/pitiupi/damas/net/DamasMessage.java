package pitiupi.damas.net;

import pitiupi.models.Messages.Message;

public class DamasMessage extends Message {
    private String senderId;
    private String senderName;
    private String type;
    private String room;
    private String payload;

    public DamasMessage() {
    }

    public DamasMessage(String senderId, String senderName, String type, String room, String payload) {
        this.senderId = senderId;
        this.senderName = senderName;
        this.type = type;
        this.room = room;
        this.payload = payload;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getType() {
        return type;
    }

    public String getRoom() {
        return room;
    }

    public String getPayload() {
        return payload;
    }
}
